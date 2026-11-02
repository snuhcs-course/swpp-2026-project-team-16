package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.data.remote.CommentRequest
import com.example.runtime.data.remote.PostRequest
import com.example.runtime.data.remote.ReportRequest

class BoardRepository(private val api: ApiService = ApiClient.api) {

    suspend fun posts(filter: String, query: String?): List<BoardPost> = api.posts(filter, query).data.orEmpty()

    suspend fun post(postId: Int): BoardPost = api.post(postId).requireData()

    suspend fun create(title: String, body: String, anonymous: Boolean): BoardPost =
        api.createPost(PostRequest(title, body, anonymous)).requireData()

    suspend fun update(postId: Int, title: String, body: String): BoardPost =
        api.updatePost(postId, PostRequest(title, body)).requireData()

    suspend fun delete(postId: Int) {
        api.deletePost(postId)
    }

    suspend fun setLiked(postId: Int, liked: Boolean): BoardPost =
        (if (liked) api.likePost(postId) else api.unlikePost(postId)).requireData()

    suspend fun setScrapped(postId: Int, scrapped: Boolean): BoardPost =
        (if (scrapped) api.scrapPost(postId) else api.unscrapPost(postId)).requireData()

    suspend fun reportPost(postId: Int, reason: String): Boolean = api.reportPost(postId, ReportRequest(reason)).requireData().hidden

    suspend fun addComment(postId: Int, body: String, anonymous: Boolean): List<BoardComment> =
        api.addComment(postId, CommentRequest(body, anonymous)).data.orEmpty()

    suspend fun deleteComment(commentId: Int): List<BoardComment> = api.deleteComment(commentId).data.orEmpty()

    suspend fun reportComment(commentId: Int, reason: String): Boolean =
        api.reportComment(commentId, ReportRequest(reason)).requireData().hidden
}
