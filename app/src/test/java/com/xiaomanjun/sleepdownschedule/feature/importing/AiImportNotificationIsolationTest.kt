package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import com.xiaomanjun.sleepdownschedule.AiImportForegroundService
import com.xiaomanjun.sleepdownschedule.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AiImportNotificationIsolationTest {
    @Test fun queuedStartForReplacedTaskCannotClaimTheForeground() {
        val context = RuntimeEnvironment.getApplication()
        AiEduImportProgressSession.beginTask(AiEduImportProgress(taskId = "new"))
        val controller = Robolectric.buildService(AiImportForegroundService::class.java).create()
        try {
            val result = controller.get().onStartCommand(Intent(context, AiImportForegroundService::class.java)
                .setAction("${BuildConfig.APPLICATION_ID}.action.AI_IMPORT_START")
                .putExtra(AiImportTaskManager.EXTRA_TASK_ID, "old"), 0, 1)
            assertEquals(Service.START_NOT_STICKY, result)
            assertEquals(0, context.getSystemService(NotificationManager::class.java).activeNotifications.size)
            assertEquals("new", AiEduImportProgressSession.progress.value!!.taskId)
        } finally {
            controller.destroy()
            AiEduImportProgressSession.update(null)
        }
    }

    @Test fun openingAnOldTaskDoesNotDismissTheNewCompletion() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("test", "test", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(20260831, Notification.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addExtras(Bundle().apply { putString(AiImportTaskManager.EXTRA_TASK_ID, "new") }).build())
        AiImportForegroundService.clearCompletion(context, "old")
        assertEquals(1, manager.activeNotifications.size)
        AiImportForegroundService.clearCompletion(context, "new")
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun newerProgressNotificationDoesNotRetargetAnEarlierPendingIntent() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        AiImportForegroundService.update(context, "first", "读取材料")
        val first = manager.activeNotifications.single().notification.contentIntent
        AiImportForegroundService.update(context, "second", "本地校验")
        val second = manager.activeNotifications.single().notification.contentIntent
        assertNotEquals(first, second)
        assertEquals("first", Shadows.shadowOf(first).savedIntent.getStringExtra(AiImportTaskManager.EXTRA_TASK_ID))
        assertEquals("second", Shadows.shadowOf(second).savedIntent.getStringExtra(AiImportTaskManager.EXTRA_TASK_ID))
    }
}
