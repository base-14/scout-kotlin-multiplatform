import UIKit
import ObjectiveC.runtime
import Scout

/// Automatic screen tracking via a one-time `UIViewController.viewDidAppear` swizzle.
/// Each non-container view controller that appears emits a `screen_view` through the engine.
enum ScreenTracking {
    private static var installed = false

    static func install() {
        guard !installed else { return }
        installed = true
        guard
            let original = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.viewDidAppear(_:))),
            let replacement = class_getInstanceMethod(UIViewController.self, #selector(UIViewController.scout_viewDidAppear(_:)))
        else { return }
        method_exchangeImplementations(original, replacement)
    }
}

extension UIViewController {
    @objc func scout_viewDidAppear(_ animated: Bool) {
        // Calls the original implementation (the methods were exchanged).
        self.scout_viewDidAppear(animated)

        // Skip container controllers — they wrap real screens and would be noise.
        if self is UINavigationController || self is UITabBarController || self is UISplitViewController || self is UIPageViewController {
            return
        }
        let name = String(describing: type(of: self))
        ScoutEngine.shared.setScreen(name: name)
    }
}
