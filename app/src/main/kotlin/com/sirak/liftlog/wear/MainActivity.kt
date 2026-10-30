package com.sirak.liftlog.wear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.sirak.liftlog.wear.sync.WearSync
import com.sirak.liftlog.wear.ui.LiftLogTheme
import com.sirak.liftlog.wear.ui.StartScreen
import com.sirak.liftlog.wear.ui.WorkoutScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as WearApp
        val repo = app.repo
        setContent {
            LiftLogTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    timeText = { TimeText() },
                    vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
                ) {
                    val workout = repo.workout
                    if (workout == null) {
                        StartScreen(
                            templates = repo.templates,
                            phoneConnected = WearSync.phoneConnected,
                            onStart = repo::start,
                            onStartTemplate = repo::startTemplate,
                        )
                    } else {
                        WorkoutScreen(workout = workout, repo = repo)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as WearApp
        WearSync.pullActive(app, app.repo)
        WearSync.pullTemplates(app, app.repo)
        WearSync.refreshPhoneLink(app)
    }
}
