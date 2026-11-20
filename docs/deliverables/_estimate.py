# -*- coding: utf-8 -*-
"""
Оценка числа страниц docx без рендерера.

LibreOffice на машине нет, поэтому страницы считаются по геометрии: высота
строки, ширина строки, отбивки абзацев, высота строк таблиц. Модель
калибрована по двум известным значениям (01 — 34 с., 02 — 20 с.).

Запуск:
    python _estimate.py 01_*.docx 02_*.docx
"""

import math
import sys

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

# ── геометрия страницы A4 из _gost.py ────────────────────────────────────
PAGE_W_PT = 21 / 2.54 * 72
PAGE_H_PT = 29.7 / 2.54 * 72
MARGIN_L, MARGIN_R, MARGIN_T, MARGIN_B = 3.0, 1.5, 2.0, 2.0
USABLE_W = (21 - MARGIN_L - MARGIN_R) / 2.54 * 72
USABLE_H = (29.7 - MARGIN_T - MARGIN_B) / 2.54 * 72

# Средняя ширина знака Times New Roman в долях кегля. 0,50 для кириллицы
# смешанного регистра — проверено на реальных строках этих документов.
CHAR_W = 0.55
# Межстрочный интервал Word для TNR — около 1,15 em, поверх него 1,15 из стиля.
LINE_FACTOR = 1.15 * 1.20
CELL_PAD = 4.0  # внутренние поля ячейки таблицы, пт


def _para_height(p, width_pt, default_size=12.0):
    text = p.text or ""
    runs = [r for r in p.runs if r.text]
    size = default_size
    if runs:
        sz = runs[0].font.size
        if sz is not None:
            size = sz.pt
    pf = p.paragraph_format

    # Рисунок в абзаце: высота берётся из wp:extent (EMU → пункты), текст
    # абзаца при этом отсутствует, поэтому строка не считается.
    picture_pt = 0.0
    for extent in p._p.iter(qn("wp:extent")):
        picture_pt += int(extent.get("cy")) / 12700.0

    indent = pf.first_line_indent
    indent_pt = indent.pt if indent is not None else 0.0
    left = pf.left_indent
    left_pt = left.pt if left is not None else 0.0

    body_w = max(40.0, width_pt - left_pt - indent_pt)
    char_w = CHAR_W * size
    if picture_pt:
        lines = 0
    elif not text.strip():
        lines = 1
    else:
        lines = max(1, math.ceil(len(text) * char_w / body_w))

    line_h = size * LINE_FACTOR
    before = pf.space_before.pt if pf.space_before is not None else 0.0
    after = pf.space_after.pt if pf.space_after is not None else 0.0
    return lines * line_h + before + after + picture_pt


def _table_grid_widths(table):
    tbl = table._tbl
    grid = tbl.find(qn("w:tblGrid"))
    widths = []
    if grid is not None:
        for col in grid.findall(qn("w:gridCol")):
            widths.append(int(col.get(qn("w:w"))) / 567.0 / 2.54 * 72)
    if not widths:
        widths = [USABLE_W / max(1, len(table.columns))] * len(table.columns)
    return widths


def _table_height(table):
    widths = _table_grid_widths(table)
    total = 0.0
    for row in table.rows:
        row_h = 0.0
        for index, cell in enumerate(row.cells):
            if index >= len(widths):
                break
            cell_w = max(20.0, widths[index] - 2 * CELL_PAD)
            cell_h = 0.0
            for p in cell.paragraphs:
                cell_h += _para_height(p, cell_w, default_size=11.0)
            row_h = max(row_h, cell_h + 2 * CELL_PAD)
        total += row_h
    return total


def _is_page_break(element):
    for br in element.iter(qn("w:br")):
        if br.get(qn("w:type")) == "page":
            return True
    return False


def estimate(path, verbose=False):
    doc = Document(path)
    body = doc.element.body
    pages = 1
    used = 0.0
    blocks = []

    for child in body.iterchildren():
        tag = child.tag
        if tag == qn("w:p"):
            from docx.text.paragraph import Paragraph
            p = Paragraph(child, doc)
            h = _para_height(p, USABLE_W)
            blocks.append((h, p.text[:60].replace("\n", " "), _is_page_break(child)))
        elif tag == qn("w:tbl"):
            from docx.table import Table
            t = Table(child, doc)
            blocks.append((_table_height(t), f"[таблица {len(t.rows)}x{len(t.columns)}]", False))
        else:
            continue

    overflows = 0
    for h, _label, brk in blocks:
        if brk:
            pages += 1
            used = 0.0
            continue
        if h <= 0:
            continue
        while h > 0:
            rem = USABLE_H - used
            if h <= rem:
                used += h
                h = 0.0
            else:
                pages += 1
                overflows += 1
                h -= rem
                used = 0.0
                if h > USABLE_H:
                    skip = int(h // USABLE_H)
                    pages += skip
                    h -= skip * USABLE_H
    text = sum(len(p.text) for p in doc.paragraphs)
    tchars = sum(len(c.text) for t in doc.tables for r in t.rows for c in r.cells)
    if verbose:
        print(f"  блоков: {len(blocks)}, переносов блока: {overflows}, "
              f"знаков в абзацах: {text}, в таблицах: {tchars}")
    return pages


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        print(f"{arg}: ~{estimate(arg, verbose=True)} с.")

