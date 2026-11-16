"""
Конструктор слайдов StaffSense: цвета, сетка, типовые блоки.

Один модуль на обе презентации: если поменяется цвет, поле или кегль,
это произойдёт сразу в обоих файлах, а не в каждом по отдельности.

Палитра взята из дизайн-системы проекта (`static/design/proto.css`):
тёмно-синий, синий акцент, светлый фон, серый текст, красный и зелёный.
"""

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_CONNECTOR, MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml import parse_xml
from pptx.oxml.ns import qn
from pptx.util import Inches, Pt

FONT = "Arial"

NAVY = "061733"       # основной тёмно-синий
NAVY_2 = "0E2A57"     # второй тон для фона титула
BRAND = "174CA3"      # синий акцент
BG = "F4F6F9"         # светлый фон слайда
INK = "435066"        # серый текст
MUTED = "8A94A6"      # подписи, сноски
RISK = "B42318"       # красный: риск, проблема
OK = "0B7A5F"         # зелёный: норма, результат
WHITE = "FFFFFF"
LINE = "D9DFE8"       # разделители и рамки
SOFT = "E8EEF8"       # светло-синяя подложка
BLUE_INK = "8FB0E0"   # приглушённый голубой на тёмном фоне

SLIDE_W = 13.333
SLIDE_H = 7.5
MARGIN = 0.85
BODY_TOP = 1.95
BODY_BOTTOM = 6.72

A_NS = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'


def rgb(value):
    return RGBColor.from_string(value)


# ── каркас ────────────────────────────────────────────────────────────────

def new_deck():
    """Пустая презентация 16:9."""
    prs = Presentation()
    prs.slide_width = Inches(SLIDE_W)
    prs.slide_height = Inches(SLIDE_H)
    return prs


def blank(prs):
    """Пустой макет: всё рисуем сами, чтобы не зависеть от темы."""
    return prs.slides.add_slide(prs.slide_layouts[6])


def background(slide, color=BG):
    """Фон на всю площадь слайда: уводится в самый низ z-порядка."""
    rect = _shape(slide, 0, 0, SLIDE_W, SLIDE_H, fill=color)
    sp_tree = slide.shapes._spTree
    sp_tree.remove(rect._element)
    sp_tree.insert(2, rect._element)
    return rect


def _shape(slide, x, y, w, h, fill=None, line=None, lw=1.0,
           shape=MSO_SHAPE.RECTANGLE, radius=None):
    item = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    if fill:
        item.fill.solid()
        item.fill.fore_color.rgb = rgb(fill)
    else:
        item.fill.background()
    if line:
        item.line.color.rgb = rgb(line)
        item.line.width = Pt(lw)
    else:
        item.line.fill.background()
    item.shadow.inherit = False
    if radius is not None and shape == MSO_SHAPE.ROUNDED_RECTANGLE:
        item.adjustments[0] = radius
    item.text_frame.word_wrap = True
    return item


def card(slide, x, y, w, h, fill=WHITE, line=LINE, radius=0.06):
    """Карточка: скруглённый прямоугольник с тонкой рамкой."""
    return _shape(slide, x, y, w, h, fill=fill, line=line, lw=0.75,
                  shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=radius)


def bar(slide, x, y, w, h, color=BRAND):
    return _shape(slide, x, y, w, h, fill=color)


# ── текст ─────────────────────────────────────────────────────────────────

