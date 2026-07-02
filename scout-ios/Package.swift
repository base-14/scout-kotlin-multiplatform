// swift-tools-version:5.9
import PackageDescription

// Consumer-facing Swift package for the Scout iOS SDK.
//
// The `ScoutNative` binary target is the Kotlin/Native engine (scout-core + scout-ios facade),
// assembled by Gradle. Build it first with:
//   ./gradlew :scout-ios:assembleScoutReleaseXCFramework -Pscout.enableIos=true
//
// `ScoutKit` is the Swift layer: public API + auto-instrumentation (screens, HTTP) + crash/ANR
// reporting (KSCrash), all forwarding into the Kotlin engine (`ScoutEngine`).
let package = Package(
    name: "Scout",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "Scout", targets: ["ScoutKit"]),
    ],
    dependencies: [
        .package(url: "https://github.com/kstenerud/KSCrash.git", exact: "2.5.1"),
    ],
    targets: [
        .binaryTarget(
            name: "ScoutNative",
            path: "build/XCFrameworks/release/Scout.xcframework"
        ),
        .target(
            name: "ScoutKit",
            dependencies: [
                "ScoutNative",
                .product(name: "Recording", package: "KSCrash"),
                .product(name: "Installations", package: "KSCrash"),
            ],
            path: "ScoutKit"
        ),
        .testTarget(
            name: "ScoutKitTests",
            dependencies: [
                "ScoutKit",
                .product(name: "Recording", package: "KSCrash"),
            ],
            path: "ScoutKitTests"
        ),
    ]
)
