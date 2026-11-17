package com.github.mytv.myearthquakealert.data.repository

/**
 * GitHub acceleration mirror a user can pin for the update pipeline.
 *
 * Probed on 2026-11-17 from mainland China against Release assets, `api.github.com` and raw files:
 * - direct access serves assets and raw files; the API is often rate limited to 403 from a shared
 *   egress IP, so 预览版 (which lists releases through the API) is the one that needs a proxy;
 * - gh-proxy.org / gh-proxy.com serve all three and proxy the API;
 * - ghfile.geekertao.top / ghfast.top / gh.3w.pm serve assets and raw files only, never the API;
 * - ghproxy.net serves assets but takes 6-22s - kept as a last resort only.
 *
 * Dropped after the same run (NXDOMAIN, refusing TLS, or now a parked domain): ghp.ci,
 * hub.gitmirror.com, github.moeyy.xyz, gh.cc, ghproxy.cn, gh-proxy.net, mirror.ghproxy.com,
 * gh-proxy.ygxz.in, gh.llkk.cc, gh.api.99988866.xyz, ghproxy.com.
 */
enum class GithubMirror(
    val label: String,
    /** Prefix prepended to the original URL; empty means direct access. */
    val prefix: String,
    /** Whether this route proxies `api.github.com`; the others only accelerate asset downloads. */
    val proxiesApi: Boolean,
) {
    /** Probe every route and use the fastest (the previous behaviour). */
    AUTO("自动测速", "", true),
    DIRECT("GitHub 直连", "", true),
    PROXY_ORG("gh-proxy.org", "https://gh-proxy.org/", true),
    PROXY_COM("gh-proxy.com", "https://gh-proxy.com/", true),
    GEEKERTAO("ghfile.geekertao.top", "https://ghfile.geekertao.top/", false),
    GHFAST("ghfast.top", "https://ghfast.top/", false),
    THREE_W("gh.3w.pm", "https://gh.3w.pm/", false),
    PROXY_NET("ghproxy.net", "https://ghproxy.net/", false),
    ;

    val isProxy: Boolean get() = this != AUTO && this != DIRECT

    companion object {
        val proxies: List<GithubMirror> = entries.filter { it.isProxy }

        fun fromName(name: String?): GithubMirror = entries.firstOrNull { it.name == name } ?: AUTO
    }
}