def _frame(slide, x, y, w, h, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = 0
    tf.margin_top = tf.margin_bottom = 0
    return tf


def _run(p, text, size, color, bold=False, italic=False, spacing=None):
    run = p.add_run()
    run.text = text
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = rgb(color)
    rpr = run._r.get_or_add_rPr()
    if spacing is not None:
        rpr.set("spc", str(int(spacing * 100)))
    return run


def text(slide, x, y, w, h, body, size=18, color=INK, bold=False,
         align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, italic=False,
         spacing=None, line_spacing=1.0):
    """Один абзац одним начертанием."""
    tf = _frame(slide, x, y, w, h, anchor)
    p = tf.paragraphs[0]
    p.alignment = align
    p.line_spacing = line_spacing
    _run(p, body, size, color, bold, italic, spacing)
    return tf


def rich(slide, x, y, w, h, parts, align=PP_ALIGN.LEFT,
         anchor=MSO_ANCHOR.TOP, line_spacing=1.0):
    """Один абзац из разных начертаний: [(текст, кегль, цвет, жирность)]."""
    tf = _frame(slide, x, y, w, h, anchor)
    p = tf.paragraphs[0]
    p.alignment = align
    p.line_spacing = line_spacing
    for piece in parts:
        body, size, color, bold = piece
        _run(p, body, size, color, bold)
    return tf


# ── измерение текста ──────────────────────────────────────────────────────
# Ширину строк считаем по метрикам самого Arial, а не «на глаз»: иначе
# однострочный пункт получает высоту двухстрочного и список идёт вразнобой.

try:
    from PIL import ImageFont
except ImportError:  # библиотеки нет — остаётся грубая оценка
    ImageFont = None

_FACES = {}
_REGULAR = "C:/Windows/Fonts/arial.ttf"
_BOLD = "C:/Windows/Fonts/arialbd.ttf"


def _face(size, bold):
    key = (round(size, 1), bold)
    if key not in _FACES:
        path = _BOLD if bold else _REGULAR
        try:
            _FACES[key] = ImageFont.truetype(path, max(1, int(round(size))))
        except OSError:
            _FACES[key] = None
    return _FACES[key]


def text_width(body, size, bold=False):
    """Ширина строки в пунктах."""
    face = _face(size, bold)
    if face is None:
        return len(body) * size * 0.55
    return face.getlength(body)


def count_lines(runs, width_pt):
    """
    Число строк после переноса по словам.

    runs — [(текст, кегль, жирность)]: пункт списка состоит из жирного
    зачина и обычного пояснения, поэтому ширину считаем по частям.
    """
    lines, current = 1, 0.0
    for body, size, bold in runs:
        for index, word in enumerate(body.split(" ")):
            word_w = text_width(word, size, bold)
            space = text_width(" ", size, bold) if index else 0
            # −2 pt: пусть лучше строка «не влезет» на волосок, чем наоборот
            if current and current + space + word_w > width_pt - 2:
                lines += 1
                current = word_w
            else:
                current += space + word_w
    return lines


def est_lines(body, width_in, size, factor=0.55, bold=False):
    """Сколько строк займёт текст в блоке заданной ширины (дюймы)."""
    if ImageFont is not None:
        return count_lines([(body, size, bold)], width_in * 72)
    per_line = max(8, int(width_in * 72 / (size * factor)))
    lines, current = 1, 0
    for word in body.split():
        extra = len(word) + (1 if current else 0)
        if current + extra > per_line:
            lines += 1
            current = len(word)
        else:
            current += extra
    return lines


# ── шапка и подвал ────────────────────────────────────────────────────────

def header(slide, kicker, title, title_size=30, width=None):
    """Надзаголовок, заголовок и акцентная черта. Заголовок называется Title."""
    width = width or (SLIDE_W - 2 * MARGIN)
    if kicker:
        text(slide, MARGIN, 0.44, width, 0.28, kicker.upper(), 11.5, BRAND,
             bold=True, spacing=1.4)
    box = slide.shapes.add_textbox(Inches(MARGIN), Inches(0.72), Inches(width),
                                   Inches(0.95))
    box.name = "Title"
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = 0
    frame.margin_top = frame.margin_bottom = 0
    paragraph = frame.paragraphs[0]
    paragraph.line_spacing = 0.95
    _run(paragraph, title, title_size, NAVY, bold=True)
    bar(slide, MARGIN, 1.63, 1.15, 0.055, BRAND)
    return frame


def footer(slide, index, total, label):
    """Тонкая линия и подпись внизу: название работы и номер слайда."""
    line = _shape(slide, MARGIN, 7.06, SLIDE_W - 2 * MARGIN, 0.012, fill=LINE)
    text(slide, MARGIN, 7.14, 9.5, 0.24, label, 10, MUTED)
    text(slide, SLIDE_W - MARGIN - 1.4, 7.14, 1.4, 0.24, f"{index} / {total}",
         10, MUTED, align=PP_ALIGN.RIGHT)


# ── блоки ─────────────────────────────────────────────────────────────────

def bullets(slide, items, x, y, w, size=18, gap=0.20, marker=BRAND,
            lead_color=NAVY, body_color=INK, bottom=BODY_BOTTOM, sep=" — "):
    """
    Список пунктов с квадратным маркером.

    Пункт — пара (жирный зачин, пояснение) или одна строка текста.
    Высота считается по числу строк, поэтому пункты не наезжают друг на друга.
    """
    cursor = y
    for item in items:
        lead, body = item if isinstance(item, tuple) else (item, "")
        box_w = w - 0.30
        parts = [(lead, size, True)]
        if body:
            parts += [(sep, size, False), (body, size, False)]
        lines = count_lines(parts, box_w * 72)
        # первая строка 1,32 кегля, каждая следующая — 1,50: замер по рендеру
        height = (1.32 + 1.50 * (lines - 1)) * size / 72
        _shape(slide, x + 0.02, cursor + 0.11, 0.085, 0.085, fill=marker)
        tf = _frame(slide, x + 0.30, cursor, box_w, height + 0.06)
        tf._parent.name = "Bullets"
        p = tf.paragraphs[0]
        p.line_spacing = 1.16
        _run(p, lead, size, lead_color, bold=True)
        if body:
            _run(p, sep, size, body_color)
            _run(p, body, size, body_color)
        cursor += height + gap
    if cursor > bottom + 0.02:
        print(f"  ! переполнение списка: низ {cursor:.2f} > {bottom:.2f}")
    return cursor


def tiles(slide, items, x, y, w, h=1.42, gap=0.20, value_size=34,
          value_color=NAVY):
    """Ряд карточек с крупной цифрой: [(значение, подпись, сноска)]."""
    count = len(items)
    width = (w - gap * (count - 1)) / count
    for index, item in enumerate(items):
        value, label = item[0], item[1]
        note = item[2] if len(item) > 2 else None
        left = x + index * (width + gap)
        card(slide, left, y, width, h)
        text(slide, left + 0.28, y + 0.20, width - 0.5, 0.62, value,
             value_size, value_color, bold=True)
        text(slide, left + 0.28, y + 0.82, width - 0.5, 0.34, label, 13,
             INK, bold=True)
        if note:
            text(slide, left + 0.28, y + 1.10, width - 0.5, 0.28, note, 11,
                 MUTED)


def panel(slide, x, y, w, h, fill=WHITE, line=LINE):
    return card(slide, x, y, w, h, fill=fill, line=line)


def note(slide, x, y, w, body, size=14, color=INK, fill=SOFT,
         accent=BRAND, height=None):
    """Выделенная вставка: акцентная полоса слева и подложка."""
    lines = est_lines(body, w - 0.55, size)
    h = height or ((1.32 + 1.50 * (lines - 1)) * size / 72 + 0.30)
    card(slide, x, y, w, h, fill=fill, line=None)
    bar(slide, x, y + 0.02, 0.055, h - 0.04, accent)
    text(slide, x + 0.26, y + 0.15, w - 0.5, h - 0.24, body, size, color,
         line_spacing=1.15)
    return y + h


def quote(slide, x, y, w, body, author=None, size=19):
    """Крупная цитата на тёмной подложке."""
    text_w = w - 1.45
    lines = est_lines(body, text_w, size)
    text_h = (1.32 + 1.50 * (lines - 1)) * size / 72
    h = 0.34 + text_h + (0.52 if author else 0.28)
    card(slide, x, y, w, h, fill=NAVY, line=None)
    text(slide, x + 0.38, y + 0.10, 0.5, 0.62, "«", 44, BRAND, bold=True)
    text(slide, x + 1.00, y + 0.32, text_w, text_h + 0.1, body, size, WHITE,
         line_spacing=1.22)
    if author:
        text(slide, x + 1.00, y + 0.34 + text_h + 0.10, w - 1.45, 0.26,
             author, 12, BLUE_INK)
    return y + h


def steps(slide, items, x, y, w, h=2.0, gap=0.38, numbered=True):
    """
    Горизонтальная цепочка шагов: [(заголовок, описание)].

    Между блоками рисуется стрелка — видно порядок, а не просто набор карточек.
    """
    count = len(items)
    width = (w - gap * (count - 1)) / count
    for index, (title, body) in enumerate(items):
        left = x + index * (width + gap)
        card(slide, left, y, width, h)
        text(slide, left + 0.28, y + 0.24, 0.4, 0.3,
             f"{index + 1}" if numbered else "", 12, BRAND, bold=True)
        text(slide, left + 0.28, y + 0.52, width - 0.56, 0.36, title, 16,
             NAVY, bold=True)
        text(slide, left + 0.28, y + 0.94, width - 0.56, h - 1.1, body, 13.5,
             INK, line_spacing=1.2)
        if index < count - 1:
            arrow(slide, left + width + 0.06, y + h / 2,
                  left + width + gap - 0.06, y + h / 2, color=BRAND, width=1.9)
    return y + h


def arrow(slide, x1, y1, x2, y2, color=BRAND, width=1.75, dashed=False):
    connector = slide.shapes.add_connector(
        MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    connector.line.color.rgb = rgb(color)
    connector.line.width = Pt(width)
    ln = connector.line._get_or_add_ln()
    if dashed:
        ln.append(parse_xml(f'<a:prstDash {A_NS} val="dash"/>'))
    ln.append(parse_xml(
        f'<a:tailEnd {A_NS} type="triangle" w="med" len="med"/>'))
    return connector


def arrow_label(slide, x, y, w, body, align=PP_ALIGN.CENTER):
    text(slide, x, y, w, 0.24, body, 10.5, MUTED, align=align)


def block(slide, x, y, w, h, title, body, fill=WHITE, accent=BRAND,
          title_size=16, body_size=13.5):
    """Прямоугольник на схеме: заголовок и уточнение."""
    card(slide, x, y, w, h, fill=fill)
    bar(slide, x, y + 0.02, 0.05, h - 0.04, accent)
    text(slide, x + 0.28, y + 0.22, w - 0.5, 0.34, title, title_size, NAVY,
         bold=True)
    if body:
        text(slide, x + 0.28, y + 0.62, w - 0.5, h - 0.75, body, body_size,
             INK, line_spacing=1.18)
    return x, y, w, h


# ── таблица ───────────────────────────────────────────────────────────────

_FILL_TAGS = {qn("a:noFill"), qn("a:solidFill"), qn("a:gradFill"),
              qn("a:pattFill"), qn("a:blipFill"), qn("a:grpFill")}


def _cell_border(cell, color=LINE, width_pt=0.75, edge="B"):
    """Граница ячейки. Вставляется до заливки: так требует схема OOXML."""
    tc_pr = cell._tc.get_or_add_tcPr()
    tag = f"a:ln{edge}"
    for old in tc_pr.findall(qn(tag)):
        tc_pr.remove(old)
    line = parse_xml(
        f'<{tag} {A_NS} w="{int(width_pt * 12700)}" cap="flat" cmpd="sng" '
        f'algn="ctr"><a:solidFill><a:srgbClr val="{color}"/></a:solidFill>'
        f'<a:prstDash val="solid"/></{tag}>')
    index = len(tc_pr)
    for position, child in enumerate(tc_pr):
        if child.tag in _FILL_TAGS:
            index = position
            break
    tc_pr.insert(index, line)


def _cell(cell, body, size=15, color=INK, bold=False,
          align=PP_ALIGN.LEFT, fill=WHITE):
    cell.fill.solid()
    cell.fill.fore_color.rgb = rgb(fill)
    cell.margin_left = Inches(0.18)
    cell.margin_right = Inches(0.12)
    cell.margin_top = Inches(0.10)
    cell.margin_bottom = Inches(0.10)
    cell.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf = cell.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = align
    _run(p, str(body), size, color, bold)


def table(slide, headers, rows, x, y, w, fractions, row_h=0.52,
          header_h=0.54, size=15, header_size=13.5, aligns=None,
          first_col_color=NAVY):
    """Таблица: тёмная шапка, белые строки, тонкие разделители."""
    aligns = aligns or [PP_ALIGN.LEFT] * len(headers)
    height = header_h + row_h * len(rows)
    frame = slide.shapes.add_table(len(rows) + 1, len(headers),
                                   Inches(x), Inches(y), Inches(w),
                                   Inches(height))
    tbl = frame.table
    tbl.first_row = False
    tbl.first_col = False
    tbl.horz_banding = False
    tbl.vert_banding = False
    tbl.rows[0].height = Inches(header_h)
    for index in range(1, len(rows) + 1):
        tbl.rows[index].height = Inches(row_h)
    for index, fraction in enumerate(fractions):
        tbl.columns[index].width = Inches(w * fraction)
    for column, head in enumerate(headers):
        _cell(tbl.cell(0, column), head, header_size, WHITE, bold=True,
              align=aligns[column], fill=NAVY)
    for row_index, row in enumerate(rows, start=1):
        for column, value in enumerate(row):
            bold = column == 0
            color = first_col_color if column == 0 else INK
            _cell(tbl.cell(row_index, column), value, size, color, bold=bold,
                  align=aligns[column], fill=WHITE)
            _cell_border(tbl.cell(row_index, column), LINE, 0.75, "B")
    return y + height

# ── титул и финал ─────────────────────────────────────────────────────────

def title_slide(prs, *, kicker, title, subtitle, university, meta, place,
                footer_text=None, title_size=33):
    """
    Титульный слайд: тёмный фон, название работы, выходные данные.

    Метаданные — три пары «подпись / значение» в одну строку: так они
    читаются как колонки, а не как список.
    """
    slide = blank(prs)
    background(slide, NAVY)
    _shape(slide, 9.05, 0, 4.283, SLIDE_H, fill=NAVY_2)
    _shape(slide, 9.05, 0, 0.02, SLIDE_H, fill=BRAND)

    _shape(slide, 0.85, 0.62, 0.46, 0.46, fill=BRAND,
           shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.22)
    text(slide, 0.85, 0.735, 0.46, 0.28, "S", 17, WHITE, bold=True,
         align=PP_ALIGN.CENTER)
    text(slide, 1.45, 0.74, 5.0, 0.26, "StaffSense", 14.5, WHITE, bold=True,
         spacing=0.4)

    text(slide, 0.85, 1.28, 7.9, 0.5, university, 12, BLUE_INK)
    text(slide, 0.85, 2.30, 7.9, 0.28, kicker.upper(), 12, BLUE_INK,
         bold=True, spacing=1.6)
    frame = text(slide, 0.85, 2.66, 7.85, 1.75, title, title_size, WHITE,
                 bold=True, line_spacing=0.98)
    frame._parent.name = "Title"
    bar(slide, 0.85, 4.62, 1.55, 0.06, BRAND)
    text(slide, 0.85, 4.85, 7.6, 0.85, subtitle, 16.5, "C9D5E8",
         line_spacing=1.2)

    column = 0.0
    for label, value in meta:
        left = 0.85 + column
        text(slide, left, 5.92, 2.55, 0.24, label.upper(), 9.5, MUTED,
             bold=True, spacing=1.0)
        text(slide, left, 6.17, 2.6, 0.32, value, 13.5, WHITE)
        column += 2.45

    text(slide, 0.85, 6.82, 7.5, 0.3, place, 12, BLUE_INK)
    if footer_text:
        text(slide, 9.55, 6.82, 3.1, 0.3, footer_text, 10.5, BLUE_INK,
             align=PP_ALIGN.RIGHT)
    return slide


def closing_slide(prs, *, title, subtitle, contacts, foot, index, total,
                  label):
    """Финальный слайд: благодарность и ссылки."""
    slide = blank(prs)
    background(slide, NAVY)
    _shape(slide, 0, 0, 0.16, SLIDE_H, fill=BRAND)
    _shape(slide, 9.6, 0, SLIDE_W - 9.6, SLIDE_H, fill=NAVY_2)
    _shape(slide, 9.6, 0, 0.02, SLIDE_H, fill=BRAND)

    text(slide, 0.85, 2.05, 8.4, 0.28, "КОНЕЦ ВЫСТУПЛЕНИЯ", 11.5, BLUE_INK,
         bold=True, spacing=1.6)
    frame = text(slide, 0.85, 2.42, 8.4, 1.1, title, 38, WHITE, bold=True)
    frame._parent.name = "Title"
    bar(slide, 0.85, 3.62, 1.55, 0.06, BRAND)
    text(slide, 0.85, 3.86, 8.2, 0.6, subtitle, 16.5, "C9D5E8")

    line = 4.85
    for caption, value in contacts:
        text(slide, 0.85, line, 2.1, 0.26, caption.upper(), 9.5, MUTED,
             bold=True, spacing=1.0)
        text(slide, 2.95, line - 0.04, 6.3, 0.3, value, 14, WHITE)
        line += 0.52

    text(slide, 0.85, 6.82, 8.6, 0.3, foot, 11.5, BLUE_INK)
    text(slide, SLIDE_W - MARGIN - 1.4, 6.82, 1.4, 0.3, f"{index} / {total}",
         10.5, BLUE_INK, align=PP_ALIGN.RIGHT)
    return slide
