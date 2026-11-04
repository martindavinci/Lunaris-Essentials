package tools.lunaris.texteditor.ui

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * While an accessibility service runs, Compose marks the holder of an AndroidView as not visible, so that
 * TalkBack does not stop on it. Services that read the tree from the top, like Select to Speak, then skip
 * everything inside it and find no text. Compose sets this as the holder attaches: hence the post.
 */
internal fun View.keepHolderVisibleToAccessibility() {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            view.post {
                val holder = view.parent as? View ?: return@post
                val fromCompose = ViewCompat.getAccessibilityDelegate(holder)
                ViewCompat.setAccessibilityDelegate(holder, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        if (fromCompose != null) {
                            fromCompose.onInitializeAccessibilityNodeInfo(host, info)
                        } else {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                        }
                        info.isVisibleToUser = true
                    }
                })
            }
        }

        override fun onViewDetachedFromWindow(view: View) = Unit
    })
}
