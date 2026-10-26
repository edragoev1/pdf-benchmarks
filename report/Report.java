/*
 * Report.java
 *
 * The report of the benchmark, as a landscape PDF made with PDFjet: a first
 * page of bar charts, one for each document, the best of each in navy, and
 * after it the article, drawn from its Markdown. The figures of the charts
 * come from charts.txt and the tables of the article from the raw results,
 * both written by make-tables.py, so that no figure is typed by hand.
 *
 *   ./make-report.sh
 */
import com.pdfjet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Report {
    static final float W = 792f, H = 612f, M = 48f, TW = W - 2 * M;
    static final int INK = 0x22262B, MUTED = 0x5F6873, NAVY = 0x1F3A5F;
    static final String FONTS = System.getProperty("user.home") + "/Projects/pdfjet/fonts/";

    static Font font(PDF pdf, String file, float size) throws Exception {
        Font font = new Font(pdf, new FileInputStream(FONTS + file));
        font.setSize(size);
        return font;
    }

    public static void main(String[] args) throws Exception {
        String markdownFile = args[0], chartsFile = args[1], out = args[2];
        PDF pdf = new PDF(new BufferedOutputStream(new FileOutputStream(out)));
        pdf.setTitle("Generating PDFs in Java: a reproducible benchmark of four libraries");

        List<Page> pages = new ArrayList<>();
        pages.add(chartsPage(pdf, Files.readAllLines(Paths.get(chartsFile), StandardCharsets.UTF_8)));

        // The article, in the fonts and colors of md2pdf, in a column of
        // reading width
        float size = 10.5f;
        Markdown markdown = new Markdown(
                font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", size),
                font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", size),
                font(pdf, "IBMPlexSans/IBMPlexSans-Italic.ttf", size),
                font(pdf, "IBMPlexSans/IBMPlexSans-SemiBoldItalic.ttf", size),
                font(pdf, "IBMPlexMono/IBMPlexMono-Regular.ttf", size * 0.86f))
                .setHeadingColor(NAVY)
                .setLinkColor(0x0969DA)
                .setFullWidthTables(true)
                .setMargins(96f, 48f, 96f, 54f);
        String text = new String(Files.readAllBytes(Paths.get(markdownFile)), StandardCharsets.UTF_8);
        markdown.drawOn(pdf, text, pages, Letter.LANDSCAPE);

        Font small = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 8f);
        for (int i = 0; i < pages.size(); i++) {
            TextLine number = new TextLine(small, (i + 1) + " / " + pages.size()).setTextColor(MUTED);
            pages.get(i).addFooter(number, 30f);
        }
        pdf.addPages(pages);
        pdf.complete();
    }

    // The first page: the title, who made the benchmark, and a bar chart for
    // each line of charts.txt, five to a row, the best of each highlighted.
    static Page chartsPage(PDF pdf, List<String> charts) throws Exception {
        Page page = new Page(pdf, Letter.LANDSCAPE, Page.DETACHED);
        Font title = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 22f);
        Font semi = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 10f);
        Font regular = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 8.5f);
        Font note = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 10f);

        page.setBrushColor(NAVY);
        page.fillRect(M, M - 6f, 36f, 4f);
        new TextLine(title, "Generating PDFs in Java: four libraries compared").setTextColor(INK)
                .setStructureType(StructElem.H1).setLocation(M, M + 22f).drawOn(page);
        List<Paragraph> intro = new ArrayList<>();
        intro.add(new Paragraph(new TextLine(note, "Five business documents and a batch of letters, made by "
                + "each library the way its own documentation shows, every PDF checked, in IBM Plex Sans. "
                + "Lower is better in every chart, and the best is in navy. Written by the author of "
                + "PDFjet, with everything needed to run it again published; the tables, the method and "
                + "where PDFjet loses are on the pages after this one.").setTextColor(MUTED)));
        new TextFrame(intro).setLocation(M, M + 34f).setWidth(TW).drawOn(page);

        float gap = 12f;
        int perRow = 5;
        float cw = (TW - (perRow - 1) * gap) / perRow;
        float top = M + 84f;
        float rowBottom = top;
        for (int c = 0; c < charts.size(); c++) {
            String[] fields = charts.get(c).split("\\|");
            if (c % perRow == 0 && c > 0) {
                top = rowBottom + gap;
            }
            float x = M + (c % perRow) * (cw + gap);
            boolean seconds = fields[1].contains(", s");
            SimpleBarChart chart = new SimpleBarChart(semi, regular)
                    .setTitle(fields[0]).setSubtitle(fields[1])
                    .setFractionDigits(seconds ? 2 : (fields[1].equals("KB") || fields[0].equals("Lines of code")) ? 0 : 1);
            int best = 0;
            float bestValue = Float.MAX_VALUE;
            for (int i = 2; i < fields.length; i++) {
                String[] part = fields[i].split("=", 3);
                float value = Float.parseFloat(part[1]);
                chart.add(part[0], value);
                if (value < bestValue) {
                    bestValue = value;
                    best = i - 2;
                }
            }
            chart.setHighlight(best).setLocation(x, top).setWidth(cw);
            float[] xy = chart.drawOn(page);
            rowBottom = Math.max(rowBottom, xy[1]);
        }
        summary(page, rowBottom + 24f, semi, note);
        return page;
    }

    // When to use which, in short, in two columns under the charts.
    static void summary(Page page, float y, Font semi, Font note) throws Exception {
        String[][] uses = {
            {"Apache PDFBox", "to read, edit, render or extract text from existing PDFs, and for the "
                    + "smallest files. The most used of the four."},
            {"iText", "for the most features in one library, and the shortest code for flowing "
                    + "documents, under the AGPL or a commercial license."},
            {"OpenPDF", "for multilingual text, a large font and SVG with Batik, under the LGPL "
                    + "or MPL, on Java 21."},
            {"PDFjet", "to generate business documents fast, with a fast first PDF, tagged PDF/UA, "
                    + "SVG and barcodes in one jar, under MIT, in Java, C#, Go and Swift."},
        };
        new TextLine(semi, "When to use which").setFontSize(13f).setTextColor(INK)
                .setStructureType(StructElem.H2).setLocation(M, y).drawOn(page);
        float colW = (TW - 24f) / 2f;
        for (int i = 0; i < uses.length; i++) {
            float x = M + (i % 2) * (colW + 24f);
            float top = y + 14f + (i / 2) * 44f;
            List<Paragraph> paragraphs = new ArrayList<>();
            Paragraph p = new Paragraph();
            p.add(new TextLine(semi, uses[i][0] + ": ").setFontSize(10f).setTextColor(NAVY));
            p.add(new TextLine(note, uses[i][1]).setTextColor(INK));
            paragraphs.add(p);
            new TextFrame(paragraphs).setLocation(x, top).setWidth(colW).drawOn(page);
        }
    }
}
