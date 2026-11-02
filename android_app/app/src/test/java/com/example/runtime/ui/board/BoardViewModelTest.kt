package com.example.runtime.ui.board

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.data.remote.CommentRequest
import com.example.runtime.data.remote.PostRequest
import com.example.runtime.data.remote.ReportRequest
import com.example.runtime.data.remote.ReportResult
import com.example.runtime.data.repository.BoardRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BoardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = BoardApi()
    private val viewModel by lazy { BoardViewModel(BoardRepository(api)) }

    @Test
    fun filtersAndSearch_sendTheirQuery() {
        viewModel.refresh()
        viewModel.changeFilter(PostFilter.SCRAPS)
        viewModel.search("  knee ")
        viewModel.search("knee")

        assertEquals(listOf("all" to null, "scraps" to null, "scraps" to "knee"), api.queries)
        assertEquals(listOf(1, 2), viewModel.posts.map { it.id })
    }

    @Test
    fun submitPost_createsThenEdits() {
        viewModel.refresh()
        var savedId: Int? = null

        viewModel.submitPost(null, " Knee pain ", " Tips? ", anonymous = true) { savedId = it }
        viewModel.submitPost(9, "Knee pain", "Fixed", anonymous = true) {}

        assertEquals(listOf(PostRequest("Knee pain", "Tips?", true), PostRequest("Knee pain", "Fixed")), api.saved)
        assertEquals(9, savedId)
        assertEquals(listOf(9, 1, 2), viewModel.posts.map { it.id })
        assertEquals("Fixed", viewModel.posts.first().body)
    }

    @Test
    fun likeAndScrap_toggleFromTheCurrentState() {
        viewModel.loadDetail(1)

        viewModel.toggleLike()
        viewModel.toggleLike()
        viewModel.toggleScrap()

        assertEquals(listOf("like", "unlike", "scrap"), api.reactions)
        assertEquals(true, viewModel.detail?.scrapped)
        assertEquals(false, viewModel.detail?.liked)
    }

    @Test
    fun likingOwnPost_showsTheServerError() {
        viewModel.loadDetail(1)
        api.rejectLike = true

        viewModel.toggleLike()

        assertEquals(UiText.Resource(R.string.error_like_own_post), viewModel.actionError)
        viewModel.clearActionError()
        assertNull(viewModel.actionError)
    }

    @Test
    fun comments_addAndDeleteUpdateTheCount() {
        viewModel.loadDetail(1)
        var added = false

        viewModel.addComment("  Ice it  ", anonymous = false) { added = true }

        assertTrue(added)
        assertEquals(listOf(CommentRequest("Ice it", false)), api.comments)
        assertEquals(1, viewModel.detail?.commentCount)
        viewModel.deleteComment(30)
        assertEquals(0, viewModel.detail?.commentCount)
    }

    @Test
    fun reportingUntilHidden_removesThePost() {
        viewModel.refresh()
        viewModel.loadDetail(1)
        var hidden: Boolean? = null

        viewModel.reportPost(ReportReason.ABUSE) { hidden = it }
        assertEquals(false, hidden)
        assertEquals(true, viewModel.detail?.reported)

        api.hideOnReport = true
        viewModel.reportPost(ReportReason.SPAM) { hidden = it }

        assertEquals(true, hidden)
        assertNull(viewModel.detail)
        assertEquals(listOf(2), viewModel.posts.map { it.id })
        assertEquals(listOf("abuse", "spam"), api.reportReasons)
    }

    @Test
    fun deletePost_removesItFromTheList() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var deleted = false

        viewModel.deletePost { deleted = true }

        assertTrue(deleted)
        assertEquals(listOf(1), viewModel.posts.map { it.id })
    }

    private class BoardApi : FakeApiService() {
        val queries = mutableListOf<Pair<String, String?>>()
        val saved = mutableListOf<PostRequest>()
        val reactions = mutableListOf<String>()
        val comments = mutableListOf<CommentRequest>()
        val reportReasons = mutableListOf<String>()
        var rejectLike = false
        var hideOnReport = false
        private var liked = false
        private var scrapped = false
        private var commentList = emptyList<BoardComment>()

        override suspend fun posts(filter: String, query: String?): ApiResponse<List<BoardPost>> {
            queries += filter to query
            return ApiResponse(status = "success", data = listOf(sample(1), sample(2)))
        }

        override suspend fun post(postId: Int) = ApiResponse(status = "success", data = sample(postId))

        override suspend fun createPost(body: PostRequest): ApiResponse<BoardPost> {
            saved += body
            return ApiResponse(status = "success", data = sample(9).copy(title = body.title, body = body.body))
        }

        override suspend fun updatePost(postId: Int, body: PostRequest): ApiResponse<BoardPost> {
            saved += body
            return ApiResponse(status = "success", data = sample(postId).copy(title = body.title, body = body.body))
        }

        override suspend fun deletePost(postId: Int): ApiResponse<Unit> = ApiResponse(status = "success")

        override suspend fun likePost(postId: Int): ApiResponse<BoardPost> {
            if (rejectLike) throw HttpException(
                Response.error<Any>(400, """{"status":"error","message":"You can't like your own post."}""".toResponseBody())
            )
            reactions += "like"
            liked = true
            return ApiResponse(status = "success", data = sample(postId))
        }

        override suspend fun unlikePost(postId: Int): ApiResponse<BoardPost> {
            reactions += "unlike"
            liked = false
            return ApiResponse(status = "success", data = sample(postId))
        }

        override suspend fun scrapPost(postId: Int): ApiResponse<BoardPost> {
            reactions += "scrap"
            scrapped = true
            return ApiResponse(status = "success", data = sample(postId))
        }

        override suspend fun reportPost(postId: Int, body: ReportRequest): ApiResponse<ReportResult> {
            reportReasons += body.reason
            return ApiResponse(status = "success", data = ReportResult(hidden = hideOnReport))
        }

        override suspend fun addComment(postId: Int, body: CommentRequest): ApiResponse<List<BoardComment>> {
            comments += body
            commentList = listOf(BoardComment(id = 30, body = body.body, anonymous = body.anonymous, createdAt = "2026-11-02T01:00:00Z"))
            return ApiResponse(status = "success", data = commentList)
        }

        override suspend fun deleteComment(commentId: Int): ApiResponse<List<BoardComment>> {
            commentList = commentList.filterNot { it.id == commentId }
            return ApiResponse(status = "success", data = commentList)
        }

        private fun sample(id: Int) = BoardPost(
            id = id,
            title = "Post $id",
            body = "Body $id",
            createdAt = "2026-11-02T01:00:00Z",
            liked = liked,
            scrapped = scrapped,
            comments = commentList,
            commentCount = commentList.size,
        )
    }
}
