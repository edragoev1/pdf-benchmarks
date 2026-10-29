// The fair test of October 2026, PDFjet Swift: the documents of ../SPEC.md, drawn
// as ../java/src/PDFjetFair.java draws them, with the same geometry (its
// constants and text generators are in ../java/src/Common.java).
//
//   .build/release/Bench <plex|noto> <warm|first> <warm-ups> <runs> [min-warm-seconds] [tasks]
import AllocCount
import Foundation
import PDFjet

let label = "pdfjet-swift"

// The geometry, in points, y from the top of the page, as in Common.java.
let W: Float = 612, H: Float = 792, M: Float = 50, TEXT_W: Float = 612 - 2 * 50
let BODY: Float = 10, LEADING: Float = 13, HEADING: Float = 16, TABLE: Float = 9, FOOTER: Float = 8
let INVOICE_PAD: Float = 3, STATEMENT_PAD: Float = 0.75, SIDE_PAD: Float = 3, RULE: Float = 0.5
let PARAGRAPH_GAP: Float = 6
let FOOTER_BASELINE: Float = 792 - 30, INVOICE_FOOTER_BASELINE: Float = 742
let LOGO_W: Float = 112, PHOTO_MAX_H: Float = 400   // The PDFjet logo, 224 by 72 pixels

let FROM = ["From: PDFjet Software", "123 Main Street", "Markham ON L3R 0A1", "Canada"]
let BILL_TO = ["Bill to: Example Corp.", "456 Market Street", "Springfield IL 62701", "USA"]
let BILL_TO_X: Float = 330
let INVOICE_HEADER = ["Item", "Qty", "Unit price", "Amount"]
let INVOICE_WIDTHS: [Float] = [260, 50, 100, 102]
let INVOICE_RIGHT = [false, true, true, true]
let STATEMENT_HEADER = ["Date", "Description", "Debit", "Credit", "Balance"]
let STATEMENT_WIDTHS: [Float] = [80, 252, 60, 60, 60]
let STATEMENT_RIGHT = [false, false, true, true, true]

// The bytes of the fonts and the images, read once into memory.
let root = ProcessInfo.processInfo.environment["HOME"]! + "/Projects/pdfjet/"
func read(_ path: String) -> Data {
    FileManager.default.contents(atPath: root + path)!
}
let args = CommandLine.arguments
let family = args[1]
let REGULAR = read(family == "plex" ? "fonts/IBMPlexSans/IBMPlexSans-Regular.ttf" : "fonts/NotoSans/NotoSans-Regular.ttf")
let BOLD = read(family == "plex" ? "fonts/IBMPlexSans/IBMPlexSans-SemiBold.ttf" : "fonts/NotoSans/NotoSans-Bold.ttf")
let LOGO = read("images/readme/pdfjet-logo.png")
let SVG_LOGO = read("images/readme/pdfjet-logo.svg")
let PHOTOS = ["photo-1.jpg", "photo-2.jpg", "photo-3.jpg", "photo-4.jpg"].map { FileManager.default.contents(atPath: "../images/" + $0)! }
// The bytes of the images, for Image(pdf, bytes), made once
let LOGO_BYTES = [UInt8](LOGO)
let PHOTO_BYTES = PHOTOS.map { [UInt8]($0) }
// The name and the paragraph of each language of the multilingual notice,
// from ../multilingual.txt
let LANGUAGES: [[String]] = String(decoding: FileManager.default.contents(atPath: "../multilingual.txt")!, as: UTF8.self)
        .split(separator: "\n").map { $0.split(separator: "|", maxSplits: 1).map(String.init) }
let NOTICE_TITLE = "A notice in eight languages"
let NAME_SIZE: Float = 13

// The text of the documents, as Common.java makes it.
func money(_ v: Double) -> String {
    var cents = Int((v * 100).rounded())
    let sign = cents < 0 ? "-" : ""
    cents = abs(cents)
    let whole = String(cents / 100)
    var out = sign
    for (i, c) in whole.enumerated() {
        if i > 0 && (whole.count - i) % 3 == 0 {
            out.append(",")
        }
        out.append(c)
    }
    let fraction = cents % 100
    out += (fraction < 10 ? ".0" : ".") + String(fraction)
    return out
}

func invoiceRows() -> [[String]] {
    var rows = [[String]]()
    for i in 1...15 {
        let unit = 10.00 + Double(i) * 2.50
        rows.append(["Service item \(i): consulting and support", String(i), money(unit), money(Double(i) * unit)])
    }
    return rows
}

