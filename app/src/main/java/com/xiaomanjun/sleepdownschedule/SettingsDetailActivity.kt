package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.app.ui.SettingsDetailActivityHost

class SettingsDetailActivity : SettingsDetailActivityHost()

/**
 * Real translucent destination for the QuickSheet anchored route.
 *
 * Android decides whether an Activity occludes the one below from its manifest theme before
 * [android.app.Activity.onCreate]. Changing the theme from the shared host is therefore too late
 * on ColorOS: the Compose window becomes transparent, but the home Activity has already been
 * removed from the visible task surface and the Morph reveals black. This class deliberately owns
 * no UI; it only supplies the correct manifest-level window classification while reusing the
 * exact same settings host.
 */
class QuickSheetSettingsDetailActivity : SettingsDetailActivityHost()
