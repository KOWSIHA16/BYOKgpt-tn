package com.example.byokgpt.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.byokgpt.api.AiClientFactory
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ModelCatalog
import com.example.byokgpt.model.ProviderConfig
import com.example.byokgpt.security.SecureKeyStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySetupDialog(
    initialProvider: AiProvider = AiProvider.GEMINI,
    secureKeyStorage: SecureKeyStorage,
    onDismiss: () -> Unit,
    onSaved: (AiProvider) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var apiKey by remember(selectedProvider) { mutableStateOf(secureKeyStorage.getApiKey(selectedProvider)) }
    var customEndpoint by remember(selectedProvider) { mutableStateOf(secureKeyStorage.getCustomEndpoint(selectedProvider)) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isTestSuccess by remember { mutableStateOf(false) }

    var currentStatusInfo by remember(selectedProvider) {
        mutableStateOf(secureKeyStorage.getProviderStatus(selectedProvider))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.sec_providers_keys),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Credentials are encrypted on-device via Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Provider Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    val isSelected = provider == selectedProvider
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                selectedProvider = provider
                                testStatus = null
                                currentStatusInfo = secureKeyStorage.getProviderStatus(provider)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = provider.iconResId),
                                contentDescription = provider.displayName,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (provider) {
                                    AiProvider.GEMINI -> "Gemini"
                                    AiProvider.CLAUDE -> "Claude"
                                    AiProvider.OPENAI -> "OpenAI"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Connection Status Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (currentStatusInfo.status) {
                            com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED -> Color(0xFF10B981).copy(alpha = 0.12f)
                            com.example.byokgpt.model.ProviderConnectionStatus.CHECKING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            com.example.byokgpt.model.ProviderConnectionStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (currentStatusInfo.status) {
                                com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED -> Color(0xFF10B981)
                                com.example.byokgpt.model.ProviderConnectionStatus.CHECKING -> MaterialTheme.colorScheme.primary
                                com.example.byokgpt.model.ProviderConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status: ${currentStatusInfo.status.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (currentStatusInfo.status) {
                            com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED -> Color(0xFF10B981)
                            com.example.byokgpt.model.ProviderConnectionStatus.CHECKING -> MaterialTheme.colorScheme.primary
                            com.example.byokgpt.model.ProviderConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (currentStatusInfo.message.isNotBlank()) {
                        Text(
                            text = currentStatusInfo.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Get Key link button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.docsUrl))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Get an API key from ${selectedProvider.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open docs",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // API Key Input
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    testStatus = null
                },
                label = { Text("${selectedProvider.displayName} API Key") },
                placeholder = { Text(selectedProvider.apiKeyPlaceholder) },
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isKeyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Endpoint Input (Optional)
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = { customEndpoint = it },
                label = { Text(stringResource(R.string.custom_endpoint_label)) },
                placeholder = { Text(selectedProvider.defaultEndpoint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connection Test Feedback Badge
            if (testStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isTestSuccess) Color(0xFF10B981).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isTestSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = testStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTestSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Buttons: Test and Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (apiKey.isBlank()) {
                            testStatus = "Please enter an API key to test."
                            isTestSuccess = false
                            return@OutlinedButton
                        }
                        isTesting = true
                        testStatus = "Testing connection..."
                        currentStatusInfo = com.example.byokgpt.model.ProviderStatusInfo(
                            status = com.example.byokgpt.model.ProviderConnectionStatus.CHECKING,
                            message = "Validating key with ${selectedProvider.displayName}..."
                        )
                        scope.launch {
                            val client = AiClientFactory.getClient(selectedProvider)
                            val config = ProviderConfig(
                                provider = selectedProvider,
                                apiKey = apiKey.trim(),
                                customEndpoint = customEndpoint.trim()
                            )
                            val result = client.testConnection(config, selectedProvider.defaultModel)
                            isTesting = false
                            if (result.isSuccess) {
                                isTestSuccess = true
                                val successMsg = "Connected successfully. Your API key is valid."
                                testStatus = successMsg
                                secureKeyStorage.saveApiKey(selectedProvider, apiKey)
                                secureKeyStorage.saveCustomEndpoint(selectedProvider, customEndpoint)
                                secureKeyStorage.saveProviderStatus(
                                    selectedProvider,
                                    com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED,
                                    successMsg
                                )
                                currentStatusInfo = secureKeyStorage.getProviderStatus(selectedProvider)
                            } else {
                                isTestSuccess = false
                                val rawErr = result.exceptionOrNull()?.message ?: "Connection failed"
                                val failMsg = "Connection failed. Please check your API key or network."
                                testStatus = "$failMsg ($rawErr)"
                                secureKeyStorage.saveProviderStatus(
                                    selectedProvider,
                                    com.example.byokgpt.model.ProviderConnectionStatus.FAILED,
                                    rawErr
                                )
                                currentStatusInfo = secureKeyStorage.getProviderStatus(selectedProvider)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.test_connection))
                    }
                }

                Button(
                    onClick = {
                        val trimmedKey = apiKey.trim()
                        if (trimmedKey.isNotEmpty()) {
                            secureKeyStorage.saveApiKey(selectedProvider, trimmedKey)
                            secureKeyStorage.saveCustomEndpoint(selectedProvider, customEndpoint)
                            if (currentStatusInfo.status != com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED) {
                                secureKeyStorage.saveProviderStatus(
                                    selectedProvider,
                                    com.example.byokgpt.model.ProviderConnectionStatus.UNKNOWN,
                                    "API key saved. Tap Test Connection to verify."
                                )
                            }
                        } else {
                            secureKeyStorage.removeApiKey(selectedProvider)
                        }
                        onSaved(selectedProvider)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            if (secureKeyStorage.hasApiKey(selectedProvider)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            secureKeyStorage.removeApiKey(selectedProvider)
                            apiKey = ""
                            testStatus = "API key removed."
                            isTestSuccess = false
                            currentStatusInfo = secureKeyStorage.getProviderStatus(selectedProvider)
                            onSaved(selectedProvider)
                        }
                    ) {
                        Text(
                            text = "Remove ${selectedProvider.displayName} Key",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
