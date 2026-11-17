package com.yatharth.yool.data.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.theme.Background
import com.yatharth.yool.ui.theme.YoolTheme

/** Privacy / permissions rationale shown by Health Connect ("Why does yool need this?"). */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YoolTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Background)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScreenTitle("Health data in yool")
                    ShadowCard {
                        SectionLabel("What we read")
                        Text(
                            "Steps, sleep, nutrition (calories and protein) and strength workouts, so your daily log fills itself in.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SectionLabel("Where it goes")
                        Text(
                            "Nowhere. Everything stays on this device. yool has no account, no server and no analytics, and never shares or sells your data.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        SectionLabel("Your control")
                        Text(
                            "yool only reads data and never writes it. You can revoke access at any time in Health Connect settings.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TxButton("Close", onClick = { finish() })
                }
            }
        }
    }
}
