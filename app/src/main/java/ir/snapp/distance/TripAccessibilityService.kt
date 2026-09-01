package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val DISTANCE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        Log.d(TAG, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""

        // متن مستقیم event
        val eventText = StringBuilder()

        event.text?.forEach {
            eventText.append(it).append(" ")
        }

        event.contentDescription?.toString()?.let {
            eventText.append(it).append(" ")
        }

        // source خود event
        try {
            collect(event.source, eventText)
        } catch (_: Exception) {
        }

        // کل صفحه
        try {
            collect(rootInActiveWindow, eventText)
        } catch (_: Exception) {
        }

        val text = normalize(eventText.toString())

        if (text.isBlank()) return

        // فقط صفحه‌ای که احتمالاً مربوط به اسنپ است
        if (
            !packageName.contains("snapp", true) &&
            !text.contains("مبدا")
        ) {
            return
        }

        if (text.contains("مبدا")) {
            Log.d(TAG, "ORIGIN_TEXT=$text")

            val distance = findDistanceNearOrigin(text)

            if (distance != null) {
                Log.d(TAG, "DISTANCE=$distance")

                if (distance <= 2.0) {
                    TripOverlay.showBlue()
                    Log.d(TAG, ">>> BLUE <<<")
                } else {
                    TripOverlay.showBlack()
                    Log.d(TAG, ">>> BLACK <<<")
                }
            }
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        output: StringBuilder
    ) {
        if (node == null) return

        node.text?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(it).append(" ")
            }
        }

        node.contentDescription?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(it).append(" ")
            }
        }

        for (i in 0 until node.childCount) {
            try {
                collect(node.getChild(i), output)
            } catch (_: Exception) {
            }
        }
    }

    private fun findDistanceNearOrigin(text: String): Double? {

        val originIndex = text.indexOf("مبدا")

        if (originIndex < 0) return null

        // فقط بخش اطراف «مبدا» بررسی شود
        val start = maxOf(0, originIndex - 30)
        val end = minOf(text.length, originIndex + 100)

        val area = text.substring(start, end)

        Log.d(TAG, "ORIGIN_AREA=$area")

        val matcher = DISTANCE.matcher(area)

        if (!matcher.find()) return null

        val value = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: return null

        val number = try {
            value.replace(',', '.').toDouble()
        } catch (_: Exception) {
            return null
        }

        return if (
            unit.contains("متر") ||
            unit.equals("m", true)
        ) {
            // قانون پروژه:
            // ۵۰۰ متر و ۸۰۰ متر = ۱ کیلومتر
            if (number < 1000.0) {
                1.0
            } else {
                number / 1000.0
            }
        } else {
            number
        }
    }

    private fun normalize(value: String): String {
        return value
            .replace('۰', '0')
            .replace('۱', '1')
            .replace('۲', '2')
            .replace('۳', '3')
            .replace('۴', '4')
            .replace('۵', '5')
            .replace('۶', '6')
            .replace('۷', '7')
            .replace('۸', '8')
            .replace('۹', '9')
            .replace('٠', '0')
            .replace('١', '1')
            .replace('٢', '2')
            .replace('٣', '3')
            .replace('٤', '4')
            .replace('٥', '5')
            .replace('٦', '6')
            .replace('٧', '7')
            .replace('٨', '8')
            .replace('٩', '9')
            .replace('٫', '.')
            .replace('٬', ',')
            .replace('–', '-')
            .replace('—', '-')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICE_INTERRUPTED")
    }
}
