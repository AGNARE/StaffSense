# Конвертация DOCX → PDF через Word COM с таймаутом.
# Использование: pwsh -File _docx2pdf.ps1 -In <docx> -Out <pdf>
param(
    [Parameter(Mandatory = $true)][string]$In,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$TimeoutSec = 120
)

$ErrorActionPreference = 'Stop'
$In = (Resolve-Path $In).Path
if (-not [System.IO.Path]::IsPathRooted($Out)) {
    $Out = Join-Path $PWD.Path $Out
}
$Out = [System.IO.Path]::GetFullPath($Out)
if (-not (Test-Path (Split-Path $Out))) { New-Item -ItemType Directory -Path (Split-Path $Out) -Force | Out-Null }

$job = Start-Job -ScriptBlock {
    param($src, $dst)
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    try {
        $doc = $word.Documents.Open($src, [ref]$false, [ref]$true)
        # 17 = wdExportFormatPDF
        $doc.ExportAsFixedFormat($dst, 17, $false, 0, 0, 0, 0, 0, $true, $true, 0, $true, $true, $false)
        $doc.Close([ref]$false)
    } finally {
        $word.Quit()
    }
} -ArgumentList $In, $Out

if (-not (Wait-Job $job -Timeout $TimeoutSec)) {
    Stop-Job $job -ErrorAction SilentlyContinue
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    Get-Process WINWORD -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Error "Word не уложился в $TimeoutSec с: PDF не собран"
    exit 2
}

Receive-Job $job | Out-Null
$state = $job.State
Remove-Job $job -Force -ErrorAction SilentlyContinue
if ($state -ne 'Completed') {
    Write-Error "Word завершился со сбоем: PDF не собран"
    exit 3
}
if (-not (Test-Path $Out)) {
    Write-Error "PDF не появился: $Out"
    exit 4
}
Write-Output ("pdf: {0} ({1:N0} bytes)" -f $Out, (Get-Item $Out).Length)
