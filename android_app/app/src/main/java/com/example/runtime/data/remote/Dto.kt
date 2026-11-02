package com.example.runtime.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val status: String,
    val message: String? = null,
    val data: T? = null,
) {
    fun requireData(): T = data ?: throw EmptyResponseException(message)
}

@Serializable
data class GeoJsonPoint(
    val coordinates: List<Double>,
    val type: String = "Point",
) {
    companion object {
        fun of(longitude: Double, latitude: Double) = GeoJsonPoint(listOf(longitude, latitude))
    }
}

@Serializable
data class GeoJsonLineString(
    val coordinates: List<List<Double>>,
    val type: String = "LineString",
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class GoogleLoginRequest(
    val credential: String,
)

@Serializable
data class AuthData(
    val username: String,
    val email: String,
    val token: String,
)

@Serializable
data class ProfileData(
    val username: String,
    val email: String,
)

@Serializable
data class GenerateRouteRequest(
    @SerialName("starting_point") val startingPoint: GeoJsonPoint,
    val distance: Int,
    val language: String? = null,
    @SerialName("end_point") val endPoint: GeoJsonPoint? = null,
    @SerialName("starting_point_name") val startingPointName: String? = null,
    @SerialName("end_point_name") val endPointName: String? = null,
    val preferences: List<String> = emptyList(),
    @SerialName("preference_text") val preferenceText: String = "",
)

@Serializable
data class ElevationCoordinate(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class ElevationLookupRequest(
    val coordinates: List<ElevationCoordinate>,
)

@Serializable
data class ElevationData(
    val elevations: List<Double>,
)

@Serializable
data class Place(
    val name: String,
    val address: String,
    val point: GeoJsonPoint,
)

@Serializable
data class GeneratedRouteData(
    @SerialName("temporary_route_id") val temporaryRouteId: Int,
    val route: RouteBody,
    val options: List<RouteOption> = emptyList(),
)

@Serializable
data class RouteOption(
    @SerialName("temporary_route_id") val temporaryRouteId: Int,
    val route: RouteBody,
)

@Serializable
data class RouteBody(
    val distance: Int,
    val briefing: String,
    val route: List<GeoJsonLineString>,
    @SerialName("is_shortest_path") val isShortestPath: Boolean = false,
    @SerialName("traffic_light_count") val trafficLightCount: Int? = null,
    @SerialName("elevation_gain_m") val elevationGainM: Int? = null,
)

@Serializable
data class RenameRouteRequest(
    val name: String,
)

@Serializable
data class RenamedRoute(
    val id: Int,
    val name: String,
)

@Serializable
data class SaveRouteRequest(
    @SerialName("temporary_route_id") val temporaryRouteId: Int? = null,
    @SerialName("route_id") val routeId: Int? = null,
)

@Serializable
data class SaveRouteResponse(
    val status: String,
    val message: String? = null,
    @SerialName("route_id") val routeId: Int? = null,
)

@Serializable
data class SavedRoute(
    val id: Int,
    val name: String = "",
    val distance: Int,
    val briefing: String,
    val route: List<GeoJsonLineString>,
    @SerialName("start_name") val startName: String = "",
    @SerialName("end_name") val endName: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("saved_at") val savedAt: String,
)

@Serializable
data class RecordRunRequest(
    @SerialName("route_id") val routeId: Int? = null,
    @SerialName("temporary_route_id") val temporaryRouteId: Int? = null,
    @SerialName("distance_m") val distanceMeters: Int,
    @SerialName("duration_s") val durationSeconds: Long,
    @SerialName("group_id") val groupId: Int? = null,
)

@Serializable
data class RunRecord(
    val id: Int,
    @SerialName("route_id") val routeId: Int,
    @SerialName("shared_route_id") val sharedRouteId: Int? = null,
)

@Serializable
data class RunHistoryItem(
    val id: Int,
    @SerialName("route_id") val routeId: Int,
    @SerialName("distance_m") val distanceMeters: Int,
    @SerialName("duration_s") val durationSeconds: Long,
    @SerialName("finished_at") val finishedAt: String,
    @SerialName("route_distance") val routeDistance: Int,
    @SerialName("start_name") val startName: String = "",
    @SerialName("end_name") val endName: String = "",
    @SerialName("shared_route_id") val sharedRouteId: Int? = null,
    @SerialName("shared_title") val sharedTitle: String = "",
    @SerialName("is_saved") val isSaved: Boolean = false,
)

@Serializable
data class ShareRouteRequest(
    @SerialName("route_id") val routeId: Int,
    val title: String,
    val description: String,
)

@Serializable
data class SharedRoute(
    val id: Int,
    @SerialName("route_id") val routeId: Int,
    val title: String,
    val description: String = "",
    @SerialName("shared_by") val sharedBy: String,
    @SerialName("shared_at") val sharedAt: String,
    val distance: Int,
    @SerialName("start_name") val startName: String = "",
    @SerialName("end_name") val endName: String = "",
    @SerialName("run_count") val runCount: Int = 0,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("rating_average") val ratingAverage: Double? = null,
    val briefing: String = "",
    val route: List<GeoJsonLineString> = emptyList(),
    @SerialName("rating_counts") val ratingCounts: Map<String, Int> = emptyMap(),
    @SerialName("can_review") val canReview: Boolean = false,
    @SerialName("my_review_id") val myReviewId: Int? = null,
)

@Serializable
data class RouteReview(
    val id: Int,
    val rating: Int,
    val comment: String = "",
    val author: String,
    @SerialName("created_at") val createdAt: String,
    val edited: Boolean = false,
    @SerialName("is_mine") val isMine: Boolean = false,
)

@Serializable
data class ReviewRequest(
    val rating: Int,
    val comment: String,
)

@Serializable
data class RunningGroup(
    val id: Int,
    val title: String,
    val description: String = "",
    val host: String,
    @SerialName("route_id") val routeId: Int,
    @SerialName("route_distance") val routeDistance: Int,
    @SerialName("start_name") val startName: String = "",
    val capacity: Int,
    @SerialName("member_count") val memberCount: Int,
    @SerialName("meet_at") val meetAt: String,
    @SerialName("join_deadline") val joinDeadline: String,
    val status: String,
    @SerialName("closing_soon") val closingSoon: Boolean = false,
    @SerialName("is_member") val isMember: Boolean = false,
    @SerialName("is_host") val isHost: Boolean = false,
    @SerialName("distance_from_me_m") val distanceFromMe: Int? = null,
    val route: List<GeoJsonLineString> = emptyList(),
    val members: List<String> = emptyList(),
    val ranking: List<GroupRanking> = emptyList(),
)

@Serializable
data class GroupRanking(
    val rank: Int,
    val username: String,
    @SerialName("pace_s_per_km") val paceSecondsPerKm: Int,
    @SerialName("distance_m") val distanceMeters: Int,
    @SerialName("duration_s") val durationSeconds: Long,
    @SerialName("is_me") val isMe: Boolean = false,
)

@Serializable
data class CreateGroupRequest(
    @SerialName("route_id") val routeId: Int,
    val title: String,
    val description: String,
    val capacity: Int,
    @SerialName("meet_at") val meetAt: String,
    @SerialName("join_deadline") val joinDeadline: String,
)

@Serializable
data class GroupMessage(
    val id: Int,
    val author: String,
    val text: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_mine") val isMine: Boolean = false,
)

@Serializable
data class SendMessageRequest(val text: String)

@Serializable
data class BoardPost(
    val id: Int,
    val title: String,
    val body: String,
    val author: String? = null,
    val anonymous: Boolean = true,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val edited: Boolean = false,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("scrap_count") val scrapCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    val liked: Boolean = false,
    val scrapped: Boolean = false,
    val reported: Boolean = false,
    val comments: List<BoardComment> = emptyList(),
)

@Serializable
data class BoardComment(
    val id: Int,
    val body: String,
    val author: String? = null,
    val anonymous: Boolean = true,
    @SerialName("anonymous_number") val anonymousNumber: Int? = null,
    @SerialName("is_post_author") val isPostAuthor: Boolean = false,
    @SerialName("is_mine") val isMine: Boolean = false,
    val reported: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PostRequest(
    val title: String,
    val body: String,
    val anonymous: Boolean? = null,
)

@Serializable
data class CommentRequest(val body: String, val anonymous: Boolean)

@Serializable
data class ReportRequest(val reason: String)

@Serializable
data class ReportResult(val hidden: Boolean = false)
