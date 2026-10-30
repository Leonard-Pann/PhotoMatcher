package com.bobby.vaudoushorter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bobby.vaudoushorter.R
import com.bobby.vaudoushorter.i18n.AppLanguage
import com.bobby.vaudoushorter.i18n.LocalText
import com.bobby.vaudoushorter.ui.theme.Mint

@Composable
fun LanguageDialog(onSelect: (String) -> Unit, onClose: () -> Unit) {
    val text = LocalText.current
    val selected = AppLanguage.selected(LocalContext.current)
    AlertDialog(onDismissRequest = onClose, title = { Text(text(R.string.language)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("system" to R.string.system_language, "fr" to R.string.french, "en" to R.string.english).forEach { (code, label) ->
                Row(Modifier.fillMaxWidth().clickable { onClose(); if (selected != code) onSelect(code) }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == code, onClick = null)
                    Text(text(label), Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(text(R.string.close)) } })
}

@Composable
fun DonationDialog(onDonate: () -> Unit, onClose: () -> Unit) {
    val text = LocalText.current
    AlertDialog(onDismissRequest = onClose,
        icon = { Mark(Glyph.HEART, Mint, Modifier.size(32.dp)) },
        title = { Text(text(R.string.donate_title)) },
        text = { Text(text(R.string.donate_body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            Button(onClick = onDonate, colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                Text(text(R.string.open_github))
            }
        }, dismissButton = { TextButton(onClick = onClose) { Text(text(R.string.close)) } })
}
