package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.model.VideoMediaItem
import com.example.player.EncryptionUtil
import java.io.File

@Composable
fun VideoCard(
    video: VideoMediaItem,
    onClick: () -> Unit,
    onMenuAction: (VideoMenuAction) -> Unit,
    showThumbnails: Boolean = true,
    showDuration: Boolean = true,
    showSize: Boolean = true,
    showResolution: Boolean = true,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_card_${video.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val thumbModel by produceState<Any?>(initialValue = null, video.uri, video.path, video.isEncrypted1ca) {
            if (video.isEncrypted1ca && video.path.isNotBlank()) {
                val file = File(video.path)
                val fastCached = EncryptionUtil.getCachedThumbnailFile(context, file)
                if (fastCached != null) {
                    value = fastCached
                } else {
                    val extracted = EncryptionUtil.getOrExtractThumbnailFileAsync(context, file)
                    value = extracted ?: video.uri
                }
            } else {
                value = video.uri
            }
        }

        Column {
            // Thumbnail container
            if (showThumbnails) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .background(Color(0xFF151928))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbModel)
                            .videoFrameMillis(2000)
                            .crossfade(true)
                            .build(),
                        contentDescription = video.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (video.isEncrypted1ca) {
                        // Sleek Vault lock badge in top-left
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xDD12121A))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted Vault",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "VAULT",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Gradient overlay at bottom of thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                    )

                    // Resolution badge (top-left)
                    if (showResolution) {
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC0D47A1))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = video.resolutionTag,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status badge (top-right of thumbnail)
                    if (video.isCompleted || video.isInProgress || video.isNewVideo) {
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        video.isCompleted -> Color(0xEE2E7D32) // Green Dark / Finished
                                        video.isInProgress -> Color(0xEEE65100) // Deep Orange / In progress
                                        video.isNewVideo -> Color(0xEE0288D1) // Bright Blue / New
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    video.isCompleted -> "Tamat"
                                    video.isInProgress -> "${video.progressPercent}% Belum Tamat"
                                    else -> "BARU"
                                },
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Duration badge (bottom-right)
                    if (showDuration) {
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xDD000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = video.formattedDuration,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // In-progress playback bar at bottom edge of thumbnail
                    if (video.isInProgress && video.progressPercent > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(Color(0x66000000))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(video.progressPercent / 100f)
                                    .height(3.dp)
                                    .background(Color(0xFFFF9800))
                            )
                        }
                    }

                    // Play icon overlay in center
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Info section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showSize) {
                            Text(
                                text = video.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = video.folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Video") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.PLAY)
                            }
                        )
                        if (video.isEncrypted1ca) {
                            DropdownMenuItem(
                                text = { Text("Buka Kunci ke Galeri") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction(VideoMenuAction.UNLOCK_VAULT)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Kunci ke Brankas") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction(VideoMenuAction.LOCK_VAULT)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.RENAME)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move File") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.MOVE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Properties / Info") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.DETAILS)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.DELETE)
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class VideoMenuAction {
    PLAY,
    LOCK_VAULT,
    UNLOCK_VAULT,
    RENAME,
    MOVE,
    DETAILS,
    DELETE
}
