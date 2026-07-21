package io.base14.scout.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectContainsPoint
import platform.Foundation.NSNotFound
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UILabel
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowDidBecomeKeyNotification
import platform.UIKit.accessibilityElementAtIndex
import platform.UIKit.accessibilityElementCount
import platform.UIKit.accessibilityFrame
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

/**
 * Automatic tap tracking living inside the Kotlin engine, so KMP apps get `user_interaction`
 * spans (and tap breadcrumbs) from the single common `Scout.initialize` call. A non-consuming
 * `UITapGestureRecognizer` is attached to every key window; each tap resolves a best-effort
 * target label the same way ScoutKit's Swift `TapTracking` did:
 *   1. hit-test view + ancestor accessibility label / identifier / button title,
 *   2. deepest accessibility element containing the tap point (SwiftUI / Compose canvas),
 *   3. cleaned host-view class name (`ComposeCanvas`, `SwiftUICanvas`, ...).
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosTapTracking {
    private var installed = false
    private val hooked = HashSet<Long>()
    private val delegate = SimultaneousDelegate()
    private val target = TapTarget()

    fun install() {
        if (installed) return
        installed = true
        // No eager window scan: install() can run before UIApplicationMain (sharedApplication
        // not ready). Key-window notifications cover every window from first launch onward.
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
            (UIApplication.sharedApplication as UIApplication?)?.windows?.forEach { (it as? UIWindow)?.let(::hook) }
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIWindowDidBecomeKeyNotification, null, NSOperationQueue.mainQueue,
        ) { note ->
            (note?.`object` as? UIWindow)?.let(::hook)
        }
    }

    private fun hook(window: UIWindow) {
        val id = window.hash().toLong()
        if (!hooked.add(id)) return
        val recognizer = UITapGestureRecognizer(target, NSSelectorFromString("handleScoutTap:"))
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = delegate
        window.addGestureRecognizer(recognizer)
    }

    private class SimultaneousDelegate : NSObject(), UIGestureRecognizerDelegateProtocol {
        override fun gestureRecognizer(
            gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
        ): Boolean = true
    }

    internal class TapTarget : NSObject() {
        @kotlinx.cinterop.ObjCAction
        fun handleScoutTap(recognizer: UITapGestureRecognizer) {
            val window = recognizer.view as? UIWindow ?: return
            val location = recognizer.locationInView(window)
            val (x, y) = location.useContents { x to y }
            val view = window.hitTest(location, null)
            val type = view?.let(::cleanClassName) ?: "unknown"
            val label = accessibilityLabelAt(x, y, window) ?: labelFor(view)
            ScoutEngine.reportTap(label, type, x, y)
        }
    }

    private fun labelFor(view: UIView?): String {
        var current: UIView? = view
        var depth = 0
        while (depth < 6) {
            val v = current ?: break
            v.accessibilityLabel()?.takeIf { it.isNotEmpty() }?.let { return it }
            (v as? platform.UIKit.UIAccessibilityIdentificationProtocol)?.accessibilityIdentifier?.takeIf { it.isNotEmpty() }?.let { return it }
            (v as? UIButton)?.titleForState(UIControlStateNormal)?.takeIf { it.isNotEmpty() }?.let { return it }
            (v as? UILabel)?.text?.takeIf { it.isNotEmpty() }?.let { return it }
            current = v.superview
            depth++
        }
        return view?.let(::cleanClassName) ?: "unknown"
    }

    private fun cleanClassName(obj: NSObject): String {
        val raw = obj.`class`()?.let { NSStringFromClass(it) } ?: "unknown"
        // Swift classes arrive from NSStringFromClass mangled, e.g.
        // `_TtCC7SwiftUI17HostingScrollView22PlatformGroupContainer`. Demangle to the
        // readable last component (`PlatformGroupContainer`); for a normal ObjC/K-N name
        // just drop any module/package prefix.
        var name = demangleSwiftClass(raw) ?: raw.substringAfterLast('.')
        name = name.trimEnd { it.isDigit() }
        return when {
            name.contains("UserInputView") || name.contains("SkikoUIView") ||
                name.contains("ComposeSceneMediator") -> "ComposeCanvas"
            name.contains("UIHostingView") || name.contains("CGDrawingView") ||
                name.startsWith("_UIGraphicsView") -> "SwiftUICanvas"
            name.isEmpty() -> raw
            else -> name
        }
    }

    /**
     * Minimal Swift class-name demangler. Swift mangles `_Tt` + type-kind markers
     * (`C` class / `V` struct / `O` enum, one per nesting level) + length-prefixed
     * identifiers, e.g. `_TtCC7SwiftUI17HostingScrollView22PlatformGroupContainer`.
     * Returns the last identifier (`PlatformGroupContainer`), or null if not mangled.
     */
    private fun demangleSwiftClass(raw: String): String? {
        if (!raw.startsWith("_Tt")) return null
        var i = 3
        while (i < raw.length && !raw[i].isDigit()) i++ // skip C/V/O nesting markers
        var last: String? = null
        while (i < raw.length && raw[i].isDigit()) {
            var len = 0
            while (i < raw.length && raw[i].isDigit()) {
                len = len * 10 + (raw[i] - '0')
                i++
            }
            if (len <= 0 || i + len > raw.length) break
            last = raw.substring(i, i + len)
            i += len
        }
        return last
    }

    private fun accessibilityLabelAt(x: Double, y: Double, window: UIWindow): String? {
        val screenPoint = window.convertPoint(CGPointMake(x, y), toWindow = null)
        var found: String? = null

        fun consider(obj: NSObject) {
            val label = obj.accessibilityLabel()
            if (!label.isNullOrEmpty()) {
                val contains = screenPoint.useContents {
                    CGRectContainsPoint(obj.accessibilityFrame(), CGPointMake(this.x, this.y))
                }
                if (contains) found = label
            }
            val count = obj.accessibilityElementCount()
            if (count == NSNotFound.toLong() || count <= 0L) return
            for (i in 0 until count) {
                (obj.accessibilityElementAtIndex(i) as? NSObject)?.let(::consider)
            }
        }

        fun walk(view: UIView) {
            consider(view)
            view.subviews.forEach { (it as? UIView)?.let(::walk) }
        }

        walk(window)
        return found
    }
}
