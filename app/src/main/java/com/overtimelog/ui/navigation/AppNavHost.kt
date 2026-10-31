package com.overtimelog.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.overtimelog.di.AppContainer
import com.overtimelog.feature.calendar.CalendarScreen
import com.overtimelog.feature.comptime.CompTimeScreen
import com.overtimelog.feature.overview.OverviewScreen
import com.overtimelog.feature.records.RecordEditScreen
import com.overtimelog.feature.records.RecordsScreen
import com.overtimelog.feature.settings.LicenseScreen
import com.overtimelog.feature.settings.PrivacyScreen
import com.overtimelog.feature.settings.SalaryRuleScreen
import com.overtimelog.feature.settings.SettingsScreen
import com.overtimelog.feature.stats.StatsScreen
import java.time.LocalDate

/** 所有路由集中在这里。带参数的页面用 [edit] 这样的构造函数生成，避免页面里手拼字符串。 */
object Routes {
    const val OVERVIEW = "overview"
    const val RECORDS = "records"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val COMP_TIME = "comptime"
    const val SALARY_RULE = "salary"
    const val PRIVACY = "privacy"
    const val LICENSE = "license"

    /** recordId 为空表示新增；date 用于"从日历某天新增"。 */
    const val EDIT = "edit?recordId={recordId}&date={date}"

    fun edit(recordId: Long? = null, date: LocalDate? = null): String =
        "edit?recordId=${recordId ?: -1L}&date=${date?.toEpochDay() ?: -1L}"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.OVERVIEW, "概览", Icons.Default.Home),
    BottomItem(Routes.RECORDS, "记录", Icons.Default.List),
    BottomItem(Routes.CALENDAR, "日历", Icons.Default.DateRange),
    BottomItem(Routes.STATS, "统计", Icons.Default.Star),
    BottomItem(Routes.SETTINGS, "设置", Icons.Default.Settings),
)

/**
 * 底栏。
 *
 * 不用 Material3 的 NavigationBar：它的选中指示器是 spring 动画，实测一次点击会持续绘制
 * 约 0.9 秒，点得快时会觉得"慢半拍"。这里改成即时切换的静态底栏，点哪页立刻到。
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * 应用导航。
 *
 * 底部 5 个主入口；新增/编辑、调休、薪资规则、隐私说明是二级页面，进入后隐藏底栏，
 * 让用户专注于当前任务。
 */
@Composable
fun OvertimeNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        // 外层只负责底栏，不消费状态栏内边距：页面内层各自有 TopAppBar，
        // 若这里也加 padding，标题会被推低一个状态栏高度（实测多出 128px）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            // 底栏是高频操作，整页淡入淡出会明显"慢半拍"；这里全局关掉转场。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.OVERVIEW, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                OverviewScreen(
                    container = container,
                    onAddRecord = { navController.navigate(Routes.edit()) },
                    onOpenRecord = { id -> navController.navigate(Routes.edit(recordId = id)) },
                    onOpenAllRecords = { navController.navigate(Routes.RECORDS) },
                    onOpenCompTime = { navController.navigate(Routes.COMP_TIME) },
                )
            }

            composable(Routes.RECORDS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                RecordsScreen(
                    container = container,
                    onOpenRecord = { id -> navController.navigate(Routes.edit(recordId = id)) },
                    onAddRecord = { navController.navigate(Routes.edit()) },
                )
            }

            composable(Routes.CALENDAR, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                CalendarScreen(
                    container = container,
                    onOpenRecord = { id -> navController.navigate(Routes.edit(recordId = id)) },
                    onAddRecordForDate = { date -> navController.navigate(Routes.edit(date = date)) },
                )
            }

            composable(Routes.STATS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                StatsScreen(container = container)
            }

            composable(Routes.SETTINGS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                SettingsScreen(
                    container = container,
                    onOpenSalaryRule = { navController.navigate(Routes.SALARY_RULE) },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenLicense = { navController.navigate(Routes.LICENSE) },
                )
            }

            composable(Routes.COMP_TIME) {
                CompTimeScreen(container = container, onBack = { navController.popBackStack() })
            }

            composable(Routes.SALARY_RULE) {
                SalaryRuleScreen(container = container, onBack = { navController.popBackStack() })
            }

            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LICENSE) {
                LicenseScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("date") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val recordId = entry.arguments?.getLong("recordId")?.takeIf { it > 0 }
                val date = entry.arguments?.getLong("date")
                    ?.takeIf { it > 0 }
                    ?.let(LocalDate::ofEpochDay)
                RecordEditScreen(
                    container = container,
                    recordId = recordId,
                    initialDate = date,
                    onDone = { navController.popBackStack() },
                    onOpenSalaryRule = { navController.navigate(Routes.SALARY_RULE) },
                )
            }
        }
    }
}
