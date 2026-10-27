package com.example.byokgpt.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.byokgpt.security.AppLockManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ThemeMode
import com.example.byokgpt.ui.chat.ChatViewModel
import com.example.byokgpt.ui.components.ApiKeySetupDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val settings by viewModel.settingsManager.settings.collectAsState()
    val totalMessages by viewModel.repository.totalMessageCount.collectAsState(initial = 0)
    val conversations by viewModel.conversations.collectAsState()

    var showKeyDialogFor by remember { mutableStateOf<AiProvider?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state for custom instructions
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var tempInstructions by remember { mutableStateOf(settings.customInstructions) }

    // Dialog state for user nickname
    var showNicknameDialog by remember { mutableStateOf(false) }
    var tempNickname by remember { mutableStateOf(settings.userName) }

    // Dialog state for AI Response Language
    var showLanguageDialog by remember { mutableStateOf(false) }

    val memories by viewModel.memories.collectAsState()
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var newMemoryKey by remember { mutableStateOf("") }
    var newMemoryContent by remember { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<com.example.byokgpt.data.MemoryEntity?>(null) }
    var editMemoryKey by remember { mutableStateOf("") }
    var editMemoryContent by remember { mutableStateOf("") }
    var showClearMemoriesDialog by remember { mutableStateOf(false) }

    // App Lock & Biometrics states
    var isAppLockEnabled by remember {
        mutableStateOf(viewModel.secureKeyStorage.isAppLockEnabled() && viewModel.secureKeyStorage.hasPin())
    }
    var isBiometricEnabled by remember {
        mutableStateOf(viewModel.secureKeyStorage.isBiometricEnabled())
    }
    val isBiometricsSupported = remember { AppLockManager.isBiometricsSupported(context) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var disablePinInput by remember { mutableStateOf("") }
    var disablePinError by remember { mutableStateOf<String?>(null) }

    // Image Generation Provider state
    var selectedImageProvider by remember {
        mutableStateOf(viewModel.secureKeyStorage.getImageGenProvider())
    }
    var showImageProviderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI Providers & API Keys
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_providers_keys),
                    icon = Icons.Default.Key
                ) {
                    AiProvider.entries.forEachIndexed { index, provider ->
                        val statusInfo = viewModel.secureKeyStorage.getProviderStatus(provider)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showKeyDialogFor = provider }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Image(
                                    painter = painterResource(id = provider.iconResId),
                                    contentDescription = provider.displayName,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (statusInfo.status) {
                                                        com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED -> Color(0xFF10B981)
                                                        com.example.byokgpt.model.ProviderConnectionStatus.CHECKING -> MaterialTheme.colorScheme.primary
                                                        com.example.byokgpt.model.ProviderConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                                                        com.example.byokgpt.model.ProviderConnectionStatus.UNKNOWN -> Color(0xFFF59E0B)
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = statusInfo.status.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (statusInfo.status) {
                                                com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED -> Color(0xFF10B981)
                                                com.example.byokgpt.model.ProviderConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                                                com.example.byokgpt.model.ProviderConnectionStatus.UNKNOWN -> Color(0xFFF59E0B)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Configure",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Configure",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (index < AiProvider.entries.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // Section 2: AI Personalization and Custom Instructions
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_personalization),
                    icon = Icons.Default.Tune
                ) {
                    SettingClickableItem(
                        title = "Custom System Instructions",
                        subtitle = if (settings.customInstructions.isBlank()) "Default AI assistant tone"
                        else settings.customInstructions.take(40) + "…",
                        onClick = {
                            tempInstructions = settings.customInstructions
                            showInstructionsDialog = true
                        }
                    )
                }
            }

            // Section 3: Memory and Memory Summary
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_memory),
                    icon = Icons.Default.Memory
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Memory Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Active conversation context is preserved across turns using local session memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 4: Appearance
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_appearance),
                    icon = Icons.Default.BrightnessMedium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val isSelected = settings.themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.settingsManager.updateThemeMode(mode) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: General
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_general),
                    icon = Icons.Default.Person
                ) {
                    SettingClickableItem(
                        title = "User Nickname",
                        subtitle = settings.userName,
                        onClick = {
                            tempNickname = settings.userName
                            showNicknameDialog = true
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingClickableItem(
                        title = "App Language",
                        subtitle = "System Default",
                        onClick = {
                            Toast.makeText(context, "Language adapts automatically to your Android system locale.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingClickableItem(
                        title = "AI Response Language",
                        subtitle = "${settings.responseLanguage.displayName} — Tap to change",
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // Section 6: Intelligence & Web Search
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_intelligence),
                    icon = Icons.Default.Psychology
                ) {
                    SettingSwitchItem(
                        title = "Fast Answers",
                        subtitle = "Request direct, concise responses from models",
                        checked = settings.fastAnswers,
                        onCheckedChange = { viewModel.settingsManager.updateFastAnswers(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingSwitchItem(
                        title = "Visual Answers",
                        subtitle = "Enable Markdown rendering and code formatting",
                        checked = settings.visualAnswers,
                        onCheckedChange = { viewModel.settingsManager.updateVisualAnswers(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingSwitchItem(
                        title = "Web Search & In-App Browser",
                        subtitle = "When ON, search the web, open URLs, and ask questions about real web content using the chat composer",
                        checked = settings.webSearch,
                        onCheckedChange = { viewModel.settingsManager.updateWebSearch(it) }
                    )
                }
            }

            // Section: Memory Summary & Personalization
            item {
                SettingsSectionCard(
                    title = "Memory Summary & Personalization",
                    icon = Icons.Default.Memory
                ) {
                    SettingSwitchItem(
                        title = "Long-Term Memory",
                        subtitle = "Automatically remembers helpful user preferences and facts to personalize future chats",
                        checked = settings.memoryEnabled,
                        onCheckedChange = { viewModel.settingsManager.updateMemoryEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved Memories (${memories.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (memories.isEmpty()) "No memories saved yet. Captured facts will appear automatically, or add them manually."
                                else "${memories.size} active memories stored locally on device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            newMemoryKey = ""
                            newMemoryContent = ""
                            showAddMemoryDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (memories.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            memories.forEach { mem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = mem.key,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "[${mem.source}]",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Text(
                                            text = mem.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingMemory = mem
                                                editMemoryKey = mem.key
                                                editMemoryContent = mem.content
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteMemory(mem.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = { showClearMemoriesDialog = true },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Clear All Memories", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 7: Storage
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_storage),
                    icon = Icons.Default.Storage
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Database",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$totalMessages messages across ${conversations.size} chats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = {
                                context.cacheDir.deleteRecursively()
                                Toast.makeText(context, "App cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear Cache")
                        }
                    }
                }
            }

            // Section 8: History & Data
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_history_data),
                    icon = Icons.Default.ClearAll
                ) {
                    SettingClickableItem(
                        title = "Export Conversations",
                        subtitle = "Copy all conversation titles and message history to clipboard",
                        onClick = {
                            scope.launch {
                                val exportText = buildString {
                                    appendLine("# BYOKGPT Conversation Export")
                                    appendLine("Exported on: ${java.util.Date()}")
                                    appendLine()
                                    for (conv in conversations) {
                                        appendLine("## Chat: ${conv.title} (${conv.provider} - ${conv.modelId})")
                                        val msgs = viewModel.repository.getMessagesSnapshot(conv.id)
                                        for (m in msgs) {
                                            appendLine("**${m.role}**: ${m.content}")
                                        }
                                        appendLine()
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(exportText))
                                Toast.makeText(context, "Export copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingClickableItem(
                        title = "Delete All Chat History",
                        subtitle = "Permanently remove all chats and messages",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirmDialog = true }
                    )
                }
            }

            // Section 9: App Lock & Security
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_security),
                    icon = Icons.Default.Security
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Android Keystore Encryption",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All API keys are encrypted on-device with AES-256-GCM. Keys never leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // App PIN Lock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pin_lock_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isAppLockEnabled) "App locked with 4-digit PIN" else stringResource(R.string.pin_lock_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAppLockEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isAppLockEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    newPinInput = ""
                                    confirmPinInput = ""
                                    pinError = null
                                    showSetPinDialog = true
                                }) {
                                    Text("Change", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = {
                                    disablePinInput = ""
                                    disablePinError = null
                                    showDisablePinDialog = true
                                }) {
                                    Text("Turn Off", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            TextButton(onClick = {
                                newPinInput = ""
                                confirmPinInput = ""
                                pinError = null
                                showSetPinDialog = true
                            }) {
                                Text("Set PIN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isAppLockEnabled && isBiometricsSupported) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingSwitchItem(
                            title = stringResource(R.string.biometric_unlock_title),
                            subtitle = stringResource(R.string.biometric_unlock_desc),
                            checked = isBiometricEnabled,
                            onCheckedChange = { checked ->
                                isBiometricEnabled = checked
                                viewModel.secureKeyStorage.setBiometricEnabled(checked)
                            }
                        )
                    }
                }
            }

            // Section: Image Generation Engine
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_image_gen),
                    icon = Icons.Default.Image
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showImageProviderDialog = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Image Provider",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (selectedImageProvider) {
                                        "GEMINI" -> "Google Gemini (Imagen 3)"
                                        "OPENAI" -> "OpenAI (DALL-E 3)"
                                        else -> "Pollinations.ai (Flux - Free, No Key Required)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To generate images, type 'draw ...' or 'generate image of ...'. Anthropic Claude does not support image generation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 10: About BYOKGPT & Community Links
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.sec_about),
                    icon = Icons.Default.Info
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Version 1.0.0 (Build 1)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_app_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.community_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Community Link 1: WhatsApp
                    CommunityLinkItem(
                        iconRes = R.drawable.ic_whatsapp,
                        title = stringResource(R.string.comm_whatsapp_title),
                        description = stringResource(R.string.comm_whatsapp_desc),
                        url = "https://chat.whatsapp.com/FPBtbxDr4S9DnCoxyNiSg4"
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Community Link 2: Telegram
                    CommunityLinkItem(
                        iconRes = R.drawable.telegram,
                        title = stringResource(R.string.comm_telegram_title),
                        description = stringResource(R.string.comm_telegram_desc),
                        url = "https://t.me/+Z-Wpyhn3DbNhZmE9"
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Community Link 3: GitHub
                    CommunityLinkItem(
                        iconRes = R.drawable.github,
                        title = stringResource(R.string.comm_github_title),
                        description = stringResource(R.string.comm_github_desc),
                        url = "https://github.com/doradev26-tn"
                    )
                }
            }
        }
    }

    // API Key Setup Sheet
    if (showKeyDialogFor != null) {
        ApiKeySetupDialog(
            initialProvider = showKeyDialogFor!!,
            secureKeyStorage = viewModel.secureKeyStorage,
            onDismiss = { showKeyDialogFor = null },
            onSaved = { provider ->
                showKeyDialogFor = null
                Toast.makeText(context, "${provider.displayName} API key saved securely", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Custom Instructions Edit Dialog
    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = { Text("Custom Instructions") },
            text = {
                OutlinedTextField(
                    value = tempInstructions,
                    onValueChange = { tempInstructions = it },
                    placeholder = { Text("e.g. Always respond in bullet points, focus on concise code...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.settingsManager.updateCustomInstructions(tempInstructions)
                        showInstructionsDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Nickname Edit Dialog
    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("Your Nickname") },
            text = {
                OutlinedTextField(
                    value = tempNickname,
                    onValueChange = { tempNickname = it },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.settingsManager.updateUserName(tempNickname)
                        showNicknameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete All Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete All History?") },
            text = { Text("This will permanently remove all your conversations and saved messages. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.repository.clearAll()
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "All history deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // AI Response Language Picker Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = "AI Response Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(com.example.byokgpt.model.ResponseLanguage.entries.size) { idx ->
                        val lang = com.example.byokgpt.model.ResponseLanguage.entries[idx]
                        val isSelected = settings.responseLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.settingsManager.updateResponseLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.promptInstruction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Add Memory Dialog
    if (showAddMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Add Memory Fact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Store a preference or context that the AI should remember in future conversations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newMemoryKey,
                        onValueChange = { newMemoryKey = it },
                        label = { Text("Category / Key (e.g. Favorite Language)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newMemoryContent,
                        onValueChange = { newMemoryContent = it },
                        label = { Text("Memory Fact (e.g. Prefers Kotlin & Compose)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newMemoryKey.isNotBlank() && newMemoryContent.isNotBlank()) {
                            viewModel.addMemory(newMemoryKey.trim(), newMemoryContent.trim())
                            showAddMemoryDialog = false
                            Toast.makeText(context, "Memory saved locally", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newMemoryKey.isNotBlank() && newMemoryContent.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Memory Dialog
    editingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Edit Memory Fact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editMemoryKey,
                        onValueChange = { editMemoryKey = it },
                        label = { Text("Category / Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editMemoryContent,
                        onValueChange = { editMemoryContent = it },
                        label = { Text("Memory Fact") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editMemoryKey.isNotBlank() && editMemoryContent.isNotBlank()) {
                            viewModel.updateMemory(
                                memory.copy(
                                    key = editMemoryKey.trim(),
                                    content = editMemoryContent.trim(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            editingMemory = null
                            Toast.makeText(context, "Memory updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = editMemoryKey.isNotBlank() && editMemoryContent.isNotBlank()
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Memories Dialog
    if (showClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesDialog = false },
            title = { Text("Clear All Memories?") },
            text = {
                Text("This will permanently remove all stored facts and preferences from your device. Normal chat history will not be affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMemories()
                        showClearMemoriesDialog = false
                        Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoriesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set / Change PIN Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(if (isAppLockEnabled) "Change 4-Digit PIN" else "Set 4-Digit PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a 4-digit numeric PIN to secure BYOKGPT upon launch and return from background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                newPinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("New PIN (4 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                confirmPinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPinInput.length != 4) {
                            pinError = "PIN must be exactly 4 digits"
                        } else if (newPinInput != confirmPinInput) {
                            pinError = "PINs do not match"
                        } else {
                            viewModel.secureKeyStorage.setPin(newPinInput)
                            isAppLockEnabled = true
                            showSetPinDialog = false
                            Toast.makeText(context, "PIN configured successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disable PIN Dialog
    if (showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            title = { Text("Turn Off App PIN Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Please enter your current 4-digit PIN to disable app lock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = disablePinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                disablePinInput = it
                                disablePinError = null
                            }
                        },
                        label = { Text("Current 4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (disablePinError != null) {
                        Text(
                            text = disablePinError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.secureKeyStorage.verifyPin(disablePinInput)) {
                            viewModel.secureKeyStorage.removePin()
                            isAppLockEnabled = false
                            showDisablePinDialog = false
                            Toast.makeText(context, "App lock turned off", Toast.LENGTH_SHORT).show()
                        } else {
                            disablePinError = "Incorrect PIN. Try again."
                        }
                    }
                ) {
                    Text("Disable Lock", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Image Provider Selection Dialog
    if (showImageProviderDialog) {
        val providers = listOf(
            Triple("POLLINATIONS", "Pollinations.ai (Flux)", "Free unlimited generation, high quality, no API key needed"),
            Triple("GEMINI", "Google Gemini (Imagen 3)", "High fidelity, requires Google Gemini API key configured"),
            Triple("OPENAI", "OpenAI (DALL-E 3)", "Photorealistic, requires OpenAI API key configured")
        )

        AlertDialog(
            onDismissRequest = { showImageProviderDialog = false },
            title = { Text("Choose Default Image Engine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { (code, name, desc) ->
                        val isSelected = selectedImageProvider == code
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedImageProvider = code
                                    viewModel.secureKeyStorage.setImageGenProvider(code)
                                    showImageProviderDialog = false
                                    Toast.makeText(context, "Default image engine: $name", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageProviderDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
fun SettingClickableItem(
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun CommunityLinkItem(
    iconRes: Int,
    title: String,
    description: String,
    url: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
