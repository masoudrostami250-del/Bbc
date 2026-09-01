package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        /*
         * مثال‌های مورد انتظار:
         *
         * ۴ دقیقه تا مبدا-۲کیلومتر
         * ۵ دقیقه تا مبدا-۵کیلومتر
         * ۳ دقیقه تا مبدا-۸۰۰متر
         */

        private val ORIGIN_DISTANCE = Pattern.compile(
            """مبدا\s*[-–—:]?\s*([0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?)\s*(کیلومتر|km|ک\s*م|ک\.م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )

        /*
         * بعضی نسخه‌های Snapp ممکن است ترتیب متن را
         * به شکل «۲کیلومتر ... مبدا» برگردانند.
         */
        private val DISTANCE_BEFORE_ORIGIN = Pattern.compile(
            """([0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?)\s*(کیلومتر|km|ک\s*م|ک\.م|متر|m)\s*.*مبدا""",
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

        if (!packageName.contains("snapp", ignoreCase = true)) {
            return
        }

        val text = StringBuilder()

        event.text?.forEach {
            text.append(" ")
            text.append(it.toString())
        }

        event.contentDescription?.toString()?.let {
            text.append(" ")
            text.append(it)
        }

        rootInActiveWindow?.let {
            collectText(it, text)
        }

        val fullText = normalizeDigits(text.toString())

        Log.d(TAG, "SNAPP_TEXT=$fullText")

        val distance = findOriginDistance(fullText)

        if (distance == null) {
            return
        }

        Log.d(TAG, "ORIGIN_DISTANCE=$distance km")

        if (distance <= 2.0) {
            Log.d(TAG, "RESULT=BLUE")
            TripOverlay.showBlue()
        } else {
            Log.d(TAG, "RESULT=BLACK")
            TripOverlay.showBlack()
        }
    }

    private fun findOriginDistance(text: String): Double? {

        /*
         * حالت اصلی:
         * «مبدا-5کیلومتر»
         */
        val direct = ORIGIN_DISTANCE.matcher(text)

        var found: Double? = null

        while (direct.find()) {
            val value = parseNumber(direct.group(1))
            val unit = direct.group(2)

            if (value != null && unit != null) {
                found = convertToKm(value, unit)
            }
        }

        if (found != null) {
            return found
        }

        /*
         * حالت معکوس، در صورت تفاوت ساختار Accessibility.
         */
        val reverse = DISTANCE_BEFORE_ORIGIN.matcher(text)

        while (reverse.find()) {
            val value = parseNumber(reverse.group(1))
            val unit = reverse.group(2)

            if (value != null && unit != null) {
                found = convertToKm(value, unit)
            }
        }

        return found
    }

    private fun convertToKm(
        value: Double,
        unit: String
    ): Double {

        val u = unit.lowercase()

        if (u.contains("متر") || u == "m") {

            /*
             * طبق قانون شما:
             * 500m -> 1km
             * 800m -> 1km
             */
            return if (value < 1000.0) {
                1.0
            } else {
                value / 1000.0
            }
        }

        return value
    }

    private fun collectText(
        node: AccessibilityNodeInfo?,
        output: StringBuilder
    ) {
        if (node == null) return

        node.text?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(" ")
                output.append(it)
            }
        }

        node.contentDescription?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(" ")
                output.append(it)
            }
        }

        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), output)
        }
    }

    private fun normalizeDigits(input: String): String {

        return input
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

    private fun parseNumber(value: String?): Double? {

        if (value == null) return null

        return try {
            value
                .replace(',', '.')
                .replace('٫', '.')
                .trim()
                .toDouble()
        } catch (_: Exception) {
            null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICE_INTERRUPTED")
    }
}
