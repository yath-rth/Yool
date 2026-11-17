package com.yatharth.yool

import android.app.Application
import android.content.Context
import com.yatharth.yool.data.FoodRepository
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.YoolDatabase
import com.yatharth.yool.data.healthconnect.HealthConnectManager
import com.yatharth.yool.sync.HealthConnectPrefs
import com.yatharth.yool.sync.SyncEngine
import com.yatharth.yool.sync.SyncWorker

/** Manual DI. Feature modules add their singletons here. */
class AppContainer(context: Context) {
    val database: YoolDatabase = YoolDatabase.build(context)
    val repository: TrackerRepository = TrackerRepository(database)
    val foodRepository: FoodRepository = FoodRepository(database)

    val healthConnectManager = HealthConnectManager(context.applicationContext)
    val syncEngine = SyncEngine(healthConnectManager, repository, HealthConnectPrefs(context))
}

class YoolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
        com.yatharth.yool.reminders.ReminderScheduler.rescheduleAllAsync(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as YoolApp).container
