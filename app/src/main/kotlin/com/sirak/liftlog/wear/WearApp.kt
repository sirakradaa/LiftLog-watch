package com.sirak.liftlog.wear

import android.app.Application
import com.sirak.liftlog.wear.data.WorkoutRepository
import com.sirak.liftlog.wear.sync.WearSync
import com.sirak.liftlog.wear.ui.loadPalette

class WearApp : Application() {
    lateinit var repo: WorkoutRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = WorkoutRepository(this)
        repo.load()
        loadPalette(this)
        WearSync.listen(this, repo)
        WearSync.pullTheme(this)
        WearSync.pullActive(this, repo)
        WearSync.pullTemplates(this, repo)
        WearSync.refreshPhoneLink(this)
    }

    companion object {
        lateinit var instance: WearApp
            private set
    }
}