func invoiceTotals() -> [String] {
    var subtotal = 0.0
    for i in 1...15 {
        subtotal += Double(i) * (10.00 + Double(i) * 2.50)
    }
    let tax = (subtotal * 13.0).rounded() / 100.0
    return ["Subtotal " + money(subtotal), "Tax (13%) " + money(tax), "Total " + money(subtotal + tax)]
}

func statementRows() -> [[String]] {
    var rows = [[String]]()
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    let start = calendar.date(from: DateComponents(year: 2026, month: 1, day: 1))!
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.timeZone = calendar.timeZone
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    var balance = 1_000_000  // in cents
    for i in 1...600 {
        let debit = (i % 3 == 0) ? i * 125 : 0
        let credit = (i % 5 == 0) ? i * 310 : 0
        balance += credit - debit
        let date = calendar.date(byAdding: .day, value: i / 2, to: start)!
        rows.append([formatter.string(from: date),
                "Transaction \(i): payment to supplier number \(i * 7919 % 1000)",
                debit == 0 ? "" : money(Double(debit) / 100), credit == 0 ? "" : money(Double(credit) / 100),
                money(Double(balance) / 100)])
    }
    return rows
}

func paragraph(_ s: Int, _ p: Int) -> String {
    var text = ""
    for k in 1...6 {
        if k > 1 {
            text += " "
        }
        text += "Sentence \(k) of paragraph \(p) in section \(s)"
                + " describes the results, the costs and the plans of the year in plain words."
    }
    return text
}

func letterParagraph(_ n: Int) -> String {
    var text = ""
    for k in 0..<5 {
        if k > 0 {
            text += " "
        }
        text += "We are writing to tell you about the changes to your account \(n) that take effect next month."
    }
    return text
}

// task helpers
func newPDF() -> (PDF, OutputStream) {
    let stream = OutputStream(toMemory: ())
    stream.open()
    return (PDF(stream), stream)
}

func finish(_ pdf: PDF, _ stream: OutputStream) throws -> Data {
    try pdf.complete()
    // The bytes of the memory stream, copied at once: "as! Data" bridges the
    // NSData of swift-corelibs-foundation byte by byte, which took 90% of the
    // time of the 3.4 MB report on Linux.
    let written = stream.property(forKey: .dataWrittenToMemoryStreamKey) as! NSData
    return Data(bytes: written.bytes, count: written.length)
}

func font(_ pdf: PDF, _ bytes: Data) throws -> Font {
    try Font(pdf, InputStream(data: bytes))
}

func line(_ font: Font, _ size: Float, _ text: String, _ x: Float, _ y: Float) -> TextLine {
    TextLine(font, text).setFontSize(size).setLocation(x, y)
}

func cell(_ font: Font, _ text: String, _ right: Bool, _ header: Bool, _ pad: Float) -> Cell {
    let cell = Cell(font, text).setFontSize(TABLE)
    cell.setTopPadding(pad).setBottomPadding(pad).setLeftPadding(SIDE_PAD).setRightPadding(SIDE_PAD)
    cell.setBorders(false).setBorder(Border.BOTTOM, true).setBorderWidth(RULE)
    if header {
        cell.setBackgroundColor(0xE6E6E6)
    }
    if right {
        cell.setTextAlignment(.RIGHT)
    }
    return cell
}

func tableData(_ regular: Font, _ bold: Font, _ header: [String], _ rows: [[String]], _ widths: [Float],
        _ right: [Bool], _ pad: Float) -> [[Cell]] {
    var data = [[Cell]]()
    var headerRow = [Cell]()
    for c in 0..<header.count {
        headerRow.append(cell(bold, header[c], right[c], true, pad).setWidth(widths[c]))
    }
    data.append(headerRow)
    for r in rows {
        var row = [Cell]()
        for c in 0..<r.count {
            row.append(cell(regular, r[c], right[c], false, pad).setWidth(widths[c]))
        }
        data.append(row)
    }
    return data
}
// end helpers

