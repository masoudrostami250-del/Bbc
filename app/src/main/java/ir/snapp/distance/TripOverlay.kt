package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

object TripOverlay {

    private var root: LinearLayout? = null
    private var windowManager: WindowManager? = null

    private var status: TextView? = null
    private var blue: TextView? = null
    private var black: TextView? = null

    fun show(service: AccessibilityService) {
        if (root != null) return

        windowManager =
            service.getSystemService(
                AccessibilityService.WINDOW_SERVICE
            ) as WindowManager

        val layout = LinearLayout(service)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(5, 5, 5, 5)

        status = button(service, "—")
        blue = button(service, "—")
        black = button(service, "—")

        val topParams = LinearLayout.LayoutParams(110, 110)
        topParams.bottomMargin = 12

        val bottomParams = LinearLayout.LayoutParams(110, 110)
        bottomParams.bottomMargin = 12

        layout.addView(status, topParams)
        layout.addView(blue, bottomParams)
        layout.addView(black, bottomParams)

        val params = WindowManager.LayoutParams(
            125,
            380,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 15
        params.y = 300

        try {
            windowManager!!.addView(layout, params)
            root = layout
            showWhite()
        } catch (_: Exception) {
        }
    }

    private fun button(
        service: AccessibilityService,
        text: String
    ): TextView {
        return TextView(service).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            background = circle(Color.WHITE)
        }
    }

    fun showWhite() {
        status?.text = "—"
        blue?.text = "—"
        black?.text = "—"

        status?.background = circle(Color.WHITE)
        blue?.background = circle(Color.WHITE)
        black?.background = circle(Color.WHITE)

        status?.setTextColor(Color.DKGRAY)
        blue?.setTextColor(Color.DKGRAY)
        black?.setTextColor(Color.DKGRAY)
    }

    fun showBlue(distance: Double) {
        status?.text = "✓"

        blue?.text = formatDistance(distance)
        black?.text = "—"

        status?.background = circle(Color.WHITE)
        blue?.background = circle(Color.rgb(25, 118, 210))
        black?.background = circle(Color.WHITE)

        status?.setTextColor(Color.DKGRAY)
        blue?.setTextColor(Color.WHITE)
        black?.setTextColor(Color.DKGRAY)
    }

    fun showBlack(distance: Double) {
        status?.text = "✓"

        blue?.text = "—"
        black?.text = formatDistance(distance)

        status?.background = circle(Color.WHITE)
        blue?.background = circle(Color.WHITE)
        black?.background = circle(Color.BLACK)

        status?.setTextColor(Color.DKGRAY)
        blue?.setTextColor(Color.DKGRAY)
        black?.setTextColor(Color.WHITE)
    }

    private fun formatDistance(distance: Double): String {
        if (distance < 1.0) return "1 km"

        return if (distance == distance.toLong().toDouble()) {
            "${distance.toLong()} km"
        } else {
            String.format("%.1f km", distance)
        }
    }

    private fun circle(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.GRAY)
        }
    }
}
