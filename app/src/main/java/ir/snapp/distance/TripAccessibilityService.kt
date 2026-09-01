package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val KM = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(?:کیلومتر|km|ک\.?م)""",
            Pattern.CASE_INSENSITIVE
        )

        private val METER = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(?:متر|m)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        TripOverlay.show(this)
        TripOverlay.showWhite()
        Log.d(TAG, "CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (!pkg.contains("snapp", true)) return

        val root = rootInActiveWindow ?: return

        val distance = findOriginDistance(root)

        if (distance == null) {
            return
        }

        Log.d(TAG, "ORIGIN_DISTANCE=$distance")

        if (distance <= 2.0) {
            TripOverlay.showBlue()
            Log.d(TAG, "BLUE")
        } else {
            TripOverlay.showBlack()
            Log.d(TAG, "BLACK")
        }
    }

    private fun findOriginDistance(
        node: AccessibilityNodeInfo?
    ): Double? {
        if (node == null) return null

        val ownText = buildString {
            node.text?.toString()?.let { append(it).append(" ") }
            node.contentDescription?.toString()?.let { append(it) }
        }

        val text = normalize(ownText)

        /*
         * فقط Node مربوط به «تا مبدا» را بررسی می‌کنیم.
         * مثال:
         * ۴ دقیقه تا مبدا-۲کیلومتر
         * ۴ دقیقه تا مبدا-۵کیلومتر
         */

        if (text.contains("مبدا")) {

            val km = KM.matcher(text)
            if (km.find()) {
                return number(km.group(1))
            }

            val meter = METER.matcher(text)
            if (meter.find()) {
                val meters = number(meter.group(1))
                if (meters != null) {
                    return if (meters < 1000.0) {
                        1.0
                    } else {
                        meters / 1000.0
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val result = findOriginDistance(node.getChild(i))
            if (result != null) return result
        }

        return null
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
    }

    private fun number(value: String?): Double? {
        if (value == null) return null

        return try {
            value.replace(',', '.').toDouble()
        } catch (_: Exception) {
            null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "INTERRUPTED")
    }
}
