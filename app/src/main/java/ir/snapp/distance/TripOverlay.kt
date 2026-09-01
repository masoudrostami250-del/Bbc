package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout

object TripOverlay {

    private var root: LinearLayout? = null
    private var windowManager: WindowManager? = null
    private var blue: View? = null
    private var black: View? = null

    fun show(service: AccessibilityService) {

        if (root != null) return

        windowManager =
            service.getSystemService(
                AccessibilityService.WINDOW_SERVICE
            ) as WindowManager

        val layout = LinearLayout(service)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        blue = View(service)
        blue!!.background = circle(Color.rgb(25, 118, 210))

        black = View(service)
        black!!.background = circle(Color.BLACK)

        val buttonParams =
            LinearLayout.LayoutParams(120, 120)

        layout.addView(blue, buttonParams)
        layout.addView(black, buttonParams)

        val params = WindowManager.LayoutParams(
            150,
            270,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 15
        params.y = 350

        try {
            windowManager!!.addView(layout, params)
            root = layout
            showBlue()
        } catch (_: Exception) {
        }
    }

    fun showBlue() {
        blue?.alpha = 1.0f
        black?.alpha = 0.25f
    }

    fun showBlack() {
        blue?.alpha = 0.25f
        black?.alpha = 1.0f
    }

    private fun circle(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
}
