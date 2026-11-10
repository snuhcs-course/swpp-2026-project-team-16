package com.example.runtime.ui.group

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.CreateGroupRequest
import com.example.runtime.data.remote.GroupInvite
import com.example.runtime.data.remote.GroupMessage
import com.example.runtime.data.remote.InvitableFriend
import com.example.runtime.data.remote.InviteRequest
import com.example.runtime.data.remote.JoinGroupRequest
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.remote.SendMessageRequest
import com.example.runtime.data.repository.GroupRepository
import com.example.runtime.data.repository.RouteRepository
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
import java.time.Instant

class GroupsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = GroupApi()
    private val viewModel by lazy { GroupsViewModel(GroupRepository(api), RouteRepository(api)) }

    @Test
    fun refresh_loadsAllGroups() {
        viewModel.refresh()

        assertEquals(listOf(1, 2), viewModel.groups.map { it.id })
        assertEquals(listOf(Query("all", null, null, null)), api.queries)
    }

    @Test
    fun filters_sendTheirQuery() {
        viewModel.refresh()

        viewModel.changeFilter(GroupFilter.CLOSING_SOON)
        viewModel.showNearby(37.46, 126.95)
        viewModel.changeRadius(5.0)

        assertEquals(
            listOf(
                Query("all", null, null, null),
                Query("closing_soon", null, null, null),
                Query("all", 37.46, 126.95, 3.0),
                Query("all", 37.46, 126.95, 5.0),
            ),
            api.queries,
        )
        assertEquals(GroupFilter.NEARBY, viewModel.filter)
    }

    @Test
    fun join_updatesTheDetailAndList() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var joined = false

        viewModel.join { joined = true }

        assertTrue(joined)
        assertEquals(true, viewModel.detail?.isMember)
        assertEquals(2, viewModel.groups.first { it.id == 2 }.memberCount)
    }

    @Test
    fun join_failureShowsTheError() {
        viewModel.loadDetail(2)
        api.failJoin = true

        viewModel.join()

        assertEquals(GroupActionState.Failed(UiText.Resource(R.string.error_group_full)), viewModel.actionState)
    }

    @Test
    fun join_sendsThePasswordOfAPrivateGroup() {
        viewModel.loadDetail(2)

        viewModel.join("run1234")

        assertEquals(JoinGroupRequest("run1234"), api.joins.single())
    }

    @Test
    fun create_rejectsAShortPrivatePassword() {
        val meet = Instant.parse("2026-11-05T07:00:00Z")

        viewModel.create(10, "Morning", "", 4, meet, meet.minusSeconds(60), isPrivate = true, password = "123") {}

        assertTrue(api.created.isEmpty())
        assertEquals(GroupActionState.Failed(UiText.Resource(R.string.error_group_password)), viewModel.createState)
    }

    @Test
    fun create_sendsThePasswordOnlyForPrivateGroups() {
        val meet = Instant.parse("2026-11-05T07:00:00Z")

        viewModel.create(10, "Secret", "", 4, meet, meet.minusSeconds(60), isPrivate = true, password = "run1234") {}
        viewModel.create(10, "Open", "", 4, meet, meet.minusSeconds(60), password = "ignored") {}

        assertEquals(listOf(true, false), api.created.map { it.isPrivate })
        assertEquals(listOf("run1234", null), api.created.map { it.password })
    }

    @Test
    fun acceptInvite_joinsWithoutAPasswordAndClearsTheInvite() {
        viewModel.loadInvites()
        var opened: Int? = null

        viewModel.acceptInvite(viewModel.invites.single()) { opened = it }

        assertEquals(JoinGroupRequest(null), api.joins.single())
        assertEquals(3, opened)
        assertTrue(viewModel.invites.isEmpty())
    }

    @Test
    fun declineInvite_removesIt() {
        viewModel.loadInvites()

        viewModel.declineInvite(viewModel.invites.single())

        assertEquals(listOf(7), api.declined)
        assertTrue(viewModel.invites.isEmpty())
    }

    @Test
    fun inviteFriend_marksThemInvited() {
        viewModel.loadDetail(2)
        viewModel.loadFriends()
        var sent = false

        viewModel.inviteFriend("friend") { sent = true }

        assertTrue(sent)
        assertEquals(listOf(2 to "friend"), api.invited)
        assertEquals(true, viewModel.friends?.first { it.username == "friend" }?.isInvited)
    }

    @Test
    fun create_rejectsADeadlineAfterTheRun() {
        val meet = Instant.parse("2026-11-05T07:00:00Z")

        viewModel.create(10, "Morning", "", 4, meet, meet.plusSeconds(60)) {}

        assertTrue(api.created.isEmpty())
        assertEquals(GroupActionState.Failed(UiText.Resource(R.string.error_group_deadline_after_run)), viewModel.createState)
    }

    @Test
    fun create_sendsIsoTimesAndOpensTheGroup() {
        val meet = Instant.parse("2026-11-05T07:00:00Z")
        var createdId: Int? = null

        viewModel.create(10, "  Morning  ", " Easy ", 30, meet, meet.minusSeconds(3600)) { createdId = it }

        assertEquals(
            CreateGroupRequest(10, "Morning", "Easy", 20, "2026-11-05T07:00:00Z", "2026-11-05T06:00:00Z"),
            api.created.single(),
        )
        assertEquals(9, createdId)
        assertEquals(9, viewModel.detail?.id)
    }

    @Test
    fun chat_loadsNewMessagesAndSends() {
        viewModel.openChat(2)
        assertEquals(listOf(1), viewModel.messages.map { it.id })

        var sent = false
        viewModel.send(2, "  hi  ") { sent = true }

        assertTrue(sent)
        assertEquals(listOf(SendMessageRequest("hi")), api.sentMessages)
        assertEquals(listOf(1, 2), viewModel.messages.map { it.id })
        viewModel.closeChat()
    }

    @Test
    fun delete_removesTheGroup() {
        viewModel.refresh()
        viewModel.loadDetail(1)
        var deleted = false

        viewModel.delete { deleted = true }

        assertTrue(deleted)
        assertEquals(listOf(2), viewModel.groups.map { it.id })
        assertNull(viewModel.detail)
    }

    private data class Query(val filter: String, val lat: Double?, val lon: Double?, val radius: Double?)

    private class GroupApi : FakeApiService() {
        val queries = mutableListOf<Query>()
        val created = mutableListOf<CreateGroupRequest>()
        val sentMessages = mutableListOf<SendMessageRequest>()
        var failJoin = false
        val joins = mutableListOf<JoinGroupRequest>()
        val invited = mutableListOf<Pair<Int, String>>()
        val declined = mutableListOf<Int>()

        override suspend fun myInvites() = ApiResponse(
            status = "success",
            data = listOf(GroupInvite(id = 7, inviter = "host", createdAt = "2026-11-01T00:00:00Z", group = sample(3).copy(myInviteId = 7)))
        )

        override suspend fun declineInvite(inviteId: Int): ApiResponse<Unit> {
            declined += inviteId
            return ApiResponse(status = "success")
        }

        override suspend fun invitableFriends(groupId: Int) = ApiResponse(
            status = "success",
            data = listOf(InvitableFriend("friend"), InvitableFriend("buddy", isMember = true))
        )

        override suspend fun inviteFriend(groupId: Int, body: InviteRequest): ApiResponse<GroupInvite> {
            invited += groupId to body.username
            return ApiResponse(status = "success", data = GroupInvite(1, "me", "2026-11-01T00:00:00Z", sample(groupId)))
        }

        override suspend fun groups(filter: String, latitude: Double?, longitude: Double?, radiusKm: Double?): ApiResponse<List<RunningGroup>> {
            queries += Query(filter, latitude, longitude, radiusKm)
            return ApiResponse(status = "success", data = listOf(sample(1), sample(2)))
        }

        override suspend fun group(groupId: Int) = ApiResponse(status = "success", data = sample(groupId))

        override suspend fun joinGroup(groupId: Int, body: JoinGroupRequest): ApiResponse<RunningGroup> {
            joins += body
            if (failJoin) throw HttpException(Response.error<Any>(400, """{"status":"error","message":"This group is full."}""".toResponseBody()))
            return ApiResponse(status = "success", data = sample(groupId).copy(memberCount = 2, isMember = true))
        }

        override suspend fun deleteGroup(groupId: Int): ApiResponse<Unit> = ApiResponse(status = "success")

        override suspend fun createGroup(body: CreateGroupRequest): ApiResponse<RunningGroup> {
            created += body
            return ApiResponse(status = "success", data = sample(9))
        }

        override suspend fun groupMessages(groupId: Int, after: Int?): ApiResponse<List<GroupMessage>> =
            ApiResponse(status = "success", data = if (after == null) listOf(message(1, "hello")) else emptyList())

        override suspend fun sendGroupMessage(groupId: Int, body: SendMessageRequest): ApiResponse<GroupMessage> {
            sentMessages += body
            return ApiResponse(status = "success", data = message(2, body.text))
        }

        private fun message(id: Int, text: String) = GroupMessage(id, "runner", text, "2026-11-02T01:00:00Z", isMine = id == 2)

        private fun sample(id: Int) = RunningGroup(
            id = id,
            title = "Group $id",
            host = "host",
            routeId = id * 10,
            routeDistance = 5000,
            capacity = 4,
            memberCount = 1,
            meetAt = "2026-11-05T07:00:00Z",
            joinDeadline = "2026-11-05T05:00:00Z",
            status = "open",
        )
    }
}
