package com.yatharth.yool

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation.compose.rememberNavController
import com.yatharth.yool.ui.navigation.Routes
import com.yatharth.yool.ui.navigation.YoolNavHost
import com.yatharth.yool.ui.navigation.navigateTab
import com.yatharth.yool.ui.theme.YoolTheme

class MainActivity : ComponentActivity() {

    /** Bumped whenever an intent asks to open Today (e.g. from a reminder notification). */
    private val openTodayRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            YoolTheme {
                val nav = rememberNavController()
                val requests = openTodayRequests.intValue
                LaunchedEffect(requests) { if (requests > 0) nav.navigateTab(Routes.TODAY) }
                YoolNavHost(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_DESTINATION) == Routes.TODAY) openTodayRequests.intValue++
    }

    companion object {
        /** Put Routes.TODAY here to open the Today tab. */
        const val EXTRA_DESTINATION = "yool.destination"
    }
}
