package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativePickerStyle

/** Inflate, but never show, a stock dialog: COUI resolves its own theme and accessibility sizes. */
internal fun ColorOsNativeUi.Source.pickerStyle(context: Context, density: Density): NativePickerStyle? =
    ColorOsNativeUi.prepare("read system picker dimensions") {
        val type = load("com.coui.appcompat.dialog.COUIAlertDialogBuilder")
        val builder = type.getConstructor(Context::class.java).newInstance(context)
        type.getMethod("setTitle", CharSequence::class.java).invoke(builder, "标题")
        type.getMethod("setPositiveButton", CharSequence::class.java, DialogInterface.OnClickListener::class.java)
            .invoke(builder, "确定", null)
        val dialog = type.getMethod("create").invoke(builder) as Dialog
        dialog.create()
        val title = requireNotNull(dialog.findViewById<TextView>(resource("alertTitle", "id")))
        val button = dialog.javaClass.getMethod("getButton", Int::class.javaPrimitiveType)
            .invoke(dialog, DialogInterface.BUTTON_POSITIVE) as TextView
        button.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        with(density) {
            NativePickerStyle(title.textSize.toSp(), FontWeight(title.typeface.weight),
                maxOf(button.measuredHeight, button.minimumHeight, button.layoutParams?.height ?: 0).toDp(),
                maxOf(button.measuredWidth, button.minimumWidth).toDp(),
                button.textSize.toSp(), FontWeight(button.typeface.weight))
        }
    }
