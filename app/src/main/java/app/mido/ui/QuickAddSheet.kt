package app.mido.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.mido.parser.ParsedTask
import app.mido.parser.QuickAddParser
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAdd: (ParsedTask) -> Unit,
    initialText: String = "",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }

    val parsed: ParsedTask? = if (text.isBlank()) null else QuickAddParser.parse(text, LocalDateTime.now())

    fun submit() {
        val p = parsed ?: return
        onAdd(p)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Wait a beat so the text field is attached before asking for focus.
        LaunchedEffect(Unit) {
            delay(150)
            runCatching { focus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("e.g. take out trash every tue at 7pm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (parsed != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(parsed.title, style = MaterialTheme.typography.titleMedium)
                    parsed.due?.let {
                        Text(
                            "📅 " + Fmt.dueLabel(it, parsed.allDay, LocalDateTime.now().toLocalDate()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    parsed.repeat?.let {
                        Text(
                            "🔁 ${it.describe()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    parsed.locationName?.let {
                        Text(
                            "📍 ${Fmt.placeLabel(it, parsed.locationTrigger)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = { submit() },
                enabled = parsed != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add task") }
        }
    }
}