// task invoice
func invoice() throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let page = Page(pdf, Letter.PORTRAIT)

    let logo = try Image(pdf, LOGO_BYTES)
    logo.resizeWidth(LOGO_W)
    logo.setLocation(M, M)
    logo.drawOn(page)
    line(bold, HEADING, "Invoice INV-2026-0042", M + LOGO_W + 12, M + 36).drawOn(page)
    for i in 0..<4 {
        line(regular, BODY, FROM[i], M, 140 + Float(i) * LEADING).drawOn(page)
        line(regular, BODY, BILL_TO[i], BILL_TO_X, 140 + Float(i) * LEADING).drawOn(page)
    }

    let table = Table()
    table.setTableData(tableData(regular, bold, INVOICE_HEADER, invoiceRows(), INVOICE_WIDTHS, INVOICE_RIGHT,
            INVOICE_PAD), 1)
    table.setLocation(M, 210)
    let end = table.drawOn(page)

    let totals = invoiceTotals()
    for i in 0..<totals.count {
        let f = (i == 2) ? bold : regular
        let total = TextLine(f, totals[i]).setFontSize(BODY)
        total.setLocation(W - M - total.getWidth(), end[1] + 20 + Float(i) * LEADING).drawOn(page)
    }
    let footer = TextLine(regular, "Thank you for your business. Payment due in 30 days.").setFontSize(FOOTER)
    footer.setLocation((W - footer.getWidth()) / 2, INVOICE_FOOTER_BASELINE).drawOn(page)
    return try finish(pdf, stream)
}
// end invoice

// task statement
func statement() throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let first = Page(pdf, Letter.PORTRAIT, Page.DETACHED)
    line(bold, HEADING, "Account statement", M, M + 16).drawOn(first)
    line(regular, BODY, "Account 0042-1234, 1 January to 30 December 2026", M, M + 36).drawOn(first)

    let table = Table()
    table.setTableData(tableData(regular, bold, STATEMENT_HEADER, statementRows(), STATEMENT_WIDTHS,
            STATEMENT_RIGHT, STATEMENT_PAD), 1)
    table.setLocation(M, M)
    table.setFirstPageTopMargin(M + 50)
    table.setBottomMargin(M)
    var pages = [first]
    table.drawOn(pdf, first, &pages, Letter.PORTRAIT)
    for (i, page) in pages.enumerated() {
        let footer = TextLine(regular, "Page \(i + 1) of \(pages.count)").setFontSize(FOOTER)
        page.addFooter(footer, H - FOOTER_BASELINE)
    }
    pdf.addPages(pages)
    return try finish(pdf, stream)
}
// end statement

// task report
func report() throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let flow = TextFlow(pdf, Letter.PORTRAIT).setMargins(M)
    flow.add(Paragraph(TextLine(bold, "Annual report 2026").setFontSize(HEADING)))
    for s in 1...4 {
        flow.addSpace(16)
        flow.add(Paragraph(TextLine(bold, "Section \(s): Results of the year").setFontSize(HEADING))).addSpace(4)
        var paragraphs = [Paragraph]()
        for p in 1...30 {
            paragraphs.append(Paragraph(TextLine(regular, paragraph(s, p)).setFontSize(BODY)))
        }
        flow.add(paragraphs, PARAGRAPH_GAP).addSpace(12)
        let photo = try Image(pdf, PHOTO_BYTES[s - 1])
        photo.resizeWidth(TEXT_W)
        if photo.getHeight() > PHOTO_MAX_H {
            photo.resizeHeight(PHOTO_MAX_H)
        }
        flow.add(photo).addSpace(4)
        flow.add(Paragraph(TextLine(regular, "Figure \(s): map").setFontSize(TABLE)))
    }
    var pages = [Page]()
    flow.drawOn(&pages)
    for (i, page) in pages.enumerated() {
        let footer = TextLine(regular, "Page \(i + 1)").setFontSize(FOOTER)
        page.addFooter(footer, H - FOOTER_BASELINE)
    }
    pdf.addPages(pages)
    return try finish(pdf, stream)
}
// end report

// task letters
func letter(_ n: Int) throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let page = Page(pdf, Letter.PORTRAIT)
    let logo = try Image(pdf, LOGO_BYTES)
    logo.resizeWidth(LOGO_W)
    logo.setLocation(M, M).drawOn(page)
    line(bold, HEADING, "Dear customer \(n),", M, 160).drawOn(page)
    var paragraphs = [Paragraph]()
    for _ in 0..<3 {
        paragraphs.append(Paragraph(TextLine(regular, letterParagraph(n)).setFontSize(BODY)))
    }
    let xy = TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
            .setLocation(M, 172).setWidth(TEXT_W).drawOn(page)
    line(regular, BODY, "PDFjet Software", M, xy[1] + 26).drawOn(page)
    return try finish(pdf, stream)
}
// end letters

