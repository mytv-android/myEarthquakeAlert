package com.github.mytv.myearthquakealert.data.source

enum class EewSource(
    val label: String,
    val wsUrl: String,
    val httpUrl: String,
) {
    SICHUAN(
        label = "四川地震局",
        wsUrl = "wss://ws-api.wolfx.jp/sc_eew",
        httpUrl = "https://api.wolfx.jp/sc_eew.json",
    ),
    CENC(
        label = "中国地震台网",
        wsUrl = "wss://ws-api.wolfx.jp/cenc_eew",
        httpUrl = "https://api.wolfx.jp/cenc_eew.json",
    ),
    FUJIAN(
        label = "福建地震局",
        wsUrl = "wss://ws-api.wolfx.jp/fj_eew",
        httpUrl = "https://api.wolfx.jp/fj_eew.json",
    ),
    CHONGQING(
        label = "重庆地震局",
        wsUrl = "wss://ws-api.wolfx.jp/cq_eew",
        httpUrl = "https://api.wolfx.jp/cq_eew.json",
    ),
}
