import UIKit
import ObjectiveC.runtime
import QuartzCore
import Scout

/// Automatic screen tracking via one-time `UIViewController` swizzles. Each non-container view
/// controller that appears emits a `screen_view`; the time from `viewDidLoad` to `viewDidAppear`
/// is emitted as a `screen_load`.
enum ScreenTracking {
    private static var installed = false
    fileprivate static var loadStartKey: UInt8 = 0

    static func install() {
        guard !installed else { return }
        installed = true
        swizzle(#selector(UIViewController.viewDidLoad), #selector(UIViewController.scout_viewDidLoad))
        swizzle(#selector(UIViewController.viewDidAppear(_:)), #selector(UIViewController.scout_viewDidAppear(_:)))
    }

    private static func swizzle(_ original: Selector, _ replacement: Selector) {
        guard
            let o = class_getInstanceMethod(UIViewController.self, original),
            let r = class_getInstanceMethod(UIViewController.self, replacement)
        else { return }
        method_exchangeImplementations(o, r)
    }
}

extension UIViewController {
    private var scoutLoadStart: CFTimeInterval? {
        get { objc_getAssociatedObject(self, &ScreenTracking.loadStartKey) as? CFTimeInterval }
        set { objc_setAssociatedObject(self, &ScreenTracking.loadStartKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC) }
    }

    @objc func scout_viewDidLoad() {
        self.scout_viewDidLoad() // original (swapped)
        scoutLoadStart = CACurrentMediaTime()
    }

    @objc func scout_viewDidAppear(_ animated: Bool) {
        self.scout_viewDidAppear(animated) // original (swapped)

        // Skip container controllers — they wrap real screens and would be noise.
        if self is UINavigationController || self is UITabBarController || self is UISplitViewController || self is UIPageViewController {
            return
        }
        let name = String(describing: type(of: self))
        ScoutEngine.shared.setScreen(name: name)

        // First-appearance load time (viewDidLoad → viewDidAppear) → screen_load.
        if let start = scoutLoadStart {
            let ms = Int64((CACurrentMediaTime() - start) * 1000.0)
            if ms >= 0 { ScoutEngine.shared.recordScreenLoad(name: name, durationMs: ms) }
            scoutLoadStart = nil
        }
    }
}
