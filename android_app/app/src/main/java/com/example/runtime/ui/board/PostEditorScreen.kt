package com.example.runtime.ui.board

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.runtime.R
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.theme.Spacing

@Composable
fun PostEditorScreen(
    boardViewModel: BoardViewModel,
    editingPostId: Int?,
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
) {
    val editing = boardViewModel.detail?.takeIf { editingPostId != null && it.id == editingPostId }
    var title by rememberSaveable { mutableStateOf(editing?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(editing?.body.orEmpty()) }
    var anonymous by rememberSaveable { mutableStateOf(editing?.anonymous ?: true) }
    val isSubmitting = boardViewModel.submitState == BoardSubmitState.Submitting

    LaunchedEffect(Unit) {
        boardViewModel.resetSubmit()
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                stringResource(if (editingPostId == null) R.string.write_post_title else R.string.edit_post_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { boardViewModel.submitPost(editingPostId, title, body, anonymous, onSaved) },
                enabled = title.isNotBlank() && body.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.padding(end = Spacing.s)
            ) {
                Text(stringResource(if (isSubmitting) R.string.saving else R.string.action_done))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_POST_TITLE_LENGTH).replace("\n", " ") },
                placeholder = { Text(stringResource(R.string.label_post_title)) },
                singleLine = true,
                keyboardOptions = doneKeyboardOptions(),
                keyboardActions = hideKeyboardActions(),
                textStyle = MaterialTheme.typography.titleMedium,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(MAX_POST_BODY_LENGTH) },
                placeholder = { Text(stringResource(R.string.label_post_body)) },
                minLines = 10,
                supportingText = {
                    Text("${body.length}/$MAX_POST_BODY_LENGTH", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            if (editingPostId == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { anonymous = !anonymous }
                ) {
                    Checkbox(checked = anonymous, onCheckedChange = { anonymous = it })
                    Text(stringResource(R.string.board_post_anonymously), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                stringResource(R.string.board_rules),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s)
            )
            (boardViewModel.submitState as? BoardSubmitState.Failed)?.let {
                Text(it.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
