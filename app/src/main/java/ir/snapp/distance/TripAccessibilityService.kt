package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class TripAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        // دکمه فقط یک بار ساخته می‌شود
        // و هنگام تغییر صفحه Snapp حذف نمی‌شود.
        TripOverlay.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        // فعلاً هیچ تحلیل سفری انجام نمی‌شود.
        // هدف این نسخه فقط تست ماندگاری Overlay است.

        if (event?.packageName?.toString()
                ?.contains("snapp", ignoreCase = true) == true
        ) {
            TripOverlay.show(this)
        }
    }

    override fun onInterrupt() {
    }
}
