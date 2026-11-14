package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.Application
import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconManager
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconMode
import com.xiaomanjun.sleepdownschedule.core.identity.AppIconStyle
import com.xiaomanjun.sleepdownschedule.core.identity.applyAppNotificationIcon
import com.xiaomanjun.sleepdownschedule.core.identity.currentLiveUpdateIconResId
import com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveUpdateIconRefreshTest {
    private val context = RuntimeEnvironment.getApplication()
    private val iconKeys = listOf(
        "miui.focus.pic_app_icon", "miui.focus.pic_app_icon_dark",
        "miui.focus.pic_small", "miui.focus.pic_small_dark"
    )

    @Test fun islandPicturesCatchUpEvenWhenTheSmallIconAlreadyMatches() {
        for (mode in listOf(AppIconMode.LIGHT, AppIconMode.DARK)) {
            AppIconManager.setMode(context, mode)
            AppIconManager.setStyle(context, AppIconStyle.KANBAN)
            val originalIcon = currentLiveUpdateIconResId(context)
            val previousPicture = Icon.createWithResource(context, originalIcon)
            AppIconManager.setStyle(context, AppIconStyle.MINIMAL)
            val selectedIcon = currentLiveUpdateIconResId(context)
            assertNotEquals(originalIcon, selectedIcon)

            val notification = Notification.Builder(context, XiaomiSuperIsland.ChannelId)
                .applyAppNotificationIcon(context)
                .setContentTitle("测试课程")
                .build()
            notification.extras.putString("miui.focus.param", JSONObject()
                .put("param_v2", JSONObject().put("business", "course_reminder").put("sequence", 10L)
                    .put("hintInfo", JSONObject().put("title", "下课")))
                .toString())
            notification.extras.putBundle("miui.focus.pics", Bundle().apply {
                iconKeys.forEach { putParcelable(it, previousPicture) }
                putString("other_picture_metadata", "keep")
            })

            val refreshed = checkNotNull(NotificationScheduler.updatedLiveUpdateIcon(context, notification))
            assertEquals(selectedIcon, refreshed.smallIcon.resId)
            assertEquals("测试课程", refreshed.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            val pictures = checkNotNull(refreshed.extras.getBundle("miui.focus.pics"))
            for (key in iconKeys) {
                val picture = checkNotNull(BundleCompat.getParcelable(pictures, key, Icon::class.java))
                assertEquals(context.packageName, picture.resPackage)
                assertEquals(selectedIcon, picture.resId)
            }
            assertEquals("keep", pictures.getString("other_picture_metadata"))
            val params = JSONObject(refreshed.extras.getString("miui.focus.param").orEmpty())
                .getJSONObject("param_v2")
            assertTrue(params.getLong("sequence") > 10L)
            assertEquals("course_reminder", params.getString("business"))
            assertEquals("下课", params.getJSONObject("hintInfo").getString("title"))
            assertNull(NotificationScheduler.updatedLiveUpdateIcon(context, refreshed))
        }
    }

    @Test fun ordinaryUpdatesKeepTheirExistingExtras() {
        AppIconManager.setMode(context, AppIconMode.LIGHT)
        AppIconManager.setStyle(context, AppIconStyle.KANBAN)
        val notification = Notification.Builder(context, "ordinary")
            .applyAppNotificationIcon(context)
            .setContentTitle("测试课程")
            .build()
        notification.extras.putString("course_state", "active")
        AppIconManager.setStyle(context, AppIconStyle.MINIMAL)
        val refreshed = checkNotNull(NotificationScheduler.updatedLiveUpdateIcon(context, notification))
        assertEquals(currentLiveUpdateIconResId(context), refreshed.smallIcon.resId)
        assertEquals("active", refreshed.extras.getString("course_state"))
        assertNull(refreshed.extras.getBundle("miui.focus.pics"))
    }
}
