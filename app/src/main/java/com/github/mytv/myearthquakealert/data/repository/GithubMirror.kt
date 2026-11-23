package com.github.mytv.myearthquakealert.data.repository

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 镜像的工作方式：[PREFIX] 把地址拼在原 URL 前面，[RAW_HOST] 只替换 raw 的主机名。 */
enum class MirrorKind { PREFIX, RAW_HOST }

/**
 * 一条可用的镜像线路。内置线路与 gitee 远程清单合并后都是这个形状，
 * 设置页、查更新测速、APK 下载共用同一份。
 */
data class MirrorRoute(
    val id: String,
    val label: String,
    /** [MirrorKind.PREFIX] 是拼在原 URL 前的前缀；[MirrorKind.RAW_HOST] 只存主机名；直连为空串。 */
    val prefix: String,
    val kind: MirrorKind,
    /** 是否转发 `api.github.com`；不转发的线路只能下载资产与 raw 文件。 */
    val proxiesApi: Boolean,
    /** 排序档位，见 [TIER_FAST] 一族常量。 */
    val tier: Int,
    val enabled: Boolean = true,
) {
    /**
     * 为 [target] 拼出这条线路的 URL。线路不适用时返回 null——
     * 例如 raw_host 线路遇到 Release 资产地址，拼出来也是 404，不如直接不生成。
     */
    fun urlFor(target: String): String? = when (kind) {
        MirrorKind.PREFIX ->
            if (prefix.isEmpty()) target else prefix.trimEnd('/') + "/" + target
        MirrorKind.RAW_HOST ->
            if (target.startsWith(RAW_URL_PREFIX)) {
                target.replaceFirst(RAW_URL_PREFIX, "https://${prefix.trimEnd('/')}/")
            } else {
                null
            }
    }
}

/** 只服务 raw 的镜像就是替换这个主机。 */
private const val RAW_URL_PREFIX = "https://raw.githubusercontent.com/"

/**
 * 线路排序档位。铁律：镜像(0) → 国内直连(1) → 慢镜像(2) → 只服务 raw 的镜像(3)。
 * 顶层常量而不是枚举伴生对象里的，避免枚举条目依赖伴生对象的初始化顺序。
 */
const val TIER_MODE = -1
const val TIER_FAST = 0
const val TIER_DIRECT = 1
const val TIER_SLOW = 2
const val TIER_RAW_ONLY = 3

/** gitee `my-mirror` 仓库的清单结构，字段与 [MirrorRoute] 一一对应。 */
@Serializable
data class RemoteMirrorList(
    val schema: String = "",
    val mirrors: List<RemoteMirror> = emptyList(),
)

@Serializable
data class RemoteMirror(
    val id: String = "",
    val label: String = "",
    val base: String = "",
    val kind: String = "prefix",
    val api: Boolean = false,
    val tier: Int = 0,
    val enabled: Boolean = true,
)

/**
 * GitHub acceleration mirror a user can pin for the update pipeline.
 *
 * Probed on 2026-11-23 from mainland China against Release assets, `api.github.com` and raw files:
 * - gh-proxy.org / gh-proxy.com serve all three and proxy the API;
 * - ghfile.geekertao.top / ghfast.top / gh.3w.pm / cors.isteed.cc serve assets and raw files only,
 *   never the API;
 * - ghproxy.net serves assets but takes 2-4.5s - kept as a last resort only;
 * - raw.githack.com / rawcdn.githack.com / githack.com serve raw/blob files only, last resort.
 *
 * Dropped after the same run (NXDOMAIN, refusing TLS, 403 git-only, or now a parked domain):
 * ghp.ci, hub.gitmirror.com, github.moeyy.xyz, gh.cc, ghproxy.cn, gh-proxy.net,
 * mirror.ghproxy.com, gh-proxy.ygxz.in, gh.llkk.cc, gh.api.99988866.xyz, ghproxy.com,
 * ghproxy.link, ghps.cc, gh.ddlc.top, kkgithub.com, bgithub.xyz, githubfast.com, gitclone.com,
 * plus 100+ community `gh.*` prefix proxies. The only liveness criterion is a prefix-style
 * fetch of a raw file returning HTTP 200 with the raw body - never an HTML landing page;
 * whether the mirror's homepage opens is irrelevant. The probe script and the full record live
 * in the gitee `mytv-android/my-mirror` repository.
 *
 * Ordering rule: **mirrors / mainland direct first, raw last** - routes are taken by [tier]
 * ascending, declaration order within a tier. A route the user pinned leads whatever the probe
 * measured; every other route still follows as a fallback.
 */
