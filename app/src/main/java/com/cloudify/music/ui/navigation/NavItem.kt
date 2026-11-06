package com.cloudify.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Stats : Screen("stats")
    data object Auth : Screen("auth")
    data object Artist : Screen("artist/{artistName}") {
        fun createRoute(artistName: String): String = "artist/${android.net.Uri.encode(artistName)}"
    }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(Screen.Home.route, "Главная", Icons.Rounded.Home)
    data object Search : BottomNavItem(Screen.Search.route, "Поиск", Icons.Rounded.Search)
    data object Library : BottomNavItem(Screen.Library.route, "Моя музыка", Icons.Rounded.LibraryMusic)
    data object Stats : BottomNavItem(Screen.Stats.route, "Статистика", Icons.Rounded.BarChart)

    companion object {
        val items = listOf(Home, Search, Library, Stats)
    }
}
