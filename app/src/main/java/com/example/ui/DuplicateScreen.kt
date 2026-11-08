package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.DuplicateFileItem
import com.example.domain.DuplicateGroup
import com.example.domain.FileCategory
import com.example.domain.SmartSelectionStrategy
import com.example.ui.components.CategoryAndSearchFilterBar
import com.example.ui.components.ConfirmBatchDeleteDialog
import com.example.ui.components.DeletionResultSummaryDialog
import com.example.ui.components.ExclusionsConfigDialog
import com.example.ui.components.FileTechnicalInspectorDialog
import com.example.ui.components.FolderSmartSelectionBottomSheet
import com.example.ui.components.SmartAutoSelectionBar
import com.example.ui.components.StoragePermissionStrip
import com.example.ui.components.TechnicalPipelineTelemetryPanel
import com.example.ui.components.UtilitarianTopBar
import com.example.ui.theme.TechAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TechEmerald
import java.io.File

@Composable
fun DuplicateScreen(
    state: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Launcher for Android 11+ (API 30+) MANAGE_EXTERNAL_STORAGE settings screen
    val manageStorageSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onEvent(ScannerUiEvent.RefreshPermissionState)
    }

    // Legacy runtime permission launcher for API 24-29
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onEvent(ScannerUiEvent.RefreshPermissionState)
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                manageStorageSettingsLauncher.launch(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageSettingsLauncher.launch(fallback)
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                UtilitarianTopBar(
                    isScanning = state.scanProgress.isScanning,
                    cachedHashesCount = state.cachedHashesCount,
                    onStartScan = { onEvent(ScannerUiEvent.StartScan) },
                    onCancelScan = { onEvent(ScannerUiEvent.CancelScan) },
                    onSeedSandbox = { onEvent(ScannerUiEvent.SeedSandboxAndScan) },
                    onOpenExclusions = { onEvent(ScannerUiEvent.ToggleExclusionsDialog(true)) }
                )

                StoragePermissionStrip(
                    hasPermission = state.hasAllFilesPermission,
                    onRequestPermission = requestStoragePermission,
                    onSeedSandbox = { onEvent(ScannerUiEvent.SeedSandboxAndScan) }
                )

                if (state.statusBannerMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(ScannerUiEvent.DismissStatusBanner) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.statusBannerMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "[OK]",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                TechnicalPipelineTelemetryPanel(
                    progress = state.scanProgress,
                    totalGroupsCount = state.allGroups.size
                )

                CategoryAndSearchFilterBar(
                    selectedCategory = state.selectedCategory,
                    categoryCounts = state.categoryCounts,
                    searchQuery = state.searchQuery,
                    onSelectCategory = { onEvent(ScannerUiEvent.SelectCategory(it)) },
                    onSearchQueryChange = { onEvent(ScannerUiEvent.UpdateSearchQuery(it)) }
                )

                SmartAutoSelectionBar(
                    unsafeGroupCount = state.unsafeAllSelectedGroupHashes.size,
                    onApplyStrategy = { onEvent(ScannerUiEvent.ApplySmartSelection(it)) },
                    onOpenFolderSelector = { onEvent(ScannerUiEvent.ToggleFolderSheet(true)) },
                    onEnforceSafetyGuardrail = { onEvent(ScannerUiEvent.EnforceSafetyGuardrail) }
                )
            }
        },
        bottomBar = {
            BottomBatchActionBar(
                selectedFilesCount = state.selectedFilesCount,
                selectedBytesToReclaim = state.selectedBytesToReclaim,
                unsafeGroupCount = state.unsafeAllSelectedGroupHashes.size,
                isDeleting = state.isDeleting,
                onDeleteClick = { onEvent(ScannerUiEvent.RequestDeleteConfirmation) }
            )
        }
    ) { innerPadding ->
        if (state.filteredGroups.isEmpty() && !state.scanProgress.isScanning) {
            EmptyDuplicatesTechnicalView(
                hasAnyGroupsAtAll = state.allGroups.isNotEmpty(),
                onResetFilters = {
                    onEvent(ScannerUiEvent.SelectCategory(FileCategory.ALL))
                    onEvent(ScannerUiEvent.UpdateSearchQuery(""))
                },
                onSeedSandboxAndScan = { onEvent(ScannerUiEvent.SeedSandboxAndScan) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("duplicate_groups_lazy_column"),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 10.dp,
                    end = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.filteredGroups,
                    key = { it.hash }
                ) { group ->
                    val isGroupUnsafelySelected = group.hash in state.unsafeAllSelectedGroupHashes
                    DuplicateGroupCompactCard(
                        group = group,
                        selectedPaths = state.selectedPaths,
                        isAllSelectedWarning = isGroupUnsafelySelected,
                        onToggleFile = { path -> onEvent(ScannerUiEvent.ToggleFileSelection(path)) },
                        onInspectFile = { item -> onEvent(ScannerUiEvent.InspectFileDetails(item)) }
                    )
                }
            }
        }
    }

    // Bottom sheets & dialogs
    if (state.showFolderSelectionSheet) {
        FolderSmartSelectionBottomSheet(
            candidateFolders = state.candidateFolders,
            onSelectFolder = { folder ->
                onEvent(ScannerUiEvent.ApplySmartSelection(SmartSelectionStrategy.SelectByFolder(folder)))
            },
            onDismiss = { onEvent(ScannerUiEvent.ToggleFolderSheet(false)) }
        )
    }

    if (state.showExclusionsDialog) {
        ExclusionsConfigDialog(
            config = state.exclusionConfig,
            cachedCount = state.cachedHashesCount,
            onAddSegment = { onEvent(ScannerUiEvent.AddExcludedPathSegment(it)) },
            onRemoveSegment = { onEvent(ScannerUiEvent.RemoveExcludedPathSegment(it)) },
            onToggleSkipHidden = { onEvent(ScannerUiEvent.ToggleSkipHiddenFiles(it)) },
            onClearCache = { onEvent(ScannerUiEvent.ClearRoomHashCache) },
            onDismiss = { onEvent(ScannerUiEvent.ToggleExclusionsDialog(false)) }
        )
    }

    if (state.showDeleteConfirmDialog) {
        ConfirmBatchDeleteDialog(
            selectedFilesCount = state.selectedFilesCount,
            selectedBytesToReclaim = state.selectedBytesToReclaim,
            unsafeGroupCount = state.unsafeAllSelectedGroupHashes.size,
            onEnforceSafetyFirst = { onEvent(ScannerUiEvent.EnforceSafetyGuardrail) },
            onConfirmDelete = { onEvent(ScannerUiEvent.ConfirmBatchDelete) },
            onDismiss = { onEvent(ScannerUiEvent.DismissDeleteConfirmation) }
        )
    }

    if (state.lastDeletionResult != null) {
        DeletionResultSummaryDialog(
            result = state.lastDeletionResult,
            onDismiss = { onEvent(ScannerUiEvent.DismissDeletionResult) }
        )
    }

    if (state.inspectFileItem != null) {
        FileTechnicalInspectorDialog(
            item = state.inspectFileItem,
            onDismiss = { onEvent(ScannerUiEvent.InspectFileDetails(null)) }
        )
    }
}

