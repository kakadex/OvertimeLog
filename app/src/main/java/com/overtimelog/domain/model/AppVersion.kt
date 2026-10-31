package com.overtimelog.domain.model

/**
 * 版本号（X.Y.Z）。
 *
 * 只做一件事：比较大小。应用里的 versionName 和 Release 的 tag（`v1.0.2`、`1.0.2`、
 * `1.0.2-beta`）都能解析成它，所以"有没有新版本"的判断和联网、界面解耦，可以直接单测。
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    /** 自己是不是比 [other] 新。 */
    fun isNewerThan(other: AppVersion): Boolean = this > other

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-+][0-9A-Za-z.\-]+)?$""")

        /**
         * 解析版本号；解析不了返回 null。
         *
         * 宁可"不提示更新"，也不要因为发布页写了奇怪的 tag 就误报新版本。
         */
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val match = PATTERN.matchEntire(text) ?: return null
            return AppVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: 0,
                patch = match.groupValues[3].toIntOrNull() ?: 0,
            )
        }
    }
}
