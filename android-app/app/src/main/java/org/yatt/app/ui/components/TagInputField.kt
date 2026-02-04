package org.yatt.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    tags: List<String>,
    placeholder: String,
    onSubmit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, tags) {
        if (value.isBlank()) {
            tags.take(10)
        } else {
            tags.filter { it.contains(value, ignoreCase = true) }.take(10)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(placeholder) },
            modifier = modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() })
        )

        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag) },
                    onClick = {
                        onValueChange(tag)
                        expanded = false
                    }
                )
            }
        }
    }
}
