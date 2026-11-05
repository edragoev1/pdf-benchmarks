import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

// What the four programs share: the inputs, read from disk once per process,
// the text of the documents (../SPEC.md), the geometry, and the timing.
// Each task makes one whole PDF in an in-memory buffer and returns its bytes;
// the last one of each task is written to out-<font>/<lib>-<task>.pdf for check.sh.
//
//   java <Lib>Fair <font> <tasks> <out-dir> <warm> <runs> [min-seconds]
//   java <Lib>Fair first <font>
//
// font is plex or noto; tasks a comma-separated list of invoice, statement,
// report, letters.
abstract class Common {
    // The benchmark's folder, run from java/: the fonts and the logo of this benchmark, in its own fonts/ and images/ (5 November 2026: PDFjet 9.1.7 moved its fonts, and has no Noto Sans)
    static final String ROOT = "../";

    // The font bytes and the image bytes, read once.
    static byte[] REGULAR, BOLD, LOGO, SVG_LOGO;
    static final byte[][] PHOTOS = new byte[4][];
    static final String[] PHOTO_FILES = {"photo-1.jpg", "photo-2.jpg", "photo-3.jpg", "photo-4.jpg"};

    // The page and its geometry, in points, y measured from the top of the
    // page as in the SPEC; each library turns it into its own coordinates.
    static final float W = 612f, H = 792f, M = 50f, TEXT_W = W - 2 * M;
    static final float BODY = 10f, LEADING = 13f, HEADING = 16f, TABLE = 9f, FOOTER = 8f;
    // The leading of a line of 9 pt table text, 1.3 times its size, as PDFjet's
    // metrics of IBM Plex Sans give it (ascent + descent).
    static final float TABLE_LEADING = 11.7f;
    // Cell paddings: the invoice's rows are roomier than the statement's.
    static final float INVOICE_PAD = 3f, STATEMENT_PAD = 0.75f, SIDE_PAD = 3f;
    static final float GRAY = 0.9f, RULE = 0.5f;
    static final float PARAGRAPH_GAP = 6f;
    // Running footers: the baseline 30 pt from the bottom of the page; the
    // flowing content stops at the bottom margin, 50 pt from the bottom.
    static final float FOOTER_BASELINE = H - 30f;
    static final float INVOICE_FOOTER_BASELINE = 742f;
    static final float LOGO_W = 112f, LOGO_H = 36f;   // The PDFjet logo, 224 by 72 pixels
    static final float PHOTO_MAX_H = 400f;

    // invoice
    static final String INVOICE_TITLE = "Invoice INV-2026-0042";
    static final String[] FROM = {"From: PDFjet Software", "123 Main Street", "Markham ON L3R 0A1", "Canada"};
    static final String[] BILL_TO = {"Bill to: Example Corp.", "456 Market Street", "Springfield IL 62701", "USA"};
    static final float BILL_TO_X = 330f;
    static final String[] INVOICE_HEADER = {"Item", "Qty", "Unit price", "Amount"};
    static final float[] INVOICE_WIDTHS = {260f, 50f, 100f, 102f};
    static final boolean[] INVOICE_RIGHT = {false, true, true, true};
    static final String INVOICE_FOOTER = "Thank you for your business. Payment due in 30 days.";

    // statement
    static final String STATEMENT_TITLE = "Account statement";
    static final String STATEMENT_SUBTITLE = "Account 0042-1234, 1 January to 30 December 2026";
    static final String[] STATEMENT_HEADER = {"Date", "Description", "Debit", "Credit", "Balance"};
    static final float[] STATEMENT_WIDTHS = {80f, 252f, 60f, 60f, 60f};
    static final boolean[] STATEMENT_RIGHT = {false, false, true, true, true};
    static final int TRANSACTIONS = 600;

    // report
    static final String REPORT_TITLE = "Annual report 2026";
    static final int SECTIONS = 4, PARAGRAPHS = 30, SENTENCES = 6;

    // letters
    static final int LETTERS = 1000;

    // multilingual: the name and the paragraph of each language, from
    // ../multilingual.txt
    static final String NOTICE_TITLE = "A notice in eight languages";
    static final float NAME_SIZE = 13f;
    static String[][] LANGUAGES;

