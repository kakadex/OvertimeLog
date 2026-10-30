package com.overtimelog.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier.padding(padding),
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
