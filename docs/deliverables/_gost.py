"""
Общий оформительский модуль для документов StaffSense.

Один файл на весь пакет: если поля, шрифт или отступы поменяются, это
произойдёт сразу во всех документах, а не в каждом по отдельности.

Оформление по ГОСТ 19 (ЕСПД) и требованиям к учебным документам:
Times New Roman 14 пт, интервал 1,5, поля 30/15/20/20 мм, абзацный отступ
1,25 см, выравнивание по ширине, нумерация страниц снизу по центру,
титульный лист без номера.
"""

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

FONT = "Times New Roman"
SIZE = Pt(12)
SMALL = Pt(11)


def _set_font(run, size=SIZE, bold=False, italic=False):
    run.font.name = FONT
    run.font.size = size
    run.bold = bold
    run.italic = italic
    # Кириллица наследуется от eastAsia-настройки, без неё Word подставит свой шрифт.
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    return run


def _plain_fonts(rpr, name=FONT):
    """
    Жёстко задаёт шрифт в rPr, убирая ссылки на шрифты темы.

    В шаблоне python-docx стили заголовков описаны через majorHAnsi: пока
    атрибут asciiTheme присутствует, Word берёт шрифт темы и игнорирует
    явно указанный Times New Roman.
    """
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    for attr in ("w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"):
        if rfonts.get(qn(attr)) is not None:
            del rfonts.attrib[qn(attr)]
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(attr), name)


def _toc_styles(doc):
    """
    Стили TOC 1 / TOC 2 — их Word применяет к строкам поля содержания.

    Без них обновлённое поле набирается шрифтом темы, а не Times New Roman.
    """
    from docx.enum.style import WD_STYLE_TYPE

    for name, indent_cm in (("TOC 1", 0.0), ("TOC 2", 0.75)):
        try:
            style = doc.styles[name]
        except KeyError:
            style = doc.styles.add_style(name, WD_STYLE_TYPE.PARAGRAPH)
            style.base_style = doc.styles["Normal"]
        style.font.name = FONT
        style.font.size = SIZE
        style.font.bold = False
        _plain_fonts(style.element.get_or_add_rPr())
        paragraph = style.paragraph_format
        paragraph.first_line_indent = Cm(0)
        paragraph.left_indent = Cm(indent_cm)
        paragraph.space_before = Pt(0)
        paragraph.space_after = Pt(2)
        paragraph.line_spacing = 1.15
        paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT


def _heading_styles(doc):
    """Heading 1/2 в оформлении документа: TNR 14, полужирный, чёрный."""
    for name, outline, before, after in (
        ("Heading 1", 0, 10, 6),
        ("Heading 2", 1, 6, 4),
    ):
        style = doc.styles[name]
        style.font.name = FONT
        style.font.size = Pt(14)
        style.font.bold = True
        style.font.italic = False
        style.font.color.rgb = RGBColor(0, 0, 0)
        rpr = style.element.get_or_add_rPr()
        _plain_fonts(rpr)
        color = rpr.find(qn("w:color"))
        if color is not None:
            for attr in ("w:themeColor", "w:themeShade", "w:themeTint"):
                if color.get(qn(attr)) is not None:
                    del color.attrib[qn(attr)]

        ppr = style.element.get_or_add_pPr()
        if ppr.find(qn("w:outlineLvl")) is None:
            level = OxmlElement("w:outlineLvl")
            level.set(qn("w:val"), str(outline))
            ppr.append(level)

        paragraph = style.paragraph_format
        paragraph.first_line_indent = Cm(0)
        paragraph.left_indent = Cm(0)
        paragraph.space_before = Pt(before)
        paragraph.space_after = Pt(after)
        paragraph.line_spacing = 1.15
        paragraph.keep_with_next = True
        paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT
        style.quick_style = True


