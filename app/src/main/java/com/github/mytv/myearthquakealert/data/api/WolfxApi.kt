package com.github.mytv.myearthquakealert.data.api

import kotlinx.serialization.json.JsonElement
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

    /**
     * The list payload is an object of "No1".."No50" entries plus a trailing
     * "md5" string — decoded loosely and filtered in the repository.
     */
    @GET("cenc_eqlist.json")
    suspend fun getCencEqlist(): Map<String, JsonElement>

    @GET("geoip.php")
    suspend fun getGeoIp(): GeoIpResponse

    @GET("geoip.php")
    suspend fun getGeoIp(@Query("ip") ip: String): GeoIpResponse

    @GET("ntp.json")
    suspend fun getNtp(): NtpResponse
}
