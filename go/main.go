// The fair test of October 2026, PDFjet Go: the documents of ../SPEC.md.
//
//	./fairbench <plex|noto> <warm|first> <warm-ups> <runs> [min-warm-seconds] [tasks]
package main

import (
	"bufio"
	"bytes"
	"fmt"
	"math"
	"os"
	"runtime"
	"slices"
	"sort"
	"strconv"
	"strings"
	"time"

	pdfjet "github.com/edragoev1/pdfjet/v9/src"
	"github.com/edragoev1/pdfjet/v9/src/alignment"
	"github.com/edragoev1/pdfjet/v9/src/border"
	"github.com/edragoev1/pdfjet/v9/src/letter"
)

const label = "pdfjet-go"

var regularBytes, boldBytes, logoBytes, svgLogoBytes []byte

var photoBytes [][]byte

// languages holds the name and the paragraph of each language of the
// multilingual notice, from ../multilingual.txt.
var languages [][]string

const noticeTitle = "A notice in eight languages"
const nameSize = 13

// load reads the bytes of the fonts and the images once into memory.
func load(family string) {
	home := os.Getenv("HOME") + "/Projects/pdfjet/"
	read := func(path string) []byte {
		b, err := os.ReadFile(home + path)
		if err != nil {
			panic(err)
		}
		return b
	}
	if family == "plex" {
		regularBytes = read("fonts/IBMPlexSans/IBMPlexSans-Regular.ttf")
		boldBytes = read("fonts/IBMPlexSans/IBMPlexSans-SemiBold.ttf")
	} else {
		regularBytes = read("fonts/NotoSans/NotoSans-Regular.ttf")
		boldBytes = read("fonts/NotoSans/NotoSans-Bold.ttf")
	}
	logoBytes = read("images/readme/pdfjet-logo.png")
	svgLogoBytes = read("images/readme/pdfjet-logo.svg")
	for _, name := range []string{"photo-1.jpg", "photo-2.jpg", "photo-3.jpg", "photo-4.jpg"} {
		b, err := os.ReadFile("../images/" + name)
		if err != nil {
			panic(err)
		}
		photoBytes = append(photoBytes, b)
	}
	notice, err := os.ReadFile("../multilingual.txt")
	if err != nil {
		panic(err)
	}
	for _, line := range strings.Split(string(notice), "\n") {
		if line != "" {
			languages = append(languages, strings.SplitN(line, "|", 2))
		}
	}
}

// money formats an amount with two decimals and a thousands comma.
func money(amount float64) string {
	cents := int64(math.Round(amount * 100))
	sign := ""
	if cents < 0 {
		sign = "-"
		cents = -cents
	}
	whole := strconv.FormatInt(cents/100, 10)
	var b strings.Builder
	b.WriteString(sign)
	for i, c := range whole {
		if i > 0 && (len(whole)-i)%3 == 0 {
			b.WriteByte(',')
		}
		b.WriteRune(c)
	}
	fmt.Fprintf(&b, ".%02d", cents%100)
	return b.String()
}

// The geometry, in points, y from the top of the page, as in Common.java.
const (
	W, H, M                  float32 = 612, 792, 50
	textW                    float32 = W - 2*M
	body, leading, heading   float32 = 10, 13, 16
	tableSize, footerSize    float32 = 9, 8
	invoicePad, statementPad float32 = 3, 0.75
	sidePad, rule            float32 = 3, 0.5
	paragraphGap             float32 = 6
	footerBaseline           float32 = H - 30
	invoiceFooterBaseline    float32 = 742
	logoW, photoMaxH         float32 = 112, 400 // The PDFjet logo, 224 by 72 pixels
	billToX                  float32 = 330
)

var (
	from            = []string{"From: PDFjet Software", "123 Main Street", "Markham ON L3R 0A1", "Canada"}
	billTo          = []string{"Bill to: Example Corp.", "456 Market Street", "Springfield IL 62701", "USA"}
	invoiceHeader   = []string{"Item", "Qty", "Unit price", "Amount"}
	invoiceWidths   = []float32{260, 50, 100, 102}
	invoiceRight    = []bool{false, true, true, true}
	statementHeader = []string{"Date", "Description", "Debit", "Credit", "Balance"}
	statementWidths = []float32{80, 252, 60, 60, 60}
	statementRight  = []bool{false, false, true, true, true}
)

// The text of the documents, as Common.java makes it.
func invoiceRows() [][]string {
	rows := make([][]string, 0, 15)
	for i := 1; i <= 15; i++ {
		unit := 10.00 + float64(i)*2.50
		rows = append(rows, []string{"Service item " + strconv.Itoa(i) + ": consulting and support",
			strconv.Itoa(i), money(unit), money(float64(i) * unit)})
	}
	return rows
}

