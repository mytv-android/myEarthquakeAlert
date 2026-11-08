package com.github.mytv.myearthquakealert.ui.map

import androidx.compose.runtime.Immutable

/** A city for the manual location picker (TV) and the offline basemap labels. */
@Immutable
data class ChinaCity(
    val name: String,
    val nameEn: String,
    val province: String,
    val provinceEn: String,
    val latitude: Double,
    val longitude: Double,
    /** Seat of the provincial-level government. */
    val capital: Boolean = false,
) {
    /** "四川 成都" — the stored manual location name ([name] alone for municipalities). */
    fun displayName(english: Boolean): String = when {
        english && name == province -> nameEn
        english -> "$nameEn, $provinceEn"
        name == province -> name
        else -> "$province $name"
    }
}

/**
 * Provincial capitals and other seismically relevant cities (city-centre coordinates,
 * WGS-84). Provinces are listed roughly from the most to the least earthquake-prone.
 */
object ChinaCities {
    private inline fun group(province: String, provinceEn: String, build: Builder.() -> Unit): List<ChinaCity> =
        Builder(province, provinceEn).apply(build).cities

    class Builder internal constructor(private val province: String, private val provinceEn: String) {
        val cities = mutableListOf<ChinaCity>()
        fun capital(name: String, en: String, lat: Double, lon: Double) {
            cities += ChinaCity(name, en, province, provinceEn, lat, lon, capital = true)
        }
        fun city(name: String, en: String, lat: Double, lon: Double) {
            cities += ChinaCity(name, en, province, provinceEn, lat, lon)
        }
    }

