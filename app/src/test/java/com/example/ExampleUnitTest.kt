package com.example

import com.example.domain.DuplicateFileItem
import com.example.domain.DuplicateGroup
import com.example.domain.FileCategory
import com.example.domain.HashUtils
import com.example.domain.SmartSelectionStrategy
import com.example.domain.SmartSelectionUseCase
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun stage2AndStage3_detectsMiddleByteMutationWhileMatchingPartialHash() {
        val tempDir = createTempDir("duphash_test")
        try {
            val size = 32 * 1024 // 32 KB (> 8 KB)
            val baseBytes = ByteArray(size) { (it % 251).toByte() }
            val mutatedMiddleBytes = baseBytes.clone().also {
                // Mutate strictly in the middle (offset 16384), outside first 4096 and last 4096 bytes
                it[16384] = (it[16384].toInt() xor 0xFF).toByte()
            }

            val fileA = File(tempDir, "a.bin").apply { writeBytes(baseBytes) }
            val fileB = File(tempDir, "b.bin").apply { writeBytes(mutatedMiddleBytes) }
            val fileC = File(tempDir, "c.bin").apply { writeBytes(baseBytes) }

            val pHashA = HashUtils.computePartialHash8KB(fileA, size.toLong())
            val pHashB = HashUtils.computePartialHash8KB(fileB, size.toLong())
            val pHashC = HashUtils.computePartialHash8KB(fileC, size.toLong())

            // Stage 2 partial hash (first 4KB + last 4KB) matches for A, B, and C
            assertEquals(pHashA, pHashB)
            assertEquals(pHashA, pHashC)

            // Stage 3 full 128-bit hash separates B while confirming A == C
            val fullA = HashUtils.computeFullHash128(fileA, size.toLong())
            val fullB = HashUtils.computeFullHash128(fileB, size.toLong())
            val fullC = HashUtils.computeFullHash128(fileC, size.toLong())

            assertEquals(fullA, fullC)
            assertNotEquals(fullA, fullB)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun smartSelectionUseCase_alwaysPreservesAtLeastOneOriginalCopy() {
        val useCase = SmartSelectionUseCase()
        val files = listOf(
            DuplicateFileItem(
                path = "/storage/emulated/0/Download/report_oldest.pdf",
                name = "report_oldest.pdf",
                parentDir = "/storage/emulated/0/Download",
                sizeBytes = 102400L,
                lastModified = 1000L,
                extension = "pdf",
                category = FileCategory.DOCUMENTS,
                partialHash = 123L,
                fullHash = "ABCDEF"
            ),
            DuplicateFileItem(
                path = "/storage/emulated/0/Download/report_middle.pdf",
                name = "report_middle.pdf",
                parentDir = "/storage/emulated/0/Download",
                sizeBytes = 102400L,
                lastModified = 2000L,
                extension = "pdf",
                category = FileCategory.DOCUMENTS,
                partialHash = 123L,
                fullHash = "ABCDEF"
            ),
            DuplicateFileItem(
                path = "/storage/emulated/0/Download/report_newest.pdf",
                name = "report_newest.pdf",
                parentDir = "/storage/emulated/0/Download",
                sizeBytes = 102400L,
                lastModified = 3000L,
                extension = "pdf",
                category = FileCategory.DOCUMENTS,
                partialHash = 123L,
                fullHash = "ABCDEF"
            )
        )
        val group = DuplicateGroup(
            hash = "102400_ABCDEF",
            fullHashHex = "ABCDEF",
            partialHashHex = "000000000000007B",
            fileSizeBytes = 102400L,
            files = files,
            category = FileCategory.DOCUMENTS
        )

        // KeepOldest keeps 1000L unselected, selects 2000L & 3000L
        val keepOldestSelection = useCase.applyStrategy(
            groups = listOf(group),
            currentSelectedPaths = emptySet(),
            strategy = SmartSelectionStrategy.KeepOldest
        )
        assertEquals(2, keepOldestSelection.size)
        assertFalse("/storage/emulated/0/Download/report_oldest.pdf" in keepOldestSelection)

        // KeepNewest keeps 3000L unselected, selects 1000L & 2000L
        val keepNewestSelection = useCase.applyStrategy(
            groups = listOf(group),
            currentSelectedPaths = emptySet(),
            strategy = SmartSelectionStrategy.KeepNewest
        )
        assertEquals(2, keepNewestSelection.size)
        assertFalse("/storage/emulated/0/Download/report_newest.pdf" in keepNewestSelection)

        // SelectByFolder("/storage/emulated/0/Download") when ALL copies are in /Download
        // MUST preserve 1 original copy (Safety Guardrail)
        val folderSelection = useCase.applyStrategy(
            groups = listOf(group),
            currentSelectedPaths = emptySet(),
            strategy = SmartSelectionStrategy.SelectByFolder("/storage/emulated/0/Download")
        )
        assertEquals(2, folderSelection.size)
        assertFalse(useCase.isGroupUnsafelyAllSelected(group, folderSelection))
        assertTrue(
            useCase.isGroupUnsafelyAllSelected(
                group,
                files.map { it.path }.toSet()
            )
        )
    }
}
