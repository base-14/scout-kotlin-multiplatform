package io.base14.scout.ios

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSStringFromClass
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UINavigationController
import platform.UIKit.UIPageViewController
import platform.UIKit.UISplitViewController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.objc.class_getInstanceMethod
import platform.objc.method_getImplementation
import platform.objc.method_setImplementation
import platform.objc.objc_getClass

/**
 * Automatic screen tracking inside the Kotlin engine via `UIViewController` IMP swizzles,
 * so KMP apps get `screen_view`/`screen_load` (+ navigation breadcrumbs) from the common
 * init. Port of ScoutKit's `ScreenTracking`. The replacement IMPs are `staticCFunction`s
 * (exact ObjC IMP ABI — a block-based IMP would box the primitive BOOL arg and crash).
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal object IosScreenTracking {
    private var installed = false
    private val loadStarts = HashMap<Long, Double>()

    internal var origDidLoad: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Unit>>? = null
    internal var origDidAppear: CPointer<CFunction<(COpaquePointer?, COpaquePointer?, Boolean) -> Unit>>? = null

    fun install() {
        if (installed) return
        installed = true
        val cls = objc_getClass("UIViewController") as? kotlinx.cinterop.ObjCClass ?: return

        val didLoadSel = NSSelectorFromString("viewDidLoad")
        val didLoadMethod = class_getInstanceMethod(cls, didLoadSel) ?: return
        origDidLoad = method_getImplementation(didLoadMethod)?.reinterpret()
        val loadImp = staticCFunction { self: COpaquePointer?, cmd: COpaquePointer? ->
            IosScreenTracking.origDidLoad?.invoke(self, cmd)
            IosScreenTracking.onDidLoad(self)
        }
        method_setImplementation(didLoadMethod, loadImp.reinterpret())

        val didAppearSel = NSSelectorFromString("viewDidAppear:")
        val didAppearMethod = class_getInstanceMethod(cls, didAppearSel) ?: return
        origDidAppear = method_getImplementation(didAppearMethod)?.reinterpret()
        val appearImp = staticCFunction { self: COpaquePointer?, cmd: COpaquePointer?, animated: Boolean ->
            IosScreenTracking.origDidAppear?.invoke(self, cmd, animated)
            IosScreenTracking.onDidAppear(self)
        }
        method_setImplementation(didAppearMethod, appearImp.reinterpret())
    }

    internal fun onDidLoad(selfPtr: COpaquePointer?) {
        val obj = objOf(selfPtr) ?: return
        loadStarts[obj.hash().toLong()] = CACurrentMediaTime()
    }

    internal fun onDidAppear(selfPtr: COpaquePointer?) {
        val vc = objOf(selfPtr) as? UIViewController ?: return
        if (vc is UINavigationController || vc is UITabBarController ||
            vc is UISplitViewController || vc is UIPageViewController
        ) {
            return
        }
        val name = vc.`class`()?.let { NSStringFromClass(it) }?.substringAfterLast('.') ?: "Screen"
        ScoutEngine.setScreen(name)
        loadStarts.remove(vc.hash().toLong())?.let { start ->
            val ms = ((CACurrentMediaTime() - start) * 1000.0).toLong()
            if (ms >= 0) ScoutEngine.recordScreenLoad(name, ms)
        }
    }

    private fun objOf(ptr: COpaquePointer?): NSObject? =
        ptr?.let { kotlinx.cinterop.interpretObjCPointerOrNull<NSObject>(it.rawValue) }
}
