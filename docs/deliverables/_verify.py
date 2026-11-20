# -*- coding: utf-8 -*-
"""Проверка готовых docx: поле TOC, стили заголовков, updateFields, рисунки."""
import re
import sys
import zipfile

from docx import Document

for f in sys.argv[1:]:
    z = zipfile.ZipFile(f)
    x = z.read("word/document.xml").decode("utf8")
    s = z.read("word/settings.xml").decode("utf8")
    d = Document(f)
    h1 = [p.text for p in d.paragraphs if p.style.name == "Heading 1"]
    h2 = [p.text for p in d.paragraphs if p.style.name == "Heading 2"]
    toc = re.search(r"TOC[^<]*", x)
    print("=" * 70)
    print(f)
    print("  H1 =", len(h1), "| H2 =", len(h2),
          "| pStyle Heading1 =", len(re.findall(r'w:val="Heading1"', x)),
          "| Heading2 =", len(re.findall(r'w:val="Heading2"', x)))
    print("  TOC:", toc.group(0) if toc else "НЕТ",
          "| updateFields:", "w:updateFields" in s)
    print("  рисунков:", len(d.inline_shapes), "| таблиц:", len(d.tables))
    print("  H1:", " | ".join(h1))
