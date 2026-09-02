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
        Log.d(TAG, "BBC_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. متن خود event
        try {
            val text = StringBuilder()
            collect(event.source, text)
            processText(text.toString())
        } catch (_: Exception) {
        }

        // 2. پنجره فعال
        try {
            rootInActiveWindow?.let {
                val text = StringBuilder()
                collect(it, text)
                processText(text.toString())
            }
        } catch (_: Exception) {
        }

        // 3. تمام پنجره‌های تعاملی؛ مهم‌ترین بخش این نسخه
        try {
            for (window in windows) {
                val root = window.root ?: continue

                val pkg = root.packageName?.toString() ?: ""

                if (!pkg.contains("snapp", ignoreCase = true)) {
                    continue
                }

                val text = StringBuilder()
                collect(root, text)

                Log.d(
                    TAG,
                    "SNAPP_WINDOW pkg=$pkg text=${text.take(500)}"
                )

                processText(text.toString())
            }
        } catch (e: Exception) {
            Log.d(TAG, "WINDOW_ERROR=${e.message}")
        }
    }

    private fun processText(raw: String) {
        if (raw.isBlank()) return

        val text = normalize(raw)

        if (!text.contains("مبدا")) return

        Log.d(TAG, "ORIGIN_TEXT=$text")

        val distance = findOriginDistance(text) ?: return

        Log.d(TAG, "FOUND_DISTANCE=$distance")

        if (distance <= 2.0) {
            TripOverlay.showBlue(distance)
            Log.d(TAG, "RESULT=BLUE")
        } else {
            TripOverlay.showBlack(distance)
            Log.d(TAG, "RESULT=BLACK")
        }
    }

    private fun findOriginDistance(text: String): Double? {
        val originIndex = text.indexOf("مبدا")

        if (originIndex < 0) return null

        val start = maxOf(0, originIndex - 30)
        val end = minOf(text.length, originIndex + 120)

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
            unit.equals("m", ignoreCase = true)
        ) {
            // هر فاصله کمتر از 1 کیلومتر = 1 کیلومتر
            if (number < 1000) 1.0
            else number / 1000.0
        } else {
            number
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        output: StringBuilder
    ) {
        if (node == null) return

        try {
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
                collect(node.getChild(i), output)
            }
        } catch (_: Exception) {
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
        Log.d(TAG, "BBC_INTERRUPTED")
    }
}
