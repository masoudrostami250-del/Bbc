package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        // مثال:
        // ۴ دقیقه تا مبدا-۲کیلومتر
        // ۴ دقیقه تا مبدا-۵کیلومتر
        // تا مبدا - ۸۰۰ متر

        private val ORIGIN_DISTANCE = Pattern.compile(
            """تا\s*مبدا\s*[-–—:]?\s*([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )

        private val ORIGIN_DISTANCE_REVERSE = Pattern.compile(
            """مبدا\s*[-–—:]?\s*([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        Log.d(TAG, "BBC SERVICE CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""

        if (!packageName.contains("snapp", ignoreCase = true)) {
            return
        }

        // متن خود Accessibility Event
        val eventText = buildString {
            event.text?.forEach {
                append(it).append(" ")
            }

            event.contentDescription?.toString()?.let {
                append(it).append(" ")
            }
        }

        // کل درخت رابط کاربری اسنپ
        val root = rootInActiveWindow

        val treeText = StringBuilder()

        if (root != null) {
            collectAllText(root, treeText)
        }

        val completeText = normalize(
            eventText + " " + treeText.toString()
        )

        Log.d(TAG, "SNAPP_TEXT=$completeText")

        val distance = findOriginDistance(completeText)

        if (distance == null) {
            // سفر پیدا نشده؛ رنگ قبلی را تغییر نمی‌دهیم.
            return
        }

        Log.d(TAG, "ORIGIN_DISTANCE=$distance")

        if (distance <= 2.0) {
            TripOverlay.showBlue()
            Log.d(TAG, "RESULT=BLUE")
        } else {
            TripOverlay.showBlack()
            Log.d(TAG, "RESULT=BLACK")
        }
    }

    private fun collectAllText(
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
            collectAllText(node.getChild(i), output)
        }
    }

    private fun findOriginDistance(text: String): Double? {

        // حالت اصلی: «تا مبدا-۲کیلومتر»
        val matcher = ORIGIN_DISTANCE.matcher(text)

        if (matcher.find()) {
            return convertDistance(
                matcher.group(1),
                matcher.group(2)
            )
        }

        // حالت جایگزین: «مبدا-۲کیلومتر»
        val reverseMatcher = ORIGIN_DISTANCE_REVERSE.matcher(text)

        if (reverseMatcher.find()) {
            return convertDistance(
                reverseMatcher.group(1),
                reverseMatcher.group(2)
            )
        }

        return null
    }

    private fun convertDistance(
        value: String?,
        unit: String?
    ): Double? {

        if (value == null || unit == null) return null

        val number = try {
            value.replace(',', '.').toDouble()
        } catch (_: Exception) {
            return null
        }

        val u = unit.lowercase()

        return if (
            u.contains("متر") ||
            u == "m"
        ) {
            // قانون پروژه:
            // هر مقدار کمتر از ۱۰۰۰ متر = ۱ کیلومتر
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
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onInterrupt() {
        Log.d(TAG, "BBC SERVICE INTERRUPTED")
    }
}
