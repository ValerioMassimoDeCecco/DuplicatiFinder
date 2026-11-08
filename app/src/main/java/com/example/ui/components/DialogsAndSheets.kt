package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DeletionResult
import com.example.domain.DuplicateFileItem
import com.example.domain.ExclusionConfig
import com.example.domain.FolderCandidate
import com.example.domain.SmartSelectionStrategy
import com.example.ui.FormatUtils
import com.example.ui.theme.TechAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TechEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSmartSelectionBottomSheet(
    candidateFolders: List<FolderCandidate>,
    onSelectFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "SELEZIONA DUPLICATI PER CARTELLA",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Seleziona automaticamente tutte le copie contenute nella cartella scelta. Safety Guardrail: se tutte le copie di un gruppo risiedono nella stessa cartella, la copia più vecchia verrà sempre preservata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Quick preset folder filters (/Download, /WhatsApp/Media)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectFolder("/Download") },
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, TechCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("/Download", style = MaterialTheme.typography.labelMedium, color = TechCyan)
                }
                OutlinedButton(
                    onClick = { onSelectFolder("/WhatsApp/Media") },
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, TechEmerald),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("/WhatsApp/Media", style = MaterialTheme.typography.labelMedium, color = TechEmerald)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(candidateFolders, key = { it.folderPath }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder(folder.folderPath) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = TechCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = folder.folderPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folder.duplicateFileCount} file duplicati · ${FormatUtils.formatBytes(folder.totalBytesInFolder)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SELEZIONA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun ExclusionsConfigDialog(
    config: ExclusionConfig,
    cachedCount: Int,
    onAddSegment: (String) -> Unit,
    onRemoveSegment: (String) -> Unit,
    onToggleSkipHidden: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    var newSegment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        title = {
            Text(
                text = "ESCLUSIONI & CACHE MOTORE",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSkipHidden(!config.skipHiddenFiles) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.skipHiddenFiles,
                        onCheckedChange = onToggleSkipHidden
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ignora file e cartelle nascoste (prefisso '.')",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PERCORSI ESCLUSI DALLA SCANSIONE:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSegment,
                        onValueChange = { newSegment = it },
                        placeholder = { Text("/Percorso/Da/Escludere", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (newSegment.isNotBlank()) {
                                onAddSegment(newSegment)
                                newSegment = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi esclusione")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    items(config.excludedPathSegments.toList()) { segment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = segment,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "RIMUOVI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { onRemoveSegment(segment) }
                                    .padding(4.dp)
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Room FileHashCacheEntity",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "$cachedCount record in cache SQLite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onClearCache,
                        shape = MaterialTheme.shapes.extraSmall,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("SVUOTA CACHE", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CHIUDI", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun ConfirmBatchDeleteDialog(
    selectedFilesCount: Int,
    selectedBytesToReclaim: Long,
    unsafeGroupCount: Int,
    onEnforceSafetyFirst: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONFERMA ELIMINAZIONE BATCH",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "FILE SELEZIONATI: $selectedFilesCount",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SPAZIO RECUPERABILE: ${FormatUtils.formatBytes(selectedBytesToReclaim)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TechEmerald
                        )
                    }
                }

                if (unsafeGroupCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TechAmber)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = TechAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AVVISO SAFETY GUARDRAIL ($unsafeGroupCount GRUPPI)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TechAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hai selezionato TUTTE le copie in $unsafeGroupCount gruppo/i. Procedendo senza preservare un originale, quei file verranno rimossi del tutto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onEnforceSafetyFirst,
                                border = BorderStroke(1.dp, TechEmerald),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "CORREGGI AUTOMATICAMENTE (MANTIENI 1 COPIA)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TechEmerald
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = TechEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Safety Check OK: Almeno 1 copia originale preservata per ogni gruppo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TechEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "L'operazione aggiornerà automaticamente MediaStore (MediaScannerConnection) e la cache Room.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.testTag("confirm_delete_dialog_button")
            ) {
                Text("ELIMINA DEFINITIVAMENTE", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun DeletionResultSummaryDialog(
    result: DeletionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        title = {
            Text(
                text = "REPORT ELIMINAZIONE BATCH",
                style = MaterialTheme.typography.headlineSmall,
                color = TechEmerald
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "FILE ELIMINATI:   ${result.deletedCount}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "ERRORI I/O:       ${result.failedCount}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "SPAZIO LIBERATO:  ${FormatUtils.formatBytes(result.bytesReclaimed)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TechEmerald
                )
                Text(
                    text = "MEDIASTORE SYNC:  ${if (result.mediaStoreUpdated) "COMPLETATO" else "IN CODA"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TechCyan
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("OK", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun FileTechnicalInspectorDialog(
    item: DuplicateFileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        title = {
            Text(
                text = "ISPEZIONE TECNICA FILE",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InspectorRow("NOME", item.name)
                InspectorRow("PERCORSO", item.path)
                InspectorRow("CARTELLA", item.parentDir)
                InspectorRow("DIMENSIONE", "${FormatUtils.formatBytes(item.sizeBytes)} (${item.sizeBytes} byte)")
                InspectorRow("ULTIMA MODIFICA", FormatUtils.formatTimestamp(item.lastModified))
                InspectorRow("CATEGORIA", "${item.category.labelIt} (.${item.extension})")
                InspectorRow("XXH64 (8KB)", item.partialHash.toULong().toString(16).padStart(16, '0').uppercase())
                InspectorRow("XXH3-128 (FULL)", item.fullHash)
                InspectorRow("STATO CACHE", if (item.fromCache) "HIT (Room SQLite)" else "CALCOLATO I/O")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CHIUDI", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
