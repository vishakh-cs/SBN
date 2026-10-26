"""Build the application report PDF from its Markdown source using ReportLab."""

from pathlib import Path
import html
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, Preformatted, KeepTogether,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "application-technical-report.md"
OUTPUT = ROOT / "docs" / "application-technical-report.pdf"


def register_fonts():
    font_dir = Path("C:/Windows/Fonts")
    regular = font_dir / "arial.ttf"
    bold = font_dir / "arialbd.ttf"
    if regular.exists() and bold.exists():
        pdfmetrics.registerFont(TTFont("ReportSans", str(regular)))
        pdfmetrics.registerFont(TTFont("ReportSans-Bold", str(bold)))
        return "ReportSans", "ReportSans-Bold"
    return "Helvetica", "Helvetica-Bold"


BODY_FONT, BOLD_FONT = register_fonts()
NAVY = colors.HexColor("#12304A")
TEAL = colors.HexColor("#087E8B")
PALE = colors.HexColor("#EAF3F6")
INK = colors.HexColor("#23313D")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="ReportTitle", parent=styles["Title"], fontName=BOLD_FONT,
    fontSize=25, leading=30, textColor=NAVY, alignment=TA_CENTER,
    spaceAfter=8 * mm,
))
styles.add(ParagraphStyle(
    name="ReportSubtitle", parent=styles["Normal"], fontName=BODY_FONT,
    fontSize=12, leading=18, textColor=TEAL, alignment=TA_CENTER,
    spaceAfter=5 * mm,
))
styles.add(ParagraphStyle(
    name="ReportH1", parent=styles["Heading1"], fontName=BOLD_FONT,
    fontSize=17, leading=21, textColor=NAVY, spaceBefore=7 * mm,
    spaceAfter=3 * mm, keepWithNext=True,
))
styles.add(ParagraphStyle(
    name="ReportH2", parent=styles["Heading2"], fontName=BOLD_FONT,
    fontSize=12, leading=15, textColor=TEAL, spaceBefore=4 * mm,
    spaceAfter=2 * mm, keepWithNext=True,
))
styles.add(ParagraphStyle(
    name="ReportBody", parent=styles["BodyText"], fontName=BODY_FONT,
    fontSize=9.2, leading=13.5, textColor=INK, spaceAfter=2.4 * mm,
))
styles.add(ParagraphStyle(
    name="ReportBullet", parent=styles["ReportBody"], leftIndent=5 * mm,
    firstLineIndent=-3 * mm, spaceAfter=1.3 * mm,
))
styles.add(ParagraphStyle(
    name="ReportCode", fontName="Courier", fontSize=7.8, leading=10.5,
    backColor=PALE, borderColor=colors.HexColor("#D2E1E7"), borderWidth=0.5,
    borderPadding=5, leftIndent=3 * mm, rightIndent=3 * mm,
    spaceBefore=1.5 * mm, spaceAfter=3 * mm,
))
styles.add(ParagraphStyle(
    name="ReportTable", parent=styles["ReportBody"], fontSize=7.7,
    leading=10.2, spaceAfter=0,
))


def inline_markup(text):
    escaped = html.escape(text.strip(), quote=False)
    escaped = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", escaped)
    escaped = re.sub(r"`([^`]+)`", r"<font name='Courier'>\1</font>", escaped)
    return escaped


def on_page(canvas, doc):
    canvas.saveState()
    width, height = A4
    canvas.setFillColor(NAVY)
    canvas.rect(0, height - 7 * mm, width, 7 * mm, stroke=0, fill=1)
    if doc.page > 1:
        canvas.setFont(BODY_FONT, 8)
        canvas.setFillColor(colors.HexColor("#647582"))
        canvas.drawString(18 * mm, 11 * mm, "Smart Blood & Organ Donation Network | Technical Report")
        canvas.drawRightString(width - 18 * mm, 11 * mm, f"Page {doc.page}")
        canvas.setStrokeColor(colors.HexColor("#D7E0E5"))
        canvas.line(18 * mm, 15 * mm, width - 18 * mm, 15 * mm)
    canvas.restoreState()


def parse_markdown(text):
    lines = text.splitlines()
    story = []
    i = 0
    first_title = True
    while i < len(lines):
        line = lines[i].strip()
        if not line or line == "---":
            i += 1
            continue
        if line.startswith("```"):
            i += 1
            code = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i].rstrip())
                i += 1
            story.append(Preformatted("\n".join(code), styles["ReportCode"], maxLineLength=95))
            i += 1
            continue
        if line.startswith("| "):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                raw = lines[i].strip().strip("|")
                cells = [c.strip() for c in raw.split("|")]
                if not all(re.fullmatch(r":?-{3,}:?", c) for c in cells):
                    rows.append([Paragraph(inline_markup(c), styles["ReportTable"]) for c in cells])
                i += 1
            if rows:
                col_count = max(map(len, rows))
                usable = A4[0] - 36 * mm
                widths = [usable / col_count] * col_count
                table = Table(rows, colWidths=widths, repeatRows=1, hAlign="LEFT")
                table.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ("FONTNAME", (0, 0), (-1, 0), BOLD_FONT),
                    ("BACKGROUND", (0, 1), (-1, -1), colors.white),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PALE]),
                    ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD8DE")),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 5),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                    ("TOPPADDING", (0, 0), (-1, -1), 5),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                ]))
                story.extend([table, Spacer(1, 3 * mm)])
            continue
        if line.startswith("# "):
            title = line[2:]
            if first_title:
                story.extend([Spacer(1, 20 * mm), Paragraph(inline_markup(title), styles["ReportTitle"])])
                first_title = False
            else:
                story.append(Paragraph(inline_markup(title), styles["ReportH1"]))
            i += 1
            continue
        if line.startswith("## "):
            heading = line[3:]
            if heading == "Application and Technical Report":
                story.append(Paragraph(inline_markup(heading), styles["ReportSubtitle"]))
            else:
                story.append(Paragraph(inline_markup(heading), styles["ReportH1"]))
            i += 1
            continue
        if line.startswith("### "):
            story.append(Paragraph(inline_markup(line[4:]), styles["ReportH2"]))
            i += 1
            continue
        if re.match(r"^\d+\.\s+", line):
            story.append(Paragraph("- " + inline_markup(re.sub(r"^\d+\.\s+", "", line)), styles["ReportBullet"]))
            i += 1
            continue
        if line.startswith("- "):
            story.append(Paragraph("- " + inline_markup(line[2:]), styles["ReportBullet"]))
            i += 1
            continue
        para = [line]
        i += 1
        while i < len(lines):
            nxt = lines[i].strip()
            if not nxt or nxt == "---" or nxt.startswith(("#", "- ", "| ", "```")) or re.match(r"^\d+\.\s+", nxt):
                break
            para.append(nxt)
            i += 1
        story.append(Paragraph(inline_markup(" ".join(para)), styles["ReportBody"]))
    return story


def main():
    content = SOURCE.read_text(encoding="utf-8")
    content = content.replace("\u00e2\u20ac\u201d", "-")
    doc = BaseDocTemplate(
        str(OUTPUT), pagesize=A4,
        leftMargin=18 * mm, rightMargin=18 * mm,
        topMargin=17 * mm, bottomMargin=20 * mm,
        title="Smart Blood & Organ Donation Network - Application and Technical Report",
        author="Project Documentation",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")
    doc.addPageTemplates([PageTemplate(id="report", frames=frame, onPage=on_page)])
    doc.build(parse_markdown(content))
    print(f"Created {OUTPUT}")


if __name__ == "__main__":
    main()
