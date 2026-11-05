import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.pdfbox.util.Matrix;
import org.w3c.dom.svg.SVGDocument;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

// Apache PDFBox 3: it has no layout, so its code does what its users write by
// hand, in the plainest common way: PDPageContentStream text at positions,
// words wrapped by measuring them with getStringWidth, table rows drawn as a
// gray rectangle, text and a line, new pages started when the next line or
// row does not fit. "Page n of N" footers are appended to every page after
// all the pages exist. Fonts: PDType0Font.load(doc, ttf, embedSubset =
// true) from a TrueTypeFont parsed once per process; images
// PDImageXObject.createFromByteArray.
public class PDFBoxFair extends Common {
    public static void main(String[] args) throws Exception {
        new PDFBoxFair().run("pdfbox", args);
    }

    // task helpers
    // The parsed fonts, by their bytes: each font file is parsed once per
    // process and the parsed font reused for every document, as PDFBox
    // documents PDType0Font.load(doc, TrueTypeFont, embedSubset) for it, and as
    // iText and OpenPDF cache their fonts by default.
    static final java.util.Map<byte[], TrueTypeFont> PARSED = new java.util.IdentityHashMap<>();

    static PDType0Font font(PDDocument doc, byte[] bytes) throws IOException {
        TrueTypeFont ttf = PARSED.get(bytes);
        if (ttf == null) {
            ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
            // No glyph substitution (GSUB), which the other three libraries do
            // not do either: PDFBox 3 shapes every showText with the font's
            // ligature tables by default, a cost that grows with the table
            // (Noto Sans: about 1.7 MB allocated per row of a table). Turned
            // off as a PDFBox user who wants speed does, for the same work
            // (the fairness review, 5 November 2026; the owner's choice).
            ttf.setEnableGsub(false);
            PARSED.put(bytes, ttf);
        }
        return PDType0Font.load(doc, ttf, true);
    }

