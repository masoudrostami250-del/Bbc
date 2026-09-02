package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val ORIGIN_DISTANCE = Pattern.compile(
            """تا\s*مبدا.{0,80}?(?:[-–—:]\s*)?([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )

        private val ORIGIN_DISTANCE_REVERSE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m).{0,80}?تا\s*مبدا""",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private val scanner = object : Runnable {
        override fun run() {
            try {
                scanAllWindows()
            } catch (e: Exception) {
                Log.d(TAG, "SCAN_ERROR=${e.message}")
            }

            handler.postDelayed(this, 300)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        Log.d(TAG, "BBC_CONNECTED")

        handler.removeCallbacks(scanner)
        handler.post(scanner)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        try {
            val text = StringBuilder()
            collect(event.source, text)
            processText(text.toString())
        } catch (_: Exception) {
        }

        scanAllWindows()
    }

    private fun scanAllWindows() {

        var foundSnappWindow = false

        try {
            for (window in windows) {

                val root = window.root ?: continue

                val pkg = root.packageName?.toString() ?: ""

                if (!pkg.contains("snapp", ignoreCase = true)) {
                    continue
                }

                foundSnappWindow = true

                val text = StringBuilder()
                collect(root, text)

                processText(text.toString())
            }
        } catch (e: Exception) {
            Log.d(TAG, "WINDOW_ERROR=${e.message}")
        }

        // بعضی نسخه‌های Snapp ممکن است window package را درست ندهند
        if (!foundSnappWindow) {
            try {
                rootInActiveWindow?.let { root ->

                    val text = StringBuilder()
                    collect(root, text)

                    processText(text.toString())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun processText(raw: String) {

        if (raw.isBlank()) return

        val text = normalize(raw)

        if (!text.contains("مبدا")) return

        Log.d(TAG, "ORIGIN_TEXT=$text")

        val distance =
            findOriginDistance(text) ?: return

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

        var matcher = ORIGIN_DISTANCE.matcher(text)

        if (matcher.find()) {
            return convertDistance(
                matcher.group(1),
                matcher.group(2)
            )
        }

        matcher = ORIGIN_DISTANCE_REVERSE.matcher(text)

        if (matcher.find()) {
            return convertDistance(
                matcher.group(1),
                matcher.group(2)
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

        return if (
            unit.contains("متر") ||
            unit.equals("m", ignoreCase = true)
        ) {

            if (number < 1000.0) {
                1.0
            } else {
                number / 1000.0
            }

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
                    output.append(it)
                    output.append(" ")
                }
            }

            node.contentDescription?.toString()?.let {
                if (it.isNotBlank()) {
                    output.append(it)
                    output.append(" ")
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

            .replace('\u200c', ' ')
            .replace('\u200f', ' ')
            .replace('\u200e', ' ')

            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')

            .replace('–', '-')
            .replace('—', '-')

            .replace('\n', ' ')
            .replace('\r', ' ')

            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onInterrupt() {
        handler.removeCallbacks(scanner)
        Log.d(TAG, "BBC_INTERRUPTED")
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanner)
        super.onDestroy()
    }
}
