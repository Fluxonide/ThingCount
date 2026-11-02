package com.ranit.thingcount.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranit.thingcount.data.model.Counter
import com.ranit.thingcount.data.model.ResetPeriod
import com.ranit.thingcount.ui.components.CounterIconView
import com.ranit.thingcount.ui.theme.CounterColors
import java.io.File
import java.io.FileOutputStream

private val PRESET_EMOJIS = listOf("🎯", "💧", "📖", "🏋️", "☕", "💰", "💊", "🚶", "⭐️", "🔥", "🧘", "🍏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterEditDialog(
    counter: Counter? = null,
    onDismiss: () -> Unit,
    onSave: (Counter) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(counter?.title ?: "") }
    var emoji by remember { mutableStateOf(counter?.emoji ?: "🎯") }
    var tag by remember { mutableStateOf(counter?.tag ?: "General") }
    var startValueText by remember { mutableStateOf(counter?.startValue?.toString() ?: "0") }
    var stepText by remember { mutableStateOf(counter?.step?.toString() ?: "1") }
    var selectedColor by remember { mutableLongStateOf(counter?.colorHex ?: CounterColors.first()) }
    var resetPeriod by remember { mutableStateOf(counter?.autoResetPeriod ?: ResetPeriod.NONE) }

    var iconMode by remember { mutableIntStateOf(0) } // 0: Emoji, 1: Direct Link, 2: Upload
    var customUrlText by remember {
        mutableStateOf(if (emoji.startsWith("http://") || emoji.startsWith("https://")) emoji else "")
    }

    var resetDropdownExpanded by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val iconFile = File(context.filesDir, "custom_icon_${System.currentTimeMillis()}.png")
                val outputStream = FileOutputStream(iconFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                emoji = iconFile.absolutePath
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (counter == null) "New Counter" else "Edit Counter",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon / Emoji Selector with Live Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CounterIconView(icon = emoji, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Counter Icon", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Choose an emoji, paste link, or upload an image", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Icon Source Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = iconMode == 0,
                        onClick = { iconMode = 0 },
                        label = { Text("Emoji") }
                    )
                    FilterChip(
                        selected = iconMode == 1,
                        onClick = { iconMode = 1 },
                        label = { Text("Link") }
                    )
                    FilterChip(
                        selected = iconMode == 2,
                        onClick = {
                            iconMode = 2
                            imagePickerLauncher.launch("image/*")
                        },
                        label = { Text("Upload") }
                    )
                }

                when (iconMode) {
                    0 -> {
                        // Preset Emojis
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PRESET_EMOJIS.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (emoji == item) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (emoji == item) 2.dp else 1.dp,
                                            color = if (emoji == item) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = CircleShape
                                        )
                                        .clickable { emoji = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(item, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    1 -> {
                        // Direct Image URL
                        OutlinedTextField(
                            value = customUrlText,
                            onValueChange = {
                                customUrlText = it
                                if (it.isNotBlank()) emoji = it.trim()
                            },
                            label = { Text("Image Direct URL (https://...)") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        // Upload / Pick Image
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Image from Gallery")
                        }
                    }
                }

                // Category / Tag
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Category / Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Values row: Start value & Step
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startValueText,
                        onValueChange = { startValueText = it },
                        label = { Text("Start Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = stepText,
                        onValueChange = { stepText = it },
                        label = { Text("Step (±)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Color Selection
                Text("Accent Color", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CounterColors.forEach { colorValue ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue))
                                .border(
                                    width = if (selectedColor == colorValue) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorValue }
                        )
                    }
                }

                // Auto Reset Dropdown
                ExposedDropdownMenuBox(
                    expanded = resetDropdownExpanded,
                    onExpandedChange = { resetDropdownExpanded = !resetDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = when (resetPeriod) {
                            ResetPeriod.NONE -> "No Auto Reset"
                            ResetPeriod.DAILY -> "Reset Daily"
                            ResetPeriod.WEEKLY -> "Reset Weekly"
                            ResetPeriod.MONTHLY -> "Reset Monthly"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auto Reset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resetDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = resetDropdownExpanded,
                        onDismissRequest = { resetDropdownExpanded = false }
                    ) {
                        ResetPeriod.values().forEach { period ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (period) {
                                            ResetPeriod.NONE -> "No Auto Reset"
                                            ResetPeriod.DAILY -> "Reset Daily"
                                            ResetPeriod.WEEKLY -> "Reset Weekly"
                                            ResetPeriod.MONTHLY -> "Reset Monthly"
                                        }
                                    )
                                },
                                onClick = {
                                    resetPeriod = period
                                    resetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startVal = startValueText.toLongOrNull() ?: 0L
                    val stepVal = stepText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L

                    val finalIcon = if (iconMode == 1 && customUrlText.isNotBlank()) customUrlText.trim() else emoji

                    val updated = counter?.copy(
                        title = title.ifBlank { "Counter" },
                        emoji = finalIcon.ifBlank { "🎯" },
                        tag = tag.ifBlank { "General" },
                        startValue = startVal,
                        step = stepVal,
                        colorHex = selectedColor,
                        autoResetPeriod = resetPeriod
                    ) ?: Counter(
                        title = title.ifBlank { "Counter" },
                        emoji = finalIcon.ifBlank { "🎯" },
                        tag = tag.ifBlank { "General" },
                        currentValue = startVal,
                        startValue = startVal,
                        step = stepVal,
                        colorHex = selectedColor,
                        autoResetPeriod = resetPeriod
                    )

                    onSave(updated)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
