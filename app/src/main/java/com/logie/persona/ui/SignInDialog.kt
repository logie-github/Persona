package com.logie.persona.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.logie.persona.backend.steam.GuardKind
import com.logie.persona.install.InstallCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one-time Steam sign-in, never shown again once it has worked. Two ways in: APPROVE (a QR code and a button that opens the Steam app, nothing typed)
 * and ACCOUNT (name and password, then the Guard code if it asks).
 */
@Composable
fun SignInDialog(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val request by InstallCenter.code.collectAsState()
    val waiting by InstallCenter.waitingForApproval.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }

    fun signIn(preferCode: Boolean) {
        busy = true; error = null
        job = scope.launch {
            val why = InstallCenter.signIn(context, name, password.toCharArray(), preferCode)
            busy = false; error = why
            if (why == null) onDone(true)
        }
    }

    // Approving: starts when this tab is shown (and again on a retry), and closes the dialog when the approval arrives.
    LaunchedEffect(mode, attempt) {
        if (mode != 0) return@LaunchedEffect
        link = null; error = null
        val why = InstallCenter.signInWithApproval(context) { link = it }
        if (why == null) onDone(true) else error = why
    }

    request?.let { r ->
        var code by remember(r) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { r.reply.complete(null) },
            title = { Text(if (r.kind == GuardKind.EMAIL_CODE) "Code from your email" else "Code from the Steam app") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { if (!busy) { mode = 0; error = null } }, label = { Text("Approve") })
                    FilterChip(selected = mode == 1, onClick = { if (!busy) { mode = 1; error = null } }, label = { Text("Account") })
                }
                Spacer(Modifier.height(12.dp))
                if (mode == 0) {
                    val bitmap = remember(link) { link?.let { qrBitmap(it).asImageBitmap() } }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bitmap != null) Image(bitmap, null, Modifier.size(200.dp), filterQuality = FilterQuality.None)
                        else if (error == null) CircularProgressIndicator(Modifier.size(48.dp))
                        link?.let { l ->
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open the Steam app") }
                        }
                    }
                } else {
                    if (waiting) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Approve the sign-in in the Steam app")
                        TextButton(onClick = { job?.cancel(); signIn(preferCode = true) }) { Text("Use a code instead") }
                    } else {
                        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Account name") }, enabled = !busy)
                        OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(), enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (mode == 1) TextButton(enabled = !busy && name.isNotBlank() && password.isNotEmpty(), onClick = { signIn(preferCode = false) }) { Text(if (busy) "Signing in" else "Sign in") }
            else if (error != null) TextButton(onClick = { attempt++ }) { Text("Try again") }
        },
        dismissButton = { TextButton(onClick = { job?.cancel(); onDone(false) }, enabled = !busy || waiting) { Text("Cancel") } },
    )
}
