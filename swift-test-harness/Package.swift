// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS(.v15),
    ],
    dependencies: [
        .package(name: "Antlr4", path: "../build/SPMPackage/macosArm64/Debug")
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "Antlr4Library", package: "Antlr4")
            ],
            swiftSettings: [
                .unsafeFlags(["-warnings-as-errors"]),
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", "../build/swift-test",
                    "-lAntlr4",
                ]),
            ]
        ),
    ],
    swiftLanguageModes: [.v6]
)
