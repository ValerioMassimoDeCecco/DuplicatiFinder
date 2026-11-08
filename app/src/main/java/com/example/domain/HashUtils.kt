package com.example.domain

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * High-performance native Kotlin implementation of xxHash64 (Stage 2 8KB Partial Hash)
 * and XXH3-128 / Dual-Lane 128-bit xxHash (Stage 3 64KB Buffered Full Hash).
 *
 * Designed for zero-JNI portability and maximum I/O throughput using direct NIO FileChannel
 * and Little-Endian 64-bit word reads.
 */
object HashUtils {

    private const val PRIME64_1: ULong = 11400714785074694791uL
    private const val PRIME64_2: ULong = 14029467366897019727uL
    private const val PRIME64_3: ULong = 1609587929392839161uL
    private const val PRIME64_4: ULong = 9650029242287828579uL
    private const val PRIME64_5: ULong = 2870177450012600261uL

    const val PARTIAL_CHUNK_SIZE = 4096 // 4 KB head + 4 KB tail = 8 KB total
    const val FULL_HASH_BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    /**
     * Stage 2: Computes an ultra-fast 64-bit xxHash64 over the first 4096 bytes and
     * last 4096 bytes of [file] using RandomAccessFile + FileChannel.
     *
     * For files <= 8192 bytes, hashes the entire file content directly.
     */
    fun computePartialHash8KB(file: File, fileSize: Long): Long {
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            if (fileSize <= PARTIAL_CHUNK_SIZE * 2L) {
                val size = fileSize.toInt()
                val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                readFully(channel, buffer, 0L)
                buffer.flip()
                return xxHash64(buffer, seed = fileSize.toULong()).toLong()
            } else {
                val combined = ByteBuffer.allocate(PARTIAL_CHUNK_SIZE * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)

                val headSlice = combined.slice().apply { limit(PARTIAL_CHUNK_SIZE) }
                readFully(channel, headSlice, 0L)

                combined.position(PARTIAL_CHUNK_SIZE)
                val tailSlice = combined.slice().apply { limit(PARTIAL_CHUNK_SIZE) }
                readFully(channel, tailSlice, fileSize - PARTIAL_CHUNK_SIZE)

                combined.position(0)
                combined.limit(PARTIAL_CHUNK_SIZE * 2)
                return xxHash64(combined, seed = fileSize.toULong()).toLong()
            }
        }
    }

    /**
     * Stage 3: Computes a 128-bit full hash (XXH128 / dual-seeded xxHash64 lanes)
     * reading in 64 KB buffers via FileChannel.
     *
     * @param onBytesRead Callback invoked after each 64 KB buffer read to track real-time MB/s throughput.
     */
    fun computeFullHash128(
        file: File,
        fileSize: Long,
        isCancelled: () -> Boolean = { false },
        onBytesRead: (Long) -> Unit = {}
    ): String {
        val stateLow = StreamingXxHash64(seed = 0uL)
        val stateHigh = StreamingXxHash64(seed = PRIME64_1 xor fileSize.toULong())

        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            val buffer = ByteBuffer.allocateDirect(FULL_HASH_BUFFER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)

            while (true) {
                if (isCancelled()) return ""
                buffer.clear()
                val bytesRead = channel.read(buffer)
                if (bytesRead <= 0) break

                buffer.flip()
                stateLow.update(buffer)
                buffer.position(0)
                stateHigh.update(buffer)

                onBytesRead(bytesRead.toLong())
            }
        }

        val low64 = stateLow.digest()
        val high64 = stateHigh.digest()
        return format128Hex(high64, low64)
    }

    private fun readFully(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var offset = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, offset)
            if (read <= 0) break
            offset += read
        }
    }

    /**
     * Pure Kotlin xxHash64 implementation operating directly on a Little-Endian ByteBuffer.
     */
    fun xxHash64(buffer: ByteBuffer, seed: ULong = 0uL): ULong {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.remaining()
        var h64: ULong

        if (length >= 32) {
            var v1 = seed + PRIME64_1 + PRIME64_2
            var v2 = seed + PRIME64_2
            var v3 = seed + 0uL
            var v4 = seed - PRIME64_1

            while (buffer.remaining() >= 32) {
                v1 = round(v1, buffer.long.toULong())
                v2 = round(v2, buffer.long.toULong())
                v3 = round(v3, buffer.long.toULong())
                v4 = round(v4, buffer.long.toULong())
            }

            h64 = v1.rotateLeft(1) + v2.rotateLeft(7) + v3.rotateLeft(12) + v4.rotateLeft(18)
            h64 = mergeRound(h64, v1)
            h64 = mergeRound(h64, v2)
            h64 = mergeRound(h64, v3)
            h64 = mergeRound(h64, v4)
        } else {
            h64 = seed + PRIME64_5
        }

        h64 += length.toULong()

        while (buffer.remaining() >= 8) {
            val k1 = round(0uL, buffer.long.toULong())
            h64 = h64 xor k1
            h64 = h64.rotateLeft(27) * PRIME64_1 + PRIME64_4
        }

        if (buffer.remaining() >= 4) {
            val k1 = (buffer.int.toLong() and 0xFFFFFFFFL).toULong()
            h64 = h64 xor (k1 * PRIME64_1)
            h64 = h64.rotateLeft(23) * PRIME64_2 + PRIME64_3
        }

        while (buffer.hasRemaining()) {
            val k1 = (buffer.get().toInt() and 0xFF).toULong()
            h64 = h64 xor (k1 * PRIME64_5)
            h64 = h64.rotateLeft(11) * PRIME64_1
        }

        return avalanche(h64)
    }

    /**
     * Streaming xxHash64 state machine for 64 KB buffer processing without allocating arrays per read.
     */
    private class StreamingXxHash64(private val seed: ULong) {
        private var v1: ULong = seed + PRIME64_1 + PRIME64_2
        private var v2: ULong = seed + PRIME64_2
        private var v3: ULong = seed + 0uL
        private var v4: ULong = seed - PRIME64_1
        private var totalLen: ULong = 0uL
        private val tailBuffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)

        fun update(input: ByteBuffer) {
            val len = input.remaining()
            if (len == 0) return
            totalLen += len.toULong()

            if (tailBuffer.position() > 0) {
                val needed = 32 - tailBuffer.position()
                if (len < needed) {
                    tailBuffer.put(input)
                    return
                }
                val oldLimit = input.limit()
                input.limit(input.position() + needed)
                tailBuffer.put(input)
                input.limit(oldLimit)

                tailBuffer.flip()
                v1 = round(v1, tailBuffer.long.toULong())
                v2 = round(v2, tailBuffer.long.toULong())
                v3 = round(v3, tailBuffer.long.toULong())
                v4 = round(v4, tailBuffer.long.toULong())
                tailBuffer.clear()
            }

            while (input.remaining() >= 32) {
                v1 = round(v1, input.long.toULong())
                v2 = round(v2, input.long.toULong())
                v3 = round(v3, input.long.toULong())
                v4 = round(v4, input.long.toULong())
            }

            if (input.hasRemaining()) {
                tailBuffer.put(input)
            }
        }

        fun digest(): ULong {
            var h64 = if (totalLen >= 32uL) {
                var acc = v1.rotateLeft(1) + v2.rotateLeft(7) + v3.rotateLeft(12) + v4.rotateLeft(18)
                acc = mergeRound(acc, v1)
                acc = mergeRound(acc, v2)
                acc = mergeRound(acc, v3)
                acc = mergeRound(acc, v4)
                acc
            } else {
                seed + PRIME64_5
            }

            h64 += totalLen
            tailBuffer.flip()

            while (tailBuffer.remaining() >= 8) {
                val k1 = round(0uL, tailBuffer.long.toULong())
                h64 = h64 xor k1
                h64 = h64.rotateLeft(27) * PRIME64_1 + PRIME64_4
            }

            if (tailBuffer.remaining() >= 4) {
                val k1 = (tailBuffer.int.toLong() and 0xFFFFFFFFL).toULong()
                h64 = h64 xor (k1 * PRIME64_1)
                h64 = h64.rotateLeft(23) * PRIME64_2 + PRIME64_3
            }

            while (tailBuffer.hasRemaining()) {
                val k1 = (tailBuffer.get().toInt() and 0xFF).toULong()
                h64 = h64 xor (k1 * PRIME64_5)
                h64 = h64.rotateLeft(11) * PRIME64_1
            }

            return avalanche(h64)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun round(acc: ULong, input: ULong): ULong {
        var res = acc + (input * PRIME64_2)
        res = res.rotateLeft(31)
        res *= PRIME64_1
        return res
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun mergeRound(acc: ULong, value: ULong): ULong {
        val valRound = round(0uL, value)
        var res = acc xor valRound
        res = res * PRIME64_1 + PRIME64_4
        return res
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun avalanche(h: ULong): ULong {
        var res = h xor (h shr 33)
        res *= PRIME64_2
        res = res xor (res shr 29)
        res *= PRIME64_3
        res = res xor (res shr 32)
        return res
    }

    private fun format128Hex(high: ULong, low: ULong): String {
        val highHex = high.toLong().toULong().toString(16).padStart(16, '0')
        val lowHex = low.toLong().toULong().toString(16).padStart(16, '0')
        return (highHex + lowHex).uppercase()
    }
}
