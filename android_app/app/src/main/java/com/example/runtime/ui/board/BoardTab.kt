package com.example.runtime.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing

@Composable
fun BoardTab(
    boardViewModel: BoardViewModel,
    isLoggedIn: Boolean,
    onPostClick: (Int) -> Unit,
    onWrite: () -> Unit,
    onLogInRequired: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf(boardViewModel.query) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        boardViewModel.refresh()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    if (it.isEmpty()) boardViewModel.search("")
                },
                placeholder = { Text(stringResource(R.string.board_search_placeholder)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (search.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            search = ""
                            boardViewModel.search("")
                        }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    boardViewModel.search(search)
                    focusManager.clearFocus()
                }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.m)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)
            ) {
                PostFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = boardViewModel.filter == filter,
                        onClick = {
                            if (filter != PostFilter.ALL && !isLoggedIn) onLogInRequired()
                            else boardViewModel.changeFilter(filter)
                        },
                        label = { Text(stringResource(filter.label)) },
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
            boardViewModel.error?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.m)
                )
            }
            when {
                boardViewModel.isLoading && boardViewModel.posts.isEmpty() -> Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.padding(Spacing.m)
                ) {
                    repeat(5) { Box(modifier = Modifier.fillMaxWidth().height(64.dp).placeholder(MaterialTheme.shapes.medium)) }
                }
                boardViewModel.posts.isEmpty() && boardViewModel.error == null -> EmptyState(
                    icon = R.drawable.ic_edit_square,
                    title = stringResource(
                        when {
                            boardViewModel.query.isNotEmpty() -> R.string.board_no_results
                            boardViewModel.filter == PostFilter.SCRAPS -> R.string.board_no_scraps
                            boardViewModel.filter == PostFilter.MINE -> R.string.board_no_my_posts
                            else -> R.string.board_empty
                        }
                    ),
                    message = stringResource(R.string.board_empty_message),
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    itemsIndexed(boardViewModel.posts, key = { _, post -> post.id }) { index, post ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PostCard(post = post, onClick = { onPostClick(post.id) })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (isLoggedIn) onWrite() else onLogInRequired() },
            icon = { Icon(painterResource(R.drawable.ic_edit_square), contentDescription = null) },
            text = { Text(stringResource(R.string.action_write_post)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m)
        )
    }
}