    static float width(PDType0Font font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    static float ascent(PDType0Font font, float size) {
        return font.getFontDescriptor().getAscent() / 1000f * size;
    }

    // Draws text with its baseline at y from the top of the page.
    static void text(PDPageContentStream cs, PDType0Font font, float size, String text, float x, float y)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, H - y);
        cs.showText(text);
        cs.endText();
    }

    // Breaks text into lines no wider than width, at the spaces, adding up
    // the widths of the words.
    static List<String> wrap(PDType0Font font, float size, String text, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        float space = width(font, size, " ");
        StringBuilder line = new StringBuilder();
        float lineWidth = 0f;
        for (String word : text.split(" ")) {
            float w = width(font, size, word);
            if (line.length() > 0 && lineWidth + space + w > width) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0f;
            }
            if (line.length() > 0) {
                line.append(' ');
                lineWidth += space;
            }
            line.append(word);
            lineWidth += w;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    // Draws one table row with its top at y and returns the y under it.
    static float row(PDPageContentStream cs, PDType0Font font, String[] cells, float[] widths, boolean[] right,
            boolean header, float pad, float y) throws IOException {
        float h = TABLE_LEADING + 2 * pad;
        if (header) {
            cs.setNonStrokingColor(GRAY);
            cs.addRect(M, H - y - h, TEXT_W, h);
            cs.fill();
            cs.setNonStrokingColor(0f);
        }
        float x = M;
        float baseline = y + pad + ascent(font, TABLE);
        for (int c = 0; c < cells.length; c++) {
            if (!cells[c].isEmpty()) {
                float tx = right[c] ? x + widths[c] - SIDE_PAD - width(font, TABLE, cells[c]) : x + SIDE_PAD;
                text(cs, font, TABLE, cells[c], tx, baseline);
            }
            x += widths[c];
        }
        cs.setLineWidth(RULE);
        cs.moveTo(M, H - y - h);
        cs.lineTo(M + TEXT_W, H - y - h);
        cs.stroke();
        return y + h;
    }

    static void drawLogo(PDDocument doc, PDPageContentStream cs) throws IOException {
        PDImageXObject logo = PDImageXObject.createFromByteArray(doc, LOGO, "logo.png");
        cs.drawImage(logo, M, H - M - LOGO_H, LOGO_W, LOGO_H);
    }

    // Draws the footer on every page, now that the number of pages is known.
    static void footers(PDDocument doc, PDType0Font font, boolean ofTotal) throws IOException {
        int pages = doc.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            String footer = "Page " + (i + 1) + (ofTotal ? " of " + pages : "");
            try (PDPageContentStream cs = new PDPageContentStream(doc, doc.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                text(cs, font, FOOTER, footer, (W - width(font, FOOTER, footer)) / 2, FOOTER_BASELINE);
            }
        }
    }

    static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        doc.save(buf);
        return buf.toByteArray();
    }
    // end helpers

    // task invoice
    @Override byte[] invoice() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawLogo(doc, cs);
                text(cs, bold, HEADING, INVOICE_TITLE, M + LOGO_W + 12f, M + 36f);
                for (int i = 0; i < 4; i++) {
                    text(cs, regular, BODY, FROM[i], M, 140f + i * LEADING);
                    text(cs, regular, BODY, BILL_TO[i], BILL_TO_X, 140f + i * LEADING);
                }
                float y = row(cs, bold, INVOICE_HEADER, INVOICE_WIDTHS, INVOICE_RIGHT, true, INVOICE_PAD, 210f);
                for (String[] r : invoiceRows()) {
                    y = row(cs, regular, r, INVOICE_WIDTHS, INVOICE_RIGHT, false, INVOICE_PAD, y);
                }
                String[] totals = invoiceTotals();
                for (int i = 0; i < totals.length; i++) {
                    PDType0Font f = (i == 2) ? bold : regular;
                    text(cs, f, BODY, totals[i], W - M - width(f, BODY, totals[i]), y + 20f + i * LEADING);
                }
                text(cs, regular, FOOTER, INVOICE_FOOTER, (W - width(regular, FOOTER, INVOICE_FOOTER)) / 2,
                        INVOICE_FOOTER_BASELINE);
            }
            return save(doc);
        }
    }
    // end invoice

    // task statement
    @Override byte[] statement() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            text(cs, bold, HEADING, STATEMENT_TITLE, M, M + 16f);
            text(cs, regular, BODY, STATEMENT_SUBTITLE, M, M + 36f);
            float rowHeight = TABLE_LEADING + 2 * STATEMENT_PAD;
            float y = row(cs, bold, STATEMENT_HEADER, STATEMENT_WIDTHS, STATEMENT_RIGHT, true, STATEMENT_PAD, M + 50f);
            for (String[] r : statementRows()) {
                if (y + rowHeight > H - M) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = row(cs, bold, STATEMENT_HEADER, STATEMENT_WIDTHS, STATEMENT_RIGHT, true, STATEMENT_PAD, M);
                }
                y = row(cs, regular, r, STATEMENT_WIDTHS, STATEMENT_RIGHT, false, STATEMENT_PAD, y);
            }
            cs.close();
            footers(doc, regular, true);
            return save(doc);
        }
    }
    // end statement

    // task report
    @Override byte[] report() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            text(cs, bold, HEADING, REPORT_TITLE, M, M + 16f);
            float y = M + 24f;
            float bodyAscent = ascent(regular, BODY);
            for (int s = 1; s <= SECTIONS; s++) {
                if (y + 40f > H - M) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = M;
                }
                text(cs, bold, HEADING, sectionHeading(s), M, y + 28f);
                y += 40f;
                for (int p = 1; p <= PARAGRAPHS; p++) {
                    for (String line : wrap(regular, BODY, paragraph(s, p), TEXT_W)) {
                        if (y + LEADING > H - M) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            cs = new PDPageContentStream(doc, page);
                            y = M;
                        }
                        text(cs, regular, BODY, line, M, y + bodyAscent);
                        y += LEADING;
                    }
                    y += PARAGRAPH_GAP;
                }
                PDImageXObject photo = PDImageXObject.createFromByteArray(doc, PHOTOS[s - 1], PHOTO_FILES[s - 1]);
                float[] size = photoSize(photo.getWidth(), photo.getHeight());
                if (y + 6f + size[1] > H - M) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = M - 6f;
                }
                cs.drawImage(photo, M, H - y - 6f - size[1], size[0], size[1]);
                y += 6f + size[1];
                text(cs, regular, TABLE, caption(s), M, y + 12f);
                y += 16f;
            }
            cs.close();
            footers(doc, regular, false);
            return save(doc);
        }
    }
    // end report

    // task letters
    @Override byte[] letter(int n) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawLogo(doc, cs);
                text(cs, bold, HEADING, salutation(n), M, 160f);
                float y = 172f;
                float bodyAscent = ascent(regular, BODY);
                for (int p = 0; p < 3; p++) {
                    for (String line : wrap(regular, BODY, letterParagraph(n), TEXT_W)) {
                        text(cs, regular, BODY, line, M, y + bodyAscent);
                        y += LEADING;
                    }
                    y += PARAGRAPH_GAP;
                }
                text(cs, regular, BODY, "PDFjet Software", M, y + 20f);
            }
            return save(doc);
        }
    }
    // end letters

    // task multilingual
    @Override byte[] multilingual() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, bold, HEADING, NOTICE_TITLE, M, M + 16f);
                float y = M + 24f;
                for (String[] language : LANGUAGES) {
                    text(cs, bold, NAME_SIZE, language[0], M, y + ascent(bold, NAME_SIZE));
                    y += 1.3f * NAME_SIZE + PARAGRAPH_GAP;
                    for (String line : wrap(regular, BODY, language[1], TEXT_W)) {
                        text(cs, regular, BODY, line, M, y + ascent(regular, BODY));
                        y += LEADING;
                    }
                    y += PARAGRAPH_GAP;
                }
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            doc.save(buf);
            return buf.toByteArray();
        }
    }
    // end multilingual

    // The letters with the PDFjet logo in SVG: PDFBox does not draw SVG, so
    // Apache Batik reads it and paints it on PdfBoxGraphics2D (the
    // pdfbox-graphics2d bridge), a form XObject drawn where the logo goes:
    // the letter again, the logo the one difference.
    // task svgletters
    @Override byte[] svgLetter(int n) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType0Font regular = font(doc, REGULAR);
            PDType0Font bold = font(doc, BOLD);
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            SVGDocument svg = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
                    .createSVGDocument(null, new ByteArrayInputStream(SVG_LOGO));
            BridgeContext context = new BridgeContext(new UserAgentAdapter());
            GraphicsNode node = new GVTBuilder().build(context, svg);
            PdfBoxGraphics2D g = new PdfBoxGraphics2D(doc, LOGO_W, LOGO_H);
            g.scale(LOGO_W / context.getDocumentSize().getWidth(), LOGO_H / context.getDocumentSize().getHeight());
            node.paint(g);
            g.dispose();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                cs.transform(Matrix.getTranslateInstance(M, H - M - LOGO_H));
                cs.drawForm(g.getXFormObject());
                cs.restoreGraphicsState();
                text(cs, bold, HEADING, salutation(n), M, 160f);
                float y = 172f;
                float bodyAscent = ascent(regular, BODY);
                for (int p = 0; p < 3; p++) {
                    for (String line : wrap(regular, BODY, letterParagraph(n), TEXT_W)) {
                        text(cs, regular, BODY, line, M, y + bodyAscent);
                        y += LEADING;
                    }
                    y += PARAGRAPH_GAP;
                }
                text(cs, regular, BODY, "PDFjet Software", M, y + 20f);
            }
            return save(doc);
        }
    }
    // end svgletters
}
