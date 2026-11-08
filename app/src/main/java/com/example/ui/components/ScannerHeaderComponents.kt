package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FileCategory
import com.example.domain.ScanProgress
import com.example.domain.ScanStage
import com.example.domain.SmartSelectionStrategy
import com.example.ui.FormatUtils
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TechAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TechEmerald

@Composable
fun UtilitarianTopBar(
    isScanning: Boolean,
    cachedHashesCount: Int,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSeedSandbox: () -> Unit,
    onOpenExclusions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isScanning) TechAmber else TechEmerald,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DUPHASH SE",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "XXH64 // XXH3-128",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "CACHE ROOM: $cachedHashesCount RECORD · I/O PARALLELISM: 6",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onSeedSandbox,
                        enabled = !isScanning,
                        modifier = Modifier.testTag("seed_sandbox_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Genera Sandbox File Reali",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(
                        onClick = onOpenExclusions,
                        enabled = !isScanning,
                        modifier = Modifier.testTag("exclusions_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configura Esclusioni Percorsi",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isScanning) {
                        Button(
                            onClick = onCancelScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("stop_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interrompi",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("start_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Scansiona",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SCAN", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun StoragePermissionStrip(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSeedSandbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasPermission) return

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TechAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "MANAGE_EXTERNAL_STORAGE NON ATTIVO (MODALITÀ SANDBOX + LEGACY)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TechAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Concedi accesso diretto a /storage/emulated/0 per scansionare l'intero dispositivo con java.io.File.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onSeedSandbox,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.testTag("banner_seed_button")
                    ) {
                        Text("TEST LAB", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechAmber,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.testTag("grant_storage_permission_button")
                    ) {
                        Text("AUTORIZZA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun TechnicalPipelineTelemetryPanel(
    progress: ScanProgress,
    totalGroupsCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Top telemetry status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "[${progress.stage.code}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress.isScanning) TechAmber else TechEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = progress.stage.titleIt,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${FormatUtils.formatSpeed(progress.speedMegabytesPerSec)} · ${FormatUtils.formatDurationMs(progress.elapsedMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TechCyan
                )
            }

            if (progress.isScanning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TechEmerald,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                if (progress.currentFilePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentFilePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4-Column Swiss Grid Metrics Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.extraSmall
                    )
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TelemetryMetricCell(
                    label = "1. SIZE GROUP",
                    value = "${progress.stage1CandidateFiles}/${progress.totalFilesWalked}",
                    subValue = "file candidati",
                    modifier = Modifier.weight(1f)
                )
                VerticalGridDivider()
                TelemetryMetricCell(
                    label = "2. 8KB HASH",
                    value = "${progress.stage2CandidateFiles}",
                    subValue = "cache: ${progress.cacheHits}",
                    modifier = Modifier.weight(1f)
                )
                VerticalGridDivider()
                TelemetryMetricCell(
                    label = "3. FULL 128B",
                    value = "${progress.stage3VerifiedDuplicates}",
                    subValue = "$totalGroupsCount gruppi",
                    modifier = Modifier.weight(1f)
                )
                VerticalGridDivider()
                TelemetryMetricCell(
                    label = "RECUPERABILI",
                    value = FormatUtils.formatBytes(progress.reclaimableBytesFound),
                    subValue = "I/O: ${FormatUtils.formatBytes(progress.totalBytesRead)}",
                    valueColor = TechEmerald,
                    modifier = Modifier.weight(1.15f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricCell(
    label: String,
    value: String,
    subValue: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerticalGridDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(46.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun CategoryAndSearchFilterBar(
    selectedCategory: FileCategory,
    categoryCounts: Map<FileCategory, Int>,
    searchQuery: String,
    onSelectCategory: (FileCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileCategory.entries.forEach { category ->
                val count = categoryCounts[category] ?: 0
                val isSelected = category == selectedCategory
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .border(1.dp, borderColor, MaterialTheme.shapes.extraSmall)
                        .background(bgColor, MaterialTheme.shapes.extraSmall)
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("category_chip_${category.name.lowercase()}")
                ) {
                    Text(
                        text = "${category.labelIt.uppercase()} [$count]",
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Compact technical path/hash filter input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.extraSmall
                )
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Filtra per percorso, cartella (/Download, /WhatsApp), nome o hash XXH3…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = JetBrainsMonoFamily
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_path_input")
                )
            }
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Pulisci filtro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onSearchQueryChange("") }
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SmartAutoSelectionBar(
    unsafeGroupCount: Int,
    onApplyStrategy: (SmartSelectionStrategy) -> Unit,
    onOpenFolderSelector: () -> Unit,
    onEnforceSafetyGuardrail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "AUTO-SELECT:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            SmartActionChip(
                icon = Icons.Default.History,
                label = "Mantieni il più vecchio",
                testTag = "smart_keep_oldest_chip",
                onClick = { onApplyStrategy(SmartSelectionStrategy.KeepOldest) }
            )

            SmartActionChip(
                icon = Icons.Default.Update,
                label = "Mantieni il più recente",
                testTag = "smart_keep_newest_chip",
                onClick = { onApplyStrategy(SmartSelectionStrategy.KeepNewest) }
            )

            SmartActionChip(
                icon = Icons.Default.FolderSpecial,
                label = "Seleziona per cartella…",
                testTag = "smart_select_folder_chip",
                accentColor = TechCyan,
                onClick = onOpenFolderSelector
            )

            SmartActionChip(
                icon = Icons.Default.Close,
                label = "Deseleziona",
                testTag = "smart_deselect_all_chip",
                onClick = { onApplyStrategy(SmartSelectionStrategy.DeselectAll) }
            )
        }

        // Safety Guardrail Banner if user manually checked ALL copies of any group
        AnimatedVisibility(visible = unsafeGroupCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = TechAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SAFETY GUARDRAIL: $unsafeGroupCount gruppo/i con TUTTE le copie spuntate (0 originali preservati)!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, TechAmber, MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                        .clickable { onEnforceSafetyGuardrail() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("enforce_safety_guardrail_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = TechEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PRESERVA 1 COPIA",
                            style = MaterialTheme.typography.labelSmall,
                            color = TechEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SmartActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.extraSmall
            )
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
