import java.io.*;
import java.util.*;
import com.pdfjet.*;

// PDFjet, from its source: Table with a header row for the invoice and the
// statement (Table.drawOn(pdf, first, pages, ...) breaks the rows onto new
// pages, and the footers are added to the pages it returns), TextFrame for
// the flowing paragraphs of the report and the letters, TextLine for the
// headings and single lines, Image for the logo and the photos. Every Font
// object is made from the font bytes, per document, as a server does;
// PDFjet embeds a font as a subset of the glyphs drawn by default.
public class PDFjetFair extends Common {
    public static void main(String[] args) throws Exception {
        new PDFjetFair().run("pdfjet", args);
    }

    // A PDF, its pages uncompressed when NOCOMPRESS is set: for the run that
    // measures the code without the compression of the pages. Not counted in
    // the lines of code, as the tasks would write new PDF(buf).
    static PDF newPDF(ByteArrayOutputStream buf) throws Exception {
        PDF pdf = new PDF(buf);
        if (System.getenv("NOCOMPRESS") != null) {
            pdf.setCompression(false);
        }
        return pdf;
    }

    // task helpers
    static Font font(PDF pdf, byte[] bytes) throws Exception {
        return new Font(pdf, new ByteArrayInputStream(bytes));
    }

    static TextLine line(Font font, float size, String text, float x, float y) {
        return new TextLine(font, text).setFontSize(size).setLocation(x, y);
    }

    static Cell cell(Font font, String text, boolean right, boolean header, float pad) {
        Cell cell = new Cell(font, text).setFontSize(TABLE);
        cell.setTopPadding(pad).setBottomPadding(pad).setLeftPadding(SIDE_PAD).setRightPadding(SIDE_PAD);
        cell.setBorders(false).setBorder(Border.BOTTOM, true).setBorderWidth(RULE);
        if (header) {
            cell.setBackgroundColor(0xE6E6E6);
        }
        if (right) {
            cell.setTextAlignment(Alignment.RIGHT);
        }
        return cell;
    }

    static List<List<Cell>> tableData(Font regular, Font bold, String[] header, String[][] rows, float[] widths,
            boolean[] right, float pad) {
        List<List<Cell>> data = new ArrayList<>();
        List<Cell> headerRow = new ArrayList<>();
        for (int c = 0; c < header.length; c++) {
            headerRow.add(cell(bold, header[c], right[c], true, pad)
                    .setWidth(widths[c]));
        }
        data.add(headerRow);
        for (String[] r : rows) {
            List<Cell> row = new ArrayList<>();
            for (int c = 0; c < r.length; c++) {
                row.add(cell(regular, r[c], right[c], false, pad)
                        .setWidth(widths[c]));
            }
            data.add(row);
        }
        return data;
    }
    // end helpers

