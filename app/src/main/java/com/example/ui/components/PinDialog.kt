package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PinDialogMode {
    UNLOCK,
    SETUP_NEW,
    CHANGE_PIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDialog(
    isSettingNewPin: Boolean,
    hasExistingPin: Boolean = false,
    initialVaultExtension: String = "1ca",
    savedSecurityQuestion: String? = null,
    onVerifyPin: (suspend (String) -> Boolean)? = null,
    onVerifySecurityAnswer: (suspend (String) -> Boolean)? = null,
    onPinSuccess: (String) -> Unit,
    onPinAndExtensionSuccess: ((String, String) -> Unit)? = null,
    onPinExtensionAndQuestionSuccess: ((String, String, String, String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onRemovePin: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    val mode = remember {
        when {
            !isSettingNewPin -> PinDialogMode.UNLOCK
            hasExistingPin -> PinDialogMode.CHANGE_PIN
            else -> PinDialogMode.SETUP_NEW
        }
    }

    var step by remember { mutableIntStateOf(0) }
    var showSecurityQuestionStep by remember { mutableStateOf(false) }
    var showExtensionStep by remember { mutableStateOf(false) }
    var isForgotPinMode by remember { mutableStateOf(false) }

    var customExtInput by remember { mutableStateOf(initialVaultExtension) }

    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var unlockPinInput by remember { mutableStateOf("") }

    // Security Question & Answer
    val presetQuestions = remember {
        listOf(
            "Apa nama hewan peliharaan pertama Anda?",
            "Di kota mana Anda dilahirkan?",
            "Apa nama sekolah dasar Anda?",
            "Apa makanan favorit Anda?",
            "Pertanyaan Khusus (Tulis Sendiri)"
        )
    }
    var selectedPresetQuestion by remember { mutableStateOf(presetQuestions[0]) }
    var customQuestionText by remember { mutableStateOf("") }
    var securityAnswerInput by remember { mutableStateOf("") }
    var forgotPinAnswerInput by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isVerifying by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDigits by remember { mutableStateOf(false) }

    val currentInput = when (mode) {
        PinDialogMode.UNLOCK -> unlockPinInput
        PinDialogMode.SETUP_NEW -> if (step == 0) newPinInput else confirmPinInput
        PinDialogMode.CHANGE_PIN -> when (step) {
            0 -> oldPinInput
            1 -> newPinInput
            else -> confirmPinInput
        }
    }

    fun handleKeyInput(key: String) {
        if (isVerifying || isSuccess) return
        errorMessage = null

        when (key) {
            "C" -> {
                when (mode) {
                    PinDialogMode.UNLOCK -> unlockPinInput = ""
                    PinDialogMode.SETUP_NEW -> {
                        if (step == 0) newPinInput = "" else confirmPinInput = ""
                    }
                    PinDialogMode.CHANGE_PIN -> {
                        when (step) {
                            0 -> oldPinInput = ""
                            1 -> newPinInput = ""
                            else -> confirmPinInput = ""
                        }
                    }
                }
            }
            "DEL" -> {
                when (mode) {
                    PinDialogMode.UNLOCK -> if (unlockPinInput.isNotEmpty()) unlockPinInput = unlockPinInput.dropLast(1)
                    PinDialogMode.SETUP_NEW -> {
                        if (step == 0) {
                            if (newPinInput.isNotEmpty()) newPinInput = newPinInput.dropLast(1)
                        } else {
                            if (confirmPinInput.isNotEmpty()) confirmPinInput = confirmPinInput.dropLast(1)
                        }
                    }
                    PinDialogMode.CHANGE_PIN -> {
                        when (step) {
                            0 -> if (oldPinInput.isNotEmpty()) oldPinInput = oldPinInput.dropLast(1)
                            1 -> if (newPinInput.isNotEmpty()) newPinInput = newPinInput.dropLast(1)
                            else -> if (confirmPinInput.isNotEmpty()) confirmPinInput = confirmPinInput.dropLast(1)
                        }
                    }
                }
            }
            else -> {
                when (mode) {
                    PinDialogMode.UNLOCK -> {
                        if (unlockPinInput.length < 4) {
                            val next = unlockPinInput + key
                            unlockPinInput = next
                            if (next.length == 4) {
                                isVerifying = true
                                coroutineScope.launch {
                                    val valid = onVerifyPin?.invoke(next) ?: true
                                    isVerifying = false
                                    if (valid) {
                                        isSuccess = true
                                        delay(300)
                                        onPinSuccess(next)
                                    } else {
                                        errorMessage = "PIN Salah! Silakan coba lagi."
                                        unlockPinInput = ""
                                    }
                                }
                            }
                        }
                    }
                    PinDialogMode.SETUP_NEW -> {
                        if (step == 0) {
                            if (newPinInput.length < 4) {
                                val next = newPinInput + key
                                newPinInput = next
                                if (next.length == 4) {
                                    step = 1
                                }
                            }
                        } else {
                            if (confirmPinInput.length < 4) {
                                val next = confirmPinInput + key
                                confirmPinInput = next
                                if (next.length == 4) {
                                    if (next == newPinInput) {
                                        showSecurityQuestionStep = true
                                    } else {
                                        errorMessage = "PIN konfirmasi tidak cocok! Silakan ulangi."
                                        confirmPinInput = ""
                                    }
                                }
                            }
                        }
                    }
                    PinDialogMode.CHANGE_PIN -> {
                        when (step) {
                            0 -> {
                                if (oldPinInput.length < 4) {
                                    val next = oldPinInput + key
                                    oldPinInput = next
                                    if (next.length == 4) {
                                        isVerifying = true
                                        coroutineScope.launch {
                                            val valid = onVerifyPin?.invoke(next) ?: true
                                            isVerifying = false
                                            if (valid) {
                                                step = 1
                                            } else {
                                                errorMessage = "PIN lama tidak sesuai!"
                                                oldPinInput = ""
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (newPinInput.length < 4) {
                                    val next = newPinInput + key
                                    newPinInput = next
                                    if (next.length == 4) {
                                        step = 2
                                    }
                                }
                            }
                            2 -> {
                                if (confirmPinInput.length < 4) {
                                    val next = confirmPinInput + key
                                    confirmPinInput = next
                                    if (next.length == 4) {
                                        if (next == newPinInput) {
                                            showSecurityQuestionStep = true
                                        } else {
                                            errorMessage = "PIN konfirmasi tidak cocok! Silakan ulangi."
                                            confirmPinInput = ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSuccess) Color(0xFF2E7D32).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isSuccess -> Icons.Default.CheckCircle
                                isForgotPinMode -> Icons.Default.Help
                                mode == PinDialogMode.UNLOCK -> Icons.Default.Lock
                                else -> Icons.Default.Security
                            },
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!showSecurityQuestionStep && !showExtensionStep && !isForgotPinMode) {
                            IconButton(onClick = { showDigits = !showDigits }) {
                                Icon(
                                    imageVector = if (showDigits) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title & Instructions
                val title = when {
                    isForgotPinMode -> "Pemulihan Lupa PIN"
                    showSecurityQuestionStep -> "Pertanyaan Keamanan"
                    showExtensionStep -> "Ekstensi Enkripsi Video"
                    else -> when (mode) {
                        PinDialogMode.UNLOCK -> "Mode Kunci (.1ca Vault)"
                        PinDialogMode.SETUP_NEW -> if (step == 0) "Buat 4-Digit PIN Keamanan" else "Konfirmasi PIN Baru"
                        PinDialogMode.CHANGE_PIN -> when (step) {
                            0 -> "Masukkan PIN Saat Ini"
                            1 -> "Masukkan PIN Baru"
                            else -> "Konfirmasi PIN Baru"
                        }
                    }
                }

                val subtitle = when {
                    isForgotPinMode -> savedSecurityQuestion ?: "Masukkan jawaban pertanyaan keamanan Anda"
                    showSecurityQuestionStep -> "Pertanyaan ini digunakan untuk memulihkan PIN jika Anda lupa"
                    showExtensionStep -> "Ekstensi ini membaca file video terenkripsi di folder lokal & jaringan"
                    else -> when (mode) {
                        PinDialogMode.UNLOCK -> "Masukkan 4-digit PIN untuk membuka video terenkripsi"
                        PinDialogMode.SETUP_NEW -> if (step == 0) "PIN akan disimpan permanen & melindungi video terenkripsi" else "Ketik ulang PIN yang sama untuk verifikasi"
                        PinDialogMode.CHANGE_PIN -> when (step) {
                            0 -> "Verifikasi PIN lama Anda terlebih dahulu"
                            1 -> "Tentukan 4-digit PIN keamanan baru"
                            else -> "Ketik ulang PIN baru untuk verifikasi"
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (isForgotPinMode) {
                    // Forgot PIN Security Question Answer Input
                    OutlinedTextField(
                        value = forgotPinAnswerInput,
                        onValueChange = {
                            forgotPinAnswerInput = it
                            errorMessage = null
                        },
                        label = { Text("Jawaban Pertanyaan Keamanan") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isForgotPinMode = false }) {
                            Text("Kembali ke PIN")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (forgotPinAnswerInput.isBlank()) {
                                    errorMessage = "Jawaban tidak boleh kosong!"
                                    return@Button
                                }
                                isVerifying = true
                                coroutineScope.launch {
                                    val valid = onVerifySecurityAnswer?.invoke(forgotPinAnswerInput) ?: false
                                    isVerifying = false
                                    if (valid) {
                                        isSuccess = true
                                        delay(300)
                                        onPinSuccess("")
                                    } else {
                                        errorMessage = "Jawaban salah! Silakan periksa kembali."
                                    }
                                }
                            },
                            enabled = !isVerifying && !isSuccess
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Verifikasi Jawaban")
                            }
                        }
                    }
                } else if (showSecurityQuestionStep) {
                    // Security Question & Answer Setup
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedPresetQuestion,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Pilih Pertanyaan Keamanan") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                presetQuestions.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            selectedPresetQuestion = item
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedPresetQuestion == presetQuestions.last()) {
                            OutlinedTextField(
                                value = customQuestionText,
                                onValueChange = { customQuestionText = it },
                                label = { Text("Tulis Pertanyaan Sendiri") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = securityAnswerInput,
                            onValueChange = {
                                securityAnswerInput = it
                                errorMessage = null
                            },
                            label = { Text("Jawaban Keamanan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showSecurityQuestionStep = false }) {
                                Text("Kembali")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val finalQuestion = if (selectedPresetQuestion == presetQuestions.last()) {
                                        customQuestionText.trim()
                                    } else {
                                        selectedPresetQuestion
                                    }

                                    if (finalQuestion.isBlank()) {
                                        errorMessage = "Pertanyaan tidak boleh kosong!"
                                        return@Button
                                    }
                                    if (securityAnswerInput.trim().isBlank()) {
                                        errorMessage = "Jawaban tidak boleh kosong!"
                                        return@Button
                                    }

                                    showSecurityQuestionStep = false
                                    showExtensionStep = true
                                }
                            ) {
                                Text("Lanjut Ke Ekstensi")
                            }
                        }
                    }
                } else if (showExtensionStep) {
                    // Extension input view
                    OutlinedTextField(
                        value = customExtInput,
                        onValueChange = {
                            customExtInput = it
                            errorMessage = null
                        },
                        label = { Text("Ekstensi Video") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .testTag("custom_extension_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isSuccess = true
                                coroutineScope.launch {
                                    delay(200)
                                    val cleanExt = customExtInput.trim().removePrefix(".").lowercase().ifBlank { "1ca" }
                                    val existingExts = initialVaultExtension.split(",")
                                        .map { it.trim().removePrefix(".").lowercase() }
                                        .filter { it.isNotBlank() }

                                    val finalQuestion = if (selectedPresetQuestion == presetQuestions.last()) customQuestionText.trim() else selectedPresetQuestion
                                    val finalAnswer = securityAnswerInput.trim()

                                    if (existingExts.contains(cleanExt)) {
                                        // Extension already pinned -> Do not duplicate, just unlock/save
                                        if (onPinExtensionAndQuestionSuccess != null) {
                                            onPinExtensionAndQuestionSuccess(newPinInput, initialVaultExtension, finalQuestion, finalAnswer)
                                        } else if (onPinAndExtensionSuccess != null) {
                                            onPinAndExtensionSuccess(newPinInput, initialVaultExtension)
                                        } else {
                                            onPinSuccess(newPinInput)
                                        }
                                    } else {
                                        if (onPinExtensionAndQuestionSuccess != null) {
                                            onPinExtensionAndQuestionSuccess(newPinInput, cleanExt, finalQuestion, finalAnswer)
                                        } else if (onPinAndExtensionSuccess != null) {
                                            onPinAndExtensionSuccess(newPinInput, cleanExt)
                                        } else {
                                            onPinSuccess(newPinInput)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("save_pin_extension_button")
                        ) {
                            Text("Simpan & Buka Kunci")
                        }
                    }
                } else {
                    // PIN indicator dots / numbers
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < currentInput.length
                            val digitChar = if (isFilled && showDigits) currentInput[i].toString() else null

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSuccess -> Color(0xFF2E7D32)
                                            isFilled -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (digitChar != null) {
                                    Text(
                                        text = digitChar,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Error or verifying progress
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        } else if (isSuccess) {
                            Text(
                                text = if (mode == PinDialogMode.UNLOCK) "PIN Benar! Membuka Vault..." else "PIN Berhasil Disimpan!",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Numpad Grid
                    val numRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    for (row in numRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (key) {
                                                "C", "DEL" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .testTag("pin_key_$key")
                                        .clickable { handleKeyInput(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (key) {
                                        "DEL" -> {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Hapus Karakter",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        "C" -> {
                                            Text(
                                                text = "C",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (mode == PinDialogMode.UNLOCK) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                isForgotPinMode = true
                                errorMessage = null
                            }
                        ) {
                            Text("Lupa PIN?", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
