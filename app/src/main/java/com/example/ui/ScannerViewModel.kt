package com.example.ui

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.HashCacheRepository
import com.example.domain.DeletionResult
import com.example.domain.DuplicateFileItem
import com.example.domain.DuplicateGroup
import com.example.domain.DuplicateScannerEngine
import com.example.domain.ExclusionConfig
import com.example.domain.FileCategory
import com.example.domain.FileDeletionManager
import com.example.domain.FolderCandidate
import com.example.domain.SandboxDatasetGenerator
import com.example.domain.ScanProgress
import com.example.domain.SmartSelectionStrategy
import com.example.domain.SmartSelectionUseCase
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ScannerUiState(
    val hasAllFilesPermission: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val allGroups: List<DuplicateGroup> = emptyList(),
    val filteredGroups: List<DuplicateGroup> = emptyList(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val searchQuery: String = "",
    val selectedPaths: Set<String> = emptySet(),
    val selectedFilesCount: Int = 0,
    val selectedBytesToReclaim: Long = 0L,
    val unsafeAllSelectedGroupHashes: Set<String> = emptySet(),
    val exclusionConfig: ExclusionConfig = ExclusionConfig(),
    val cachedHashesCount: Int = 0,
    val candidateFolders: List<FolderCandidate> = emptyList(),
    val showFolderSelectionSheet: Boolean = false,
    val showExclusionsDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val deletionProgressPair: Pair<Int, Int>? = null,
    val lastDeletionResult: DeletionResult? = null,
    val statusBannerMessage: String? = null,
    val inspectFileItem: DuplicateFileItem? = null
)

sealed interface ScannerUiEvent {
    data object RefreshPermissionState : ScannerUiEvent
    data object StartScan : ScannerUiEvent
    data object CancelScan : ScannerUiEvent
    data object SeedSandboxAndScan : ScannerUiEvent
    data object ClearRoomHashCache : ScannerUiEvent
    data class SelectCategory(val category: FileCategory) : ScannerUiEvent
    data class UpdateSearchQuery(val query: String) : ScannerUiEvent
    data class ToggleFileSelection(val path: String) : ScannerUiEvent
    data class ApplySmartSelection(val strategy: SmartSelectionStrategy) : ScannerUiEvent
    data object EnforceSafetyGuardrail : ScannerUiEvent
    data class ToggleFolderSheet(val show: Boolean) : ScannerUiEvent
    data class ToggleExclusionsDialog(val show: Boolean) : ScannerUiEvent
    data class AddExcludedPathSegment(val segment: String) : ScannerUiEvent
    data class RemoveExcludedPathSegment(val segment: String) : ScannerUiEvent
    data class ToggleSkipHiddenFiles(val skip: Boolean) : ScannerUiEvent
    data object RequestDeleteConfirmation : ScannerUiEvent
    data object DismissDeleteConfirmation : ScannerUiEvent
    data object ConfirmBatchDelete : ScannerUiEvent
    data object DismissDeletionResult : ScannerUiEvent
    data object DismissStatusBanner : ScannerUiEvent
    data class InspectFileDetails(val item: DuplicateFileItem?) : ScannerUiEvent
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val hashCacheRepository = HashCacheRepository(database.fileHashCacheDao())
    private val scannerEngine = DuplicateScannerEngine(hashCacheRepository)
    private val smartSelectionUseCase = SmartSelectionUseCase()
    private val fileDeletionManager = FileDeletionManager(application, hashCacheRepository)

    private val internalState = MutableStateFlow(InternalViewModelState())
    private var scanJob: Job? = null

    val uiState: StateFlow<ScannerUiState> = combine(
        internalState,
        scannerEngine.scanProgress,
        hashCacheRepository.cachedCountFlow
    ) { state, progress, cachedCount ->
        val filtered = filterGroups(
            groups = state.allGroups,
            category = state.selectedCategory,
            query = state.searchQuery
        )

        val categoryCounts = HashMap<FileCategory, Int>()
        categoryCounts[FileCategory.ALL] = state.allGroups.size
        for (cat in FileCategory.entries) {
            if (cat != FileCategory.ALL) {
                categoryCounts[cat] = state.allGroups.count { it.category == cat }
            }
        }

        // Calculate selected files & bytes across all groups
        var selectedCount = 0
        var selectedBytes = 0L
        val unsafeGroupHashes = HashSet<String>()

        for (group in state.allGroups) {
            var selectedInGroup = 0
            for (file in group.files) {
                if (file.path in state.selectedPaths) {
                    selectedCount++
                    selectedBytes += file.sizeBytes
                    selectedInGroup++
                }
            }
            if (group.files.isNotEmpty() && selectedInGroup == group.files.size) {
                unsafeGroupHashes.add(group.hash)
            }
        }

        val folderCandidates = smartSelectionUseCase.extractCandidateFolders(filtered)

        ScannerUiState(
            hasAllFilesPermission = state.hasAllFilesPermission,
            scanProgress = progress,
            allGroups = state.allGroups,
            filteredGroups = filtered,
            selectedCategory = state.selectedCategory,
            categoryCounts = categoryCounts,
            searchQuery = state.searchQuery,
            selectedPaths = state.selectedPaths,
            selectedFilesCount = selectedCount,
            selectedBytesToReclaim = selectedBytes,
            unsafeAllSelectedGroupHashes = unsafeGroupHashes,
            exclusionConfig = state.exclusionConfig,
            cachedHashesCount = cachedCount,
            candidateFolders = folderCandidates,
            showFolderSelectionSheet = state.showFolderSelectionSheet,
            showExclusionsDialog = state.showExclusionsDialog,
            showDeleteConfirmDialog = state.showDeleteConfirmDialog,
            isDeleting = state.isDeleting,
            deletionProgressPair = state.deletionProgressPair,
            lastDeletionResult = state.lastDeletionResult,
            statusBannerMessage = state.statusBannerMessage,
            inspectFileItem = state.inspectFileItem
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScannerUiState(hasAllFilesPermission = checkAllFilesPermission())
    )

    init {
        refreshPermissionAndInitialScan()
    }

    private fun refreshPermissionAndInitialScan() {
        val granted = checkAllFilesPermission()
        internalState.update { it.copy(hasAllFilesPermission = granted) }

        viewModelScope.launch {
            val roots = SandboxDatasetGenerator.resolveScanRoots(getApplication())
            val hasExistingSandbox = roots.any { root ->
                File(root, "DCIM/Camera/IMG_20261012_091422_RAW.png").exists() ||
                    File(root, "DupHashLab").exists()
            }
            if (!hasExistingSandbox) {
                // Seed real duplicate files on disk so the user can immediately benchmark
                // the 3-stage scanner even on a fresh browser emulator
                SandboxDatasetGenerator.generateRealFilesOnDisk(getApplication())
            }
            startScanInternal()
        }
    }

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            is ScannerUiEvent.RefreshPermissionState -> {
                val prev = internalState.value.hasAllFilesPermission
                val now = checkAllFilesPermission()
                internalState.update { it.copy(hasAllFilesPermission = now) }
                if (!prev && now) {
                    startScanInternal()
                }
            }

            is ScannerUiEvent.StartScan -> startScanInternal()

            is ScannerUiEvent.CancelScan -> {
                scannerEngine.requestCancel()
                scanJob?.cancel()
            }

            is ScannerUiEvent.SeedSandboxAndScan -> {
                scanJob?.cancel()
                scanJob = viewModelScope.launch {
                    val report = SandboxDatasetGenerator.generateRealFilesOnDisk(getApplication())
                    internalState.update {
                        it.copy(
                            statusBannerMessage = "Creato dataset reale su disco: ${report.filesCreated} file (${FormatUtils.formatBytes(report.totalBytesWritten)}) in ${report.rootPath}"
                        )
                    }
                    startScanInternal()
                }
            }

            is ScannerUiEvent.ClearRoomHashCache -> {
                viewModelScope.launch {
                    hashCacheRepository.clearAllCache()
                    internalState.update {
                        it.copy(statusBannerMessage = "Cache Room FileHashCacheEntity svuotata.")
                    }
                }
            }

            is ScannerUiEvent.SelectCategory -> {
                internalState.update { it.copy(selectedCategory = event.category) }
            }

            is ScannerUiEvent.UpdateSearchQuery -> {
                internalState.update { it.copy(searchQuery = event.query) }
            }

            is ScannerUiEvent.ToggleFileSelection -> {
                internalState.update { state ->
                    val next = HashSet(state.selectedPaths)
                    if (!next.add(event.path)) {
                        next.remove(event.path)
                    }
                    state.copy(selectedPaths = next)
                }
            }

            is ScannerUiEvent.ApplySmartSelection -> {
                internalState.update { state ->
                    val targetGroups = filterGroups(
                        groups = state.allGroups,
                        category = state.selectedCategory,
                        query = state.searchQuery
                    )
                    val updatedPaths = smartSelectionUseCase.applyStrategy(
                        groups = targetGroups,
                        currentSelectedPaths = state.selectedPaths,
                        strategy = event.strategy
                    )
                    state.copy(
                        selectedPaths = updatedPaths,
                        showFolderSelectionSheet = false
                    )
                }
            }

            is ScannerUiEvent.EnforceSafetyGuardrail -> {
                internalState.update { state ->
                    val safePaths = smartSelectionUseCase.enforceAtLeastOneOriginalPerGroup(
                        groups = state.allGroups,
                        selectedPaths = state.selectedPaths
                    )
                    state.copy(
                        selectedPaths = safePaths,
                        statusBannerMessage = "Safety Guardrail applicato: preservata almeno 1 copia originale per ogni gruppo."
                    )
                }
            }

            is ScannerUiEvent.ToggleFolderSheet -> {
                internalState.update { it.copy(showFolderSelectionSheet = event.show) }
            }

            is ScannerUiEvent.ToggleExclusionsDialog -> {
                internalState.update { it.copy(showExclusionsDialog = event.show) }
            }

            is ScannerUiEvent.AddExcludedPathSegment -> {
                val trimmed = event.segment.trim()
                if (trimmed.isNotEmpty()) {
                    internalState.update { state ->
                        val updated = state.exclusionConfig.excludedPathSegments + trimmed
                        state.copy(
                            exclusionConfig = state.exclusionConfig.copy(excludedPathSegments = updated)
                        )
                    }
                }
            }

            is ScannerUiEvent.RemoveExcludedPathSegment -> {
                internalState.update { state ->
                    val updated = state.exclusionConfig.excludedPathSegments - event.segment
                    state.copy(
                        exclusionConfig = state.exclusionConfig.copy(excludedPathSegments = updated)
                    )
                }
            }

            is ScannerUiEvent.ToggleSkipHiddenFiles -> {
                internalState.update { state ->
                    state.copy(
                        exclusionConfig = state.exclusionConfig.copy(skipHiddenFiles = event.skip)
                    )
                }
            }

            is ScannerUiEvent.RequestDeleteConfirmation -> {
                if (internalState.value.selectedPaths.isNotEmpty()) {
                    internalState.update { it.copy(showDeleteConfirmDialog = true) }
                }
            }

            is ScannerUiEvent.DismissDeleteConfirmation -> {
                internalState.update { it.copy(showDeleteConfirmDialog = false) }
            }

            is ScannerUiEvent.ConfirmBatchDelete -> {
                executeBatchDelete()
            }

            is ScannerUiEvent.DismissDeletionResult -> {
                internalState.update { it.copy(lastDeletionResult = null) }
            }

            is ScannerUiEvent.DismissStatusBanner -> {
                internalState.update { it.copy(statusBannerMessage = null) }
            }

            is ScannerUiEvent.InspectFileDetails -> {
                internalState.update { it.copy(inspectFileItem = event.item) }
            }
        }
    }

    private fun startScanInternal() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val roots = SandboxDatasetGenerator.resolveScanRoots(getApplication())
            val config = internalState.value.exclusionConfig
            val groups = scannerEngine.scanDirectories(roots, config)

            // Default smart selection on scan completion: keep oldest original unselected,
            // pre-select redundant newer copies so the user can review or adjust immediately
            val autoSelected = smartSelectionUseCase.applyStrategy(
                groups = groups,
                currentSelectedPaths = emptySet(),
                strategy = SmartSelectionStrategy.KeepOldest
            )

            internalState.update {
                it.copy(
                    allGroups = groups,
                    selectedPaths = autoSelected
                )
            }
        }
    }

    private fun executeBatchDelete() {
        val state = internalState.value
        val selectedSet = state.selectedPaths
        if (selectedSet.isEmpty()) return

        val itemsToDelete = state.allGroups
            .flatMap { it.files }
            .filter { it.path in selectedSet }

        viewModelScope.launch {
            internalState.update {
                it.copy(
                    showDeleteConfirmDialog = false,
                    isDeleting = true,
                    deletionProgressPair = 0 to itemsToDelete.size
                )
            }

            val result = fileDeletionManager.deleteFilesBatch(itemsToDelete) { done, total ->
                internalState.update { it.copy(deletionProgressPair = done to total) }
            }

            // Update remaining groups in memory without needing a full re-walk
            val deletedSuccessSet = itemsToDelete.map { it.path }.toSet() - result.failedPaths.toSet()
            val updatedGroups = state.allGroups.mapNotNull { group ->
                val remainingFiles = group.files.filter { it.path !in deletedSuccessSet }
                if (remainingFiles.size >= 2) {
                    group.copy(files = remainingFiles)
                } else {
                    null
                }
            }

            internalState.update {
                it.copy(
                    allGroups = updatedGroups,
                    selectedPaths = state.selectedPaths - deletedSuccessSet,
                    isDeleting = false,
                    deletionProgressPair = null,
                    lastDeletionResult = result
                )
            }
        }
    }

    private fun filterGroups(
        groups: List<DuplicateGroup>,
        category: FileCategory,
        query: String
    ): List<DuplicateGroup> {
        val catFiltered = if (category == FileCategory.ALL) {
            groups
        } else {
            groups.filter { it.category == category }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return catFiltered

        return catFiltered.filter { group ->
            group.fullHashHex.contains(trimmed, ignoreCase = true) ||
                group.files.any { file ->
                    file.name.contains(trimmed, ignoreCase = true) ||
                        file.path.contains(trimmed, ignoreCase = true)
                }
        }
    }

    private fun checkAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private data class InternalViewModelState(
        val hasAllFilesPermission: Boolean = false,
        val allGroups: List<DuplicateGroup> = emptyList(),
        val selectedCategory: FileCategory = FileCategory.ALL,
        val searchQuery: String = "",
        val selectedPaths: Set<String> = emptySet(),
        val exclusionConfig: ExclusionConfig = ExclusionConfig(),
        val showFolderSelectionSheet: Boolean = false,
        val showExclusionsDialog: Boolean = false,
        val showDeleteConfirmDialog: Boolean = false,
        val isDeleting: Boolean = false,
        val deletionProgressPair: Pair<Int, Int>? = null,
        val lastDeletionResult: DeletionResult? = null,
        val statusBannerMessage: String? = null,
        val inspectFileItem: DuplicateFileItem? = null
    )
}
