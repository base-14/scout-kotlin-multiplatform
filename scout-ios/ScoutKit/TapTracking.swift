import UIKit
import ObjectiveC.runtime
import Scout

/// Automatic tap tracking via a one-time `UIWindow.sendEvent` swizzle. Each touch-up emits a
/// `user_interaction` with a best-effort target label and the touch coordinates.
///
/// Target resolution is layered so it works for both UIKit and SwiftUI apps:
///   1. `touch.view`, falling back to a `hitTest` at the touch point (SwiftUI consumes the touch
///      via gesture recognizers, so `touch.view` is usually nil there).
///   2. The view + its ancestors' accessibility label / identifier / button title (UIKit).
///   3. The accessibility element at the touch point (SwiftUI renders into one hosting view with
///      no per-widget UIViews, but still exposes labels through the accessibility tree).
enum TapTracking {
    private static var installed = false

    static func install() {
        guard !installed else { return }
        installed = true
        guard
            let original = class_getInstanceMethod(UIWindow.self, #selector(UIWindow.sendEvent(_:))),
            let replacement = class_getInstanceMethod(UIWindow.self, #selector(UIWindow.scout_sendEvent(_:)))
        else { return }
        method_exchangeImplementations(original, replacement)
    }

    /// Best-effort label from a view and up to a few of its ancestors.
    static func label(for view: UIView?) -> String {
        var current = view
        var depth = 0
        while let v = current, depth < 6 {
            if let label = v.accessibilityLabel, !label.isEmpty { return label }
            if let id = v.accessibilityIdentifier, !id.isEmpty { return id }
            if let button = v as? UIButton, let title = button.title(for: .normal), !title.isEmpty { return title }
            if let text = (v as? UILabel)?.text, !text.isEmpty { return text }
            current = v.superview
            depth += 1
        }
        if let view = view { return cleanClassName(view) }
        return "unknown"
    }

    /// Compose Multiplatform / SwiftUI host views come through as mangled Kotlin/Native or private
    /// SwiftUI class names (e.g. `ConfettiKitandroidx.compose.ui.window.UserInputView10`). Reduce
    /// them to a short, stable, groupable name (`UserInputView`) so they're usable in RUM queries.
    static func cleanClassName(_ obj: AnyObject) -> String {
        let raw = String(describing: type(of: obj))
        // Last dot-separated component drops the module + package prefix.
        var name = raw.split(separator: ".").last.map(String.init) ?? raw
        // Drop Kotlin/Native trailing disambiguation digits.
        while let last = name.last, last.isNumber { name.removeLast() }
        // Known canvas-based UI hosts: label them by framework, since every tap hits the same view.
        if name.contains("UserInputView") || name.contains("SkikoUIView") || name.contains("ComposeSceneMediator") {
            return "ComposeCanvas"
        }
        if name.contains("UIHostingView") || name.contains("CGDrawingView") || name.hasPrefix("_UIGraphicsView") {
            return "SwiftUICanvas"
        }
        return name.isEmpty ? raw : name
    }

    /// Deepest accessibility element whose frame contains the tap point. Recovers semantic labels
    /// (button text, list-row title) in SwiftUI, where there is no per-widget UIView to inspect.
    static func accessibilityLabel(at point: CGPoint, in window: UIWindow) -> String? {
        let screenPoint = window.convert(point, to: nil)
        var found: String? = nil

        func consider(_ object: NSObject) {
            if let label = object.accessibilityLabel, !label.isEmpty,
               object.accessibilityFrame.contains(screenPoint) {
                found = label // depth-first: deepest match wins
            }
            let count = object.accessibilityElementCount()
            guard count != NSNotFound, count > 0 else { return }
            for i in 0..<count {
                if let child = object.accessibilityElement(at: i) as? NSObject { consider(child) }
            }
        }

        func walk(_ view: UIView) {
            consider(view)
            for sub in view.subviews { walk(sub) }
        }

        walk(window)
        return found
    }
}

extension UIWindow {
    @objc func scout_sendEvent(_ event: UIEvent) {
        self.scout_sendEvent(event) // original (swapped)
        guard event.type == .touches, let touches = event.allTouches else { return }
        for touch in touches where touch.phase == .ended {
            let location = touch.location(in: self)
            // touch.view is typically nil in SwiftUI (gesture-consumed) — fall back to a hit-test.
            let view = touch.view ?? self.hitTest(location, with: event)
            let type = view.map { TapTracking.cleanClassName($0) } ?? "unknown"

            // Prefer a semantic accessibility label (works for UIKit, and for Compose/SwiftUI when
            // accessibility is active); otherwise fall back to the cleaned host-view name.
            var target = TapTracking.accessibilityLabel(at: location, in: self)
                ?? TapTracking.label(for: view)

            ScoutEngine.shared.reportTap(
                target: target,
                targetType: type,
                x: Double(location.x),
                y: Double(location.y)
            )
        }
    }
}
