package com.example.energiehuishouding.data.remote.solaredge

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SolarEdgeApi {
    @GET("site/{siteId}/energy")
    suspend fun getEnergy(
        @Path("siteId") siteId: String,
        @Query("api_key") apiKey: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("timeUnit") timeUnit: String = "DAY"
    ): Response<SolarEdgeEnergyResponse>

    @GET("site/{siteId}/overview")
    suspend fun getOverview(
        @Path("siteId") siteId: String,
        @Query("api_key") apiKey: String
    ): Response<SolarEdgeOverviewResponse>
}
