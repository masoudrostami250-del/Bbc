package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val KM_PATTERN = Pattern.compile(
            """([0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?)\s*کیلومتر""",
            Pattern.CASE_INSENSITIVE
        )

        private val METER_PATTERN = Pattern.compile(
            """([0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?)\s*متر""",
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

        val root = rootInActiveWindow

        if (root != null) {
            collectText(root, text)
        }

        val fullText = text.toString()

        Log.d(TAG, "SNAPP_TEXT=$fullText")

        val distance = findDistance(fullText)

        if (distance == null) {
            return
        }

        Log.d(TAG, "DISTANCE=$distance")

        if (distance <= 2.0) {
            TripOverlay.showBlue()
            Log.d(TAG, "COLOR=BLUE")
        } else {
            TripOverlay.showBlack()
            Log.d(TAG, "COLOR=BLACK")
        }
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

    private fun findDistance(text: String): Double? {

        val normalized = normalizeDigits(text)

        Log.d(TAG, "NORMALIZED=$normalized")

        /*
         * مثال واقعی Snapp:
         *
         * ۴ دقیقه تا مبدا-۲کیلومتر
         *
         * فقط قسمت بعد از آخرین خط تیره بررسی می‌شود.
         */

        val distanceText =
            normalized.substringAfterLast("-", normalized)

        Log.d(TAG, "DISTANCE_TEXT=$distanceText")

        val kmMatcher = KM_PATTERN.matcher(distanceText)

        if (kmMatcher.find()) {

            val value = parseNumber(kmMatcher.group(1))

            if (value != null) {
                return value
            }
        }

        val meterMatcher = METER_PATTERN.matcher(distanceText)

        if (meterMatcher.find()) {

            val meters = parseNumber(meterMatcher.group(1))

            if (meters != null) {

                // طبق قانون شما:
                // ۵۰۰ متر و ۸۰۰ متر = ۱ کیلومتر

                return if (meters < 1000.0) {
                    1.0
                } else {
                    meters / 1000.0
                }
            }
        }

        return null
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