    static String money(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    // The 15 rows of the invoice: item, qty, unit price, amount.
    static String[][] invoiceRows() {
        String[][] rows = new String[15][];
        for (int i = 1; i <= 15; i++) {
            double unit = 10.00 + i * 2.50;
            rows[i - 1] = new String[] {"Service item " + i + ": consulting and support",
                    String.valueOf(i), money(unit), money(i * unit)};
        }
        return rows;
    }

    // The three lines under the invoice table, right-aligned: the last is the total.
    static String[] invoiceTotals() {
        double subtotal = 0;
        for (int i = 1; i <= 15; i++) {
            subtotal += i * (10.00 + i * 2.50);
        }
        double tax = Math.round(subtotal * 13.0) / 100.0;
        return new String[] {"Subtotal " + money(subtotal), "Tax (13%) " + money(tax),
                "Total " + money(subtotal + tax)};
    }

    // The 600 rows of the statement: date, description, debit, credit, balance.
    static String[][] statementRows() {
        String[][] rows = new String[TRANSACTIONS][];
        LocalDate start = LocalDate.of(2026, 1, 1);
        long balance = 1_000_000L;  // in cents
        for (int i = 1; i <= TRANSACTIONS; i++) {
            long debit = (i % 3 == 0) ? i * 125L : 0L;
            long credit = (i % 5 == 0) ? i * 310L : 0L;
            balance += credit - debit;
            rows[i - 1] = new String[] {start.plusDays(i / 2).toString(),
                    "Transaction " + i + ": payment to supplier number " + (i * 7919 % 1000),
                    debit == 0 ? "" : money(debit / 100.0), credit == 0 ? "" : money(credit / 100.0),
                    money(balance / 100.0)};
        }
        return rows;
    }

    static String sectionHeading(int s) {
        return "Section " + s + ": Results of the year";
    }

    static String paragraph(int s, int p) {
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= SENTENCES; k++) {
            if (k > 1) {
                sb.append(' ');
            }
            sb.append("Sentence ").append(k).append(" of paragraph ").append(p).append(" in section ").append(s)
                    .append(" describes the results, the costs and the plans of the year in plain words.");
        }
        return sb.toString();
    }

    static String caption(int s) {
        return "Figure " + s + ": map";
    }

    static String salutation(int n) {
        return "Dear customer " + n + ",";
    }