    // task invoice
    @Override byte[] invoice() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);

        Image logo = new Image(pdf, LOGO);
        logo.resizeWidth(LOGO_W);
        logo.setLocation(M, M);
        logo.drawOn(page);
        line(bold, HEADING, INVOICE_TITLE, M + LOGO_W + 12f, M + 36f).drawOn(page);
        for (int i = 0; i < 4; i++) {
            line(regular, BODY, FROM[i], M, 140f + i * LEADING).drawOn(page);
            line(regular, BODY, BILL_TO[i], BILL_TO_X, 140f + i * LEADING).drawOn(page);
        }

        Table table = new Table();
        table.setTableData(tableData(regular, bold, INVOICE_HEADER, invoiceRows(), INVOICE_WIDTHS, INVOICE_RIGHT,
                INVOICE_PAD), 1);
        table.setLocation(M, 210f);
        float[] end = table.drawOn(page);

        String[] totals = invoiceTotals();
        for (int i = 0; i < totals.length; i++) {
            Font f = (i == 2) ? bold : regular;
            TextLine total = new TextLine(f, totals[i]).setFontSize(BODY);
            total.setLocation(W - M - total.getWidth(), end[1] + 20f + i * LEADING).drawOn(page);
        }
        TextLine footer = new TextLine(regular, INVOICE_FOOTER).setFontSize(FOOTER);
        footer.setLocation((W - footer.getWidth()) / 2, INVOICE_FOOTER_BASELINE).drawOn(page);
        pdf.complete();
        return buf.toByteArray();
    }
    // end invoice

    // task statement
    @Override byte[] statement() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        Page first = new Page(pdf, Letter.PORTRAIT, Page.DETACHED);
        line(bold, HEADING, STATEMENT_TITLE, M, M + 16f).drawOn(first);
        line(regular, BODY, STATEMENT_SUBTITLE, M, M + 36f).drawOn(first);

        Table table = new Table();
        table.setTableData(tableData(regular, bold, STATEMENT_HEADER, statementRows(), STATEMENT_WIDTHS,
                STATEMENT_RIGHT, STATEMENT_PAD), 1);
        table.setLocation(M, M);
        table.setFirstPageTopMargin(M + 50f);
        table.setBottomMargin(M);
        List<Page> pages = new ArrayList<>();
        pages.add(first);
        table.drawOn(pdf, first, pages, Letter.PORTRAIT);
        for (int i = 0; i < pages.size(); i++) {
            TextLine footer = new TextLine(regular, "Page " + (i + 1) + " of " + pages.size()).setFontSize(FOOTER);
            pages.get(i).addFooter(footer, H - FOOTER_BASELINE);
        }
        pdf.addPages(pages);
        pdf.complete();
        return buf.toByteArray();
    }
    // end statement

    // task report
    @Override byte[] report() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        TextFlow flow = new TextFlow(pdf, Letter.PORTRAIT).setMargins(M);
        flow.add(new Paragraph(new TextLine(bold, REPORT_TITLE).setFontSize(HEADING)));
        for (int s = 1; s <= SECTIONS; s++) {
            flow.addSpace(16f);
            flow.add(new Paragraph(new TextLine(bold, sectionHeading(s)).setFontSize(HEADING))).addSpace(4f);
            List<Paragraph> paragraphs = new ArrayList<>();
            for (int p = 1; p <= PARAGRAPHS; p++) {
                paragraphs.add(new Paragraph(new TextLine(regular, paragraph(s, p)).setFontSize(BODY)));
            }
            flow.add(paragraphs, PARAGRAPH_GAP).addSpace(12f);
            Image photo = new Image(pdf, PHOTOS[s - 1]);
            photo.resizeWidth(TEXT_W);
            if (photo.getHeight() > PHOTO_MAX_H) {
                photo.resizeHeight(PHOTO_MAX_H);
            }
            flow.add(photo).addSpace(4f);
            flow.add(new Paragraph(new TextLine(regular, caption(s)).setFontSize(TABLE)));
        }
        List<Page> pages = new ArrayList<>();
        flow.drawOn(pages);
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).addFooter(new TextLine(regular, "Page " + (i + 1)).setFontSize(FOOTER), H - FOOTER_BASELINE);
        }
        pdf.addPages(pages);
        pdf.complete();
        return buf.toByteArray();
    }
    // end report

    // task letters
    @Override byte[] letter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        Image logo = new Image(pdf, LOGO);
        logo.resizeWidth(LOGO_W);
        logo.setLocation(M, M).drawOn(page);
        line(bold, HEADING, salutation(n), M, 160f).drawOn(page);
        List<Paragraph> paragraphs = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            paragraphs.add(new Paragraph(new TextLine(regular, letterParagraph(n)).setFontSize(BODY)));
        }
        float[] xy = new TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
                .setLocation(M, 172f).setWidth(TEXT_W).drawOn(page);
        line(regular, BODY, "PDFjet Software", M, xy[1] + 26f).drawOn(page);
        pdf.complete();
        return buf.toByteArray();
    }
    // end letters

    // The letters with the PDFjet logo in SVG, drawn as paths: the letters
    // again, the logo the one difference.
    // task svgletters
    @Override byte[] svgLetter(int n) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        SVGImage logo = new SVGImage(new ByteArrayInputStream(SVG_LOGO));
        logo.scaleBy(LOGO_W / logo.getWidth());
        logo.setLocation(M, M).drawOn(page);
        line(bold, HEADING, salutation(n), M, 160f).drawOn(page);
        List<Paragraph> paragraphs = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            paragraphs.add(new Paragraph(new TextLine(regular, letterParagraph(n)).setFontSize(BODY)));
        }
        float[] xy = new TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
                .setLocation(M, 172f).setWidth(TEXT_W).drawOn(page);
        line(regular, BODY, "PDFjet Software", M, xy[1] + 26f).drawOn(page);
        pdf.complete();
        return buf.toByteArray();
    }
    // end svgletters

    // task multilingual
    @Override byte[] multilingual() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PDF pdf = newPDF(buf);
        Font regular = font(pdf, REGULAR);
        Font bold = font(pdf, BOLD);
        Page page = new Page(pdf, Letter.PORTRAIT);
        line(bold, HEADING, NOTICE_TITLE, M, M + 16f).drawOn(page);
        List<Paragraph> paragraphs = new ArrayList<>();
        for (String[] language : LANGUAGES) {
            paragraphs.add(new Paragraph(new TextLine(bold, language[0]).setFontSize(NAME_SIZE)));
            paragraphs.add(new Paragraph(new TextLine(regular, language[1]).setFontSize(BODY)));
        }
        new TextFrame(paragraphs).setParagraphGap(PARAGRAPH_GAP)
                .setLocation(M, M + 24f).setWidth(TEXT_W).drawOn(page);
        pdf.complete();
        return buf.toByteArray();
    }
    // end multilingual
}
