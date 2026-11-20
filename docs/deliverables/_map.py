# -*- coding: utf-8 -*-
"""Карта страниц по разделам: где именно уходит объём."""

import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import _estimate as E  # noqa: E402
from docx import Document  # noqa: E402
from docx.oxml.ns import qn  # noqa: E402
from docx.table import Table  # noqa: E402
from docx.text.paragraph import Paragraph  # noqa: E402

HEAD = re.compile(r"^(\d+ [А-ЯЁ\s]+|ПРИЛОЖЕНИЕ [А-Я]\.)")


def report(path):
    doc = Document(path)
    pages, used = 1, 0.0
    marks = []
    current = "титул"
    start = 1.0
    for child in doc.element.body.iterchildren():
        if child.tag == qn("w:p"):
            p = Paragraph(child, doc)
            h = E._para_height(p, E.USABLE_W)
            brk = E._is_page_break(child)
            label = p.text.strip()
        elif child.tag == qn("w:tbl"):
            t = Table(child, doc)
            h = E._table_height(t)
            brk, label = False, ""
        else:
            continue

        if label and HEAD.match(label) and "ВВЕДЕНИЕ" in label.upper() or \
           (label and HEAD.match(label) and label.isupper()):
            marks.append((current, start, pages + used / E.USABLE_H))
            current, start = label[:48], pages + used / E.USABLE_H

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
    marks.append((current, start, pages + used / E.USABLE_H))

    seen = set()
    total = None
    for name, st, en in marks:
        total = en
        if name in seen:
            continue
        seen.add(name)
        print(f"  {st:5.1f} → {en:5.1f}  ({en - st:4.1f})  {name}")
    print(f"  ИТОГО ~{total:.1f} с.")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        print("==", arg)
        report(arg)
