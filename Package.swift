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
            url: "https://github.com/base-14/scout-kotlin-multiplatform/releases/download/ios-0.1.3/Scout.xcframework.zip",
            checksum: "b65c587ff474452efac4acbce50802a2f15c345ae651345f08117f9f8f2cd0c2"
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
