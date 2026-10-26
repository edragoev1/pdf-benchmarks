import java.io.ByteArrayInputStream;
import com.itextpdf.svg.converter.SvgConverter;
import java.io.ByteArrayOutputStream;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;

// iText Core 9 with its layout module: Document, Paragraph, Table (header
// cells repeated on every page; the statement in large-table mode, added to
// the Document first and flushed every 50 rows, as iText documents it for
// big tables), Image (scaleToFit for the photos). The running footers are an
// END_PAGE event handler; "of N" is iText's documented "Page X of Y"
// approach: a PdfFormXObject placeholder drawn on every page and filled with
// the number of pages just before the document is closed (the PageXofY
// sample). Fonts: PdfFontFactory.createFont(bytes, IDENTITY_H,
// PREFER_EMBEDDED), embedded as subsets by default, per document.
public class ITextFair extends Common {
    public static void main(String[] args) throws Exception {
        new ITextFair().run("itext", args);
    }

    // task helpers
    static PdfFont font(byte[] bytes) throws Exception {
        return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }

    static Paragraph text(String text, PdfFont font, float size) {
        return new Paragraph(text).setFont(font).setFontSize(size);
    }

    static Cell cell(String text, PdfFont font, boolean right, boolean header, float pad) {
        Cell cell = new Cell().add(text(text, font, TABLE).setFixedLeading(TABLE_LEADING).setMargin(0));
        cell.setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(RULE));
        // iText adds the width of the bottom border to the height of the row,
        // which the other libraries draw across their rows: the bottom
        // padding gives it back, so that the rows are as tall as theirs.
        cell.setPaddingTop(pad).setPaddingBottom(pad - RULE).setPaddingLeft(SIDE_PAD).setPaddingRight(SIDE_PAD);
        if (header) {
            cell.setBackgroundColor(new DeviceGray(GRAY));
        }
        if (right) {
            cell.setTextAlignment(TextAlignment.RIGHT);
        }
        return cell;
    }

    // "Page n" or "Page n of N" at the foot of every page; for "of N", the
    // number of pages goes into the placeholder when writeTotal is called.
    static final class Footer extends AbstractPdfDocumentEventHandler {
        final PdfFont font;
        final PdfFormXObject total;

        Footer(PdfFont font, boolean ofTotal) {
            this.font = font;
            this.total = ofTotal ? new PdfFormXObject(new Rectangle(0, 0, 30, 16)) : null;
        }

        @Override protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            String text = "Page " + pdf.getPageNumber(page) + (total != null ? " of " : "");
            float w = font.getWidth(text, FOOTER) + (total != null ? font.getWidth("00", FOOTER) : 0);
            float x = (W - w) / 2, y = H - FOOTER_BASELINE;
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
            canvas.beginText().setFontAndSize(font, FOOTER).moveText(x, y).showText(text).endText();
            if (total != null) {
                canvas.addXObjectAt(total, x + font.getWidth(text, FOOTER), y - 4f);
            }
            canvas.release();
        }

        void writeTotal(PdfDocument pdf) {
            new PdfCanvas(total, pdf).beginText().setFontAndSize(font, FOOTER).moveText(0, 4f)
                    .showText(String.valueOf(pdf.getNumberOfPages())).endText();
        }
    }
    // end helpers

    // task invoice
    @Override byte[] invoice() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);

        Table head = new Table(new float[] {LOGO_W + 12f, TEXT_W - LOGO_W - 12f});
        head.addCell(new Cell().add(new Image(ImageDataFactory.create(LOGO)).scaleAbsolute(LOGO_W, LOGO_H))
                .setBorder(Border.NO_BORDER).setPadding(0));
        head.addCell(new Cell().add(text(INVOICE_TITLE, bold, HEADING)).setBorder(Border.NO_BORDER)
                .setPadding(0).setVerticalAlignment(VerticalAlignment.MIDDLE));
        doc.add(head);

        Table parties = new Table(new float[] {BILL_TO_X - M, W - M - BILL_TO_X}).setMarginTop(19f);
        parties.addCell(new Cell().add(text(String.join("\n", FROM), regular, BODY).setFixedLeading(LEADING))
                .setBorder(Border.NO_BORDER).setPadding(0));
        parties.addCell(new Cell().add(text(String.join("\n", BILL_TO), regular, BODY).setFixedLeading(LEADING))
                .setBorder(Border.NO_BORDER).setPadding(0));
        doc.add(parties);

        Table table = new Table(INVOICE_WIDTHS).setMarginTop(28f);
        for (int c = 0; c < INVOICE_HEADER.length; c++) {
            table.addHeaderCell(cell(INVOICE_HEADER[c], bold, INVOICE_RIGHT[c], true, INVOICE_PAD));
        }
        for (String[] row : invoiceRows()) {
            for (int c = 0; c < row.length; c++) {
                table.addCell(cell(row[c], regular, INVOICE_RIGHT[c], false, INVOICE_PAD));
            }
        }
        doc.add(table);

        String[] totals = invoiceTotals();
        for (int i = 0; i < totals.length; i++) {
            doc.add(text(totals[i], i == 2 ? bold : regular, BODY).setFixedLeading(LEADING)
                    .setTextAlignment(TextAlignment.RIGHT).setMargin(0).setMarginTop(i == 0 ? 10f : 0f));
        }
        doc.showTextAligned(text(INVOICE_FOOTER, regular, FOOTER), W / 2, H - INVOICE_FOOTER_BASELINE - 2f,
                TextAlignment.CENTER);
        doc.close();
        return buf.toByteArray();
    }
    // end invoice

    // task statement
    @Override byte[] statement() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);
        Footer footer = new Footer(regular, true);
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, footer);
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        doc.add(text(STATEMENT_TITLE, bold, HEADING).setMargin(0));
        doc.add(text(STATEMENT_SUBTITLE, regular, BODY).setMargin(0).setMarginBottom(10f));

        Table table = new Table(STATEMENT_WIDTHS, true);
        for (int c = 0; c < STATEMENT_HEADER.length; c++) {
            table.addHeaderCell(cell(STATEMENT_HEADER[c], bold, STATEMENT_RIGHT[c], true, STATEMENT_PAD));
        }
        doc.add(table);
        String[][] rows = statementRows();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                table.addCell(cell(rows[r][c], regular, STATEMENT_RIGHT[c], false, STATEMENT_PAD));
            }
            if (r % 50 == 49) {
                table.flush();
            }
        }
        table.complete();
        footer.writeTotal(pdf);
        doc.close();
        return buf.toByteArray();
    }
    // end statement

    // task report
    @Override byte[] report() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new Footer(regular, false));
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        doc.add(text(REPORT_TITLE, bold, HEADING).setMargin(0));
        for (int s = 1; s <= SECTIONS; s++) {
            doc.add(text(sectionHeading(s), bold, HEADING).setMargin(0).setMarginTop(12f).setMarginBottom(6f));
            for (int p = 1; p <= PARAGRAPHS; p++) {
                doc.add(text(paragraph(s, p), regular, BODY).setFixedLeading(LEADING).setMargin(0)
                        .setMarginBottom(PARAGRAPH_GAP));
            }
            doc.add(new Image(ImageDataFactory.create(PHOTOS[s - 1])).scaleToFit(TEXT_W, PHOTO_MAX_H)
                    .setMarginTop(6f));
            doc.add(text(caption(s), regular, TABLE).setMargin(0).setMarginTop(2f));
        }
        doc.close();
        return buf.toByteArray();
    }
    // end report

    // task letters
    @Override byte[] letter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);
        doc.add(new Image(ImageDataFactory.create(LOGO)).scaleAbsolute(LOGO_W, LOGO_H));
        doc.add(text(salutation(n), bold, HEADING).setMargin(0).setMarginTop(30f).setMarginBottom(6f));
        for (int p = 0; p < 3; p++) {
            doc.add(text(letterParagraph(n), regular, BODY).setFixedLeading(LEADING).setMargin(0)
                    .setMarginBottom(PARAGRAPH_GAP));
        }
        doc.add(text("PDFjet Software", regular, BODY).setMargin(0).setMarginTop(14f));
        doc.close();
        return buf.toByteArray();
    }
    // end letters

    // task multilingual
    @Override byte[] multilingual() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        doc.add(text(NOTICE_TITLE, bold, HEADING).setMargin(0).setMarginBottom(6f));
        for (String[] language : LANGUAGES) {
            doc.add(text(language[0], bold, NAME_SIZE).setFixedLeading(1.3f * NAME_SIZE).setMargin(0)
                    .setMarginBottom(PARAGRAPH_GAP));
            doc.add(text(language[1], regular, BODY).setFixedLeading(LEADING).setMargin(0)
                    .setMarginBottom(PARAGRAPH_GAP));
        }
        doc.close();
        return buf.toByteArray();
    }
    // end multilingual

    // The letters with the PDFjet logo in SVG, drawn with iText's svg module
    // (com.itextpdf:svg): the letter again, the logo the one difference.
    // task svgletters
    @Override byte[] svgLetter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(buf, new WriterProperties().setFullCompressionMode(true)));
        Document doc = new Document(pdf, PageSize.LETTER);
        doc.setMargins(M, M, M, M);
        PdfFont regular = font(REGULAR);
        PdfFont bold = font(BOLD);
        doc.add(SvgConverter.convertToImage(new ByteArrayInputStream(SVG_LOGO), pdf).scaleAbsolute(LOGO_W, LOGO_H));
        doc.add(text(salutation(n), bold, HEADING).setMargin(0).setMarginTop(30f).setMarginBottom(6f));
        for (int p = 0; p < 3; p++) {
            doc.add(text(letterParagraph(n), regular, BODY).setFixedLeading(LEADING).setMargin(0)
                    .setMarginBottom(PARAGRAPH_GAP));
        }
        doc.add(text("PDFjet Software", regular, BODY).setMargin(0).setMarginTop(14f));
        doc.close();
        return buf.toByteArray();
    }
    // end svgletters
}
