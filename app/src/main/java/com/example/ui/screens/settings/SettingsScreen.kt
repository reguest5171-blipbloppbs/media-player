package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPreferencesManager
import com.example.ui.components.PinDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }

    val defaultDecoder by preferencesManager.defaultDecoderFlow.collectAsStateWithLifecycle(initialValue = "HW")
    val autoScan by preferencesManager.autoScanFlow.collectAsStateWithLifecycle(initialValue = true)
    val resumePlayback by preferencesManager.resumePlaybackFlow.collectAsStateWithLifecycle(initialValue = true)
    val savedPin by preferencesManager.pinCodeFlow.collectAsStateWithLifecycle(initialValue = null)
    val vaultExtension by preferencesManager.vaultExtensionFlow.collectAsStateWithLifecycle(initialValue = "1ca")

    var showDecoderDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings & Playback Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Decoding Engine Section
            Text(
                text = "Decoder & Compatibility",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDecoderDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Video Decoder",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (defaultDecoder) {
                                    "SW" -> "SW (Classic MX Software Decoder - Best for older/unsupported codecs)"
                                    "HW_PLUS" -> "HW+ (Hardware Accelerated Extended)"
                                    else -> "HW (Hardware Acceleration - Standard)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Resume Playback",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Remember last playback position for all videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = resumePlayback,
                            onCheckedChange = { checked ->
                                scope.launch { preferencesManager.setResumePlayback(checked) }
                            }
                        )
                    }
                }
            }

            // Storage & Startup Section
            Text(
                text = "Library & Startup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Scan Media on Startup",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Instantly detect new local videos when opening the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoScan,
                            onCheckedChange = { checked ->
                                scope.launch { preferencesManager.setAutoScan(checked) }
                            }
                        )
                    }
                }
            }

            // Security & Mode Kunci Section
            Text(
                text = "Security & Mode Kunci (.1ca Vault)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPinDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (savedPin.isNullOrBlank()) "Atur 4-Digit PIN Keamanan" else "Ubah PIN Keamanan",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (savedPin.isNullOrBlank()) 
                                    "Buat PIN untuk melindungi video rahasia .1ca (Tersimpan permanen)"
                                else 
                                    "PIN aktif • Tersimpan permanen walau Hapus Data Aplikasi",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (savedPin.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Architecture & Low-End Compatibility Info
            Text(
                text = "Device Compatibility & Optimization",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Low-End / HP Kentang & Android 7+ Ready",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Ultra-lightweight APK footprint minimizing internal storage consumption.\n• Zero internet connection needed for local files.\n• Integrated Software (SW) Fallback decoder enables playback of high-res & unsupported codecs on legacy chipsets.\n• Built-in FTP and Samba streaming client.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    // Decoder Selector Dialog
    if (showDecoderDialog) {
        Dialog(onDismissRequest = { showDecoderDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Select Default Decoder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val options = listOf(
                        "HW" to "HW (Hardware Acceleration - Faster, battery efficient)",
                        "SW" to "SW (Classic MX Software Decoder - Maximum codec compatibility)",
                        "HW_PLUS" to "HW+ (Hardware Accelerated Extended)"
                    )

                    options.forEach { (code, title) ->
                        val isSelected = defaultDecoder == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch { preferencesManager.setDefaultDecoder(code) }
                                    showDecoderDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { preferencesManager.setDefaultDecoder(code) }
                                    showDecoderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    val savedQuestion by preferencesManager.securityQuestionFlow.collectAsStateWithLifecycle(initialValue = null)

    if (showPinDialog) {
        PinDialog(
            isSettingNewPin = true,
            hasExistingPin = !savedPin.isNullOrBlank(),
            initialVaultExtension = vaultExtension,
            savedSecurityQuestion = savedQuestion,
            onVerifyPin = { pin -> preferencesManager.vaultSecurityManager.verifyPin(pin) },
            onVerifySecurityAnswer = { ans -> preferencesManager.verifySecurityAnswer(ans) },
            onPinExtensionAndQuestionSuccess = { newPin, ext, question, answer ->
                scope.launch {
                    if (newPin.isNotBlank()) preferencesManager.setPinCode(newPin)
                    preferencesManager.setVaultExtension(ext)
                    if (question.isNotBlank() && answer.isNotBlank()) {
                        preferencesManager.setSecurityQuestionAndAnswer(question, answer)
                    }
                }
                showPinDialog = false
            },
            onPinSuccess = { newPin ->
                scope.launch { preferencesManager.setPinCode(newPin) }
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
            onRemovePin = {
                scope.launch { preferencesManager.clearPinCode() }
            }
        )
    }
}
