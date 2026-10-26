import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;
import java.io.ByteArrayOutputStream;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

// OpenPDF 3: Document, Paragraph, PdfPTable (header row repeated; the
// statement added incomplete every 50 rows, as OpenPDF documents it for big
// tables), Image (scaleToFit for the photos). The running footers are a
// PdfPageEventHelper; "of N" is a PdfTemplate drawn on every page and
// filled in onCloseDocument, the documented way. Fonts:
// BaseFont.createFont(name, IDENTITY_H, EMBEDDED, cached = true, bytes,
// null), per document; OpenPDF embeds an Identity-H font as a subset.
public class OpenPDFFair extends Common {
    public static void main(String[] args) throws Exception {
        new OpenPDFFair().run("openpdf", args);
    }

    // task helpers
    static BaseFont font(byte[] bytes, String name) throws Exception {
        return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
    }

    static PdfPCell cell(String text, Font font, boolean right, boolean header, float pad) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        // The text from the ascender down to the descender, with the padding
        // grown so that a row is as tall as the other libraries' rows:
        // OpenPDF's ascent and descent of the font are its OS/2 typo values,
        // 1.0 em for Plex where the others take 1.3 em.
        cell.setLeading(TABLE_LEADING, 0f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        BaseFont bf = font.getBaseFont();
        float extra = (TABLE_LEADING - bf.getFontDescriptor(BaseFont.ASCENT, TABLE)
                + bf.getFontDescriptor(BaseFont.DESCENT, TABLE)) / 2;
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(RULE);
        cell.setPaddingTop(pad + extra);
        cell.setPaddingBottom(pad + extra);
        cell.setPaddingLeft(SIDE_PAD);
        cell.setPaddingRight(SIDE_PAD);
        if (header) {
            cell.setGrayFill(GRAY);
        }
        if (right) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        return cell;
    }

    static PdfPCell plain(PdfPCell cell) {
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        return cell;
    }

    // "Page n" or "Page n of N" at the foot of every page; N is written into
    // the template when the document closes.
    static final class Footer extends PdfPageEventHelper {
        final BaseFont font;
        final boolean ofTotal;
        PdfTemplate total;

        Footer(BaseFont font, boolean ofTotal) {
            this.font = font;
            this.ofTotal = ofTotal;
        }

        @Override public void onOpenDocument(PdfWriter writer, Document doc) {
            total = writer.getDirectContent().createTemplate(30f, 16f);
        }

        @Override public void onEndPage(PdfWriter writer, Document doc) {
            String text = "Page " + writer.getPageNumber() + (ofTotal ? " of " : "");
            float w = font.getWidthPoint(text, FOOTER) + (ofTotal ? font.getWidthPoint("00", FOOTER) : 0f);
            float x = (W - w) / 2, y = H - FOOTER_BASELINE;
            PdfContentByte cb = writer.getDirectContent();
            cb.beginText();
            cb.setFontAndSize(font, FOOTER);
            cb.setTextMatrix(x, y);
            cb.showText(text);
            cb.endText();
            if (ofTotal) {
                cb.addTemplate(total, x + font.getWidthPoint(text, FOOTER), y);
            }
        }

        @Override public void onCloseDocument(PdfWriter writer, Document doc) {
            if (ofTotal) {
                total.beginText();
                total.setFontAndSize(font, FOOTER);
                total.setTextMatrix(0f, 0f);
                total.showText(String.valueOf(writer.getPageNumber() - 1));
                total.endText();
            }
        }
    }
    // end helpers

    // task invoice
    @Override byte[] invoice() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter writer = PdfWriter.getInstance(doc, buf);
        doc.open();
        BaseFont regularBase = font(REGULAR, "Regular.ttf");
        Font regular = new Font(regularBase, BODY);
        Font bold = new Font(font(BOLD, "Bold.ttf"), BODY);
        Font headingFont = new Font(bold.getBaseFont(), HEADING);
        Font tableRegular = new Font(regularBase, TABLE);
        Font tableBold = new Font(bold.getBaseFont(), TABLE);

        PdfPTable head = new PdfPTable(new float[] {LOGO_W + 12f, TEXT_W - LOGO_W - 12f});
        head.setTotalWidth(TEXT_W);
        head.setLockedWidth(true);
        Image logo = Image.getInstance(LOGO);
        logo.scaleAbsolute(LOGO_W, LOGO_H);
        head.addCell(plain(new PdfPCell(logo, false)));
        PdfPCell title = plain(new PdfPCell(new Phrase(INVOICE_TITLE, headingFont)));
        title.setVerticalAlignment(Element.ALIGN_MIDDLE);
        head.addCell(title);
        doc.add(head);