// A letter with the PDFjet logo in SVG, drawn as paths: the letter again,
// the logo the one difference.
// task svgletters
func svgLetter(_ n: Int) throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let page = Page(pdf, Letter.PORTRAIT)
    let logo = try SVGImage(stream: InputStream(data: SVG_LOGO))
    logo.scaleBy(LOGO_W / logo.getWidth())
    logo.setLocation(M, M).drawOn(page)
    line(bold, HEADING, "Dear customer \(n),", M, 160).drawOn(page)
    var paragraphs = [Paragraph]()
    for _ in 0..<3 {
        paragraphs.append(Paragraph(TextLine(regular, letterParagraph(n)).setFontSize(BODY)))
    }
    let xy = TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
            .setLocation(M, 172).setWidth(TEXT_W).drawOn(page)
    line(regular, BODY, "PDFjet Software", M, xy[1] + 26).drawOn(page)
    return try finish(pdf, stream)
}
// end svgletters

// task multilingual
func multilingual() throws -> Data {
    let (pdf, stream) = newPDF()
    let regular = try font(pdf, REGULAR)
    let bold = try font(pdf, BOLD)
    let page = Page(pdf, Letter.PORTRAIT)
    line(bold, HEADING, NOTICE_TITLE, M, M + 16).drawOn(page)
    var paragraphs = [Paragraph]()
    for language in LANGUAGES {
        paragraphs.append(Paragraph(TextLine(bold, language[0]).setFontSize(NAME_SIZE)))
        paragraphs.append(Paragraph(TextLine(regular, language[1]).setFontSize(BODY)))
    }
    TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
            .setLocation(M, M + 24).setWidth(TEXT_W).drawOn(page)
    return try finish(pdf, stream)
}
// end multilingual

func svgLetters() throws -> Data {
    var last = Data()
    for n in 1...1000 {
        last = try svgLetter(n)
    }
    return last
}

func letters() throws -> Data {
    var last = Data()
    for n in 1...1000 {
        last = try letter(n)
    }
    return last
}

func ms(_ start: DispatchTime) -> Double {
    Double(DispatchTime.now().uptimeNanoseconds - start.uptimeNanoseconds) / 1e6
}

let mode = args[2]
let warm = Int(args[3])!
let runs = Int(args[4])!
let minWarmSeconds = args.count > 5 ? Double(args[5])! : 3.0
let only = (args.count > 6 ? args[6] : "invoice,statement,report,letters,svgletters,multilingual").split(separator: ",").map(String.init)
let outDir = "out-" + family
try FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

if mode == "first" {
    // The first document of a fresh process: the invoice unless another is
    // named, and one letter for the letters
    let name = args.count > 6 ? args[6] : "invoice"
    let makers: [String: () throws -> Data] = [
        "invoice": invoice, "statement": statement, "report": report, "multilingual": multilingual,
        "svgletters": { try svgLetter(1) },
    ]
    let t0 = DispatchTime.now()
    let pdf = try makers[name]!()
    print(String(format: "%@ %@ first %@ median-ms %.2f bytes %d", label, family, name, ms(t0), pdf.count))
    exit(0)
}

let tasks: [(String, () throws -> Data, Int)] = [
    ("invoice", invoice, 1),
    ("statement", statement, 1),
    ("report", report, 1),
    ("letters", letters, 1000),
    ("multilingual", multilingual, 1),
    ("svgletters", svgLetters, 1000),
]
for (name, task, docs) in tasks where only.contains(name) {
    // Letters: timed as the whole batch, 2 warm-up batches, 5 timed runs.
    let batch = docs > 1
    let w = batch ? min(warm, 2) : warm
    let r = batch ? min(runs, 5) : runs
    let minSeconds = batch ? 0.0 : minWarmSeconds
    let warmStart = DispatchTime.now()
    var i = 0
    while i < w || ms(warmStart) < minSeconds * 1000 {
        _ = try task()
        i += 1
    }
    var times = [Double]()
    var allocs = [UInt64]()
    var pdf = Data()
    for _ in 0..<r {
        let a = alloc_count_thread_bytes()
        let t0 = DispatchTime.now()
        pdf = try task()
        times.append(ms(t0))
        allocs.append((alloc_count_thread_bytes() - a) / UInt64(docs))
    }
    times.sort()
    allocs.sort()
    try pdf.write(to: URL(fileURLWithPath: "\(outDir)/\(label)-\(name).pdf"))
    print(String(format: "%@ %@ %@ median-ms %.2f min-ms %.2f max-ms %.2f bytes %d alloc-bytes %llu",
                 label, family, name, times[r / 2], times[0], times[r - 1], pdf.count, allocs[r / 2]))
}
