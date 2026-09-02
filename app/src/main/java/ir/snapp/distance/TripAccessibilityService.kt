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
        private const val SNAPP = "snapp"

        private val DISTANCE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|متر|km|m)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private var snappForeground = false
    private var lastTripText = ""

    private val scanner = object : Runnable {
        override fun run() {
            if (snappForeground) {
                scanSnapp()
            }
            handler.postDelayed(this, 400)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        snappForeground = false
        lastTripText = ""

        handler.post(scanner)

        Log.d(TAG, "BBC_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()?.lowercase() ?: ""

        /*
         * فقط تغییر پنجره مشخص می‌کند که برنامه جلویی عوض شده.
         * تغییرات محتوایی برنامه‌های دیگر نباید وضعیت Snapp را خراب کنند.
         */
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            if (pkg.contains(SNAPP)) {
                if (!snappForeground) {
                    Log.d(TAG, "SNAPP_FOREGROUND")
                }
                snappForeground = true
            } else {
                if (snappForeground) {
                    Log.d(TAG, "SNAPP_LEFT")
                }

                snappForeground = false
                lastTripText = ""
                TripOverlay.showWhite()
            }
        }

        if (pkg.contains(SNAPP)) {
            snappForeground = true
            handler.removeCallbacks(scanner)
            handler.post(scanner)
        }
    }

    private fun scanSnapp() {

        var selected: String? = null

        try {
            /*
             * فقط پنجره‌های Snapp.
             * پنجره فعال اولویت مطلق دارد.
             */
            val active = windows
                .filter {
                    val root = it.root
                    root != null &&
                    root.packageName?.toString()?.contains(SNAPP, true) == true
                }
                .sortedByDescending { it.isActive }

            for (window in active) {
                val root = window.root ?: continue

                val candidate = findBestTripNode(root)

                if (candidate != null) {
                    selected = candidate
                    break
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "SCAN_ERROR=${e.message}")
        }

        /*
         * هیچ کارت معتبر فعلی پیدا نشده.
         * نتیجه قبلی پاک می‌شود تا سفر قبلی روی سفر جدید نماند.
         */
        if (selected == null) {
            if (lastTripText.isNotEmpty()) {
                Log.d(TAG, "TRIP_CLEARED")
            }

            lastTripText = ""
            TripOverlay.showWhite()
            return
        }

        val tripText = normalize(selected)

        if (tripText == lastTripText) {
            return
        }

        lastTripText = tripText

        Log.d(TAG, "NEW_TRIP=$tripText")

        val distance = findOriginDistance(tripText)

        if (distance == null) {
            Log.d(TAG, "NO_ORIGIN_DISTANCE")
            TripOverlay.showWhite()
            return
        }

        Log.d(TAG, "ORIGIN_DISTANCE=$distance")

        /*
         * قانون نهایی:
         * <= 2 km = BLUE
         * > 2 km = BLACK
         */
        if (distance <= 2.0) {
            TripOverlay.showBlue(distance)
            Log.d(TAG, "RESULT=BLUE")
        } else {
            TripOverlay.showBlack(distance)
            Log.d(TAG, "RESULT=BLACK")
        }
    }

    /*
     * از بین Nodeها فقط Nodeهایی بررسی می‌شوند که:
     *
     * 1. قابل مشاهده‌اند
     * 2. خودشان «تا مبدا» دارند
     * 3. فاصله بعد از «تا مبدا» دارند
     *
     * به جای candidates.last()، کوتاه‌ترین Node معتبر انتخاب می‌شود.
     * این معمولاً همان Label واقعی فاصله مبدأ است، نه Container بزرگ کارت.
     */
    private fun findBestTripNode(
        root: AccessibilityNodeInfo
    ): String? {

        val candidates = ArrayList<String>()

        collectCandidates(root, candidates)

        if (candidates.isEmpty()) {
            return null
        }

        return candidates
            .distinct()
            .minByOrNull { it.length }
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo?,
        candidates: MutableList<String>
    ) {
        if (node == null) return

        try {

            if (node.isVisibleToUser) {

                val ownText = buildString {
                    node.text?.toString()?.let {
                        if (it.isNotBlank()) {
                            append(it).append(" ")
                        }
                    }

                    node.contentDescription?.toString()?.let {
                        if (it.isNotBlank()) {
                            append(it)
                        }
                    }
                }

                val text = normalize(ownText)

                if (text.contains("تا مبدا")) {

                    val distance = findOriginDistance(text)

                    if (distance != null) {
                        candidates.add(text)

                        Log.d(
                            TAG,
                            "VALID_ORIGIN=$text DIST=$distance"
                        )
                    }
                }
            }

            for (i in 0 until node.childCount) {
                collectCandidates(node.getChild(i), candidates)
            }

        } catch (_: Exception) {
        }
    }

    /*
     * بسیار مهم:
     * جستجو فقط AFTER «تا مبدا» انجام می‌شود.
     *
     * بنابراین عددی که قبل از «تا مبدا» یا مربوط به بخش دیگری
     * از کارت باشد دیگر نمی‌تواند فاصله مبدأ محسوب شود.
     */
    private fun findOriginDistance(text: String): Double? {

        val originIndex = text.indexOf("تا مبدا")

        if (originIndex < 0) {
            return null
        }

        val start = originIndex + "تا مبدا".length
        val end = minOf(text.length, start + 100)

        if (start >= end) {
            return null
        }

        val area = text.substring(start, end)

        val matcher = DISTANCE.matcher(area)

        if (!matcher.find()) {
            return null
        }

        val value = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: return null

        val number = try {
            value.replace(',', '.').toDouble()
        } catch (_: Exception) {
            return null
        }

        return if (
            unit.contains("متر", true) ||
            unit.equals("m", true)
        ) {
            /*
             * هر فاصله کمتر از ۱ کیلومتر = ۱ کیلومتر
             */
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

            .replace('\u200c', ' ')
            .replace('\u200e', ' ')
            .replace('\u200f', ' ')

            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')

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