func invoiceTotals() []string {
	subtotal := 0.0
	for i := 1; i <= 15; i++ {
		subtotal += float64(i) * (10.00 + float64(i)*2.50)
	}
	tax := math.Round(subtotal*13.0) / 100.0
	return []string{"Subtotal " + money(subtotal), "Tax (13%) " + money(tax), "Total " + money(subtotal+tax)}
}

func statementRows() [][]string {
	rows := make([][]string, 0, 600)
	start := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	var balance int64 = 1_000_000 // in cents
	for i := 1; i <= 600; i++ {
		var debit, credit int64
		if i%3 == 0 {
			debit = int64(i) * 125
		}
		if i%5 == 0 {
			credit = int64(i) * 310
		}
		balance += credit - debit
		debitText, creditText := "", ""
		if debit != 0 {
			debitText = money(float64(debit) / 100)
		}
		if credit != 0 {
			creditText = money(float64(credit) / 100)
		}
		rows = append(rows, []string{start.AddDate(0, 0, i/2).Format("2006-01-02"),
			"Transaction " + strconv.Itoa(i) + ": payment to supplier number " + strconv.Itoa(i*7919%1000),
			debitText, creditText, money(float64(balance) / 100)})
	}
	return rows
}

func paragraph(s, p int) string {
	var sb strings.Builder
	for k := 1; k <= 6; k++ {
		if k > 1 {
			sb.WriteByte(' ')
		}
		sb.WriteString("Sentence " + strconv.Itoa(k) + " of paragraph " + strconv.Itoa(p) + " in section " + strconv.Itoa(s) +
			" describes the results, the costs and the plans of the year in plain words.")
	}
	return sb.String()
}

func letterParagraph(n int) string {
	var sb strings.Builder
	for k := 0; k < 5; k++ {
		if k > 0 {
			sb.WriteByte(' ')
		}
		sb.WriteString("We are writing to tell you about the changes to your account " + strconv.Itoa(n) +
			" that take effect next month.")
	}
	return sb.String()
}

// task helpers
func newFont(pdf *pdfjet.PDF, b []byte) *pdfjet.Font {
	return pdfjet.NewFont(pdf, bytes.NewReader(b))
}

func line(font *pdfjet.Font, size float32, text string, x, y float32) pdfjet.Drawable {
	return pdfjet.NewTextLine(font, text).SetFontSize(size).SetLocation(x, y)
}

func newCell(font *pdfjet.Font, text string, right, header bool, pad float32) *pdfjet.Cell {
	cell := pdfjet.NewCell(font, text).SetFontSize(tableSize)
	cell.SetTopPadding(pad).SetBottomPadding(pad).SetLeftPadding(sidePad).SetRightPadding(sidePad)
	cell.SetBorders(false).SetBorder(border.Bottom, true).SetBorderWidth(rule)
	if header {
		cell.SetBackgroundColor(0xE6E6E6)
	}
	if right {
		cell.SetTextAlignment(alignment.Right)
	}
	return cell
}

func tableData(regular, bold *pdfjet.Font, header []string, rows [][]string, widths []float32,
	right []bool, pad float32) [][]*pdfjet.Cell {
	data := make([][]*pdfjet.Cell, 0, len(rows)+1)
	headerRow := make([]*pdfjet.Cell, 0, len(header))
	for c := range header {
		headerRow = append(headerRow, newCell(bold, header[c], right[c], true, pad).SetWidth(widths[c]))
	}
	data = append(data, headerRow)
	for _, r := range rows {
		row := make([]*pdfjet.Cell, 0, len(r))
		for c := range r {
			row = append(row, newCell(regular, r[c], right[c], false, pad).SetWidth(widths[c]))
		}
		data = append(data, row)
	}
	return data
}

// end helpers

// task invoice
func invoice() []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	page := pdfjet.NewPage(pdf, letter.Portrait())

	logo := pdfjet.NewImageFromBytes(pdf, logoBytes)
	logo.ResizeWidth(logoW)
	logo.SetLocation(M, M)
	logo.DrawOn(page)
	line(bold, heading, "Invoice INV-2026-0042", M+logoW+12, M+36).DrawOn(page)
	for i := 0; i < 4; i++ {
		line(regular, body, from[i], M, 140+float32(i)*leading).DrawOn(page)
		line(regular, body, billTo[i], billToX, 140+float32(i)*leading).DrawOn(page)
	}

	table := pdfjet.NewTable()
	table.SetTableData(tableData(regular, bold, invoiceHeader, invoiceRows(), invoiceWidths, invoiceRight,
		invoicePad), 1)
	table.SetLocation(M, 210)
	end := table.DrawOn(page)

	totals := invoiceTotals()
	for i := range totals {
		f := regular
		if i == 2 {
			f = bold
		}
		total := pdfjet.NewTextLine(f, totals[i]).SetFontSize(body)
		total.SetLocation(W-M-total.GetWidth(), end[1]+20+float32(i)*leading).DrawOn(page)
	}
	footer := pdfjet.NewTextLine(regular, "Thank you for your business. Payment due in 30 days.").SetFontSize(footerSize)
	footer.SetLocation((W-footer.GetWidth())/2, invoiceFooterBaseline).DrawOn(page)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end invoice