@Composable
private fun DuplicateGroupCompactCard(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    isAllSelectedWarning: Boolean,
    onToggleFile: (String) -> Unit,
    onInspectFile: (DuplicateFileItem) -> Unit
) {
    val borderColor = if (isAllSelectedWarning) {
        TechAmber
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val oldestFilePath = group.files.minWithOrNull(
        compareBy<DuplicateFileItem> { it.lastModified }.thenBy { it.path }
    )?.path

    val newestFilePath = group.files.maxWithOrNull(
        compareBy<DuplicateFileItem> { it.lastModified }.thenBy { it.path }
    )?.path

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
    ) {
        // Group Compact Technical Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.extraSmall
                        )
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${group.copyCount} COPIE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${FormatUtils.formatBytes(group.fileSizeBytes)} / file",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "XXH3:${group.fullHashHex.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAllSelectedWarning) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, TechAmber, MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Avviso: tutte le copie selezionate",
                            tint = TechAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "0 ORIGINALI",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TechAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = "-${FormatUtils.formatBytes(group.reclaimableBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TechEmerald
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // File rows inside the group
        group.files.forEachIndexed { index, item ->
            val isChecked = item.path in selectedPaths
            val isOldest = item.path == oldestFilePath
            val isNewest = item.path == newestFilePath && !isOldest

            DuplicateFileCompactRow(
                item = item,
                isChecked = isChecked,
                isOldest = isOldest,
                isNewest = isNewest,
                onToggle = { onToggleFile(item.path) },
                onInspect = { onInspectFile(item) }
            )

            if (index < group.files.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DuplicateFileCompactRow(
    item: DuplicateFileItem,
    isChecked: Boolean,
    isOldest: Boolean,
    isNewest: Boolean,
    onToggle: () -> Unit,
    onInspect: () -> Unit
) {
    val rowBg = if (isChecked) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 40.dp Compact Thumbnail (Coil for Images, Technical MIME Badge for other types)
        FileCompactThumbnail40dp(
            item = item,
            modifier = Modifier.clickable(onClick = onInspect)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Center column: Filename + Full Visible Path + Timestamp & Origin Badges
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isOldest) {
                    TechnicalMicroBadge(
                        text = "PIÙ VECCHIO",
                        borderColor = TechEmerald,
                        textColor = TechEmerald
                    )
                } else if (isNewest) {
                    TechnicalMicroBadge(
                        text = "PIÙ RECENTE",
                        borderColor = TechCyan,
                        textColor = TechCyan
                    )
                }

                if (item.fromCache) {
                    TechnicalMicroBadge(
                        text = "CACHE",
                        borderColor = MaterialTheme.colorScheme.outline,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Full readable directory path in Monospace (critical for knowing which copy is being deleted)
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = if (isChecked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MOD: ${FormatUtils.formatTimestamp(item.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.clickable(onClick = onInspect),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Dettagli hash",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ISPEZIONA",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.error,
                uncheckedColor = MaterialTheme.colorScheme.outline,
                checkmarkColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.testTag("checkbox_${item.name}")
        )
    }
}

@Composable
private fun FileCompactThumbnail40dp(
    item: DuplicateFileItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.extraSmall
            )
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (item.category == FileCategory.IMAGES) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(item.path))
                    .crossfade(false)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val (icon, tint) = when (item.category) {
                FileCategory.VIDEOS -> Icons.Default.VideoFile to TechCyan
                FileCategory.AUDIO -> Icons.Default.AudioFile to TechAmber
                FileCategory.ARCHIVES -> Icons.Default.Archive to TechEmerald
                FileCategory.IMAGES -> Icons.Default.Image to TechEmerald
                else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.category.labelIt,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = item.extension.take(4).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TechnicalMicroBadge(
    text: String,
    borderColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .border(1.dp, borderColor, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomBatchActionBar(
    selectedFilesCount: Int,
    selectedBytesToReclaim: Long,
    unsafeGroupCount: Int,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELEZIONATI: $selectedFilesCount FILE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RECUPERO: ${FormatUtils.formatBytes(selectedBytesToReclaim)}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp),
                            color = if (selectedFilesCount > 0) TechEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (unsafeGroupCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[$unsafeGroupCount GRUPPI A RISCHIO]",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = onDeleteClick,
                    enabled = selectedFilesCount > 0 && !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("delete_selected_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDeleting) "ELIMINAZIONE…" else "ELIMINA ($selectedFilesCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDuplicatesTechnicalView(
    hasAnyGroupsAtAll: Boolean,
    onResetFilters: () -> Unit,
    onSeedSandboxAndScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasAnyGroupsAtAll) {
                    "NESSUN DUPLICATO PER IL FILTRO ATTIVO"
                } else {
                    "0 DUPLICATI RILEVATI NELLO STORAGE"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyGroupsAtAll) {
                    "Modifica la categoria selezionata o cancella la query di ricerca sul percorso."
                } else {
                    "Puoi generare un dataset reale di test su disco (Immagini PNG, Video, Audio, PDF, ZIP + file esca Stage 2/Stage 3) per testare la pipeline a 3 stadi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (hasAnyGroupsAtAll) {
                OutlinedButton(
                    onClick = onResetFilters,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text("MOSTRA TUTTE LE CATEGORIE", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onSeedSandboxAndScan,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.testTag("empty_seed_sandbox_button")
                ) {
                    Text("GENERA SANDBOX FILE REALI & SCANSIONA", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
