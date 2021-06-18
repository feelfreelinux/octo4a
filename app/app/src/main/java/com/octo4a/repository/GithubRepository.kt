package com.octo4a.repository

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

data class GithubAsset(val name: String, val url: String, val browserDownloadUrl: String)

data class GithubRelease(val tagName: String, val zipballUrl: String, val body: String, val assets: List<GithubAsset>, val htmlUrl: String)

interface GithubRepository {
    suspend fun getNewestRelease(repository: String): GithubRelease
    suspend fun getNewestReleases(repository: String): List<GithubRelease>
}

class GithubRepositoryImpl(val httpClient: HttpClient): GithubRepository {
    private val baseUrl = "https://api.github.com/"

    override suspend fun getNewestRelease(repository: String): GithubRelease {
        return httpClient.get {
            url("${baseUrl}repos/${repository}/releases/latest")
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun getNewestReleases(repository: String): List<GithubRelease> {
        return httpClient.get {
            url("${baseUrl}repos/${repository}/releases")
            contentType(ContentType.Application.Json)
        }
    }
}