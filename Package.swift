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
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "ScoutNative",
            url: "https://github.com/base-14/scout-kotlin-multiplatform/releases/download/ios-0.1.5/Scout.xcframework.zip",
            checksum: "258ac61acc2228f7fe021f5b37aab86a19773f9f6e17ca553f98c8559b421da2"
        ),
        .target(
            name: "ScoutKit",
            dependencies: [
                "ScoutNative",
            ],
            path: "scout-ios/ScoutKit"
        ),
    ]
)
