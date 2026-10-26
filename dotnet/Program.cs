// The fair test of October 2026, PDFjet C#: the documents of ../SPEC.md, drawn
// as ../java/src/PDFjetFair.java draws them, with the same geometry (its
// constants and text generators are in ../java/src/Common.java).
//
//   dotnet bin/Release/net8.0/Bench.dll <plex|noto> <warm|first> <warm-ups> <runs> [min-warm-seconds] [tasks]
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using PDFjet.NET;

static class Bench {
    const string Label = "pdfjet-dotnet";

    // The geometry, in points, y from the top of the page, as in Common.java.
    const float W = 612f, H = 792f, M = 50f, TEXT_W = W - 2 * M;
    const float BODY = 10f, LEADING = 13f, HEADING = 16f, TABLE = 9f, FOOTER = 8f;
    const float INVOICE_PAD = 3f, STATEMENT_PAD = 0.75f, SIDE_PAD = 3f, RULE = 0.5f;
    const float PARAGRAPH_GAP = 6f;
    const float FOOTER_BASELINE = H - 30f, INVOICE_FOOTER_BASELINE = 742f;
    const float LOGO_W = 112f, PHOTO_MAX_H = 400f;   // The PDFjet logo, 224 by 72 pixels

    static readonly string[] FROM = {"From: PDFjet Software", "123 Main Street", "Markham ON L3R 0A1", "Canada"};
    static readonly string[] BILL_TO = {"Bill to: Example Corp.", "456 Market Street", "Springfield IL 62701", "USA"};
    const float BILL_TO_X = 330f;
    static readonly string[] INVOICE_HEADER = {"Item", "Qty", "Unit price", "Amount"};
    static readonly float[] INVOICE_WIDTHS = {260f, 50f, 100f, 102f};
    static readonly bool[] INVOICE_RIGHT = {false, true, true, true};
    static readonly string[] STATEMENT_HEADER = {"Date", "Description", "Debit", "Credit", "Balance"};
    static readonly float[] STATEMENT_WIDTHS = {80f, 252f, 60f, 60f, 60f};
    static readonly bool[] STATEMENT_RIGHT = {false, false, true, true, true};

    static byte[] REGULAR, BOLD, LOGO, SVG_LOGO;
    static readonly byte[][] PHOTOS = new byte[4][];
    // The name and the paragraph of each language of the multilingual
    // notice, from ../multilingual.txt
    static readonly List<string[]> LANGUAGES = new List<string[]>();
    const string NOTICE_TITLE = "A notice in eight languages";
    const float NAME_SIZE = 13f;

    // The bytes of the fonts and the images, read once into memory.
    static void ReadInputs(string family) {
        string root = Environment.GetEnvironmentVariable("HOME") + "/Projects/pdfjet/";
        if (family == "plex") {
            REGULAR = File.ReadAllBytes(root + "fonts/IBMPlexSans/IBMPlexSans-Regular.ttf");
            BOLD = File.ReadAllBytes(root + "fonts/IBMPlexSans/IBMPlexSans-SemiBold.ttf");
        } else if (family == "noto") {
            REGULAR = File.ReadAllBytes(root + "fonts/NotoSans/NotoSans-Regular.ttf");
            BOLD = File.ReadAllBytes(root + "fonts/NotoSans/NotoSans-Bold.ttf");
        } else {
            throw new ArgumentException(family);
        }
        LOGO = File.ReadAllBytes(root + "images/readme/pdfjet-logo.png");
        SVG_LOGO = File.ReadAllBytes(root + "images/readme/pdfjet-logo.svg");
        string[] photos = {"photo-1.jpg", "photo-2.jpg", "photo-3.jpg", "photo-4.jpg"};
        for (int i = 0; i < 4; i++) {
            PHOTOS[i] = File.ReadAllBytes("../images/" + photos[i]);
        }
        foreach (string line in File.ReadAllLines("../multilingual.txt", System.Text.Encoding.UTF8)) {
            if (line.Length > 0) {
                LANGUAGES.Add(line.Split('|', 2));
            }
        }
    }

