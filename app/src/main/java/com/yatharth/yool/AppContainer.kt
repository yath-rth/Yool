package com.yatharth.yool

import android.app.Application
import android.content.Context
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.YoolDatabase

/** Manual DI. Feature modules add their singletons here. */
class AppContainer(context: Context) {
    val database: YoolDatabase = YoolDatabase.build(context)
    val repository: TrackerRepository = TrackerRepository(database)
}

class YoolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as YoolApp).container