    static String letterParagraph(int n) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 5; k++) {
            if (k > 0) {
                sb.append(' ');
            }
            sb.append("We are writing to tell you about the changes to your account ").append(n)
                    .append(" that take effect next month.");
        }
        return sb.toString();
    }

    // The size a photo is drawn at: the text width, or 400 pt high when that
    // would make it taller; pixelW and pixelH give its aspect.
    static float[] photoSize(float pixelW, float pixelH) {
        float w = TEXT_W, h = TEXT_W * pixelH / pixelW;
        if (h > PHOTO_MAX_H) {
            h = PHOTO_MAX_H;
            w = PHOTO_MAX_H * pixelW / pixelH;
        }
        return new float[] {w, h};
    }

    abstract byte[] invoice() throws Exception;
    abstract byte[] statement() throws Exception;
    abstract byte[] report() throws Exception;
    abstract byte[] letter(int n) throws Exception;
    abstract byte[] multilingual() throws Exception;

    // A letter with the logo in SVG, for the task svgletters, or null when
    // the library does not draw SVG and the task is skipped.
    byte[] svgLetter(int n) throws Exception {
        return null;
    }

    // True while the task svgletters runs
    boolean svgLogo;

    byte[] letters() throws Exception {
        byte[] last = null;
        for (int n = 1; n <= LETTERS; n++) {
            last = svgLogo ? svgLetter(n) : letter(n);
        }
        return last;
    }

    static void readInputs(String font) throws Exception {
        if (font.equals("plex")) {
            REGULAR = Files.readAllBytes(Paths.get(ROOT + "fonts/IBMPlexSans/IBMPlexSans-Regular.ttf"));
            BOLD = Files.readAllBytes(Paths.get(ROOT + "fonts/IBMPlexSans/IBMPlexSans-SemiBold.ttf"));
        } else if (font.equals("noto")) {
            REGULAR = Files.readAllBytes(Paths.get(ROOT + "fonts/NotoSans/NotoSans-Regular.ttf"));
            BOLD = Files.readAllBytes(Paths.get(ROOT + "fonts/NotoSans/NotoSans-Bold.ttf"));
        } else {
            throw new IllegalArgumentException(font);
        }
        LOGO = Files.readAllBytes(Paths.get(ROOT + "images/pdfjet-logo.png"));
        List<String[]> languages = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("../multilingual.txt"), StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) {
                languages.add(line.split("\\|", 2));
            }
        }
        LANGUAGES = languages.toArray(new String[0][]);
        SVG_LOGO = Files.readAllBytes(Paths.get(ROOT + "images/pdfjet-logo.svg"));
        for (int i = 0; i < 4; i++) {
            PHOTOS[i] = Files.readAllBytes(Paths.get("../images/" + PHOTO_FILES[i]));
        }
    }

    interface Task {
        byte[] call() throws Exception;
    }

    void run(String lib, String[] args) throws Exception {
        if (args[0].equals("first")) {
            // The first document of a fresh process: the invoice unless another
            // is named, and one letter for the letters
            String font = args[1];
            String name = args.length > 2 ? args[2] : "invoice";
            readInputs(font);
            long t0 = System.nanoTime();
            byte[] pdf;
            switch (name) {
            case "invoice": pdf = invoice(); break;
            case "statement": pdf = statement(); break;
            case "report": pdf = report(); break;
            case "multilingual": pdf = multilingual(); break;
            case "svgletters": pdf = svgLetter(1); break;
            default: throw new IllegalArgumentException(name);
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            System.out.printf(Locale.US, "%s %s first %s median-ms %.2f bytes %d%n", lib, font, name, ms, pdf.length);
            return;
        }
        String font = args[0];
        String[] tasks = args[1].split(",");
        String out = args[2];
        int warm = Integer.parseInt(args[3]), runs = Integer.parseInt(args[4]);
        double minSeconds = args.length > 5 ? Double.parseDouble(args[5]) : 3.0;
        readInputs(font);
        Files.createDirectories(Paths.get(out));
        com.sun.management.ThreadMXBean mx =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (String name : tasks) {
            Task task;
            int docs = 1;
            int w = warm, r = runs;
            long until = System.nanoTime() + (long) (minSeconds * 1e9);
            switch (name) {
            case "invoice": task = this::invoice; break;
            case "statement": task = this::statement; break;
            case "report": task = this::report; break;
            case "multilingual": task = this::multilingual; break;
            case "letters":
            case "svgletters":
                if (name.equals("svgletters") && svgLetter(1) == null) {
                    continue;
                }
                svgLogo = name.equals("svgletters");
                // Timed as the whole batch: 2 warm-up batches, 5 timed runs.
                task = this::letters;
                docs = LETTERS;
                w = Math.min(warm, 2);
                r = Math.min(runs, 5);
                until = 0;
                break;
            default: throw new IllegalArgumentException(name);
            }
            // At least w warm-up runs and min-seconds, so that each library's
            // code is compiled before it is timed.
            for (int i = 0; i < w || System.nanoTime() < until; i++) {
                task.call();
            }
            double[] ms = new double[r];
            long[] alloc = new long[r];
            byte[] pdf = null;
            for (int i = 0; i < r; i++) {
                long a = mx.getCurrentThreadAllocatedBytes();
                long s = System.nanoTime();
                pdf = task.call();
                ms[i] = (System.nanoTime() - s) / 1e6;
                alloc[i] = (mx.getCurrentThreadAllocatedBytes() - a) / docs;
            }
            Arrays.sort(ms);
            Arrays.sort(alloc);
            Files.write(Paths.get(out, lib + "-" + name + ".pdf"), pdf);
            System.out.printf(Locale.US, "%s %s %s median-ms %.2f min-ms %.2f max-ms %.2f bytes %d alloc-bytes %d%n",
                    lib, font, name, ms[r / 2], ms[0], ms[r - 1], pdf.length, alloc[r / 2]);
        }
    }
}