// task statement
func statement() []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	first := pdfjet.NewPageDetached(pdf, letter.Portrait())
	line(bold, heading, "Account statement", M, M+16).DrawOn(first)
	line(regular, body, "Account 0042-1234, 1 January to 30 December 2026", M, M+36).DrawOn(first)

	table := pdfjet.NewTable()
	table.SetTableData(tableData(regular, bold, statementHeader, statementRows(), statementWidths,
		statementRight, statementPad), 1)
	table.SetLocation(M, M)
	table.SetFirstPageTopMargin(M + 50)
	table.SetBottomMargin(M)
	pages := []*pdfjet.Page{first}
	table.DrawOnPagesFrom(pdf, first, &pages, letter.Portrait())
	for i, page := range pages {
		footer := pdfjet.NewTextLine(regular, "Page "+strconv.Itoa(i+1)+" of "+strconv.Itoa(len(pages))).SetFontSize(footerSize)
		page.AddFooterOffsetBy(footer, H-footerBaseline)
	}
	pdf.AddPages(pages)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end statement

// task report
func report() []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	flow := pdfjet.NewTextFlow(pdf, letter.Portrait()).SetMargins(M)
	flow.Add(pdfjet.NewParagraph().Add(pdfjet.NewTextLine(bold, "Annual report 2026").SetFontSize(heading)))
	for s := 1; s <= 4; s++ {
		flow.AddSpace(16)
		flow.Add(pdfjet.NewParagraph().Add(pdfjet.NewTextLine(bold, "Section "+strconv.Itoa(s)+": Results of the year").SetFontSize(heading))).AddSpace(4)
		paragraphs := make([]*pdfjet.Paragraph, 0, 30)
		for p := 1; p <= 30; p++ {
			paragraphs = append(paragraphs, pdfjet.NewParagraph().Add(pdfjet.NewTextLine(regular, paragraph(s, p)).SetFontSize(body)))
		}
		flow.AddParagraphs(paragraphs, paragraphGap).AddSpace(12)
		photo := pdfjet.NewImageFromBytes(pdf, photoBytes[s-1])
		photo.ResizeWidth(textW)
		if photo.GetHeight() > photoMaxH {
			photo.ResizeHeight(photoMaxH)
		}
		flow.AddImage(photo).AddSpace(4)
		flow.Add(pdfjet.NewParagraph().Add(pdfjet.NewTextLine(regular, "Figure "+strconv.Itoa(s)+": map").SetFontSize(tableSize)))
	}
	var pages []*pdfjet.Page
	flow.DrawOnPages(&pages)
	for i, page := range pages {
		footer := pdfjet.NewTextLine(regular, "Page "+strconv.Itoa(i+1)).SetFontSize(footerSize)
		page.AddFooterOffsetBy(footer, H-footerBaseline)
	}
	pdf.AddPages(pages)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end report

// task letters
func letter1(n int) []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	page := pdfjet.NewPage(pdf, letter.Portrait())
	logo := pdfjet.NewImageFromBytes(pdf, logoBytes)
	logo.ResizeWidth(logoW)
	logo.SetLocation(M, M).DrawOn(page)
	line(bold, heading, "Dear customer "+strconv.Itoa(n)+",", M, 160).DrawOn(page)
	paragraphs := make([]*pdfjet.Paragraph, 0, 3)
	for p := 0; p < 3; p++ {
		paragraphs = append(paragraphs, pdfjet.NewParagraph().Add(pdfjet.NewTextLine(regular, letterParagraph(n)).SetFontSize(body)))
	}
	frame := pdfjet.NewTextFrameFromParagraphs(paragraphs).SetParagraphGap(paragraphGap)
	frame.SetLocation(M, 172)
	xy := frame.SetWidth(textW).DrawOn(page)
	line(regular, body, "PDFjet Software", M, xy[1]+26).DrawOn(page)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end letters