def new_document() -> Document:
    """Документ с полями и стилем Normal по ГОСТ."""
    doc = Document()

    section = doc.sections[0]
    # Шаблон python-docx — Letter; для учебных документов нужен A4.
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2)
    section.bottom_margin = Cm(2)
    section.left_margin = Cm(3)
    section.right_margin = Cm(1.5)

    style = doc.styles["Normal"]
    style.font.name = FONT
    style.font.size = SIZE
    style.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    paragraph = style.paragraph_format
    paragraph.line_spacing = 1.15
    paragraph.space_after = Pt(0)
    paragraph.space_before = Pt(0)
    paragraph.first_line_indent = Cm(1.25)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

    # Заголовки разделов оформляются стилями Word: без них поле TOC ничего
    # не найдёт и содержание останется пустым.
    _heading_styles(doc)
    _toc_styles(doc)

    return doc


def _add_page_number(paragraph):
    """Номер страницы полем PAGE — обновляется самим Word."""
    run = paragraph.add_run()
    field_begin = OxmlElement("w:fldChar")
    field_begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = "PAGE"
    field_end = OxmlElement("w:fldChar")
    field_end.set(qn("w:fldCharType"), "end")
    run._r.append(field_begin)
    run._r.append(instruction)
    run._r.append(field_end)
    _set_font(run, SMALL)


