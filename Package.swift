// swift-tools-version:5.9
import PackageDescription

// Public Swift Package Manager manifest for the Scout iOS SDK, consumed as:
//   https://github.com/base-14/scout-kotlin-multiplatform  (pin to an `ios-<version>` tag)
//
// `ScoutNative` is the Kotlin/Native engine, distributed as a hosted xcframework attached to the
// matching `ios-<version>` GitHub Release. The publish-ios workflow builds the xcframework,
// uploads Scout.xcframework.zip to the release, and rewrites the `url` + `checksum` below.
// `ScoutKit` is the Swift layer (public API + auto-instrumentation), compiled from source.
let package = Package(
    name: "Scout",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "Scout", targets: ["ScoutKit"]),
        .library(name: "ScoutNative", targets: ["ScoutNative"]),
    ],
    dependencies: [
        .package(url: "https://github.com/kstenerud/KSCrash.git", exact: "2.5.1"),
    ],
    targets: [
        .binaryTarget(
            name: "ScoutNative",
            url: "https://github.com/base-14/scout-kotlin-multiplatform/releases/download/ios-0.1.4/Scout.xcframework.zip",
            checksum: "bac7b5e723c7db7ad8c13b247131195738fe5a7d1c8d01f848ded363a5db9299"
        ),
        .target(
            name: "ScoutKit",
            dependencies: [
                "ScoutNative",
                .product(name: "Recording", package: "KSCrash"),
                .product(name: "Installations", package: "KSCrash"),
            ],
            path: "scout-ios/ScoutKit"
        ),
    ]
)
