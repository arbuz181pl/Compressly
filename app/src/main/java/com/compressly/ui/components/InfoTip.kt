package com.compressly.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small "?" affordance that explains a setting in plain language.
 *
 * Rendered as a dialog rather than inline help so the editor stays uncluttered, and so the
 * explanations do not shift the layout as the user works through the options.
 */
@Composable
fun InfoTip(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    var showing by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showing = true },
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "About $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { showing = false }) { Text("Got it") }
            },
        )
    }
}