def title_page(doc, *, university, department, title, subtitle, author,
               group, teacher, city, year, kind="ПОЯСНИТЕЛЬНАЯ ЗАПИСКА"):
    """
    Титульный лист. Номер страницы на нём не ставится: он идёт первым,
    а нумерация начинается со второго листа.
    """
    for line, size, bold in (
        (university, Pt(12), False),
        (department, Pt(12), False),
    ):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Cm(0)
        _set_font(p.add_run(line), size, bold)

    for _ in range(4):
        spacer = doc.add_paragraph()
        spacer.paragraph_format.first_line_indent = Cm(0)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    _set_font(p.add_run(kind), Pt(16), True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(8)
    _set_font(p.add_run(title), Pt(18), True)

    if subtitle:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.space_before = Pt(10)
        _set_font(p.add_run(subtitle), Pt(14), False)

    for _ in range(5):
        doc.add_paragraph().paragraph_format.first_line_indent = Cm(0)

    # Блок «выполнил / проверил» — таблица без границ, чтобы подписи стояли ровно.
    table = doc.add_table(rows=4, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    rows = (
        ("Выполнил:", author),
        ("Группа:", group),
        ("Руководитель:", teacher),
        ("", ""),
    )
    for index, (left, right) in enumerate(rows):
        for col, text in enumerate((left, right)):
            cell = table.cell(index, col)
            cell.text = ""
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.first_line_indent = Cm(0)
            paragraph.paragraph_format.line_spacing = 1.0
            paragraph.alignment = (WD_ALIGN_PARAGRAPH.RIGHT if col == 0
                                   else WD_ALIGN_PARAGRAPH.LEFT)
            _set_font(paragraph.add_run(text), Pt(13))
    table.columns[0].width = Cm(6)
    table.columns[1].width = Cm(8)

    for _ in range(3):
        doc.add_paragraph().paragraph_format.first_line_indent = Cm(0)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    _set_font(p.add_run(f"{city} · {year}"), Pt(13))

    doc.add_page_break()


def _field_char(paragraph, kind):
    """Служебный символ поля Word (begin / separate / end)."""
    run = paragraph.add_run()
    _set_font(run)
    element = OxmlElement("w:fldChar")
    element.set(qn("w:fldCharType"), kind)
    if kind == "begin":
        element.set(qn("w:dirty"), "true")
    run._r.append(element)
    return run


def _set_update_fields(doc):
    """
    w:updateFields=true — Word пересобирает поле TOC при открытии файла.

    Без этого содержания не будет видно до ручного F9: у поля нет
    закэшированного результата, только подсказка.
    """
    settings = doc.settings.element
    for old in settings.findall(qn("w:updateFields")):
        settings.remove(old)
    element = OxmlElement("w:updateFields")
    element.set(qn("w:val"), "true")
    # Порядок элементов в settings.xsd: updateFields идёт перед compat/rsids.
    anchor = None
    for tag in ("w:hdrShapeDefaults", "w:footnotePr", "w:endnotePr",
                "w:compat", "w:docVars", "w:rsids"):
        anchor = settings.find(qn(tag))
        if anchor is not None:
            break
    if anchor is not None:
        anchor.addprevious(element)
    else:
        settings.append(element)


def toc(doc, entries=None):
    """
    Содержание полем Word: TOC \\o "1-2" \\h \\z \\u.

    Записи вручную больше не перечисляются — поле собирает их по стилям
    Heading 1 / Heading 2. Параметр entries оставлен для совместимости с
    генераторами, которые ещё передают список разделов.
    """
    heading(doc, "СОДЕРЖАНИЕ", numbered=False, center=True)

    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.first_line_indent = Cm(0)
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.paragraph_format.line_spacing = 1.15
    paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT

    _field_char(paragraph, "begin")
    run = paragraph.add_run()
    _set_font(run)
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = ' TOC \\o "1-2" \\h \\z \\u '
    run._r.append(instruction)
    _field_char(paragraph, "separate")

    hint = paragraph.add_run("Обновите поле (F9)")
    _set_font(hint)
    _field_char(paragraph, "end")

    _set_update_fields(doc)
    doc.add_page_break()


def heading(doc, text, *, level=1, numbered=True, center=False, in_toc=None):
    """
    Заголовок раздела стилем Heading 1 / Heading 2.

    Стиль нужен не для красоты: поле TOC находит разделы только по стилям.
    Внешний вид (TNR 14, полужирный, чёрный, отступы) задан в _heading_styles.
    """
    if in_toc is None:
        in_toc = not center

    p = doc.add_paragraph()
    if in_toc and level in (1, 2):
        p.style = doc.styles[f"Heading {level}"]

    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(10 if level == 1 else 6)
    p.paragraph_format.space_after = Pt(6 if level == 1 else 4)
    p.paragraph_format.keep_with_next = True
    p.alignment = (WD_ALIGN_PARAGRAPH.CENTER if center
                   else WD_ALIGN_PARAGRAPH.LEFT)
    run = p.add_run(text.upper() if level == 1 else text)
    _set_font(run, Pt(14), bold=True)
    return p


def para(doc, text, *, indent=True, align=WD_ALIGN_PARAGRAPH.JUSTIFY,
         bold=False, italic=False, size=SIZE, space_after=6):
    p = doc.add_paragraph()
    p.alignment = align
    p.paragraph_format.first_line_indent = Cm(1.25 if indent else 0)
    p.paragraph_format.space_after = Pt(space_after)
    _set_font(p.add_run(text), size, bold, italic)
    return p


def bullets(doc, items, *, dash="-"):
    for item in items:
        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.left_indent = Cm(1.25)
        p.paragraph_format.space_after = Pt(4)
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        _set_font(p.add_run(f"{dash} {item}"))


def table(doc, header, rows, *, widths=None, caption=None, font_size=SMALL):
    """Таблица с шапкой, повтором шапки на новой странице и рамками."""
    if caption:
        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.space_before = Pt(10)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.keep_with_next = True
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        _set_font(p.add_run(caption), SMALL, italic=True)

    result = doc.add_table(rows=1, cols=len(header))
    result.style = "Table Grid"
    result.alignment = WD_TABLE_ALIGNMENT.CENTER

    header_row = result.rows[0]
    header_row.cells[0].paragraphs[0].paragraph_format.first_line_indent = Cm(0)
    for index, text in enumerate(header):
        cell = header_row.cells[index]
        cell.text = ""
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.first_line_indent = Cm(0)
        paragraph.paragraph_format.line_spacing = 1.0
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        _set_font(paragraph.add_run(str(text)), font_size, bold=True)

    # Шапка повторяется, если таблица разорвана между страницами.
    tr_pr = header_row._tr.get_or_add_trPr()
    repeat = OxmlElement("w:tblHeader")
    repeat.set(qn("w:val"), "true")
    tr_pr.append(repeat)

    for row in rows:
        cells = result.add_row().cells
        for index, text in enumerate(row):
            cells[index].text = ""
            paragraph = cells[index].paragraphs[0]
            paragraph.paragraph_format.first_line_indent = Cm(0)
            paragraph.paragraph_format.line_spacing = 1.0
            paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT
            _set_font(paragraph.add_run("" if text is None else str(text)), font_size)

    if widths:
        for index, width in enumerate(widths):
            for row in result.rows:
                row.cells[index].width = Cm(width)
        _fix_table_layout(result, widths)

    doc.add_paragraph().paragraph_format.space_after = Pt(6)
    return result


def _fix_table_layout(table_, widths):
    """
    Жёстко фиксирует раскладку таблицы.

    Без этого Word пересчитывает колонки как равные: ширины ячеек игнорируются,
    таблица раздувается и документ вырастает вдвое. Нужны три вещи сразу —
    фиксированная раскладка, общая ширина и явная сетка колонок.
    """
    tbl_pr = table_._tbl.tblPr

    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tbl_pr.append(layout)

    total = sum(widths)
    width_element = OxmlElement("w:tblW")
    width_element.set(qn("w:w"), str(int(total * 567)))  # сантиметры в twips
    width_element.set(qn("w:type"), "dxa")
    tbl_pr.append(width_element)

    grid = table_._tbl.find(qn("w:tblGrid"))
    if grid is not None:
        table_._tbl.remove(grid)
    grid = OxmlElement("w:tblGrid")
    for width in widths:
        column = OxmlElement("w:gridCol")
        column.set(qn("w:w"), str(int(width * 567)))
        grid.append(column)
    table_._tbl.insert(list(table_._tbl).index(tbl_pr) + 1, grid)


def figure_placeholder(doc, caption, *, height_cm=6.0):
    """
    Место под скриншот с подписью.

    Пока скриншота нет — рисуется рамка с текстом-подсказкой, что сюда
    вставить. Так видно, каких иллюстраций не хватает, и не приходится
    угадывать их по тексту.
    """
    table_ = doc.add_table(rows=1, cols=1)
    table_.style = "Table Grid"
    cell = table_.cell(0, 0)
    cell.text = ""
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.first_line_indent = Cm(0)
    paragraph.paragraph_format.line_spacing = 1.0
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_font(paragraph.add_run("[ место для скриншота ]"), SMALL, italic=True)

    paragraph_caption = doc.add_paragraph()
    paragraph_caption.paragraph_format.first_line_indent = Cm(0)
    paragraph_caption.paragraph_format.space_before = Pt(4)
    paragraph_caption.paragraph_format.space_after = Pt(10)
    paragraph_caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_font(paragraph_caption.add_run(caption), SMALL)
    return table_


def figure(doc, path, caption, *, width_cm=15.0):
    """Вставка готового изображения с подписью под ним."""
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.first_line_indent = Cm(0)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.add_run().add_picture(path, width=Cm(width_cm))

    paragraph_caption = doc.add_paragraph()
    paragraph_caption.paragraph_format.first_line_indent = Cm(0)
    paragraph_caption.paragraph_format.space_before = Pt(4)
    paragraph_caption.paragraph_format.space_after = Pt(10)
    paragraph_caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_font(paragraph_caption.add_run(caption), SMALL)


def note(doc, text):
    """Выделенная вставка: важное ограничение или предупреждение."""
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.left_indent = Cm(1)
    p.paragraph_format.right_indent = Cm(1)
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(8)
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    _set_font(p.add_run(text), SMALL, italic=True)
    return p


def code(doc, text):
    """Фрагмент кода или вывода: моноширинный, без отступа первой строки."""
    for line in text.strip("\n").split("\n"):
        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.left_indent = Cm(1)
        p.paragraph_format.line_spacing = 1.0
        p.paragraph_format.space_after = Pt(0)
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        run = p.add_run(line if line else " ")
        run.font.name = "Consolas"
        run.font.size = Pt(10)
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
    doc.add_paragraph().paragraph_format.space_after = Pt(6)


def finish(doc, *, numbering_from_zero=False):
    """
    Нумерация страниц в нижнем колонтитуле по центру.

    Титульный лист в той же секции, но с особым колонтитулом, поэтому
    номер на нём не печатается, а счёт страниц всё равно идёт с единицы.
    """
    section = doc.sections[0]
    section.different_first_page_header_footer = True

    header = section.footer.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.CENTER
    header.paragraph_format.first_line_indent = Cm(0)
    _add_page_number(header)