    // The text of the documents, as Common.java makes it.
    static string Money(double v) {
        return v.ToString("#,##0.00", CultureInfo.InvariantCulture);
    }

    static string[][] InvoiceRows() {
        string[][] rows = new string[15][];
        for (int i = 1; i <= 15; i++) {
            double unit = 10.00 + i * 2.50;
            rows[i - 1] = new string[] {"Service item " + i + ": consulting and support",
                    i.ToString(CultureInfo.InvariantCulture), Money(unit), Money(i * unit)};
        }
        return rows;
    }

    static string[] InvoiceTotals() {
        double subtotal = 0;
        for (int i = 1; i <= 15; i++) {
            subtotal += i * (10.00 + i * 2.50);
        }
        double tax = Math.Round(subtotal * 13.0, MidpointRounding.AwayFromZero) / 100.0;
        return new string[] {"Subtotal " + Money(subtotal), "Tax (13%) " + Money(tax),
                "Total " + Money(subtotal + tax)};
    }

    static string[][] StatementRows() {
        string[][] rows = new string[600][];
        DateTime start = new DateTime(2026, 1, 1);
        long balance = 1_000_000L;  // in cents
        for (int i = 1; i <= 600; i++) {
            long debit = (i % 3 == 0) ? i * 125L : 0L;
            long credit = (i % 5 == 0) ? i * 310L : 0L;
            balance += credit - debit;
            rows[i - 1] = new string[] {start.AddDays(i / 2).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                    "Transaction " + i + ": payment to supplier number " + (i * 7919 % 1000),
                    debit == 0 ? "" : Money(debit / 100.0), credit == 0 ? "" : Money(credit / 100.0),
                    Money(balance / 100.0)};
        }
        return rows;
    }

