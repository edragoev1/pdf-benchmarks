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
        // Counts the bytes each thread allocates (malloc replaced, Linux)
        .target(name: "AllocCount"),
        .executableTarget(name: "Bench", dependencies: ["AllocCount", .product(name: "PDFjet", package: "pdfjet")]),
    ]
)