    val all: List<ChinaCity> = listOf(
        group("四川", "Sichuan") {
            capital("成都", "Chengdu", 30.573, 104.066)
            city("雅安", "Ya'an", 29.980, 103.013)
            city("绵阳", "Mianyang", 31.468, 104.679)
            city("乐山", "Leshan", 29.552, 103.766)
            city("宜宾", "Yibin", 28.752, 104.643)
            city("泸州", "Luzhou", 28.872, 105.442)
            city("南充", "Nanchong", 30.837, 106.111)
            city("西昌", "Xichang", 27.894, 102.264)
            city("康定", "Kangding", 30.050, 101.964)
            city("马尔康", "Barkam", 31.906, 102.206)
        },
        group("云南", "Yunnan") {
            capital("昆明", "Kunming", 25.038, 102.718)
            city("大理", "Dali", 25.606, 100.268)
            city("丽江", "Lijiang", 26.855, 100.227)
            city("昭通", "Zhaotong", 27.338, 103.717)
            city("玉溪", "Yuxi", 24.352, 102.543)
            city("普洱", "Pu'er", 22.825, 100.966)
            city("保山", "Baoshan", 25.112, 99.162)
        },
        group("西藏", "Tibet") {
            capital("拉萨", "Lhasa", 29.652, 91.172)
            city("日喀则", "Shigatse", 29.267, 88.881)
            city("林芝", "Nyingchi", 29.649, 94.362)
            city("昌都", "Qamdo", 31.141, 97.172)
        },
        group("新疆", "Xinjiang") {
            capital("乌鲁木齐", "Ürümqi", 43.825, 87.617)
            city("喀什", "Kashgar", 39.470, 75.990)
            city("伊宁", "Yining", 43.909, 81.324)
            city("库尔勒", "Korla", 41.764, 86.146)
            city("阿克苏", "Aksu", 41.169, 80.260)
            city("和田", "Hotan", 37.114, 79.922)
            city("哈密", "Hami", 42.819, 93.515)
        },
        group("青海", "Qinghai") {
            capital("西宁", "Xining", 36.617, 101.778)
            city("格尔木", "Golmud", 36.402, 94.903)
            city("玉树", "Yushu", 33.004, 97.007)
        },
        group("甘肃", "Gansu") {
            capital("兰州", "Lanzhou", 36.061, 103.834)
            city("天水", "Tianshui", 34.581, 105.725)
            city("陇南", "Longnan", 33.401, 104.922)
            city("张掖", "Zhangye", 38.926, 100.450)
            city("酒泉", "Jiuquan", 39.732, 98.494)
        },
        group("宁夏", "Ningxia") {
            capital("银川", "Yinchuan", 38.487, 106.231)
            city("固原", "Guyuan", 36.016, 106.243)
        },
        group("台湾", "Taiwan") {
            capital("台北", "Taipei", 25.033, 121.565)
            city("台中", "Taichung", 24.148, 120.674)
            city("高雄", "Kaohsiung", 22.627, 120.301)
            city("花莲", "Hualien", 23.992, 121.601)
        },
        group("重庆", "Chongqing") {
            capital("重庆", "Chongqing", 29.563, 106.551)
        },
        group("陕西", "Shaanxi") {
            capital("西安", "Xi'an", 34.341, 108.940)
            city("宝鸡", "Baoji", 34.362, 107.238)
            city("汉中", "Hanzhong", 33.068, 107.023)
            city("延安", "Yan'an", 36.585, 109.490)
        },
        group("山西", "Shanxi") {
            capital("太原", "Taiyuan", 37.871, 112.549)
            city("大同", "Datong", 40.077, 113.300)
            city("临汾", "Linfen", 36.088, 111.519)
        },
        group("河北", "Hebei") {
            capital("石家庄", "Shijiazhuang", 38.042, 114.515)
            city("唐山", "Tangshan", 39.631, 118.180)
            city("保定", "Baoding", 38.874, 115.465)
            city("张家口", "Zhangjiakou", 40.824, 114.887)
            city("邢台", "Xingtai", 37.071, 114.505)
        },
        group("北京", "Beijing") {
            capital("北京", "Beijing", 39.904, 116.407)
        },
        group("天津", "Tianjin") {
            capital("天津", "Tianjin", 39.084, 117.201)
        },
        group("内蒙古", "Inner Mongolia") {
            capital("呼和浩特", "Hohhot", 40.842, 111.749)
            city("包头", "Baotou", 40.657, 109.840)
            city("赤峰", "Chifeng", 42.258, 118.887)
        },
        group("辽宁", "Liaoning") {
            capital("沈阳", "Shenyang", 41.806, 123.432)
            city("大连", "Dalian", 38.914, 121.615)
        },
        group("吉林", "Jilin") {
            capital("长春", "Changchun", 43.817, 125.324)
            city("吉林", "Jilin City", 43.838, 126.550)
            city("延吉", "Yanji", 42.891, 129.509)
        },
        group("黑龙江", "Heilongjiang") {
            capital("哈尔滨", "Harbin", 45.803, 126.535)
            city("齐齐哈尔", "Qiqihar", 47.354, 123.918)
        },
        group("山东", "Shandong") {
            capital("济南", "Jinan", 36.651, 117.120)
            city("青岛", "Qingdao", 36.067, 120.383)
            city("临沂", "Linyi", 35.104, 118.356)
            city("烟台", "Yantai", 37.464, 121.448)
        },
        group("河南", "Henan") {
            capital("郑州", "Zhengzhou", 34.746, 113.625)
            city("洛阳", "Luoyang", 34.620, 112.454)
            city("南阳", "Nanyang", 32.991, 112.528)
        },
        group("江苏", "Jiangsu") {
            capital("南京", "Nanjing", 32.060, 118.797)
            city("苏州", "Suzhou", 31.299, 120.585)
            city("徐州", "Xuzhou", 34.205, 117.284)
            city("宿迁", "Suqian", 33.963, 118.275)
        },
        group("上海", "Shanghai") {
            capital("上海", "Shanghai", 31.230, 121.474)
        },
        group("浙江", "Zhejiang") {
            capital("杭州", "Hangzhou", 30.274, 120.155)
            city("宁波", "Ningbo", 29.868, 121.544)
            city("温州", "Wenzhou", 27.994, 120.699)
        },
        group("安徽", "Anhui") {
            capital("合肥", "Hefei", 31.821, 117.227)
            city("芜湖", "Wuhu", 31.353, 118.433)
            city("安庆", "Anqing", 30.543, 117.063)
        },
        group("福建", "Fujian") {
            capital("福州", "Fuzhou", 26.074, 119.296)
            city("厦门", "Xiamen", 24.480, 118.089)
            city("泉州", "Quanzhou", 24.874, 118.676)
        },
        group("江西", "Jiangxi") {
            capital("南昌", "Nanchang", 28.682, 115.858)
            city("九江", "Jiujiang", 29.705, 116.001)
            city("赣州", "Ganzhou", 25.831, 114.935)
        },
        group("湖北", "Hubei") {
            capital("武汉", "Wuhan", 30.593, 114.305)
            city("宜昌", "Yichang", 30.692, 111.287)
            city("襄阳", "Xiangyang", 32.009, 112.122)
        },
        group("湖南", "Hunan") {
            capital("长沙", "Changsha", 28.228, 112.939)
            city("岳阳", "Yueyang", 29.357, 113.129)
            city("衡阳", "Hengyang", 26.894, 112.572)
        },
        group("广东", "Guangdong") {
            capital("广州", "Guangzhou", 23.129, 113.264)
            city("深圳", "Shenzhen", 22.543, 114.058)
            city("汕头", "Shantou", 23.354, 116.682)
            city("湛江", "Zhanjiang", 21.271, 110.359)
        },
        group("广西", "Guangxi") {
            capital("南宁", "Nanning", 22.817, 108.366)
            city("桂林", "Guilin", 25.274, 110.290)
            city("柳州", "Liuzhou", 24.326, 109.428)
            city("百色", "Baise", 23.903, 106.618)
        },
        group("贵州", "Guizhou") {
            capital("贵阳", "Guiyang", 26.647, 106.630)
            city("遵义", "Zunyi", 27.725, 106.927)
            city("六盘水", "Liupanshui", 26.593, 104.830)
        },
        group("海南", "Hainan") {
            capital("海口", "Haikou", 20.044, 110.199)
            city("三亚", "Sanya", 18.253, 109.512)
        },
        group("香港", "Hong Kong") {
            capital("香港", "Hong Kong", 22.320, 114.169)
        },
        group("澳门", "Macao") {
            capital("澳门", "Macao", 22.199, 113.544)
        },
    ).flatten()

    /** Provinces in list order, each with its cities (capital first). */
    val byProvince: List<Pair<String, List<ChinaCity>>> by lazy {
        all.groupBy { it.province }.toList()
    }

    val capitals: List<ChinaCity> by lazy { all.filter { it.capital } }
}
