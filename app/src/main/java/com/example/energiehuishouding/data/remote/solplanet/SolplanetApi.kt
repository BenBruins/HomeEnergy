package com.example.energiehuishouding.data.remote.solplanet

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Targeted Solplanet API.
 */
interface SolplanetApi {

    @GET("getdev.cgi")
    suspend fun getLocalInverterInfo(
        @Query("device") device: Int = 2
    ): ResponseBody

    @GET("getdevdata.cgi")
    suspend fun getLocalInverterData(
        @Query("device") device: Int = 2,
        @Query("sn") serialNumber: String
    ): ResponseBody

    @GET
    suspend fun getGateway(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    @POST
    suspend fun postGateway(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>
}
