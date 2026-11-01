package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute

class CommunityRepository(private val api: ApiService = ApiClient.api) {

    suspend fun recordRun(routeId: Int?, temporaryRouteId: Int?, distanceMeters: Int, durationSeconds: Long): RunRecord =
        api.recordRun(RecordRunRequest(routeId, temporaryRouteId, distanceMeters, durationSeconds)).requireData()

    suspend fun sharedRoutes(): List<SharedRoute> = api.sharedRoutes().data.orEmpty()

    suspend fun sharedRoute(sharedRouteId: Int): SharedRoute = api.sharedRoute(sharedRouteId).requireData()

    suspend fun shareRoute(routeId: Int, title: String, description: String): SharedRoute =
        api.shareRoute(ShareRouteRequest(routeId, title, description)).requireData()

    suspend fun deleteSharedRoute(sharedRouteId: Int) {
        api.deleteSharedRoute(sharedRouteId)
    }

    suspend fun saveRoute(routeId: Int) {
        api.saveRoute(SaveRouteRequest(routeId = routeId))
    }
}
