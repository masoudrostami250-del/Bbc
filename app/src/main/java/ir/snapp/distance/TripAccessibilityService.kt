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

        private val DISTANCE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|km|ک\.?\s*م|متر|m)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private val scanner = object : Runnable {
        override fun run() {
            scanSnapp()
            handler.postDelayed(this, 250)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        handler.removeCallbacks(scanner)
        handler.post(scanner)

        Log.d(TAG, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // اسکن مداوم انجام می‌شود.
        // اینجا عمداً چیزی را ریست نمی‌کنیم.
    }

    private fun scanSnapp() {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        if (root == null) return

        val packageName =
            root.packageName?.toString() ?: ""

        if (!packageName.contains("snapp", true)) {
            return
        }

        val text = StringBuilder()

        collect(root, text)

        val normalized = normalize(text.toString())

        if (!normalized.contains("مبدا")) {
            return
        }

        val distance = findOriginDistance(normalized)

        if (distance == null) {
            return
        }

        Log.d(TAG, "ORIGIN=$distance")

        if (distance <= 2.0) {
            TripOverlay.showBlue(distance)
            Log.d(TAG, "BLUE $distance")
        } else {
            TripOverlay.showBlack(distance)
            Log.d(TAG, "BLACK $distance")
        }
    }

    private fun findOriginDistance(text: String): Double? {

        val origin = text.indexOf("مبدا")

        if (origin < 0) return null

        // فاصله‌ای که بلافاصله در محدوده متن مبدا آمده
        val end = minOf(text.length, origin + 120)

        val area = text.substring(origin, end)

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
        handler.removeCallbacks(scanner)
        Log.d(TAG, "SERVICE_INTERRUPTED")
    }
}
