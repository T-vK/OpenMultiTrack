package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmultitrack.domain.mixer.MixerRoutingConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSettingsSheet(
    mixerName: String,
    channelCount: Int,
    config: MixerRoutingConfig,
    onDismiss: () -> Unit,
    onSave: (MixerRoutingConfig) -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "$mixerName — routing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Override how USB inputs map to channel strips, outputs for soundcheck, and which channels are hidden per mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SectionTitle("Input matrix (record)")
            if (channelCount <= 0) {
                Text("No channels detected yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                (0 until channelCount).forEach { logical ->
                    MappingRow(
                        label = "Strip ${logical + 1} ← USB in",
                        value = draft.inputMap[logical]?.let { it + 1 }?.toString() ?: (logical + 1).toString(),
                        onValueChange = { text ->
                            val usb = text.toIntOrNull()?.minus(1) ?: return@MappingRow
                            draft = draft.copy(inputMap = draft.inputMap + (logical to usb.coerceAtLeast(0)))
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Output matrix (soundcheck)")
            if (channelCount > 0) {
                (0 until channelCount).forEach { logical ->
                    MappingRow(
                        label = "Strip ${logical + 1} → USB out",
                        value = draft.outputMap[logical]?.let { it + 1 }?.toString() ?: (logical + 1).toString(),
                        onValueChange = { text ->
                            val usb = text.toIntOrNull()?.minus(1) ?: return@MappingRow
                            draft = draft.copy(outputMap = draft.outputMap + (logical to usb.coerceAtLeast(0)))
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Hidden channels — recording")
            HiddenChannelChecks(channelCount, draft.hiddenRecord) { hidden ->
                draft = draft.copy(hiddenRecord = hidden)
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Hidden channels — soundcheck")
            HiddenChannelChecks(channelCount, draft.hiddenSoundcheck) { hidden ->
                draft = draft.copy(hiddenSoundcheck = hidden)
            }

            androidx.compose.material3.Button(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun MappingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(0.28f),
            singleLine = true,
        )
    }
}

@Composable
private fun HiddenChannelChecks(
    channelCount: Int,
    hidden: Set<Int>,
    onChange: (Set<Int>) -> Unit,
) {
    if (channelCount <= 0) {
        Text("No channels.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    (0 until channelCount).forEach { ch ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = ch in hidden,
                onCheckedChange = { checked ->
                    onChange(if (checked) hidden + ch else hidden - ch)
                },
            )
            Text("Hide channel ${ch + 1}")
        }
    }
}
