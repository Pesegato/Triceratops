package com.pesegato.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pesegato.data.Token
import com.pesegato.gui.tokenColorToComposeColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CreateTokenDialog(
    onConfirm: (label: String, secret: String, color: Token.Color, number: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var numberInput by remember { mutableStateOf("2") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Token.Color.GREEN) }
    val (labelFocus, secretFocus) = remember { FocusRequester.createRefs() }

    val isNumberValid = numberInput.toIntOrNull()?.let { it >= 2 } ?: false

    Dialog(onDismissRequest = onDismiss) {
        Surface(tonalElevation = 8.dp) {
            Column(
                modifier = Modifier.padding(16.dp).width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Create New Token", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Token Label") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { secretFocus.requestFocus() }
                    ),
                    modifier = Modifier.focusRequester(labelFocus)
                )

                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Token Secret") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) "Hide password" else "Show password"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    },
                    modifier = Modifier.focusRequester(secretFocus)
                )

                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it },
                    label = { Text("Number of Parts") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isNumberValid
                )
                if (!isNumberValid) {
                    Text("Must be a number greater than or equal to 2", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                // --- Visual Color Picker ---
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(label, secret, selectedColor, numberInput.toInt()) },
                        enabled = isNumberValid && label.isNotBlank() && secret.isNotBlank()
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        labelFocus.requestFocus()
    }
}

@Composable
fun ColorPicker(selectedColor: Token.Color, onColorSelected: (Token.Color) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Token.Color.entries.forEach { color ->
            val composeColor = tokenColorToComposeColor(color)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = if (composeColor.luminance() > 0.5) Color.Black else Color.White
                    )
                }
            }
        }
    }
}
