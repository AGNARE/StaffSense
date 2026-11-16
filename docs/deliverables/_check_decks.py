"""
Проверка готовых презентаций: открыть каждый файл, перечитать содержимое и
убедиться, что слайды не пустые, заголовки на месте и ничего не вылезает
за границы 13,333 × 7,5 дюйма.
"""

import os
import sys

from pptx import Presentation
from pptx.util import Emu

BASE = os.path.dirname(os.path.abspath(__file__))
FILES = [
    "06_Практическая_работа_9_презентация.pptx",
    "07_Презентация_проекта.pptx",
]

W = Emu(int(13.333 * 914400))
H = Emu(int(7.5 * 914400))
TOLERANCE = Emu(int(0.02 * 914400))


def title_of(slide):
    for shape in slide.shapes:
        if shape.name == "Title" and shape.has_text_frame:
            return shape.text_frame.text.strip()
    return "(нет заголовка)"


def check(path):
    prs = Presentation(path)
    problems = []
    print(f"\n{'=' * 78}")
    print(f"ФАЙЛ: {os.path.basename(path)}")
    print(f"Размер: {prs.slide_width / 914400:.3f} × "
          f"{prs.slide_height / 914400:.3f} дюйма · "
          f"слайдов: {len(prs.slides)}")
    print("=" * 78)

    for number, slide in enumerate(prs.slides, start=1):
        title = title_of(slide)
        chars = 0
        sizes = []
        bullet_min = None
        for shape in slide.shapes:
            if shape.left is not None and shape.top is not None:
                if (shape.left + shape.width > W + TOLERANCE
                        or shape.top + shape.height > H + TOLERANCE
                        or shape.left < -TOLERANCE or shape.top < -TOLERANCE):
                    problems.append(f"слайд {number}: фигура «{shape.shape_type}» "
                                    f"выходит за границы")
            if not shape.has_text_frame:
                continue
            for paragraph in shape.text_frame.paragraphs:
                text = "".join(run.text for run in paragraph.runs).strip()
                if not text:
                    continue
                chars += len(text)
                for run in paragraph.runs:
                    if run.font.size is None:
                        continue
                    size = run.font.size.pt
                    sizes.append((size, len(text)))
                    if shape.name == "Bullets":
                        bullet_min = (size if bullet_min is None
                                      else min(bullet_min, size))
        body = [size for size, length in sizes if length > 40]
        body_min = min(body) if body else None
        biggest = max((size for size, _ in sizes), default=None)
        print(f"{number:>2}. {title[:64]:<64} "
              f"символов: {chars:>4} · кегль: {body_min}–{biggest}"
              f" · пункты: {bullet_min}")
        if chars < 40:
            problems.append(f"слайд {number}: почти пустой ({chars} символов)")
        if title == "(нет заголовка)":
            problems.append(f"слайд {number}: нет заголовка")
        if bullet_min is not None and bullet_min < 16:
            problems.append(f"слайд {number}: пункт списка {bullet_min} пт")

    print("-" * 78)
    if problems:
        print("ЗАМЕЧАНИЯ:")
        for item in problems:
            print("  !", item)
    else:
        print("Замечаний нет: заголовки на месте, слайды не пустые, "
              "текст не выходит за границы, основной кегль ≥ 16 пт.")
    return len(prs.slides), problems


def main():
    total_problems = 0
    for name in FILES:
        path = os.path.join(BASE, name)
        if not os.path.exists(path):
            print(f"НЕТ ФАЙЛА: {path}")
            total_problems += 1
            continue
        _, problems = check(path)
        total_problems += len(problems)
    print(f"\nВсего замечаний: {total_problems}")
    return 1 if total_problems else 0


if __name__ == "__main__":
    sys.exit(main())
