// swift-tools-version:5.9
import PackageDescription

// PDFjet Swift from source: the package at ~/Projects/pdfjet, whose identity
// is its folder name, "pdfjet".
let package = Package(
    name: "Bench",
    dependencies: [
        .package(path: "../../pdfjet"),
    ],
    targets: [
        .executableTarget(name: "Bench", dependencies: [.product(name: "PDFjet", package: "pdfjet")]),
    ]
)
