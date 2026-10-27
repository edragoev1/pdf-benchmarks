/*
 * Report.java
 *
 * The report of the benchmark, as a landscape PDF made with PDFjet: pages of
 * bar charts, one for each measurement, the best of each in navy, and after
 * them the article, drawn from its Markdown, with its tables in an appendix. The figures of the charts
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
        // charts.txt: the charts of the first page, then for each other page a
        // line "#page|heading" and its charts; "#caption" starts a row
        List<List<String>> groups = new ArrayList<>();
        groups.add(new ArrayList<>());
        for (String line : Files.readAllLines(Paths.get(chartsFile), StandardCharsets.UTF_8)) {
            if (line.startsWith("#page|")) {
                groups.add(new ArrayList<>());
            }
            groups.get(groups.size() - 1).add(line);
        }
        pages.add(chartsPage(pdf, groups.get(0)));
        for (int i = 1; i < groups.size(); i++) {
            pages.add(moreChartsPage(pdf, groups.get(i)));
        }

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
        // The appendix starts on a page of its own, as each drawOn does
        int appendix = text.indexOf("\n## Appendix");
        if (appendix == -1) {
            markdown.drawOn(pdf, text, pages, Letter.LANDSCAPE);
        } else {
            markdown.drawOn(pdf, text.substring(0, appendix), pages, Letter.LANDSCAPE);
            markdown.drawOn(pdf, text.substring(appendix + 1), pages, Letter.LANDSCAPE);
        }

        Font small = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 8f);
        for (int i = 0; i < pages.size(); i++) {
            TextLine number = new TextLine(small, (i + 1) + " / " + pages.size()).setTextColor(MUTED);
            pages.get(i).addFooter(number, 30f);
        }
        pdf.addPages(pages);
        pdf.complete();
    }

    // The first page: the title, who made the benchmark, a bar chart for each
    // of its lines of charts.txt, and the libraries compared.
    static Page chartsPage(PDF pdf, List<String> charts) throws Exception {
        Page page = new Page(pdf, Letter.LANDSCAPE, Page.DETACHED);
        Font title = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 22f);
        Font semi = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 10f);
        Font note = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 10f);

        page.setBrushColor(NAVY);
        page.fillRect(M, M - 6f, 36f, 4f);
        new TextLine(title, "Generating PDFs in Java: four libraries compared").setTextColor(INK)
                .setStructureType(StructElem.H1).setLocation(M, M + 22f).drawOn(page);
        List<Paragraph> intro = new ArrayList<>();
        // The fonts of the test in semibold
        Paragraph text = new Paragraph();
        text.add(new TextLine(note, "Five business documents and a batch of letters, made by each library "
                + "the way its own documentation shows, every PDF checked, in").setTextColor(MUTED));
        text.add(new TextLine(semi, "IBM Plex Sans").setTextColor(INK));
        text.addJoined(new TextLine(note, ", and all of them again in").setTextColor(MUTED));
        text.add(new TextLine(semi, "Noto Sans").setTextColor(INK));
        text.addJoined(new TextLine(note, ". Written by the "
                + "author of PDFjet, with everything needed to run it again published; the other "
                + "measurements, the features of each library, the method and the tables of every figure "
                + "are on the pages after this one.").setTextColor(MUTED));
        intro.add(text);
        new TextFrame(intro).setLocation(M, M + 34f).setWidth(TW).drawOn(page);
        lowerIsBetter(page, semi, M + 86f);

        float bottom = drawCharts(pdf, page, charts, M + 100f);
        libraries(page, bottom + 24f, semi, note);
        return page;
    }

    // The line on every page of charts, in bold, that says how to read them.
    static void lowerIsBetter(Page page, Font semi, float y) throws Exception {
        new TextLine(semi, "Lower is better, and the best is in navy.").setTextColor(INK)
                .setLocation(M, y).drawOn(page);
    }

    // A page of more charts, under its heading and the line that says how to
    // read them.
    static Page moreChartsPage(PDF pdf, List<String> charts) throws Exception {
        Page page = new Page(pdf, Letter.LANDSCAPE, Page.DETACHED);
        Font semi = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 15f);
        Font note = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 10f);
        String[] head = charts.get(0).split("\\|", 2);
        page.setBrushColor(NAVY);
        page.fillRect(M, M - 6f, 36f, 4f);
        new TextLine(semi, head[1]).setTextColor(INK)
                .setStructureType(StructElem.H2).setLocation(M, M + 16f).drawOn(page);
        lowerIsBetter(page, font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 10f), M + 36f);
        drawCharts(pdf, page, charts.subList(1, charts.size()), M + 50f);
        return page;
    }

    // A bar chart for each line, five to a row, the best (the lowest) of each
    // highlighted; a line "#caption|header|note" starts a row under its header
    // and the font its tests use. Returns the bottom of the last row.
    static float drawCharts(PDF pdf, Page page, List<String> charts, float top) throws Exception {
        Font semi = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 10f);
        Font regular = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 8.5f);
        Font header = font(pdf, "IBMPlexSans/IBMPlexSans-SemiBold.ttf", 11f);
        Font note = font(pdf, "IBMPlexSans/IBMPlexSans-Regular.ttf", 10f);
        float gap = 12f;
        int perRow = 5;
        float cw = (TW - (perRow - 1) * gap) / perRow;
        float rowBottom = top;
        boolean drawn = false;
        int column = 0;
        for (String line : charts) {
            if (line.startsWith("#caption|")) {
                String[] caption = line.split("\\|", 3);
                if (drawn) {
                    top = rowBottom + 2 * gap;
                }
                Paragraph paragraph = new Paragraph();
                paragraph.add(new TextLine(header, caption[1] + ".").setTextColor(INK));
                paragraph.add(new TextLine(note, caption[2]).setTextColor(MUTED));
                List<Paragraph> paragraphs = new ArrayList<>();
                paragraphs.add(paragraph);
                new TextFrame(paragraphs).setLocation(M, top).setWidth(TW).drawOn(page);
                top += 22f;
                rowBottom = top;
                column = 0;
                continue;
            }
            if (column == perRow) {
                top = rowBottom + 2 * gap;
                column = 0;
            }
            drawn = true;
            String[] fields = line.split("\\|");
            float x = M + column * (cw + gap);
            column++;
            // As many decimals as the figures of the tables have
            int digits = 0;
            for (int i = 2; i < fields.length; i++) {
                String label = fields[i].split("=", 4)[2].split(" ")[0];
                if (label.contains(".")) {
                    digits = Math.max(digits, label.length() - label.indexOf('.') - 1);
                }
            }
            SimpleBarChart chart = new SimpleBarChart(semi, regular)
                    .setTitle(fields[0]).setSubtitle(fields[1])
                    .setFractionDigits(digits);
            int best = 0;
            float bestValue = Float.MAX_VALUE;
            for (int i = 2; i < fields.length; i++) {
                // name=value=label=unrounded: the bar as the label shows it,
                // the best by the value before it was rounded
                String[] part = fields[i].split("=", 4);
                chart.add(part[0], Float.parseFloat(part[1]));
                float unrounded = Float.parseFloat(part[3]);
                if (unrounded < bestValue) {
                    bestValue = unrounded;
                    best = i - 2;
                }
            }
            chart.setHighlight(best).setLocation(x, top).setWidth(cw);
            float[] xy = chart.drawOn(page);
            rowBottom = Math.max(rowBottom, xy[1]);
        }
        return rowBottom;
    }

    // The libraries compared, with their versions and licenses, in two
    // columns under the charts.
    static void libraries(Page page, float y, Font semi, Font note) throws Exception {
        String[][] uses = {
            {"Apache PDFBox 3.0.8", "Apache License 2.0, Java 8 and later."},
            {"iText Core 9.8.0", "AGPL-3.0 or a commercial license, Java 8 and later."},
            {"OpenPDF 3.0.5", "LGPL-2.1 or MPL-2.0, Java 21 and later."},
            {"PDFjet 9.1.5", "MIT License, Java 8 and later; also in C#, Go and Swift."},
        };
        new TextLine(semi, "The libraries").setFontSize(13f).setTextColor(INK)
                .setStructureType(StructElem.H2).setLocation(M, y).drawOn(page);
        float colW = (TW - 24f) / 2f;
        for (int i = 0; i < uses.length; i++) {
            float x = M + (i % 2) * (colW + 24f);
            float top = y + 14f + (i / 2) * 20f;
            List<Paragraph> paragraphs = new ArrayList<>();
            Paragraph p = new Paragraph();
            p.add(new TextLine(semi, uses[i][0] + ": ").setFontSize(10f).setTextColor(NAVY));
            p.add(new TextLine(note, uses[i][1]).setTextColor(INK));
            paragraphs.add(p);
            new TextFrame(paragraphs).setLocation(x, top).setWidth(colW).drawOn(page);
        }
    }
}
