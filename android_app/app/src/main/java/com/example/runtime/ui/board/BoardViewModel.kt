package com.example.runtime.ui.board

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.data.repository.BoardRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class PostFilter(val key: String, @StringRes val label: Int) {
    ALL("all", R.string.board_filter_all),
    SCRAPS("scraps", R.string.board_filter_scraps),
    MINE("mine", R.string.board_filter_mine),
    FOLLOWING("following", R.string.filter_following),
}

enum class ReportReason(val key: String, @StringRes val label: Int) {
    SPAM("spam", R.string.report_reason_spam),
    ABUSE("abuse", R.string.report_reason_abuse),
    INAPPROPRIATE("inappropriate", R.string.report_reason_inappropriate),
    OTHER("other", R.string.report_reason_other),
}

const val MAX_POST_TITLE_LENGTH = 100
const val MAX_POST_BODY_LENGTH = 2000
const val MAX_COMMENT_LENGTH = 500

sealed interface BoardSubmitState {
    data object Idle : BoardSubmitState
    data object Submitting : BoardSubmitState
    data class Failed(val message: UiText) : BoardSubmitState
}

class BoardViewModel(
    private val repository: BoardRepository = BoardRepository(),
) : ViewModel() {

    var posts by mutableStateOf<List<BoardPost>>(emptyList())
        private set

    var filter by mutableStateOf(PostFilter.ALL)
        private set

    var query by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<UiText?>(null)
        private set

    var detail by mutableStateOf<BoardPost?>(null)
        private set

    var detailError by mutableStateOf<UiText?>(null)
        private set

    var actionError by mutableStateOf<UiText?>(null)
        private set

    var submitState by mutableStateOf<BoardSubmitState>(BoardSubmitState.Idle)
        private set

    var commentState by mutableStateOf<BoardSubmitState>(BoardSubmitState.Idle)
        private set

    private var refreshJob: Job? = null
    private var detailJob: Job? = null

    fun changeFilter(filter: PostFilter) {
        if (filter == this.filter) return
        this.filter = filter
        reload()
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed == this.query) return
        this.query = trimmed
        reload()
    }

    private fun reload() {
        refreshJob?.cancel()
        isLoading = false
        refresh()
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        val filter = filter
        val query = query.ifBlank { null }
        refreshJob = viewModelScope.launch {
            try {
                posts = repository.posts(filter.key, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadDetail(postId: Int) {
        detailJob?.cancel()
        detail = detail?.takeIf { it.id == postId } ?: posts.firstOrNull { it.id == postId }
        detailError = null
        commentState = BoardSubmitState.Idle
        detailJob = viewModelScope.launch {
            try {
                showPost(repository.post(postId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            }
        }
    }

    private fun showPost(post: BoardPost, comments: List<BoardComment> = post.comments) {
        val shown = post.copy(comments = comments, commentCount = comments.size)
        detail = shown
        posts = posts.map { if (it.id == post.id) shown.copy(comments = emptyList()) else it }
    }

    private fun updateComments(comments: List<BoardComment>) {
        detail?.let { showPost(it, comments) }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = e.toUiText()
            }
        }
    }

    fun clearActionError() {
        actionError = null
    }

    fun toggleLike() {
        val post = detail ?: return
        launchAction { showPost(repository.setLiked(post.id, !post.liked), post.comments) }
    }

    fun toggleScrap() {
        val post = detail ?: return
        launchAction { showPost(repository.setScrapped(post.id, !post.scrapped), post.comments) }
    }

    fun reportPost(reason: ReportReason, onReported: (Boolean) -> Unit) {
        val post = detail ?: return
        launchAction {
            val hidden = repository.reportPost(post.id, reason.key)
            if (hidden) {
                posts = posts.filterNot { it.id == post.id }
                detail = null
            } else {
                showPost(post.copy(reported = true), post.comments)
            }
            onReported(hidden)
        }
    }

    fun deletePost(onDeleted: () -> Unit) {
        val post = detail ?: return
        launchAction {
            repository.delete(post.id)
            posts = posts.filterNot { it.id == post.id }
            detail = null
            onDeleted()
        }
    }

    fun resetSubmit() {
        submitState = BoardSubmitState.Idle
    }

    fun submitPost(editingId: Int?, title: String, body: String, anonymous: Boolean, onSaved: (Int) -> Unit) {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isEmpty() || trimmedBody.isEmpty() || submitState == BoardSubmitState.Submitting) return
        submitState = BoardSubmitState.Submitting
        viewModelScope.launch {
            submitState = try {
                val saved = if (editingId == null) {
                    repository.create(trimmedTitle, trimmedBody, anonymous).also { posts = listOf(it) + posts }
                } else {
                    repository.update(editingId, trimmedTitle, trimmedBody)
                }
                showPost(saved, detail?.takeIf { it.id == saved.id }?.comments ?: emptyList())
                onSaved(saved.id)
                BoardSubmitState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BoardSubmitState.Failed(e.toUiText())
            }
        }
    }

    fun addComment(body: String, anonymous: Boolean, onAdded: () -> Unit) {
        val post = detail ?: return
        val trimmed = body.trim()
        if (trimmed.isEmpty() || commentState == BoardSubmitState.Submitting) return
        commentState = BoardSubmitState.Submitting
        viewModelScope.launch {
            commentState = try {
                updateComments(repository.addComment(post.id, trimmed.take(MAX_COMMENT_LENGTH), anonymous))
                onAdded()
                BoardSubmitState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BoardSubmitState.Failed(e.toUiText())
            }
        }
    }

    fun deleteComment(commentId: Int) {
        launchAction { updateComments(repository.deleteComment(commentId)) }
    }

    fun reportComment(commentId: Int, reason: ReportReason, onReported: (Boolean) -> Unit) {
        launchAction {
            val hidden = repository.reportComment(commentId, reason.key)
            val comments = detail?.comments.orEmpty()
            updateComments(
                if (hidden) comments.filterNot { it.id == commentId }
                else comments.map { if (it.id == commentId) it.copy(reported = true) else it }
            )
            onReported(hidden)
        }
    }

    fun reset() {
        posts = emptyList()
        detail = null
        filter = PostFilter.ALL
        query = ""
    }
}
