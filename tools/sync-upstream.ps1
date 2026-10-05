#Requires PWA? No. Simple rebase helper.
param([string]$Ref = "master")

$ErrorActionPreference = "Stop"
function Step($m) { Write-Host "`n== $m ==" -ForegroundColor Cyan }

Step "fetch upstream"
git fetch upstream
if ($LASTEXITCODE -ne 0) { throw "fetch failed" }

Step "rebase onto upstream/$Ref"
git rebase "upstream/$Ref"
if ($LASTEXITCODE -ne 0) {
  Write-Host "CONFLICT: чини только хуки в org.telegram.*, свой bossgram/ не трогай." -ForegroundColor Yellow
  Write-Host "  git status -> правишь -> git add -> git rebase --continue" -ForegroundColor Yellow
  exit 1
}

Step "check hooks patch still applies (if hooks not committed)"
if (Test-Path "patches/0001-boss-hooks.patch") {
  git apply --check "patches/0001-boss-hooks.patch" 2>$null
  if ($LASTEXITCODE -eq 0) { Write-Host "hooks patch applies clean" -ForegroundColor Green }
  else { Write-Host "hooks patch already in history or needs refresh — ok" -ForegroundColor DarkGray }
}

Step "check touch points + security"
python tools/check-touch-points.py
python tools/security-check.py

Step "done"
Write-Host "Дальше: скопируй bossgram/ в сборку Telegram и собери." -ForegroundColor Green
Write-Host "  xcopy bossgram ..\Telegram-upstream\TMessagesProj\src\main\java\com\bossgram /E /I /Y" -ForegroundColor Gray
