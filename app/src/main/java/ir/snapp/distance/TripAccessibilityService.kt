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
        private const val SNAPP_PACKAGE = "snapp"

        private val DISTANCE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|متر)"""
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private var snappActive = false
    private var lastSignature = ""

    private val scanner = object : Runnable {
        override fun run() {
            if (snappActive) {
                scanSnapp()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        snappActive = false
        lastSignature = ""

        handler.post(scanner)

        Log.d(TAG, "BBC_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""

        if (!pkg.contains(SNAPP_PACKAGE, ignoreCase = true)) {
            if (snappActive) {
                snappActive = false
                lastSignature = ""
                TripOverlay.showWhite()
                Log.d(TAG, "LEFT_SNAPP -> WHITE")
            }
            return
        }

        if (!snappActive) {
            Log.d(TAG, "ENTERED_SNAPP")
        }

        snappActive = true

        // بلافاصله بعد از رویداد اسنپ اسکن کن
        handler.removeCallbacks(scanner)
        scanSnapp()
        handler.postDelayed(scanner, 500)
    }

    private fun scanSnapp() {

        if (!snappActive) {
            TripOverlay.showWhite()
            return
        }

        var best: ParsedTrip? = null

        try {
            val root = rootInActiveWindow

            if (root != null) {
                best = findTrip(root)
            }
        } catch (e: Exception) {
            Log.d(TAG, "SCAN_ERROR=${e.message}")
        }

        if (best == null) {
            // اینجا سفید نمی‌کنیم!
            // ممکن است tree موقتاً خالی باشد.
            return
        }

        val signature = "${best.text}|${best.distance}"

        if (signature == lastSignature) {
            return
        }

        lastSignature = signature

        Log.d(TAG, "TRIP=${best.text}")
        Log.d(TAG, "DISTANCE=${best.distance}")

        if (best.distance <= 2.0) {
            TripOverlay.showBlue(best.distance)
            Log.d(TAG, "RESULT=BLUE")
        } else {
            TripOverlay.showBlack(best.distance)
            Log.d(TAG, "RESULT=BLACK")
        }
    }

    private data class ParsedTrip(
        val text: String,
        val distance: Double
    )

    private fun findTrip(root: AccessibilityNodeInfo): ParsedTrip? {

        val candidates = ArrayList<ParsedTrip>()

        collect(root, candidates)

        if (candidates.isEmpty()) {
            return null
        }

        // نزدیک‌ترین نتیجه به ابتدای درخت را به عنوان کارت فعلی می‌گیریم
        return candidates.first()
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        output: MutableList<ParsedTrip>
    ) {
        if (node == null) return

        try {

            val text = buildString {
                node.text?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }

                node.contentDescription?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }
            }

            val normalized = normalize(text)

            if (normalized.contains("تا مبدا")) {

                val originDistance = findOriginDistance(normalized)

                if (originDistance != null) {
                    output.add(
                        ParsedTrip(
                            text = normalized,
                            distance = originDistance
                        )
                    )

                    Log.d(
                        TAG,
                        "CANDIDATE=$normalized DIST=$originDistance"
                    )
                }
            }

            for (i in 0 until node.childCount) {
                collect(node.getChild(i), output)
            }

        } catch (_: Exception) {
        }
    }

    private fun findOriginDistance(text: String): Double? {

        val index = text.indexOf("تا مبدا")

        if (index < 0) return null

        // فقط بعد از «تا مبدا»
        val after = text.substring(
            index + "تا مبدا".length
        )

        val matcher = DISTANCE.matcher(after)

        if (!matcher.find()) {
            return null
        }

        val raw = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: return null

        val value = try {
            raw.replace(',', '.').toDouble()
        } catch (_: Exception) {
            return null
        }

        return if (unit == "متر") {
            if (value < 1000.0) 1.0 else value / 1000.0
        } else {
            value
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
            .replace('\u200e', ' ')
            .replace('\u200f', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onInterrupt() {
        handler.removeCallbacks(scanner)
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanner)
        super.onDestroy()
    }
}