enum class GithubMirror(
    val id: String,
    val label: String,
    /** Prefix prepended to the original URL; empty means direct access. */
    val prefix: String,
    val kind: MirrorKind,
    /** Whether this route proxies `api.github.com`; the others only accelerate asset downloads. */
    val proxiesApi: Boolean,
    val tier: Int,
) {
    /** Probe every route and use the fastest (the previous behaviour). */
    AUTO("auto", "自动测速", "", MirrorKind.PREFIX, true, TIER_MODE),
    DIRECT("direct", "GitHub 直连", "", MirrorKind.PREFIX, true, TIER_DIRECT),
    PROXY_ORG("gh-proxy.org", "gh-proxy.org", "https://gh-proxy.org/", MirrorKind.PREFIX, true, TIER_FAST),
    PROXY_COM("gh-proxy.com", "gh-proxy.com", "https://gh-proxy.com/", MirrorKind.PREFIX, true, TIER_FAST),
    GEEKERTAO("ghfile.geekertao.top", "ghfile.geekertao.top", "https://ghfile.geekertao.top/", MirrorKind.PREFIX, false, TIER_FAST),
    GHFAST("ghfast.top", "ghfast.top", "https://ghfast.top/", MirrorKind.PREFIX, false, TIER_FAST),
    THREE_W("gh.3w.pm", "gh.3w.pm", "https://gh.3w.pm/", MirrorKind.PREFIX, false, TIER_FAST),
    CORS_ISTEED("cors.isteed.cc", "cors.isteed.cc", "https://cors.isteed.cc/", MirrorKind.PREFIX, false, TIER_FAST),
    PROXY_NET("ghproxy.net", "ghproxy.net", "https://ghproxy.net/", MirrorKind.PREFIX, false, TIER_SLOW),
    RAW_GITHACK("raw.githack.com", "raw.githack.com", "raw.githack.com", MirrorKind.RAW_HOST, false, TIER_RAW_ONLY),
    RAWCDN_GITHACK("rawcdn.githack.com", "rawcdn.githack.com", "rawcdn.githack.com", MirrorKind.RAW_HOST, false, TIER_RAW_ONLY),
    GITHACK("githack.com", "githack.com", "githack.com", MirrorKind.RAW_HOST, false, TIER_RAW_ONLY),
    ;

    val isProxy: Boolean get() = this != AUTO && this != DIRECT

    fun toRoute(): MirrorRoute =
        MirrorRoute(id, label, prefix, kind, proxiesApi, tier, enabled = true)

    companion object {
        val proxies: List<GithubMirror> = entries.filter { it.isProxy }

        /** 内置线路顺序：tier 升序，同 tier 保持声明顺序。 */
        private val BUILT_IN_ROUTES: List<MirrorRoute> =
            entries.filter { it != AUTO }.sortedBy { it.tier }.map { it.toRoute() }

        /**
         * 最新镜像清单。gitee 国内直连，本身就是给国内用户准备的——
         * 加镜像、调顺序、下线失效镜像都改这个仓库，不用发新版本。
         */
        const val REMOTE_LIST_URL = "https://gitee.com/mytv-android/my-mirror/raw/master/mirrors.json"
        private const val PREFS_NAME = "myearthquakealert_mirrors"
        private const val SP_LIST = "mirror_list"
        private const val SP_LIST_AT = "mirror_list_at"
        private const val LIST_TTL_MS = 6 * 60 * 60 * 1000L
        private const val LIST_SCHEMA = "mytv.mirror/v1"
        /** 远程清单上限：挡住写飘了的列表把探测成本放大。 */
        private const val MAX_REMOTE_ENTRIES = 40
        private const val USER_AGENT = "myEarthquakeAlert-Android"

        private val remoteJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        private val listClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        fun fromName(name: String?): GithubMirror = entries.firstOrNull { it.name == name } ?: AUTO

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * 内置清单与 gitee 远程清单合并后的线路顺序。远程拉不到就用内置——
         * 远程只是让新镜像不用发版本就能生效，不是必需项。
         */
        fun routes(context: Context): List<MirrorRoute> {
            val cached = runCatching { prefs(context).getString(SP_LIST, "").orEmpty() }
                .getOrNull().orEmpty()
            return mergeRoutes(BUILT_IN_ROUTES, parseRemote(cached).orEmpty())
        }

        /**
         * 候选线路：用户钉住的排最前，其余按铁律顺序兜底。
         * [forApi] 时剔掉不转发 `api.github.com` 的线路，[target] 不适用的线路也不生成。
         */
        fun candidates(
            context: Context,
            target: String,
            pinned: GithubMirror?,
            forApi: Boolean = false,
        ): List<MirrorRoute> {
            val applicable = routes(context)
                .filter { !forApi || it.proxiesApi }
                .filter { it.urlFor(target) != null }
            if (pinned == null || pinned == AUTO) return applicable
            val lead = applicable.firstOrNull { it.id == pinned.id } ?: return applicable
            return listOf(lead) + applicable.filterNot { it == lead }
        }

        /** 拉取 gitee 上的最新清单并写进缓存；成功返回 true，任何失败都只当没这回事。 */
        suspend fun refreshList(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                val body = listClient.newCall(
                    Request.Builder().url(REMOTE_LIST_URL).header("User-Agent", USER_AGENT).build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    response.body?.string().orEmpty()
                }
                if (parseRemote(body) == null) return@withContext false
                prefs(context).edit()
                    .putString(SP_LIST, body)
                    .putLong(SP_LIST_AT, System.currentTimeMillis())
                    .apply()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

        /** 缓存没过期就不拉；关键路径上可以无条件调它。 */
        suspend fun refreshListIfStale(context: Context): Boolean {
            val cachedAt = runCatching { prefs(context).getLong(SP_LIST_AT, 0L) }.getOrDefault(0L)
            if (System.currentTimeMillis() - cachedAt < LIST_TTL_MS) return false
            return refreshList(context)
        }

        private fun parseRemote(body: String): List<MirrorRoute>? {
            if (body.isBlank()) return null
            val parsed = runCatching { remoteJson.decodeFromString<RemoteMirrorList>(body) }
                .getOrNull() ?: return null
            if (parsed.schema != LIST_SCHEMA) return null
            return parsed.mirrors.take(MAX_REMOTE_ENTRIES).mapNotNull { entry ->
                if (entry.id.isBlank() || entry.base.isBlank()) return@mapNotNull null
                MirrorRoute(
                    id = entry.id,
                    label = entry.label.ifBlank { entry.id },
                    prefix = entry.base.trim(),
                    kind = when (entry.kind.lowercase()) {
                        "raw_host" -> MirrorKind.RAW_HOST
                        else -> MirrorKind.PREFIX
                    },
                    proxiesApi = entry.api,
                    tier = entry.tier.coerceIn(TIER_FAST, TIER_RAW_ONLY),
                    enabled = entry.enabled,
                )
            }
        }

        /**
         * 远程覆盖内置：同 id 整体替换（改地址、改档位、下线都行），新 id 追加。
         * 档位夹在 0..3，直连永远保留——远程清单再坏也换不掉「国内直连」这条兜底。
         */
        private fun mergeRoutes(
            builtIn: List<MirrorRoute>,
            remote: List<MirrorRoute>,
        ): List<MirrorRoute> {
            if (remote.isEmpty()) return builtIn
            val merged = LinkedHashMap<String, MirrorRoute>()
            builtIn.forEach { merged[it.id] = it }
            remote.forEach { entry ->
                val clamped = entry.copy(
                    tier = entry.tier.coerceIn(TIER_FAST, TIER_RAW_ONLY),
                    label = entry.label.ifBlank { entry.id },
                )
                merged[entry.id] = if (entry.id == DIRECT.id) {
                    clamped.copy(tier = TIER_DIRECT, enabled = true)
                } else {
                    clamped
                }
            }
            return merged.values.filter { it.enabled }.sortedBy { it.tier }
        }
    }
}
