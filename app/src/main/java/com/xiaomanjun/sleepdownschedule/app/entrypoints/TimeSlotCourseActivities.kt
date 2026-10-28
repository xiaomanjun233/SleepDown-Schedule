// Preserve the shipped component FQCNs while Activity assembly belongs to app/.
package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.SettingsDetailActivityHost

/** Opaque Activity hosts are application assembly; the feature owns the editor content. */
class SpecialCourseEditorActivity : SettingsDetailActivityHost()
class NativeSpecialCourseEditorActivity : SettingsDetailActivityHost()
