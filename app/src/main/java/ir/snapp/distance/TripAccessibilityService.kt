package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val KM_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(?:کیلومتر|km|ک\\.م)",
            Pattern.CASE_INSENSITIVE
        )

        private val METER_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(?:متر|m)",
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

        val packageName = event.packageName?.toString() ?: return

        if (!packageName.contains("snapp", ignoreCase = true)) {
            return
        }

        val root = rootInActiveWindow ?: return

        val allText = StringBuilder()

        collectText(root, allText)

        val text = allText.toString()

        val distance = findDistance(text)

        if (distance != null) {
            Log.d(TAG, "TRIP_DISTANCE_KM=$distance")

            if (distance <= 2.0) {
                TripOverlay.showBlue()
            } else {
                TripOverlay.showBlack()
            }
        } else {
            /*
             * وقتی فاصله‌ای در کارت سفر پیدا نشد،
             * یعنی فعلاً سفری قابل تشخیص نیست.
             */
            TripOverlay.showWhite()
        }
    }

    private fun collectText(
        node: AccessibilityNodeInfo?,
        output: StringBuilder
    ) {
        if (node == null) return

        node.text?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(' ')
                output.append(it)
            }
        }

        node.contentDescription?.toString()?.let {
            if (it.isNotBlank()) {
                output.append(' ')
                output.append(it)
            }
        }

        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), output)
        }
    }

    private fun findDistance(text: String): Double? {

        val kmMatcher = KM_PATTERN.matcher(text)

        if (kmMatcher.find()) {
            val value = normalizeNumber(kmMatcher.group(1))

            if (value != null) {
                return value
            }
        }

        val meterMatcher = METER_PATTERN.matcher(text)

        if (meterMatcher.find()) {
            val meters = normalizeNumber(meterMatcher.group(1))

            if (meters != null) {
                /*
                 * طبق قانون برنامه:
                 * هر مقدار متر = حداقل ۱ کیلومتر
                 *
                 * 500m -> 1km
                 * 800m -> 1km
                 */
                return if (meters < 1000.0) {
                    1.0
                } else {
                    meters / 1000.0
                }
            }
        }

        return null
    }

    private fun normalizeNumber(value: String?): Double? {

        if (value == null) return null

        return try {
            value
                .replace(',', '.')
                .replace('٬', '.')
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
