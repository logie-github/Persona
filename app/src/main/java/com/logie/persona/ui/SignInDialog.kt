package com.logie.persona.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.logie.persona.backend.steam.GuardKind
import com.logie.persona.install.InstallCenter
import kotlinx.coroutines.launch

/** The one-time Steam sign-in: name, password, then the Steam Guard code if it asks. Never shown again once it has worked. */
@Composable
fun SignInDialog(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val request by InstallCenter.code.collectAsState()

    request?.let { r ->
        var code by remember(r) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { r.reply.complete(null) },
            title = { Text(if (r.kind == GuardKind.EMAIL_CODE) "Code from your email" else "Steam Guard code") },
            text = {
                OutlinedTextField(code, { code = it.trim() }, singleLine = true, isError = r.previousWasWrong,
                    label = { Text(if (r.previousWasWrong) "That code didn't work" else "Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            },
            confirmButton = { TextButton(onClick = { r.reply.complete(code) }, enabled = code.isNotEmpty()) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { r.reply.complete(null) }) { Text("Cancel") } },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        title = { Text("Sign in to Steam") },
        text = {
            Column {
                Text("Once, to download your games. The password isn't kept.")
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Account name") }, enabled = !busy)
                OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (failed) Text("Sign-in failed.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && password.isNotEmpty(), onClick = {
                busy = true; failed = false
                scope.launch {
                    val ok = InstallCenter.signIn(context, name, password.toCharArray())
                    busy = false; failed = !ok
                    if (ok) onDone(true)
                }
            }) { Text(if (busy) "Signing in" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("Cancel") } },
    )
}
