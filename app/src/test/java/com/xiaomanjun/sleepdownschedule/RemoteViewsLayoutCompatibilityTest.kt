package com.xiaomanjun.sleepdownschedule

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class RemoteViewsLayoutCompatibilityTest {
    @Test
    fun widgetLayoutsOnlyUseRemoteViewsSafeClasses() {
        val layoutDirectory = sequenceOf(
            File("src/main/res/layout"),
            File("app/src/main/res/layout")
        ).first(File::isDirectory)
        val widgetLayouts = layoutDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("widget_") && it.extension == "xml" }
        val unsupported = buildList {
            widgetLayouts.forEach { layout ->
                val root = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(layout)
                    .documentElement
                collectUnsupported(layout.name, root, this)
            }
        }

        assertTrue(
            "RemoteViews cannot inflate these widget classes: ${unsupported.joinToString()}",
            unsupported.isEmpty()
        )
    }

    private fun collectUnsupported(
        layoutName: String,
        element: Element,
        result: MutableList<String>
    ) {
        val className = element.tagName.substringAfterLast('.')
        if (className !in supportedClasses) {
            result += "$layoutName:<${element.tagName}>"
        }
        val children = element.childNodes
        repeat(children.length) { index ->
            val child = children.item(index)
            if (child is Element) collectUnsupported(layoutName, child, result)
        }
    }

    private companion object {
        val supportedClasses = setOf(
            "AdapterViewFlipper",
            "AnalogClock",
            "Button",
            "Chronometer",
            "FrameLayout",
            "GridLayout",
            "GridView",
            "ImageButton",
            "ImageView",
            "LinearLayout",
            "ListView",
            "ProgressBar",
            "RelativeLayout",
            "StackView",
            "TextClock",
            "TextView",
            "ViewFlipper"
        )
    }
}
