// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PushappIonic",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "PushappIonic",
            targets: ["PushAppPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "PushAppPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/PushAppPlugin"),
        .testTarget(
            name: "PushAppPluginTests",
            dependencies: ["PushAppPlugin"],
            path: "ios/Tests/PushAppPluginTests")
    ]
)