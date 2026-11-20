# -*- coding: utf-8 -*-
"""Карта страниц по разделам: где именно уходит объём.

Заголовок первого уровня опознаётся по кеглю 14 пт — записи содержания
набраны 12 пт, поэтому в карту не попадают.
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import _estimate as E  # noqa: E402
from docx import Document  # noqa: E402
from docx.oxml.ns import qn  # noqa: E402
from docx.table import Table  # noqa: E402
from docx.text.paragraph import Paragraph  # noqa: E402


def blocks(doc):
    for child in doc.element.body.iterchildren():
        if child.tag == qn("w:p"):
            p = Paragraph(child, doc)
            yield ("p", p, E._para_height(p, E.USABLE_W), E._is_page_break(child))
        elif child.tag == qn("w:tbl"):
            t = Table(child, doc)
            yield ("t", t, E._table_height(t), False)


def is_h1(p):
    for run in p.runs:
        if run.font.size is not None and run.font.size.pt >= 14:
            return True
    return False


def report(path):
    doc = Document(path)
    pages, used = 1, 0.0
    marks = []
    current, start = "титул и содержание", 1.0

    def close():
        marks.append((current, start, pages + used / E.USABLE_H))

    for kind, obj, h, brk in blocks(doc):
        if kind == "p" and is_h1(obj) and obj.text.strip() and not brk:
            if not obj.text.strip().startswith("СОДЕРЖАНИЕ"):
                close()
                current, start = obj.text.strip()[:52], pages + used / E.USABLE_H
        if brk:
            pages += 1
            used = 0.0
            continue
        while h > 0:
            rem = E.USABLE_H - used
            if h <= rem:
                used += h
                h = 0.0
            else:
                pages += 1
                h -= rem
                used = 0.0
                if h > E.USABLE_H:
                    skip = int(h // E.USABLE_H)
                    pages += skip
                    h -= skip * E.USABLE_H
    close()

    for name, st, en in marks:
        print(f"  {st:5.1f} → {en:5.1f}  ({en - st:4.1f})  {name}")
    print(f"  ИТОГО ~{marks[-1][2]:.1f} с.")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        print("==", arg)
        report(arg)
