# -*- coding: utf-8 -*-
"""Постраничный рендер PDF в PNG для визуальной проверки вёрстки.

Использование: python _render_pages.py <pdf> <out_dir> [dpi]
"""

import sys
from pathlib import Path

import pypdfium2 as pdfium


def main() -> None:
    pdf_path = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    dpi = int(sys.argv[3]) if len(sys.argv) > 3 else 110
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("page-*.png"):
        old.unlink()

    doc = pdfium.PdfDocument(str(pdf_path))
    print("pages:", len(doc))
    for index in range(len(doc)):
        page = doc[index]
        bitmap = page.render(scale=dpi / 72)
        image = bitmap.to_pil()
        target = out_dir / f"page-{index + 1:02d}.png"
        image.save(target)
        print(target.name, image.size)
    doc.close()


if __name__ == "__main__":
    main()
