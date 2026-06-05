<#
  End-to-end test driver for the Create Trip Plan Agent file flow.

  Prereqs:
    * App running on $BaseUrl (./mvnw spring-boot:run) — RESTART after code changes.
    * Postgres seeded:  psql ... -f seed_staff_department.sql   (corp 1234567890)
    * MinIO + qwen3-14b reachable.
    * Test files present (this folder): run  python gen_spreadsheets.py  and  python gen_trip_pdfs.py

  Run:  powershell -ExecutionPolicy Bypass -File src/test/data/run_tests.ps1
#>

$BaseUrl = "http://localhost:8080/api/v1/agent-conversations"
$Corp    = "1234567890"
$Dir     = $PSScriptRoot

function Upload($file) {
    $path = Join-Path $Dir $file
    $json = & curl.exe -s -X POST "$BaseUrl/files/create" -F "file=@$path"
    $id = ($json | ConvertFrom-Json).data.fileId
    Write-Host ("uploaded {0,-22} -> {1}" -f $file, $id)
    return $id
}

function TripPlan($body) {
    $json = $body | ConvertTo-Json -Depth 8
    $resp = Invoke-RestMethod -Uri "$BaseUrl/agents/trip-plan" -Method Post -ContentType "application/json" -Body $json
    return $resp.data
}

function Show($d) {
    Write-Host "  status     :" $d.status
    Write-Host "  subAgents  :" ($d.subAgents -join ", ")
    Write-Host "  reply      :" $d.reply
    Write-Host "  travelers  :" (($d.draftJson.TripInformation.Travelers | ForEach-Object { $_.Name }) -join ", ")
    Write-Host "  destination:" $d.draftJson.TripInformation.Destination "| purpose:" $d.draftJson.TripInformation.Purpose
    Write-Host "  missing    :" ($d.draftJson.missingFields -join ", ")
    Write-Host ""
}

Write-Host "`n=== Uploading files ===" -ForegroundColor Cyan
$xlsx    = Upload "staff_standard.xlsx"
$pdfOk   = Upload "toronto_booking.pdf"
$pdfBad  = Upload "busan_trip.pdf"
$pdfNda  = Upload "nda_contract.pdf"

$torontoMsg = "Business trip to Toronto, Canada from 2026-06-20 to 2026-06-25 for quarterly client meetings and partner site visits. The title is 'Business Trip to Toronto'. All travelers depart from Seoul."

Write-Host "=== Scenario 1: message + staff xlsx + matching Toronto PDF (PDF fills transport/return) ===" -ForegroundColor Cyan
$s1 = TripPlan @{ corpNo=$Corp; message=$torontoMsg; fileIds=@($xlsx,$pdfOk) }
Show $s1
$sid = $s1.sessionId

Write-Host "=== Scenario 2: message (Toronto) + DIFFERENT-trip PDF (Busan) -> PDF ignored by alignment ===" -ForegroundColor Cyan
Show (TripPlan @{ corpNo=$Corp; message=$torontoMsg; fileIds=@($pdfBad) })

Write-Host "=== Scenario 3: message (Toronto) + non-trip PDF (NDA) -> PDF ignored by relevance ===" -ForegroundColor Cyan
Show (TripPlan @{ corpNo=$Corp; message=$torontoMsg; fileIds=@($pdfNda) })

Write-Host "=== Scenario 4: PDF only, no message -> PDF is authoritative ===" -ForegroundColor Cyan
Show (TripPlan @{ corpNo=$Corp; fileIds=@($pdfOk) })

Write-Host "=== Scenario 5: spreadsheet only ===" -ForegroundColor Cyan
Show (TripPlan @{ corpNo=$Corp; fileIds=@($xlsx) })

Write-Host "Done. First session id = $sid" -ForegroundColor Green
