package com.github.mytv.myearthquakealert.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface WolfxApi {
    @GET("sc_eew.json")
    suspend fun getScEew(): ScEewResponse

    @GET("cenc_eew.json")
    suspend fun getCencEew(): CencEewResponse

    @GET("fj_eew.json")
    suspend fun getFjEew(): FjEewResponse

    @GET("cq_eew.json")
    suspend fun getCqEew(): CqEewResponse

    @GET("cenc_eqlist.json")
    suspend fun getCencEqlist(): Map<String, EarthquakeListEntry>

    @GET("geoip.php")
    suspend fun getGeoIp(): GeoIpResponse

    @GET("geoip.php")
    suspend fun getGeoIp(@Query("ip") ip: String): GeoIpResponse

    @GET("ntp.json")
    suspend fun getNtp(): NtpResponse
}
