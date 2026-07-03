import UIKit
import ObjectiveC.runtime
import Scout

/// Automatic tap tracking via a one-time `UIWindow.sendEvent` swizzle. Each touch-up emits a
/// `user_interaction` with a best-effort target label (accessibility label/identifier, button
/// title, else the view class) and the touch coordinates.
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

    static func label(for view: UIView?) -> String {
        guard let view = view else { return "unknown" }
        if let label = view.accessibilityLabel, !label.isEmpty { return label }
        if let id = view.accessibilityIdentifier, !id.isEmpty { return id }
        if let button = view as? UIButton, let title = button.title(for: .normal), !title.isEmpty { return title }
        return String(describing: type(of: view))
    }
}

extension UIWindow {
    @objc func scout_sendEvent(_ event: UIEvent) {
        self.scout_sendEvent(event) // original (swapped)
        guard event.type == .touches, let touches = event.allTouches else { return }
        for touch in touches where touch.phase == .ended {
            let location = touch.location(in: self)
            let view = touch.view
            let type = view.map { String(describing: Swift.type(of: $0)) } ?? "unknown"
            ScoutEngine.shared.reportTap(
                target: TapTracking.label(for: view),
                targetType: type,
                x: Double(location.x),
                y: Double(location.y)
            )
        }
    }
}
