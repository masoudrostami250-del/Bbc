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

    private var lastTripText = ""
    private var lastDistance: Double? = null
    private var hasTrip = false

    private val scanner = object : Runnable {
        override fun run() {
            scanCurrentTrip()
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        lastTripText = ""
        lastDistance = null
        hasTrip = false

        handler.post(scanner)

        Log.d(TAG, "BBC_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        /*
         * هر تغییر واقعی در Snapp باعث بررسی مجدد می‌شود.
         * نتیجه سفر قبلی نگه داشته نمی‌شود.
         */
        handler.removeCallbacks(scanner)
        handler.post(scanner)
    }

    private fun scanCurrentTrip() {

        var bestText: String? = null

        try {
            /*
             * اول source خود Event/پنجره فعال بررسی می‌شود.
             * این کمک می‌کند سفر جدید جای سفر قبلی را بگیرد.
             */
            rootInActiveWindow?.let { root ->
                val text = findTripCard(root)

                if (text != null) {
                    bestText = text
                }
            }
        } catch (_: Exception) {
        }

        /*
         * اگر پنجره فعال جواب نداد، پنجره‌های Snapp بررسی می‌شوند.
         */
        if (bestText == null) {
            try {
                for (window in windows) {

                    val root = window.root ?: continue

                    val pkg = root.packageName?.toString() ?: ""

                    if (!pkg.contains("snapp", true)) {
                        continue
                    }

                    val text = findTripCard(root)

                    if (text != null) {
                        bestText = text
                        break
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "WINDOW_ERROR=${e.message}")
            }
        }

        /*
         * اگر سفر فعلی پیدا نشد، نتیجه سفر قبلی نباید باقی بماند.
         */
        if (bestText == null) {

            if (hasTrip) {
                Log.d(TAG, "TRIP_CLEARED")
            }

            hasTrip = false
            lastTripText = ""
            lastDistance = null

            TripOverlay.showWhite()
            return
        }

        val currentText = normalize(bestText!!)

        /*
         * اگر همان کارت قبلی است، دوباره نتیجه‌سازی نکن.
         */
        if (currentText == lastTripText && hasTrip) {
            return
        }

        /*
         * سفر جدید یا کارت تغییر کرده.
         */
        lastTripText = currentText
        hasTrip = true

        Log.d(TAG, "NEW_TRIP=$currentText")

        val distance = findDistance(currentText)

        if (distance == null) {

            lastDistance = null

            Log.d(TAG, "NO_DISTANCE")

            TripOverlay.showWhite()
            return
        }

        lastDistance = distance

        Log.d(TAG, "NEW_TRIP_DISTANCE=$distance")

        if (distance <= 2.0) {

            TripOverlay.showBlue(distance)

            Log.d(TAG, "NEW_TRIP_RESULT=BLUE")

        } else {

            TripOverlay.showBlack(distance)

            Log.d(TAG, "NEW_TRIP_RESULT=BLACK")
        }
    }

    /*
     * کارت سفر را از بین Nodeها پیدا می‌کند.
     * دیگر کل درخت را یکجا به‌عنوان یک سفر در نظر نمی‌گیریم.
     */
    private fun findTripCard(
        root: AccessibilityNodeInfo
    ): String? {

        val candidates = ArrayList<String>()

        collectTripCandidates(root, candidates)

        if (candidates.isEmpty()) {
            return null
        }

        /*
         * آخرین کاندیدای معتبر معمولاً کارت فعلی‌ای است
         * که Snapp در UI ساخته/به‌روزرسانی کرده.
         */
        return candidates.last()
    }

    private fun collectTripCandidates(
        node: AccessibilityNodeInfo?,
        candidates: MutableList<String>
    ) {

        if (node == null) return

        try {

            val ownText = buildString {

                node.text?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }

                node.contentDescription?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }
            }

            val normalizedOwn = normalize(ownText)

            /*
             * مهم:
             * فقط Nodeهایی که خودشان مربوط به «تا مبدا» هستند
             * کاندیدای سفر می‌شوند.
             */
            if (normalizedOwn.contains("تا مبدا")) {

                val distance = findDistance(normalizedOwn)

                if (distance != null) {
                    candidates.add(normalizedOwn)

                    Log.d(
                        TAG,
                        "TRIP_CANDIDATE=$normalizedOwn"
                    )
                }
            }

            for (i in 0 until node.childCount) {
                collectTripCandidates(
                    node.getChild(i),
                    candidates
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun findDistance(text: String): Double? {

        val originIndex = text.indexOf("تا مبدا")

        if (originIndex < 0) return null

        /*
         * فقط محدوده نزدیک «تا مبدا» را بررسی می‌کنیم
         * تا فاصله سفر دیگری وارد محاسبه نشود.
         */
        val start = maxOf(0, originIndex - 20)
        val end = minOf(text.length, originIndex + 100)

        val area = text.substring(start, end)

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

            .replace('–', '-')
            .replace('—', '-')

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
