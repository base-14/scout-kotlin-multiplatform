package io.base14.scout.android

import androidx.navigation.NavController
import androidx.navigation.NavDestination

fun NavController.trackScoutScreens() {
    addOnDestinationChangedListener { _, destination, _ ->
        Scout.setScreen(routeOf(destination))
    }
}

private fun routeOf(destination: NavDestination): String = destination.route ?: destination.displayName
