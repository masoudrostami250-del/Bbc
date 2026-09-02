package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import java.util.regex.Pattern

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"

        private val DISTANCE = Pattern.compile(
            """([0-9]+(?:[.,][0-9]+)?)\s*(کیلومتر|متر)"""
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private var lastTripSignature = ""
    private var hasTrip = false

    private val scanner = object : Runnable {
        override fun run() {
            scanCurrentTrip()
            handler.postDelayed(this, 350)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)
        TripOverlay.showWhite()

        lastTripSignature = ""
        hasTrip = false

        handler.post(scanner)

        Log.d(TAG, "BBC_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handler.removeCallbacks(scanner)
        handler.post(scanner)
    }

    private data class Candidate(
        val text: String,
        val distance: Double,
        val area: Int,
        val top: Int,
        val left: Int
    )

    private fun scanCurrentTrip() {

        val candidates = ArrayList<Candidate>()

        try {
            for (window in windows) {
                val root = window.root ?: continue

                val pkg = root.packageName?.toString() ?: ""
                if (!pkg.contains("snapp", true)) continue

                collectCandidates(root, candidates)
            }
        } catch (e: Exception) {
            Log.d(TAG, "WINDOW_ERROR=${e.message}")
        }

        if (candidates.isEmpty()) {
            if (hasTrip) {
                Log.d(TAG, "TRIP_CLEARED")
            }

            hasTrip = false
            lastTripSignature = ""
            TripOverlay.showWhite()
            return
        }

        // فقط کاندیدهای قابل مشاهده
        val unique = candidates
            .distinctBy { "${it.text}|${it.distance}|${it.top}|${it.left}" }
            .sortedWith(
                compareByDescending<Candidate> { it.area }
                    .thenBy { it.top }
                    .thenBy { it.left }
            )

        /*
         * اولویت:
         * 1) کارت قابل مشاهده
         * 2) کارتی که متن "تا مبدا" و فاصله را با هم دارد
         * 3) بزرگ‌ترین ناحیه کارت
         *
         * این قسمت جلوی انتخاب تصادفی candidates.last() را می‌گیرد.
         */
        val selected = unique.first()

        val signature = "${selected.text}|${selected.distance}"

        if (signature == lastTripSignature && hasTrip) {
            return
        }

        lastTripSignature = signature
        hasTrip = true

        Log.d(TAG, "SELECTED_TRIP=${selected.text}")
        Log.d(TAG, "SELECTED_DISTANCE=${selected.distance}")

        /*
         * قانون نهایی:
         * <= 2 کیلومتر  آبی
         * > 2 کیلومتر   مشکی
         */
        if (selected.distance <= 2.0) {
            TripOverlay.showBlue(selected.distance)
            Log.d(TAG, "RESULT=BLUE")
        } else {
            TripOverlay.showBlack(selected.distance)
            Log.d(TAG, "RESULT=BLACK")
        }
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo?,
        out: MutableList<Candidate>
    ) {
        if (node == null) return

        try {
            if (!node.isVisibleToUser) {
                return
            }

            val own = buildString {
                node.text?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }

                node.contentDescription?.toString()?.let {
                    if (it.isNotBlank()) append(it).append(" ")
                }
            }

            val normalized = normalize(own)

            if (normalized.contains("تا مبدا")) {
                val distance = findOriginDistance(normalized)

                if (distance != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)

                    val width = maxOf(1, rect.width())
                    val height = maxOf(1, rect.height())
                    val area = width * height

                    out.add(
                        Candidate(
                            text = normalized,
                            distance = distance,
                            area = area,
                            top = rect.top,
                            left = rect.left
                        )
                    )

                    Log.d(
                        TAG,
                        "CANDIDATE=$normalized DIST=$distance AREA=$area"
                    )
                }
            }

            for (i in 0 until node.childCount) {
                collectCandidates(node.getChild(i), out)
            }

        } catch (_: Exception) {
        }
    }

    private fun findOriginDistance(text: String): Double? {

        val originIndex = text.indexOf("تا مبدا")
        if (originIndex < 0) return null

        /*
         * فقط اطراف عبارت «تا مبدا» بررسی می‌شود.
         * بنابراین فاصله مقصد یا اطلاعات قدیمی وارد محاسبه نمی‌شود.
         */
        val start = maxOf(0, originIndex - 35)
        val end = minOf(text.length, originIndex + 80)

        val area = text.substring(start, end)

        Log.d(TAG, "ORIGIN_AREA=$area")

        val matcher = DISTANCE.matcher(area)

        var bestDistance: Double? = null
        var bestDistanceFromOrigin = Int.MAX_VALUE

        while (matcher.find()) {

            val raw = matcher.group(1) ?: continue
            val unit = matcher.group(2) ?: continue

            val value = try {
                raw.replace(',', '.').toDouble()
            } catch (_: Exception) {
                continue
            }

            val distance = if (unit == "متر") {
                /*
                 * هر فاصله کمتر از ۱ کیلومتر = ۱ کیلومتر
                 */
                if (value < 1000.0) {
                    1.0
                } else {
                    value / 1000.0
                }
            } else {
                value
            }

            val position = start + matcher.start()

            val distanceFromOrigin =
                abs(position - originIndex)

            if (distanceFromOrigin < bestDistanceFromOrigin) {
                bestDistanceFromOrigin = distanceFromOrigin
                bestDistance = distance

                Log.d(
                    TAG,
                    "ORIGIN_DISTANCE=$value $unit => $distance"
                )
            }
        }

        Log.d(TAG, "FINAL_DISTANCE=$bestDistance")

        return bestDistance
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
