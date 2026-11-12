package com.example.energiehuishouding.data.remote.homewizard

import retrofit2.http.*

interface HomeWizardApi {
    
    // API v1 (Standard P1 Meter)
    @GET("api/v1/data")
    suspend fun getLocalDataV1(): HomeWizardData

    // API v1 Batteries (Via P1 meter)
    @GET("api/v1/batteries")
    suspend fun getBatteriesV1(): HomeWizardBatteryResponse

    // API v2 (Standard Measurement Path)
    @GET("api/measurement")
    suspend fun getLocalDataV2(
        @Header("Authorization") token: String,
        @Header("X-Api-Version") version: Int = 2
    ): HomeWizardData

    // API v2 Batteries (Direct Battery or Group)
    @GET("api/batteries")
    suspend fun getBatteriesV2(
        @Header("Authorization") token: String,
        @Header("X-Api-Version") version: Int = 2
    ): HomeWizardBatteryResponseV2

    // API v2 Pairing
    @POST("api/user")
    suspend fun pair(
        @Body request: HomeWizardUserRequest,
        @Header("X-Api-Version") version: Int = 2
    ): HomeWizardUserResponse
}
