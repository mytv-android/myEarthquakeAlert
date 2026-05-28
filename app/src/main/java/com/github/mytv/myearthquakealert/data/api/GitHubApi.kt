package com.github.mytv.myearthquakealert.data.api

import com.github.mytv.myearthquakealert.data.model.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String = "mytv-android",
        @Path("repo") repo: String = "myEarthquakeAlert",
    ): GitHubRelease

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
