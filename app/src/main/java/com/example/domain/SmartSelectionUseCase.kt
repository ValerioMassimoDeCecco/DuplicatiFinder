package com.example.domain

/**
 * Smart Auto-Selection UseCase.
 *
 * Computes the set of file paths to mark for deletion across duplicate groups while
 * strictly enforcing the Safety Guardrail: at least 1 original copy MUST always remain
 * unselected in every duplicate group.
 */
class SmartSelectionUseCase {

    /**
     * Applies [strategy] across [groups] and returns the new set of selected file paths.
     *
     * Guaranteed invariant: For every group in [groups], at most `group.files.size - 1`
     * files will be selected by any automatic strategy.
     */
    fun applyStrategy(
        groups: List<DuplicateGroup>,
        currentSelectedPaths: Set<String>,
        strategy: SmartSelectionStrategy
    ): Set<String> {
        return when (strategy) {
            is SmartSelectionStrategy.DeselectAll -> emptySet()

            is SmartSelectionStrategy.KeepOldest -> {
                val selected = HashSet<String>(currentSelectedPaths)
                for (group in groups) {
                    if (group.files.size < 2) continue
                    // Remove any previous selection for this group first
                    group.files.forEach { selected.remove(it.path) }

                    // Sort by ascending lastModified (oldest first); tie-break by shortest path
                    val sorted = group.files.sortedWith(
                        compareBy<DuplicateFileItem> { it.lastModified }
                            .thenBy { it.path.length }
                            .thenBy { it.path }
                    )
                    // Keep index 0 (oldest), select all subsequent copies
                    for (i in 1 until sorted.size) {
                        selected.add(sorted[i].path)
                    }
                }
                selected
            }

            is SmartSelectionStrategy.KeepNewest -> {
                val selected = HashSet<String>(currentSelectedPaths)
                for (group in groups) {
                    if (group.files.size < 2) continue
                    group.files.forEach { selected.remove(it.path) }

                    // Sort by descending lastModified (newest first); tie-break by shortest path
                    val sorted = group.files.sortedWith(
                        compareByDescending<DuplicateFileItem> { it.lastModified }
                            .thenBy { it.path.length }
                            .thenBy { it.path }
                    )
                    // Keep index 0 (newest), select all older copies
                    for (i in 1 until sorted.size) {
                        selected.add(sorted[i].path)
                    }
                }
                selected
            }

            is SmartSelectionStrategy.SelectByFolder -> {
                val normalizedFolder = strategy.folderPath.trimEnd('/')
                val selected = HashSet<String>(currentSelectedPaths)

                for (group in groups) {
                    if (group.files.size < 2) continue
                    group.files.forEach { selected.remove(it.path) }

                    val matchingInFolder = group.files.filter { file ->
                        file.parentDir.equals(normalizedFolder, ignoreCase = true) ||
                            file.path.startsWith("$normalizedFolder/", ignoreCase = true) ||
                            file.parentDir.endsWith(normalizedFolder, ignoreCase = true)
                    }

                    if (matchingInFolder.isEmpty()) continue

                    if (matchingInFolder.size < group.files.size) {
                        // Safe to select all copies in target folder because at least 1 copy exists outside it
                        matchingInFolder.forEach { selected.add(it.path) }
                    } else {
                        // SAFETY GUARDRAIL: All copies of this group live inside the target folder!
                        // Preserve the oldest copy and select the remaining (size - 1) copies.
                        val sorted = matchingInFolder.sortedWith(
                            compareBy<DuplicateFileItem> { it.lastModified }.thenBy { it.path }
                        )
                        for (i in 1 until sorted.size) {
                            selected.add(sorted[i].path)
                        }
                    }
                }
                selected
            }
        }
    }

    /**
     * Extracts all distinct parent folders across [groups] sorted by how many duplicate
     * files they contain descending, enabling quick "Seleziona per cartella..." picking.
     */
    fun extractCandidateFolders(groups: List<DuplicateGroup>): List<FolderCandidate> {
        val folderCounts = HashMap<String, Int>()
        val folderBytes = HashMap<String, Long>()

        for (group in groups) {
            for (file in group.files) {
                folderCounts[file.parentDir] = (folderCounts[file.parentDir] ?: 0) + 1
                folderBytes[file.parentDir] = (folderBytes[file.parentDir] ?: 0L) + file.sizeBytes
            }
        }

        return folderCounts.entries
            .map { (folder, count) ->
                FolderCandidate(
                    folderPath = folder,
                    duplicateFileCount = count,
                    totalBytesInFolder = folderBytes[folder] ?: 0L
                )
            }
            .sortedWith(
                compareByDescending<FolderCandidate> { it.duplicateFileCount }
                    .thenByDescending { it.totalBytesInFolder }
            )
    }

    /**
     * Returns true if the user has checked EVERY copy in [group], leaving 0 originals.
     */
    fun isGroupUnsafelyAllSelected(group: DuplicateGroup, selectedPaths: Set<String>): Boolean {
        return group.files.isNotEmpty() && group.files.all { it.path in selectedPaths }
    }

    /**
     * Enforces safety across all groups by unselecting the oldest copy in any group where
     * all copies were manually selected.
     */
    fun enforceAtLeastOneOriginalPerGroup(
        groups: List<DuplicateGroup>,
        selectedPaths: Set<String>
    ): Set<String> {
        val safeSelected = HashSet(selectedPaths)
        for (group in groups) {
            if (isGroupUnsafelyAllSelected(group, safeSelected)) {
                val oldest = group.files.minWithOrNull(
                    compareBy<DuplicateFileItem> { it.lastModified }.thenBy { it.path }
                )
                if (oldest != null) {
                    safeSelected.remove(oldest.path)
                }
            }
        }
        return safeSelected
    }
}

data class FolderCandidate(
    val folderPath: String,
    val duplicateFileCount: Int,
    val totalBytesInFolder: Long
)