        PdfPTable parties = new PdfPTable(new float[] {BILL_TO_X - M, W - M - BILL_TO_X});
        parties.setTotalWidth(TEXT_W);
        parties.setLockedWidth(true);
        parties.setSpacingBefore(16f);
        for (String[] lines : new String[][] {FROM, BILL_TO}) {
            PdfPCell party = plain(new PdfPCell(new Phrase(String.join("\n", lines), regular)));
            party.setLeading(LEADING, 0f);
            parties.addCell(party);
        }
        doc.add(parties);

        PdfPTable table = new PdfPTable(INVOICE_WIDTHS);
        table.setTotalWidth(TEXT_W);
        table.setLockedWidth(true);
        table.setSpacingBefore(32f);
        table.setHeaderRows(1);
        for (int c = 0; c < INVOICE_HEADER.length; c++) {
            table.addCell(cell(INVOICE_HEADER[c], tableBold, INVOICE_RIGHT[c], true, INVOICE_PAD));
        }
        for (String[] row : invoiceRows()) {
            for (int c = 0; c < row.length; c++) {
                table.addCell(cell(row[c], tableRegular, INVOICE_RIGHT[c], false, INVOICE_PAD));
            }
        }
        doc.add(table);

        String[] totals = invoiceTotals();
        for (int i = 0; i < totals.length; i++) {
            Paragraph total = new Paragraph(LEADING, totals[i], i == 2 ? bold : regular);
            total.setAlignment(Element.ALIGN_RIGHT);
            total.setSpacingBefore(i == 0 ? 10f : 0f);
            doc.add(total);
        }
        ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                new Phrase(INVOICE_FOOTER, new Font(regularBase, FOOTER)), W / 2, H - INVOICE_FOOTER_BASELINE, 0);
        doc.close();
        return buf.toByteArray();
    }
    // end invoice

    // task statement
    @Override byte[] statement() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter writer = PdfWriter.getInstance(doc, buf);
        BaseFont regularBase = font(REGULAR, "Regular.ttf");
        BaseFont boldBase = font(BOLD, "Bold.ttf");
        writer.setPageEvent(new Footer(regularBase, true));
        doc.open();
        doc.add(new Paragraph(HEADING, STATEMENT_TITLE, new Font(boldBase, HEADING)));
        Paragraph subtitle = new Paragraph(LEADING, STATEMENT_SUBTITLE, new Font(regularBase, BODY));
        subtitle.setSpacingBefore(7f);
        subtitle.setSpacingAfter(15f);
        doc.add(subtitle);

        Font regular = new Font(regularBase, TABLE);
        Font bold = new Font(boldBase, TABLE);
        PdfPTable table = new PdfPTable(STATEMENT_WIDTHS);
        table.setTotalWidth(TEXT_W);
        table.setLockedWidth(true);
        table.setHeaderRows(1);
        table.setComplete(false);
        for (int c = 0; c < STATEMENT_HEADER.length; c++) {
            table.addCell(cell(STATEMENT_HEADER[c], bold, STATEMENT_RIGHT[c], true, STATEMENT_PAD));
        }
        String[][] rows = statementRows();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                table.addCell(cell(rows[r][c], regular, STATEMENT_RIGHT[c], false, STATEMENT_PAD));
            }
            if (r % 50 == 49) {
                doc.add(table);
            }
        }
        table.setComplete(true);
        doc.add(table);
        doc.close();
        return buf.toByteArray();
    }
    // end statement

    // task report
    @Override byte[] report() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter writer = PdfWriter.getInstance(doc, buf);
        // An image that does not fit goes to the next page, and the text after
        // it stays after it (OpenPDF's default moves the text up before it).
        writer.setStrictImageSequence(true);
        BaseFont regularBase = font(REGULAR, "Regular.ttf");
        BaseFont boldBase = font(BOLD, "Bold.ttf");
        writer.setPageEvent(new Footer(regularBase, false));
        doc.open();
        Font heading = new Font(boldBase, HEADING);
        Font body = new Font(regularBase, BODY);
        Font small = new Font(regularBase, TABLE);
        doc.add(new Paragraph(HEADING, REPORT_TITLE, heading));
        for (int s = 1; s <= SECTIONS; s++) {
            Paragraph h = new Paragraph(20f, sectionHeading(s), heading);
            h.setSpacingBefore(12f);
            h.setSpacingAfter(6f);
            doc.add(h);
            for (int p = 1; p <= PARAGRAPHS; p++) {
                Paragraph para = new Paragraph(LEADING, paragraph(s, p), body);
                para.setSpacingAfter(PARAGRAPH_GAP);
                doc.add(para);
            }
            Image photo = Image.getInstance(PHOTOS[s - 1]);
            photo.scaleToFit(TEXT_W, PHOTO_MAX_H);
            photo.setSpacingBefore(6f);
            doc.add(photo);
            doc.add(new Paragraph(12f, caption(s), small));
        }
        doc.close();
        return buf.toByteArray();
    }
    // end report

    // task letters
    @Override byte[] letter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter.getInstance(doc, buf);
        doc.open();
        Font body = new Font(font(REGULAR, "Regular.ttf"), BODY);
        Font heading = new Font(font(BOLD, "Bold.ttf"), HEADING);
        Image logo = Image.getInstance(LOGO);
        logo.scaleAbsolute(LOGO_W, LOGO_H);
        doc.add(logo);
        Paragraph salutation = new Paragraph(20f, salutation(n), heading);
        salutation.setSpacingBefore(30f);
        salutation.setSpacingAfter(6f);
        doc.add(salutation);
        for (int p = 0; p < 3; p++) {
            Paragraph para = new Paragraph(LEADING, letterParagraph(n), body);
            para.setSpacingAfter(PARAGRAPH_GAP);
            doc.add(para);
        }
        Paragraph signature = new Paragraph(LEADING, "PDFjet Software", body);
        signature.setSpacingBefore(14f);
        doc.add(signature);
        doc.close();
        return buf.toByteArray();
    }
    // end letters

    // task multilingual
    @Override byte[] multilingual() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter.getInstance(doc, buf);
        BaseFont regularBase = font(REGULAR, "Regular.ttf");
        BaseFont boldBase = font(BOLD, "Bold.ttf");
        doc.open();
        Font name = new Font(boldBase, NAME_SIZE);
        Font body = new Font(regularBase, BODY);
        Paragraph title = new Paragraph(HEADING, NOTICE_TITLE, new Font(boldBase, HEADING));
        title.setSpacingAfter(6f);
        doc.add(title);
        for (String[] language : LANGUAGES) {
            Paragraph heading = new Paragraph(1.3f * NAME_SIZE, language[0], name);
            heading.setSpacingAfter(PARAGRAPH_GAP);
            doc.add(heading);
            Paragraph text = new Paragraph(LEADING, language[1], body);
            text.setSpacingAfter(PARAGRAPH_GAP);
            doc.add(text);
        }
        doc.close();
        return buf.toByteArray();
    }
    // end multilingual

    // The letters with the PDFjet logo in SVG: OpenPDF does not draw SVG, so
    // Apache Batik reads it and paints it on OpenPDF's PdfGraphics2D, the way
    // OpenPDF's users do it: the letter again, the logo the one difference.
    // task svgletters
    @Override byte[] svgLetter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, M, M, M, M);
        PdfWriter writer = PdfWriter.getInstance(doc, buf);
        doc.open();
        Font body = new Font(font(REGULAR, "Regular.ttf"), BODY);
        Font heading = new Font(font(BOLD, "Bold.ttf"), HEADING);
        SVGDocument svg = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
                .createSVGDocument(null, new ByteArrayInputStream(SVG_LOGO));
        BridgeContext context = new BridgeContext(new UserAgentAdapter());
        GraphicsNode node = new GVTBuilder().build(context, svg);
        PdfTemplate template = writer.getDirectContent().createTemplate(LOGO_W, LOGO_H);
        Graphics2D g = template.createGraphics(LOGO_W, LOGO_H);
        g.scale(LOGO_W / context.getDocumentSize().getWidth(), LOGO_H / context.getDocumentSize().getHeight());
        node.paint(g);
        g.dispose();
        doc.add(Image.getInstance(template));
        Paragraph salutation = new Paragraph(20f, salutation(n), heading);
        salutation.setSpacingBefore(30f);
        salutation.setSpacingAfter(6f);
        doc.add(salutation);
        for (int p = 0; p < 3; p++) {
            Paragraph para = new Paragraph(LEADING, letterParagraph(n), body);
            para.setSpacingAfter(PARAGRAPH_GAP);
            doc.add(para);
        }
        Paragraph signature = new Paragraph(LEADING, "PDFjet Software", body);
        signature.setSpacingBefore(14f);
        doc.add(signature);
        doc.close();
        return buf.toByteArray();
    }
    // end svgletters
}
