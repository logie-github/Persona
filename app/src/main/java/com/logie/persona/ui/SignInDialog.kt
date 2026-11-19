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
import com.logie.persona.install.InstallCenter.SignInUi

/**
 * The one-time Steam sign-in, never shown again once it has worked. Two ways in: APPROVE (a QR code and a button that opens the Steam app, nothing typed)
 * and ACCOUNT (name and password, then approval in the Steam app or the Guard code). The sign-in itself runs in [InstallCenter], kept alive while the person is
 * in the Steam app approving it; this screen only shows where it is.
 */
@Composable
fun SignInDialog(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    val ui by InstallCenter.signInUi.collectAsState()
    val request by InstallCenter.code.collectAsState()
    val waiting by InstallCenter.waitingForApproval.collectAsState()
    val busy = ui is SignInUi.Working || (mode == 0 && ui is SignInUi.Link)
    val error = (ui as? SignInUi.Failed)?.why
    val link = (ui as? SignInUi.Link)?.url

    // Approving starts when that tab is shown (and on a retry); choosing the other tab stops it.
    LaunchedEffect(mode, attempt) {
        if (mode == 0) { if (attempt > 0 || !InstallCenter.isApproving()) InstallCenter.startApproval(context) }
        else if (InstallCenter.isApproving()) InstallCenter.cancelSignIn(context)
    }
    LaunchedEffect(ui) { if (ui is SignInUi.Done) { InstallCenter.resetSignIn(); onDone(true) } }
    fun close() { InstallCenter.cancelSignIn(context); onDone(false) }

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
        onDismissRequest = { close() },
        title = { Text("Sign in to Steam") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Approve") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Account") })
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
                } else if (waiting) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Approve the sign-in in the Steam app")
                        TextButton(onClick = { InstallCenter.startAccount(context, name, password, preferCode = true) }) { Text("Use a code instead") }
                    }
                } else {
                    val typing = ui !is SignInUi.Working
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Account name") }, enabled = typing)
                    OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(), enabled = typing,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (mode == 1) TextButton(enabled = ui !is SignInUi.Working && name.isNotBlank() && password.isNotEmpty(),
                onClick = { InstallCenter.startAccount(context, name, password, preferCode = false) }) { Text(if (ui is SignInUi.Working) "Signing in" else "Sign in") }
            else if (error != null) TextButton(onClick = { attempt++ }) { Text("Try again") }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancel") } },
    )
}
