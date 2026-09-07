package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SortOption
import com.example.data.model.ViewMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortBottomSheet(
    currentSort: SortOption,
    isSortAscending: Boolean,
    currentViewMode: ViewMode,
    showThumbnails: Boolean,
    showDuration: Boolean,
    showSize: Boolean,
    showResolution: Boolean,
    showHiddenFiles: Boolean,
    onSortSelected: (SortOption) -> Unit,
    onSortDirectionChanged: (Boolean) -> Unit,
    onViewModeSelected: (ViewMode) -> Unit,
    onToggleThumbnails: (Boolean) -> Unit,
    onToggleDuration: (Boolean) -> Unit,
    onToggleSize: (Boolean) -> Unit,
    onToggleResolution: (Boolean) -> Unit,
    onToggleHiddenFiles: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Pengatur Daftar & Tampilan (MX Player Style)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sesuaikan urutan, tata letak, dan elemen informasi video",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 1: TAMPILAN / LAYOUT
            Text(
                text = "1. Tata Letak (Tampilan)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewModeButton(
                    label = "Kotak (Grid)",
                    icon = Icons.Default.GridView,
                    isSelected = currentViewMode == ViewMode.GRID,
                    onClick = { onViewModeSelected(ViewMode.GRID) },
                    modifier = Modifier.weight(1f)
                )
                ViewModeButton(
                    label = "Daftar (List)",
                    icon = Icons.Default.ViewList,
                    isSelected = currentViewMode == ViewMode.LIST,
                    onClick = { onViewModeSelected(ViewMode.LIST) },
                    modifier = Modifier.weight(1f)
                )
                ViewModeButton(
                    label = "Ringkas",
                    icon = Icons.Default.ViewHeadline,
                    isSelected = currentViewMode == ViewMode.COMPACT,
                    onClick = { onViewModeSelected(ViewMode.COMPACT) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 2: SORTIR / URUTKAN BERDASARKAN
            Text(
                text = "2. Urutkan Berdasarkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val primarySortOptions = listOf(
                SortOption.NAME to "Judul / Nama",
                SortOption.DATE to "Tanggal Dimodifikasi",
                SortOption.SIZE to "Ukuran File",
                SortOption.DURATION to "Panjang Durasi",
                SortOption.RESOLUTION to "Resolusi Video"
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                primarySortOptions.forEach { (option, label) ->
                    val isSelected = when (currentSort) {
                        option -> true
                        SortOption.NAME_ASC, SortOption.NAME_DESC -> option == SortOption.NAME
                        SortOption.DATE_ASC, SortOption.DATE_DESC -> option == SortOption.DATE
                        SortOption.SIZE_ASC, SortOption.SIZE_DESC -> option == SortOption.SIZE
                        SortOption.DURATION_ASC, SortOption.DURATION_DESC -> option == SortOption.DURATION
                        else -> false
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortSelected(option) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 3: ARAH URUTAN
            Text(
                text = "3. Arah Urutan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SortDirectionButton(
                    label = "Naik (A-Z / Lama / Kecil)",
                    icon = Icons.Default.ArrowUpward,
                    isSelected = isSortAscending,
                    onClick = { onSortDirectionChanged(true) },
                    modifier = Modifier.weight(1f)
                )
                SortDirectionButton(
                    label = "Turun (Z-A / Baru / Besar)",
                    icon = Icons.Default.ArrowDownward,
                    isSelected = !isSortAscending,
                    onClick = { onSortDirectionChanged(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 4: ELEMEN TAMPILAN INFORMASI
            Text(
                text = "4. Elemen Tampilan Informasi (Fields)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            DisplayCheckboxRow(
                title = "Tampilkan Gambar Mini (Thumbnail)",
                subtitle = "Tampilkan bingkai cuplikan gambar dari video",
                isChecked = showThumbnails,
                onCheckedChange = onToggleThumbnails
            )
            DisplayCheckboxRow(
                title = "Tampilkan Durasi Video",
                subtitle = "Tampilkan panjang durasi waktu pada setiap video",
                isChecked = showDuration,
                onCheckedChange = onToggleDuration
            )
            DisplayCheckboxRow(
                title = "Tampilkan Ukuran File",
                subtitle = "Tampilkan total ukuran MB / GB file video",
                isChecked = showSize,
                onCheckedChange = onToggleSize
            )
            DisplayCheckboxRow(
                title = "Tampilkan Resolusi (Badge)",
                subtitle = "Tampilkan lencana resolusi (1080p FHD, 4K, 720p)",
                isChecked = showResolution,
                onCheckedChange = onToggleResolution
            )
            DisplayCheckboxRow(
                title = "Tampilkan File Tersembunyi (.dot)",
                subtitle = "Tampilkan file atau folder tersembunyi berawalan titik",
                isChecked = showHiddenFiles,
                onCheckedChange = onToggleHiddenFiles
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Done Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sort_sheet_apply_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Terapkan & Tutup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SortDirectionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun DisplayCheckboxRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