    static string Paragraph(int s, int p) {
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= 6; k++) {
            if (k > 1) {
                sb.Append(' ');
            }
            sb.Append("Sentence ").Append(k).Append(" of paragraph ").Append(p).Append(" in section ").Append(s)
                    .Append(" describes the results, the costs and the plans of the year in plain words.");
        }
        return sb.ToString();
    }

    static string LetterParagraph(int n) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 5; k++) {
            if (k > 0) {
                sb.Append(' ');
            }
            sb.Append("We are writing to tell you about the changes to your account ").Append(n)
                    .Append(" that take effect next month.");
        }
        return sb.ToString();
    }

    // A PDF, its pages uncompressed when NOCOMPRESS is set: for the run that
    // measures the code without the compression of the pages. Not counted in
    // the lines of code, as the tasks would write new PDF(buf).
    static PDF NewPDF(Stream buf) {
        PDF pdf = new PDF(buf);
        if (Environment.GetEnvironmentVariable("NOCOMPRESS") != null) {
            pdf.SetCompression(false);
        }
        return pdf;
    }

    // task helpers
    static Font NewFont(PDF pdf, byte[] bytes) {
        return new Font(pdf, new MemoryStream(bytes));
    }

    static TextLine Line(Font font, float size, string text, float x, float y) {
        return new TextLine(font, text).SetFontSize(size).SetLocation(x, y);
    }

    static Cell NewCell(Font font, string text, bool right, bool header, float pad) {
        Cell cell = new Cell(font, text).SetFontSize(TABLE);
        cell.SetTopPadding(pad).SetBottomPadding(pad).SetLeftPadding(SIDE_PAD).SetRightPadding(SIDE_PAD);
        cell.SetBorders(false).SetBorder(Border.BOTTOM, true).SetBorderWidth(RULE);
        if (header) {
            cell.SetBackgroundColor(0xE6E6E6);
        }
        if (right) {
            cell.SetTextAlignment(Alignment.RIGHT);
        }
        return cell;
    }

    static List<List<Cell>> TableData(Font regular, Font bold, string[] header, string[][] rows, float[] widths,
            bool[] right, float pad) {
        List<List<Cell>> data = new List<List<Cell>>();
        List<Cell> headerRow = new List<Cell>();
        for (int c = 0; c < header.Length; c++) {
            headerRow.Add(NewCell(bold, header[c], right[c], true, pad).SetWidth(widths[c]));
        }
        data.Add(headerRow);
        foreach (string[] r in rows) {
            List<Cell> row = new List<Cell>();
            for (int c = 0; c < r.Length; c++) {
                row.Add(NewCell(regular, r[c], right[c], false, pad).SetWidth(widths[c]));
            }
            data.Add(row);
        }
        return data;
    }
    // end helpers

    // task invoice
    static byte[] Invoice() {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);

        Image logo = new Image(pdf, LOGO);
        logo.ResizeWidth(LOGO_W);
        logo.SetLocation(M, M);
        logo.DrawOn(page);
        Line(bold, HEADING, "Invoice INV-2026-0042", M + LOGO_W + 12f, M + 36f).DrawOn(page);
        for (int i = 0; i < 4; i++) {
            Line(regular, BODY, FROM[i], M, 140f + i * LEADING).DrawOn(page);
            Line(regular, BODY, BILL_TO[i], BILL_TO_X, 140f + i * LEADING).DrawOn(page);
        }

        Table table = new Table();
        table.SetTableData(TableData(regular, bold, INVOICE_HEADER, InvoiceRows(), INVOICE_WIDTHS, INVOICE_RIGHT,
                INVOICE_PAD), 1);
        table.SetLocation(M, 210f);
        float[] end = table.DrawOn(page);

        string[] totals = InvoiceTotals();
        for (int i = 0; i < totals.Length; i++) {
            Font f = (i == 2) ? bold : regular;
            TextLine total = new TextLine(f, totals[i]).SetFontSize(BODY);
            total.SetLocation(W - M - total.GetWidth(), end[1] + 20f + i * LEADING).DrawOn(page);
        }
        TextLine footer = new TextLine(regular, "Thank you for your business. Payment due in 30 days.")
                .SetFontSize(FOOTER);
        footer.SetLocation((W - footer.GetWidth()) / 2, INVOICE_FOOTER_BASELINE).DrawOn(page);
        pdf.Complete();
        return buf.ToArray();
    }
    // end invoice

    // task statement
    static byte[] Statement() {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        Page first = new Page(pdf, Letter.PORTRAIT, Page.DETACHED);
        Line(bold, HEADING, "Account statement", M, M + 16f).DrawOn(first);
        Line(regular, BODY, "Account 0042-1234, 1 January to 30 December 2026", M, M + 36f).DrawOn(first);

        Table table = new Table();
        table.SetTableData(TableData(regular, bold, STATEMENT_HEADER, StatementRows(), STATEMENT_WIDTHS,
                STATEMENT_RIGHT, STATEMENT_PAD), 1);
        table.SetLocation(M, M);
        table.SetFirstPageTopMargin(M + 50f);
        table.SetBottomMargin(M);
        List<Page> pages = new List<Page>();
        pages.Add(first);
        table.DrawOn(pdf, first, pages, Letter.PORTRAIT);
        for (int i = 0; i < pages.Count; i++) {
            TextLine footer = new TextLine(regular, "Page " + (i + 1) + " of " + pages.Count).SetFontSize(FOOTER);
            pages[i].AddFooter(footer, H - FOOTER_BASELINE);
        }
        pdf.AddPages(pages);
        pdf.Complete();
        return buf.ToArray();
    }
    // end statement

    // task report
    static byte[] Report() {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        TextFlow flow = new TextFlow(pdf, Letter.PORTRAIT).SetMargins(M);
        flow.Add(new Paragraph(new TextLine(bold, "Annual report 2026").SetFontSize(HEADING)));
        for (int s = 1; s <= 4; s++) {
            flow.AddSpace(16f);
            flow.Add(new Paragraph(new TextLine(bold, "Section " + s + ": Results of the year").SetFontSize(HEADING))).AddSpace(4f);
            List<Paragraph> paragraphs = new List<Paragraph>();
            for (int p = 1; p <= 30; p++) {
                paragraphs.Add(new Paragraph(new TextLine(regular, Paragraph(s, p)).SetFontSize(BODY)));
            }
            flow.Add(paragraphs, PARAGRAPH_GAP).AddSpace(12f);
            Image photo = new Image(pdf, PHOTOS[s - 1]);
            photo.ResizeWidth(TEXT_W);
            if (photo.GetHeight() > PHOTO_MAX_H) {
                photo.ResizeHeight(PHOTO_MAX_H);
            }
            flow.Add(photo).AddSpace(4f);
            flow.Add(new Paragraph(new TextLine(regular, "Figure " + s + ": map").SetFontSize(TABLE)));
        }
        List<Page> pages = new List<Page>();
        flow.DrawOn(pages);
        for (int i = 0; i < pages.Count; i++) {
            pages[i].AddFooter(new TextLine(regular, "Page " + (i + 1)).SetFontSize(FOOTER), H - FOOTER_BASELINE);
        }
        pdf.AddPages(pages);
        pdf.Complete();
        return buf.ToArray();
    }
    // end report

    // task letters
    static byte[] Letter1(int n) {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        Image logo = new Image(pdf, LOGO);
        logo.ResizeWidth(LOGO_W);
        logo.SetLocation(M, M).DrawOn(page);
        Line(bold, HEADING, "Dear customer " + n + ",", M, 160f).DrawOn(page);
        List<Paragraph> paragraphs = new List<Paragraph>();
        for (int p = 0; p < 3; p++) {
            paragraphs.Add(new Paragraph(new TextLine(regular, LetterParagraph(n)).SetFontSize(BODY)));
        }
        float[] xy = new TextFrame(paragraphs).SetParagraphGap(PARAGRAPH_GAP)
                .SetLocation(M, 172f).SetWidth(TEXT_W).DrawOn(page);
        Line(regular, BODY, "PDFjet Software", M, xy[1] + 26f).DrawOn(page);
        pdf.Complete();
        return buf.ToArray();
    }
    // end letters

    // A letter with the PDFjet logo in SVG, drawn as paths: the letter again,
    // the logo the one difference.
    // task svgletters
    static byte[] SvgLetter1(int n) {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        SVGImage logo = new SVGImage(new MemoryStream(SVG_LOGO));
        logo.ScaleBy(LOGO_W / logo.GetWidth());
        logo.SetLocation(M, M).DrawOn(page);
        Line(bold, HEADING, "Dear customer " + n + ",", M, 160f).DrawOn(page);
        List<Paragraph> paragraphs = new List<Paragraph>();
        for (int p = 0; p < 3; p++) {
            paragraphs.Add(new Paragraph(new TextLine(regular, LetterParagraph(n)).SetFontSize(BODY)));
        }
        float[] xy = new TextFrame(paragraphs).SetParagraphGap(PARAGRAPH_GAP)
                .SetLocation(M, 172f).SetWidth(TEXT_W).DrawOn(page);
        Line(regular, BODY, "PDFjet Software", M, xy[1] + 26f).DrawOn(page);
        pdf.Complete();
        return buf.ToArray();
    }
    // end svgletters

    // task multilingual
    static byte[] Multilingual() {
        MemoryStream buf = new MemoryStream();
        PDF pdf = NewPDF(buf);
        Font regular = NewFont(pdf, REGULAR);
        Font bold = NewFont(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        Line(bold, HEADING, NOTICE_TITLE, M, M + 16f).DrawOn(page);
        List<Paragraph> paragraphs = new List<Paragraph>();
        foreach (string[] language in LANGUAGES) {
            paragraphs.Add(new Paragraph(new TextLine(bold, language[0]).SetFontSize(NAME_SIZE)));
            paragraphs.Add(new Paragraph(new TextLine(regular, language[1]).SetFontSize(BODY)));
        }
        new TextFrame(paragraphs).SetParagraphGap(PARAGRAPH_GAP)
                .SetLocation(M, M + 24f).SetWidth(TEXT_W).DrawOn(page);
        pdf.Complete();
        return buf.ToArray();
    }
    // end multilingual

    static byte[] SvgLetters() {
        byte[] last = null;
        for (int n = 1; n <= 1000; n++) {
            last = SvgLetter1(n);
        }
        return last;
    }

    static byte[] Letters() {
        byte[] last = null;
        for (int n = 1; n <= 1000; n++) {
            last = Letter1(n);
        }
        return last;
    }

    static void Main(string[] args) {
        string family = args[0];
        string mode = args[1];
        int warm = int.Parse(args[2]);
        int runs = int.Parse(args[3]);
        double minWarmSeconds = (args.Length > 4) ? double.Parse(args[4], CultureInfo.InvariantCulture) : 3.0;
        List<string> only = new List<string>(((args.Length > 5) ? args[5] : "invoice,statement,report,letters,svgletters,multilingual").Split(','));
        ReadInputs(family);
        string outDir = "out-" + family;
        if (Environment.GetEnvironmentVariable("NOCOMPRESS") != null) {
            outDir += "-raw";   // The uncompressed PDFs apart from the others
        }
        Directory.CreateDirectory(outDir);

        if (mode == "first") {
            Stopwatch sw = Stopwatch.StartNew();
            byte[] pdf = Invoice();
            double ms = sw.Elapsed.TotalMilliseconds;
            Console.WriteLine(string.Format(CultureInfo.InvariantCulture,
                    "{0} {1} first median-ms {2:F2} bytes {3}", Label, family, ms, pdf.Length));
            return;
        }

        var tasks = new (string, Func<byte[]>, int)[] {
            ("invoice", Invoice, 1),
            ("statement", Statement, 1),
            ("report", Report, 1),
            ("letters", Letters, 1000),
            ("multilingual", Multilingual, 1),
            ("svgletters", SvgLetters, 1000),
        };
        foreach (var (name, task, docs) in tasks) {
            if (!only.Contains(name)) {
                continue;
            }
            // Letters: timed as the whole batch, 2 warm-up batches, 5 timed runs.
            bool batch = docs > 1;
            int w = batch ? Math.Min(warm, 2) : warm;
            int r = batch ? Math.Min(runs, 5) : runs;
            double minSeconds = batch ? 0.0 : minWarmSeconds;
            Stopwatch warmClock = Stopwatch.StartNew();
            for (int i = 0; i < w || warmClock.Elapsed.TotalSeconds < minSeconds; i++) {
                task();
            }
            double[] ms = new double[r];
            long[] alloc = new long[r];
            byte[] pdf = null;
            for (int i = 0; i < r; i++) {
                long a = GC.GetAllocatedBytesForCurrentThread();
                long t0 = Stopwatch.GetTimestamp();
                pdf = task();
                ms[i] = (Stopwatch.GetTimestamp() - t0) * 1000.0 / Stopwatch.Frequency;
                alloc[i] = (GC.GetAllocatedBytesForCurrentThread() - a) / docs;
            }
            Array.Sort(ms);
            Array.Sort(alloc);
            File.WriteAllBytes(outDir + "/" + Label + "-" + name + ".pdf", pdf);
            Console.WriteLine(string.Format(CultureInfo.InvariantCulture,
                    "{0} {1} {2} median-ms {3:F2} min-ms {4:F2} max-ms {5:F2} bytes {6} alloc-bytes {7}",
                    Label, family, name, ms[r / 2], ms[0], ms[r - 1], pdf.Length, alloc[r / 2]));
        }
    }
}
