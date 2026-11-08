package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates real files on the device's storage to test and benchmark the 3-stage
 * DuplicateScannerEngine even on a clean Android Emulator.
 *
 * Includes:
 * 1. Multi-copy true duplicates across simulated `/DCIM/Camera`, `/Download`, `/WhatsApp/Media`, `/Documents`
 *    spanning Images (real rendered PNGs for Coil thumbnails), Videos (.mp4), Audio (.flac/.mp3),
 *    Documents (.pdf/.json), and Archives (.zip).
 * 2. Stage-2 Decoy Files: Files with identical size (e.g. 96 KB) but different 8 KB head/tail,
 *    proving Stage 2 filters them out without computing a full hash.
 * 3. Stage-3 Decoy Files: Files > 64 KB with identical size AND identical 4 KB head + 4 KB tail,
 *    but differing bytes at offset 32 KB, proving Stage 3 catches false partial-hash collisions.
 */
object SandboxDatasetGenerator {

    suspend fun generateRealFilesOnDisk(context: Context): SandboxGenerationReport = withContext(Dispatchers.IO) {
        val baseDir = resolveWritableSandboxRoot(context)
        val dcimCamera = File(baseDir, "DCIM/Camera").apply { mkdirs() }
        val downloadDir = File(baseDir, "Download").apply { mkdirs() }
        val whatsappImg = File(baseDir, "WhatsApp/Media/WhatsApp Images").apply { mkdirs() }
        val whatsappVid = File(baseDir, "WhatsApp/Media/WhatsApp Video").apply { mkdirs() }
        val documentsDir = File(baseDir, "Documents/Backups").apply { mkdirs() }

        val now = System.currentTimeMillis()
        val dayMs = 24 * 3600 * 1000L
        var filesCreated = 0
        var totalBytesWritten = 0L

        // 1. Real Image Group A: RAW_BURST_0042.png (3 copies across DCIM, Download, WhatsApp)
        val imgBytesA = createTechnicalBitmapPngBytes(
            title = "RAW_0042",
            accentColor = Color.rgb(0, 230, 118),
            extraPaddingBytes = 148 * 1024
        )
        val imgAFiles = listOf(
            File(dcimCamera, "IMG_20261012_091422_RAW.png") to (now - 14 * dayMs),
            File(downloadDir, "IMG_20261012_091422_RAW(1).png") to (now - 5 * dayMs),
            File(whatsappImg, "IMG-20261015-WA0018.png") to (now - 2 * dayMs)
        )
        for ((target, timestamp) in imgAFiles) {
            writeBytesWithTimestamp(target, imgBytesA, timestamp)
            filesCreated++
            totalBytesWritten += imgBytesA.size
        }

        // 2. Real Image Group B: PCB_SCHEMATIC_REV4.png (2 copies across Documents & Download)
        val imgBytesB = createTechnicalBitmapPngBytes(
            title = "PCB_REV4",
            accentColor = Color.rgb(0, 176, 255),
            extraPaddingBytes = 212 * 1024
        )
        val imgBFiles = listOf(
            File(dcimCamera, "DSC_8810_SCHEMATIC.png") to (now - 21 * dayMs),
            File(downloadDir, "DSC_8810_SCHEMATIC_copy.png") to (now - 1 * dayMs)
        )
        for ((target, timestamp) in imgBFiles) {
            writeBytesWithTimestamp(target, imgBytesB, timestamp)
            filesCreated++
            totalBytesWritten += imgBytesB.size
        }

        // 3. Video Group: DRONE_TELEMETRY_4K.mp4 (3 copies, 1.2 MB each)
        val videoPayload = buildDeterministicBinaryPayload(
            sizeBytes = 1_240_000,
            headerAscii = "ftypisom0002isomiso2avc1mp41_TELEMETRY_STREAM_4K",
            seed = 0x55AA1199L
        )
        val videoFiles = listOf(
            File(dcimCamera, "VID_20261020_183000.mp4") to (now - 10 * dayMs),
            File(downloadDir, "VID_20261020_183000_dl.mp4") to (now - 4 * dayMs),
            File(whatsappVid, "VID-20261022-WA0004.mp4") to (now - 1 * dayMs)
        )
        for ((target, timestamp) in videoFiles) {
            writeBytesWithTimestamp(target, videoPayload, timestamp)
            filesCreated++
            totalBytesWritten += videoPayload.size
        }

        // 4. Audio Group: SYNTH_MASTER_24BIT.flac (2 copies, 640 KB each)
        val audioPayload = buildDeterministicBinaryPayload(
            sizeBytes = 655_360,
            headerAscii = "fLaC_24BIT_96KHZ_MASTER_BUS_RENDER",
            seed = 0x77CC3311L
        )
        val audioFiles = listOf(
            File(documentsDir, "Synth_Master_24bit_v1.flac") to (now - 18 * dayMs),
            File(downloadDir, "Synth_Master_24bit_v1 (1).flac") to (now - 3 * dayMs)
        )
        for ((target, timestamp) in audioFiles) {
            writeBytesWithTimestamp(target, audioPayload, timestamp)
            filesCreated++
            totalBytesWritten += audioPayload.size
        }

        // 5. Document Group: KERNEL_BENCHMARK_REPORT.pdf (3 copies, 420 KB each)
        val docPayload = buildDeterministicBinaryPayload(
            sizeBytes = 430_080,
            headerAscii = "%PDF-1.7\n%DUPHASH_SE_BENCHMARK_SPEC_2026",
            seed = 0x99EE4422L
        )
        val docFiles = listOf(
            File(documentsDir, "Kernel_IO_Benchmark_2026.pdf") to (now - 30 * dayMs),
            File(downloadDir, "Kernel_IO_Benchmark_2026.pdf") to (now - 7 * dayMs),
            File(downloadDir, "Kernel_IO_Benchmark_2026(1).pdf") to (now - 6 * hourMs())
        )
        for ((target, timestamp) in docFiles) {
            writeBytesWithTimestamp(target, docPayload, timestamp)
            filesCreated++
            totalBytesWritten += docPayload.size
        }

        // 6. Archive Group: PROD_SYMBOLS_ARM64.zip (2 copies, 890 KB each)
        val zipPayload = buildDeterministicBinaryPayload(
            sizeBytes = 911_360,
            headerAscii = "PK\u0003\u0004_ARM64_V8A_NATIVE_DEBUG_SYMBOLS",
            seed = 0x13572468L
        )
        val zipFiles = listOf(
            File(documentsDir, "prod-symbols-arm64-v8a.zip") to (now - 12 * dayMs),
            File(downloadDir, "prod-symbols-arm64-v8a-backup.zip") to (now - 2 * dayMs)
        )
        for ((target, timestamp) in zipFiles) {
            writeBytesWithTimestamp(target, zipPayload, timestamp)
            filesCreated++
            totalBytesWritten += zipPayload.size
        }

        // 7. Funnel Decoy Pair A (Same Size 96 KB, Different 8 KB Head/Tail -> Filtered out in Stage 2!)
        val decoyStage2A = buildDeterministicBinaryPayload(98_304, "DECOY_STAGE2_ALPHA", 101L)
        val decoyStage2B = buildDeterministicBinaryPayload(98_304, "DECOY_STAGE2_BETA_", 202L)
        writeBytesWithTimestamp(File(documentsDir, "telemetry_dump_nodeA.bin"), decoyStage2A, now - 8 * dayMs)
        writeBytesWithTimestamp(File(documentsDir, "telemetry_dump_nodeB.bin"), decoyStage2B, now - 8 * dayMs)
        filesCreated += 2
        totalBytesWritten += decoyStage2A.size * 2

        // 8. Funnel Decoy Pair B (Same Size 128 KB AND Identical 4KB Head + 4KB Tail, but different middle at 64KB -> Filtered out in Stage 3!)
        val decoyStage3Base = buildDeterministicBinaryPayload(131_072, "DECOY_STAGE3_SAME_HEAD_TAIL", 777L)
        val decoyStage3Modified = decoyStage3Base.clone().also { bytes ->
            // Mutate bytes strictly in the middle (offset 65536), outside first 4096 and last 4096 bytes
            for (i in 65536 until 65600) {
                bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
            }
        }
        writeBytesWithTimestamp(File(documentsDir, "firmware_image_slot_a.img"), decoyStage3Base, now - 9 * dayMs)
        writeBytesWithTimestamp(File(documentsDir, "firmware_image_slot_b.img"), decoyStage3Modified, now - 9 * dayMs)
        filesCreated += 2
        totalBytesWritten += decoyStage3Base.size * 2

        SandboxGenerationReport(
            rootPath = baseDir.absolutePath,
            filesCreated = filesCreated,
            totalBytesWritten = totalBytesWritten
        )
    }

