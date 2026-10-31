package com.overtimelog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.overtimelog.data.settings.ThemeMode

// 主色取偏稳的靛蓝，和"记账/工时"这类工具应用的气质一致；加班类型另用橙/紫/红区分。
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5AA6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF77536D),
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFAF8FF),
    surfaceVariant = Color(0xFFE2E1EC),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF062E6E),
    primaryContainer = Color(0xFF26438A),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFFE6BAD9),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF45464F),
)

/**
 * 应用主题。深浅色跟随设置；V1.0 先提供深色，PRD 里标的是 P1，但实现成本很低就不推后了。
 */
@Composable
fun OvertimeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // 不播水波纹：系统 RippleDrawable 一次点击会让 UI 线程持续重绘约 0.5–0.9 秒，
    // 是底栏动画之后最后一个能明显感觉到的延迟。按下时按钮还有 120ms 的高程变化作为反馈，
    // 需要更明显的按压反馈时，改成静态按压色，不要恢复默认水波纹。
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
