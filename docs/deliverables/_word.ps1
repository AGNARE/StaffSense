param([string[]]$files)
# Обновление полей (TOC) в Word и замер реального числа страниц.
# Запуск: powershell -File _word.ps1 -files "a.docx","b.docx"
$ErrorActionPreference = "Stop"
$w = New-Object -ComObject Word.Application
$w.Visible = $false
$w.DisplayAlerts = 0
foreach ($p in $files) {
    $d = $w.Documents.Open($p, $false, $false)
    # Обновить все поля (включая TOC) и оглавление целиком.
    $d.Fields.Update() | Out-Null
    foreach ($toc in $d.TablesOfContents) { $toc.Update() }
    $d.Repaginate()
    $pages = $d.Content.Information(4)
    $h1 = 0; $h2 = 0
    foreach ($par in $d.Paragraphs) {
        $s = $par.Style.NameLocal
        if ($s -eq "Heading 1") { $h1++ }
        if ($s -eq "Heading 2") { $h2++ }
    }
    $d.Save()
    "{0} | страниц {1} | H1 {2} H2 {3} | слов {4}" -f (Split-Path $p -Leaf), $pages, $h1, $h2, $d.ComputeStatistics(0)
    $d.Close(0)
}
$w.Quit()