// svgLetter1 is a letter with the PDFjet logo in SVG, drawn as paths: the
// letter again, the logo the one difference.
// task svgletters
func svgLetter1(n int) []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	page := pdfjet.NewPage(pdf, letter.Portrait())
	logo, err := pdfjet.NewSVGImage(bytes.NewReader(svgLogoBytes))
	if err != nil {
		panic(err)
	}
	logo.ScaleBy(logoW / logo.GetWidth())
	logo.SetLocation(M, M).DrawOn(page)
	line(bold, heading, "Dear customer "+strconv.Itoa(n)+",", M, 160).DrawOn(page)
	paragraphs := make([]*pdfjet.Paragraph, 0, 3)
	for p := 0; p < 3; p++ {
		paragraphs = append(paragraphs, pdfjet.NewParagraph().Add(pdfjet.NewTextLine(regular, letterParagraph(n)).SetFontSize(body)))
	}
	frame := pdfjet.NewTextFrameFromParagraphs(paragraphs).SetParagraphGap(paragraphGap)
	frame.SetLocation(M, 172)
	xy := frame.SetWidth(textW).DrawOn(page)
	line(regular, body, "PDFjet Software", M, xy[1]+26).DrawOn(page)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end svgletters

// task multilingual
func multilingual() []byte {
	var buf bytes.Buffer
	pdf := pdfjet.NewPDF(bufio.NewWriter(&buf))
	regular := newFont(pdf, regularBytes)
	bold := newFont(pdf, boldBytes)
	page := pdfjet.NewPage(pdf, letter.Portrait())
	line(bold, heading, noticeTitle, M, M+16).DrawOn(page)
	paragraphs := make([]*pdfjet.Paragraph, 0, 2*len(languages))
	for _, language := range languages {
		paragraphs = append(paragraphs, pdfjet.NewParagraph().Add(pdfjet.NewTextLine(bold, language[0]).SetFontSize(nameSize)))
		paragraphs = append(paragraphs, pdfjet.NewParagraph().Add(pdfjet.NewTextLine(regular, language[1]).SetFontSize(body)))
	}
	frame := pdfjet.NewTextFrameFromParagraphs(paragraphs).SetParagraphGap(paragraphGap)
	frame.SetLocation(M, M+24)
	frame.SetWidth(textW).DrawOn(page)
	if err := pdf.Complete(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

// end multilingual

func svgLetters() []byte {
	var last []byte
	for n := 1; n <= 1000; n++ {
		last = svgLetter1(n)
	}
	return last
}

func letters() []byte {
	var last []byte
	for n := 1; n <= 1000; n++ {
		last = letter1(n)
	}
	return last
}

func totalAlloc() uint64 {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	return m.TotalAlloc
}

func ms(d time.Duration) float64 {
	return float64(d.Nanoseconds()) / 1e6
}

func main() {
	family, mode := os.Args[1], os.Args[2]
	warm, _ := strconv.Atoi(os.Args[3])
	runs, _ := strconv.Atoi(os.Args[4])
	minWarmSeconds := 3.0
	if len(os.Args) > 5 {
		minWarmSeconds, _ = strconv.ParseFloat(os.Args[5], 64)
	}
	only := "invoice,statement,report,letters,svgletters,multilingual"
	if len(os.Args) > 6 {
		only = os.Args[6]
	}
	load(family)
	outDir := "out-" + family
	if err := os.MkdirAll(outDir, 0o755); err != nil {
		panic(err)
	}

	if mode == "first" {
		t0 := time.Now()
		pdf := invoice()
		fmt.Printf("%s %s first median-ms %.2f bytes %d\n", label, family, ms(time.Since(t0)), len(pdf))
		return
	}

	tasks := []struct {
		name string
		run  func() []byte
		docs int
	}{
		{"invoice", invoice, 1},
		{"statement", statement, 1},
		{"report", report, 1},
		{"letters", letters, 1000},
		{"multilingual", multilingual, 1},
		{"svgletters", svgLetters, 1000},
	}
	for _, task := range tasks {
		if !slices.Contains(strings.Split(only, ","), task.name) {
			continue
		}
		// Letters: timed as the whole batch, 2 warm-up batches, 5 timed runs.
		w, r, minSeconds := warm, runs, minWarmSeconds
		if task.docs > 1 {
			w, r, minSeconds = min(warm, 2), min(runs, 5), 0
		}
		warmStart := time.Now()
		for i := 0; i < w || time.Since(warmStart).Seconds() < minSeconds; i++ {
			task.run()
		}
		times := make([]float64, r)
		alloc := make([]uint64, r)
		var pdf []byte
		for i := 0; i < r; i++ {
			a := totalAlloc()
			t0 := time.Now()
			pdf = task.run()
			times[i] = ms(time.Since(t0))
			alloc[i] = (totalAlloc() - a) / uint64(task.docs)
		}
		sort.Float64s(times)
		sort.Slice(alloc, func(i, j int) bool { return alloc[i] < alloc[j] })
		if err := os.WriteFile(outDir+"/"+label+"-"+task.name+".pdf", pdf, 0o644); err != nil {
			panic(err)
		}
		fmt.Printf("%s %s %s median-ms %.2f min-ms %.2f max-ms %.2f bytes %d alloc-bytes %d\n",
			label, family, task.name, times[r/2], times[0], times[r-1], len(pdf), alloc[r/2])
	}
}
