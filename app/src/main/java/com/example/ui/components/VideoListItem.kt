package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun VideoListItem(
    video: VideoMediaItem,
    onClick: () -> Unit,
    onMenuAction: (VideoMenuAction) -> Unit,
    showThumbnails: Boolean = true,
    showDuration: Boolean = true,
    showSize: Boolean = true,
    showResolution: Boolean = true,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_list_item_${video.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        val thumbModel = remember(video.uri, video.path, video.isEncrypted1ca) {
            if (video.isEncrypted1ca && video.path.isNotBlank()) {
                val file = File(video.path)
                val cached = EncryptionUtil.getOrExtractThumbnailFile(context, file)
                cached ?: video.uri
            } else {
                video.uri
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container
            if (showThumbnails) {
                Box(
                    modifier = Modifier
                        .size(
                            width = if (isCompact) 76.dp else 110.dp,
                            height = if (isCompact) 46.dp else 66.dp
                        )
                        .clip(RoundedCornerShape(8.dp))
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
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xDD12121A))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Vault Encrypted",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(if (isCompact) 10.dp else 12.dp)
                            )
                        }
                    }

                    // Duration badge
                    if (showDuration) {
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xDD000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = video.formattedDuration,
                                color = Color.White,
                                fontSize = if (isCompact) 9.sp else 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showResolution) {
                        Text(
                            text = video.resolutionTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Options menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
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
                            text = { Text("Unlock / Restore Video") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(VideoMenuAction.UNLOCK_VAULT)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Lock to .1ca Vault") },
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
