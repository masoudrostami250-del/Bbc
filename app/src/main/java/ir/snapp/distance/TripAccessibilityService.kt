package ir.snapp.distance

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BBC_TRIP"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        TripOverlay.show(this)

        Log.d(TAG, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (!packageName.contains("snapp", ignoreCase = true)) {
            return
        }

        val root = rootInActiveWindow ?: return

        Log.d(TAG, "===== SNAPP EVENT =====")
        Log.d(TAG, "event=${event.eventType}")

        dumpNode(root, 0)

        Log.d(TAG, "===== END EVENT =====")
    }

    private fun dumpNode(
        node: AccessibilityNodeInfo?,
        depth: Int
    ) {
        if (node == null || depth > 15) return

        val text = node.text?.toString()?.trim()
        val description = node.contentDescription?.toString()?.trim()
        val className = node.className?.toString()

        if (!text.isNullOrEmpty() ||
            !description.isNullOrEmpty()
        ) {
            Log.d(
                TAG,
                "depth=$depth class=$className text=$text desc=$description"
            )
        }

        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), depth + 1)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICE_INTERRUPTED")
    }
}
