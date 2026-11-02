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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing

@Composable
fun PostDetailScreen(
    boardViewModel: BoardViewModel,
    postId: Int,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLogInRequired: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportingCommentId by remember { mutableStateOf<Int?>(null) }
    var deletingCommentId by remember { mutableStateOf<Int?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(true) }
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val reportedMessage = stringResource(R.string.report_sent_message)
    val hiddenMessage = stringResource(R.string.report_hidden_message)
    val deletedMessage = stringResource(R.string.post_deleted_message)
    val actionError = boardViewModel.actionError?.asString()

    LaunchedEffect(postId) {
        boardViewModel.loadDetail(postId)
    }
    LaunchedEffect(actionError) {
        actionError?.let {
            messages?.show(it)
            boardViewModel.clearActionError()
        }
    }
    BackHandler(onBack = onBack)

    fun requireLogin(action: () -> Unit) {
        if (isLoggedIn) action() else onLogInRequired()
    }

    val post = boardViewModel.detail?.takeIf { it.id == postId }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.board_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (post != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (post.isMine) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(if (post.reported) R.string.already_reported else R.string.action_report),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = !post.reported,
                                onClick = {
                                    showMenu = false
                                    requireLogin { showReportDialog = true }
                                }
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            if (post == null) {
                boardViewModel.detailError?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } ?: Box(modifier = Modifier.fillMaxWidth().height(200.dp).placeholder(MaterialTheme.shapes.medium))
                return@Column
            }
            PostBody(
                post = post,
                onLike = {
                    requireLogin {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        boardViewModel.toggleLike()
                    }
                },
                onScrap = { requireLogin { boardViewModel.toggleScrap() } }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = Spacing.m))
            if (post.comments.isEmpty()) {
                Text(
                    stringResource(R.string.board_no_comments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.s)
                )
            }
            post.comments.forEachIndexed { index, comment ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CommentItem(
                    comment = comment,
                    onDelete = { deletingCommentId = comment.id },
                    onReport = { requireLogin { reportingCommentId = comment.id } }
                )
            }
            Spacer(modifier = Modifier.height(Spacing.l))
        }

        if (post != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { anonymous = !anonymous }.padding(end = Spacing.xs)
                ) {
                    Checkbox(checked = anonymous, onCheckedChange = { anonymous = it })
                    Text(stringResource(R.string.board_anonymous), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_COMMENT_LENGTH) },
                    placeholder = { Text(stringResource(R.string.board_comment_placeholder)) },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        requireLogin {
                            boardViewModel.addComment(draft, anonymous) { draft = "" }
                        }
                    },
                    enabled = draft.isNotBlank() && boardViewModel.commentState != BoardSubmitState.Submitting
                ) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.action_send))
                }
            }
            (boardViewModel.commentState as? BoardSubmitState.Failed)?.let {
                Text(
                    it.message.asString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.s)
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_post_title),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                boardViewModel.deletePost {
                    messages?.show(deletedMessage)
                    onBack()
                }
            }
        )
    }

    deletingCommentId?.let { commentId ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_comment_title),
            onDismiss = { deletingCommentId = null },
            onConfirm = {
                deletingCommentId = null
                boardViewModel.deleteComment(commentId)
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            title = stringResource(R.string.report_post_title),
            onDismiss = { showReportDialog = false },
            onReport = { reason ->
                showReportDialog = false
                boardViewModel.reportPost(reason) { hidden ->
                    messages?.show(if (hidden) hiddenMessage else reportedMessage)
                    if (hidden) onBack()
                }
            }
        )
    }

    reportingCommentId?.let { commentId ->
        ReportDialog(
            title = stringResource(R.string.report_comment_title),
            onDismiss = { reportingCommentId = null },
            onReport = { reason ->
                reportingCommentId = null
                boardViewModel.reportComment(commentId, reason) { hidden ->
                    messages?.show(if (hidden) hiddenMessage else reportedMessage)
                }
            }
        )
    }
}

@Composable
private fun PostBody(post: BoardPost, onLike: () -> Unit, onScrap: () -> Unit) {
    Text(
        postAuthorLabel(post),
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        timeAgoText(post.createdAt) + if (post.edited) " · " + stringResource(R.string.review_edited) else "",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.m))
    Text(post.title, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(Spacing.s))
    Text(post.body, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(Spacing.m))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CountLabel(R.drawable.ic_thumb_up, post.likeCount, MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(Spacing.s))
        CountLabel(R.drawable.ic_chat, post.commentCount, MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.width(Spacing.s))
        CountLabel(R.drawable.ic_bookmark, post.scrapCount, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(Spacing.m))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ReactionButton(
            icon = if (post.liked) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up,
            label = stringResource(R.string.action_like),
            active = post.liked,
            enabled = !post.isMine,
            onClick = onLike
        )
        ReactionButton(
            icon = if (post.scrapped) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark,
            label = stringResource(R.string.action_scrap),
            active = post.scrapped,
            enabled = true,
            onClick = onScrap
        )
    }
}

@Composable
private fun ReactionButton(icon: Int, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun CommentItem(comment: BoardComment, onDelete: () -> Unit, onReport: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                commentAuthorLabel(comment),
                style = MaterialTheme.typography.titleSmall,
                color = if (comment.isPostAuthor) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                timeAgoText(comment.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu), modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (comment.isMine) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(if (comment.reported) R.string.already_reported else R.string.action_report),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        enabled = !comment.reported,
                        onClick = {
                            showMenu = false
                            onReport()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
