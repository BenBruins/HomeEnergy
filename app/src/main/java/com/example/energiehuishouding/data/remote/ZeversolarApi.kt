package com.example.energiehuishouding.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET

interface ZeversolarApi {
    @GET("home.cgi")
    suspend fun getInverterData(): ResponseBody

    @GET("getdevdata.cgi")
    suspend fun getInverterDataV2(): ResponseBody
}
