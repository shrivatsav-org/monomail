// swift-tools-version:5.9
import PackageDescription

// Local SPM wrapper around the Kotlin XCFramework.
// The .xcframework is produced by: ./gradlew :shared:assembleSharedDebugXCFramework
// (output: shared/build/XCFrameworks/debug/shared.xcframework)
let package = Package(
    name: "Shared",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "Shared", targets: ["shared"])
    ],
    targets: [
        .binaryTarget(
            name: "shared",
            path: "../../shared/build/XCFrameworks/debug/shared.xcframework"
        )
    ]
)