    fun resolveScanRoots(context: Context): List<File> {
        val roots = ArrayList<File>()
        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        if (hasAllFilesAccess) {
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot.exists() && extRoot.canRead()) {
                roots.add(extRoot)
            }
        }

        // Always include the sandbox directory inside app files if external root isn't accessible yet
        val localSandbox = File(context.filesDir, "storage_sandbox")
        if (localSandbox.exists()) {
            roots.add(localSandbox)
        }
        return roots.distinctBy { it.absolutePath }
    }

    private fun resolveWritableSandboxRoot(context: Context): File {
        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasAllFilesAccess) {
            val publicRoot = File(Environment.getExternalStorageDirectory(), "DupHashLab")
            if (publicRoot.exists() || publicRoot.mkdirs()) {
                return publicRoot
            }
        }
        return File(context.filesDir, "storage_sandbox").apply { mkdirs() }
    }

    private fun createTechnicalBitmapPngBytes(
        title: String,
        accentColor: Int,
        extraPaddingBytes: Int
    ): ByteArray {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(16, 16, 16))

        val gridPaint = Paint().apply {
            color = Color.rgb(42, 42, 42)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        for (i in 0..160 step 20) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), 160f, gridPaint)
            canvas.drawLine(0f, i.toFloat(), 160f, i.toFloat(), gridPaint)
        }

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(18f, 18f, 142f, 142f, accentPaint)
        canvas.drawLine(18f, 142f, 142f, 18f, accentPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            isFakeBoldText = true
        }
        canvas.drawText(title, 26f, 88f, textPaint)

        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        val pngCore = out.toByteArray()

        // Append deterministic trailer bytes so image size is realistic for MB/s benchmarks
        val combined = ByteArray(pngCore.size + extraPaddingBytes)
        System.arraycopy(pngCore, 0, combined, 0, pngCore.size)
        var state = title.hashCode().toLong()
        for (i in pngCore.size until combined.size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            combined[i] = (state ushr 24).toByte()
        }
        return combined
    }

    private fun buildDeterministicBinaryPayload(
        sizeBytes: Int,
        headerAscii: String,
        seed: Long
    ): ByteArray {
        val bytes = ByteArray(sizeBytes)
        val header = headerAscii.encodeToByteArray()
        val copyLen = minOf(header.size, sizeBytes)
        System.arraycopy(header, 0, bytes, 0, copyLen)

        var state = seed
        for (i in copyLen until sizeBytes) {
            state = state * 2862933555777941757L + 3037000493L
            bytes[i] = (state ushr 32).toByte()
        }
        return bytes
    }

    private fun writeBytesWithTimestamp(file: File, bytes: ByteArray, lastModifiedMs: Long) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.fd.sync()
        }
        RandomAccessFile(file, "rw").close()
        file.setLastModified(lastModifiedMs)
    }

    private fun hourMs(): Long = 3600 * 1000L
}

data class SandboxGenerationReport(
    val rootPath: String,
    val filesCreated: Int,
    val totalBytesWritten: Long
)
