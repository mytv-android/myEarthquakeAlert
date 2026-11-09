package com.github.mytv.myearthquakealert.widget

/**
 * Coarse "how long ago" for the widget (刚刚 / 3 分钟前 / 2 小时前 / 3 天前) and when that text
 * next changes, so the widget is redrawn exactly when a label would go stale.
 */
object RelativeTime {
    enum class Unit { NOW, MINUTES, HOURS, DAYS }

    data class Age(val unit: Unit, val value: Long)

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun of(ageMs: Long): Age {
        val age = ageMs.coerceAtLeast(0L)
        return when {
            age < MINUTE -> Age(Unit.NOW, 0)
            age < HOUR -> Age(Unit.MINUTES, age / MINUTE)
            age < DAY -> Age(Unit.HOURS, age / HOUR)
            else -> Age(Unit.DAYS, age / DAY)
        }
    }

    /** Milliseconds until [of] gives a different label for something [ageMs] old (> 0). */
    fun untilChange(ageMs: Long): Long {
        val age = ageMs.coerceAtLeast(0L)
        val step = when {
            age < HOUR -> MINUTE
            age < DAY -> HOUR
            else -> DAY
        }
        return step - age % step
    }
}
