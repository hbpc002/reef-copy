package dev.pranav.reef.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.pranav.reef.util.prefs
import java.security.MessageDigest

@Composable
fun PasswordSettingsContent(
    onBackPressed: () -> Unit
) {
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var storedPinHash by remember { mutableStateOf(prefs.getString("app_lock_pin_hash", null)) }
    var currentPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                title = { Text("App Lock") },
                navigationIcon = {
                    TextButton(onClick = onBackPressed) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                ListItem(
                    headlineContent = {
                        Text("App Lock", style = MaterialTheme.typography.titleMedium)
                    },
                    supportingContent = {
                        Text("Require a PIN to open Reef", style = MaterialTheme.typography.bodySmall)
                    },
                    trailingContent = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                prefs.edit { putBoolean("app_lock_enabled", enabled) }
                                if (!enabled) {
                                    success = "App lock disabled"
                                    error = null
                                }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Spacer(Modifier.height(16.dp))

            if (appLockEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (storedPinHash != null) "Change PIN" else "Set PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {
                                if (it.length <= 6) currentPin = it.filter { c -> c.isDigit() }
                            },
                            label = { Text("New PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = {
                                if (it.length <= 6) confirmPin = it.filter { c -> c.isDigit() }
                            },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        success?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                when {
                                    currentPin.length < 4 -> error = "PIN must be at least 4 digits"
                                    currentPin != confirmPin -> error = "PINs do not match"
                                    else -> {
                                        val hash = hashPin(currentPin)
                                        prefs.edit {
                                            putString("app_lock_pin_hash", hash)
                                            putBoolean("app_lock_enabled", true)
                                        }
                                        storedPinHash = hash
                                        appLockEnabled = true
                                        currentPin = ""
                                        confirmPin = ""
                                        error = null
                                        success = "PIN saved successfully"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Save PIN")
                        }

                        if (storedPinHash != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    prefs.edit {
                                        remove("app_lock_pin_hash")
                                        putBoolean("app_lock_enabled", false)
                                    }
                                    storedPinHash = null
                                    appLockEnabled = false
                                    currentPin = ""
                                    confirmPin = ""
                                    error = null
                                    success = "PIN removed"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove PIN")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun verifyPin(pin: String): Boolean {
    val storedHash = prefs.getString("app_lock_pin_hash", null) ?: return false
    return hashPin(pin) == storedHash
}
