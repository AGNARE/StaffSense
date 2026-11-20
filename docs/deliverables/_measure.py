# -*- coding: utf-8 -*-
"""
Оценка объёма документа в страницах без LibreOffice/Word.

Считается не «знаки на страницу», а вертикальный метр: каждая страница —
25,7 см полезной высоты (A4, поля 2+2 см). Для абзаца оценивается число
строк переноса по ширине колонки и кеглю, для таблицы — по самой длинной
ячейке строки, для рисунка — его фактическая высота при заданной ширине.

Модель откалибрована по трём готовым документам (19, 20 и 18 страниц),
поэтому годится для проверки «стало короче / стало длиннее», а не для
точности до строки.
"""

import sys
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Cm

USABLE_W = 16.5          # см, ширина полосы набора
USABLE_H = 25.7          # см, высота полосы набора
EMU_PER_CM = 360000


def para_chars_per_line(size_pt, width_cm):
    """Средняя ширина знака Times New Roman ≈ 0,56 кегля (кириллица шире)."""
    char_cm = 0.56 * size_pt * 0.03528
    return max(10.0, width_cm / char_cm)


def para_height(text, size_pt, width_cm, spacing=1.15, space_after_pt=0):
    per_line = para_chars_per_line(size_pt, width_cm)
    lines = max(1, int(len(text) / per_line) + (1 if len(text) % per_line else 0))
    line_cm = size_pt * 1.15 * spacing * 0.03528
    return lines * line_cm + space_after_pt * 0.03528


def para_size(paragraph):
    for run in paragraph.runs:
        if run.font.size is not None:
            return run.font.size.pt
    return 12.0


def spacing(paragraph):
    value = paragraph.paragraph_format.line_spacing
    return value if isinstance(value, float) else 1.15


def col_widths(table):
    grid = table._tbl.find(qn("w:tblGrid"))
    if grid is None:
        return None
    widths = []
    for col in grid.findall(qn("w:gridCol")):
        twips = int(col.get(qn("w:w")) or 0)
        widths.append(twips / 567.0)
    return widths or None


def table_height(table):
    widths = col_widths(table)
    if not widths:
        widths = [USABLE_W / len(table.columns)] * len(table.columns)
    total = 0.0
    for row in table.rows:
        row_cm = 0.0
        for index, cell in enumerate(row.cells):
            if index >= len(widths):
                break
            size = 11.0
            text = ""
            for paragraph in cell.paragraphs:
                size = para_size(paragraph) or 11.0
                text += " " + paragraph.text
            lines = para_height(text.strip(), size, widths[index] - 0.2, spacing=1.0)
            row_cm = max(row_cm, lines)
        total += max(row_cm, 0.5) + 0.08
    return total


def measure(path):
    doc = Document(path)
    body = doc.element.body
    height = 0.0
    chars = 0
    tables = 0
    for child in body.iterchildren():
        if child.tag == qn("w:p"):
            from docx.text.paragraph import Paragraph
            paragraph = Paragraph(child, doc)
            text = paragraph.text
            chars += len(text)
            size = para_size(paragraph)
            after = paragraph.paragraph_format.space_after
            after_pt = after.pt if after is not None else 0
            before = paragraph.paragraph_format.space_before
            before_pt = before.pt if before is not None else 0
            height += para_height(text, size, USABLE_W,
                                  spacing(paragraph), after_pt + before_pt)
        elif child.tag == qn("w:tbl"):
            from docx.table import Table
            table = Table(child, doc)
            tables += 1
            height += table_height(table)
            chars += sum(len(c.text) for r in table.rows for c in r.cells)
        elif child.tag == qn("w:sectPr"):
            continue

    for shape in doc.inline_shapes:
        width_cm = shape.width / EMU_PER_CM
        ratio = shape.height / shape.width if shape.width else 0.6
        # Картинка уже посчитана как абзац; заменяем его высоту на реальную.
        height += width_cm * ratio - 0.6

    pages = height / USABLE_H
    return {
        "file": Path(path).name,
        "height_cm": height,
        "pages": pages,
        "chars": chars,
        "chars_per_page": chars / pages if pages else 0,
        "tables": tables,
        "shapes": len(doc.inline_shapes),
        "paragraphs": len(doc.paragraphs),
    }


if __name__ == "__main__":
    files = sys.argv[1:] or [
        "03_Руководство_программиста.docx",
        "04_Руководство_пользователя.docx",
        "05_Оценка_программы.docx",
    ]
    base = Path(__file__).resolve().parent
    for name in files:
        path = base / name if not Path(name).is_absolute() else Path(name)
        m = measure(path)
        print(f"{m['file']:<42} стр≈{m['pages']:5.1f}  абзацев={m['paragraphs']:4d} "
              f"табл={m['tables']:2d} рис={m['shapes']:2d} "
              f"знаков={m['chars']:6d} ({m['chars_per_page']:.0f}/стр)")
