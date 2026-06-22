$ErrorActionPreference = "Stop"

$backendDir = "d:\SOLO-2\AI_solo_coder_task_B_151\backend"
$sourceBase = Join-Path $backendDir "src\main\java\com\waterwheel\chaintransmission"
$pkgPath = "com\waterwheel\chaintransmission"

function Copy-Dir {
    param(
        [string]$Source,
        [string]$Dest,
        [string]$Tag
    )
    if (Test-Path $Source) {
        if (!(Test-Path $Dest)) {
            New-Item -ItemType Directory -Path $Dest -Force | Out-Null
        }
        Copy-Item -Path (Join-Path $Source "*") -Destination $Dest -Recurse -Force
        $count = (Get-ChildItem -Path $Dest -Recurse -File).Count
        Write-Host ("[OK] " + $Tag + " - copied dir: " + $count + " files") -ForegroundColor Green
    } else {
        Write-Host ("[WARN] " + $Tag + " - source not found: " + $Source) -ForegroundColor Yellow
    }
}

function Copy-F {
    param(
        [string]$Source,
        [string]$Dest,
        [string]$Tag
    )
    if (Test-Path $Source) {
        $destDir = Split-Path $Dest -Parent
        if (!(Test-Path $destDir)) {
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        }
        Copy-Item -Path $Source -Destination $Dest -Force
        Write-Host ("[OK] " + $Tag + " - copied file") -ForegroundColor Green
    } else {
        Write-Host ("[WARN] " + $Tag + " - source not found: " + $Source) -ForegroundColor Yellow
    }
}

function List-ModuleFiles {
    param([string]$ModDir)
    $all = @()
    $paths = @("src\main\java", "src\test", "src\main\resources")
    foreach ($p in $paths) {
        $fp = Join-Path $ModDir $p
        if (Test-Path $fp) {
            Get-ChildItem -Path $fp -Recurse -File | ForEach-Object {
                $all += $_.FullName.Replace($ModDir + "\", "")
            }
        }
    }
    return $all
}

Write-Host "========== START ==========" -ForegroundColor Cyan
Write-Host ""

# 1. core-common
$mod = "core-common"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-Dir -Source (Join-Path $sourceBase "entity") -Dest (Join-Path $db "entity") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "dto") -Dest (Join-Path $db "dto") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "repository") -Dest (Join-Path $db "repository") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "events") -Dest (Join-Path $db "events") -Tag $mod
Copy-F -Source (Join-Path $sourceBase "config\CorsConfig.java") -Dest (Join-Path $db "config\CorsConfig.java") -Tag $mod
Copy-F -Source (Join-Path $sourceBase "config\MqttConfig.java") -Dest (Join-Path $db "config\MqttConfig.java") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "chain_simulator\config") -Dest (Join-Path $db "chain_simulator\config") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "efficiency_optimizer\config") -Dest (Join-Path $db "efficiency_optimizer\config") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "alarm_mqtt\config") -Dest (Join-Path $db "alarm_mqtt\config") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "dtu_receiver\config") -Dest (Join-Path $db "dtu_receiver\config") -Tag $mod
Write-Host ""

# 2. core-simulation
$mod = "core-simulation"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-Dir -Source (Join-Path $sourceBase "simulation") -Dest (Join-Path $db "simulation") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "chain_simulator\service") -Dest (Join-Path $db "chain_simulator\service") -Tag $mod
Write-Host ""

# 3. chain-comparator
$mod = "chain-comparator"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-F -Source (Join-Path $sourceBase "comparison\service\ChainTypeComparisonService.java") -Dest (Join-Path $db "comparison\service\ChainTypeComparisonService.java") -Tag $mod
Write-Host ""

# 4. era-comparator
$mod = "era-comparator"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-F -Source (Join-Path $sourceBase "comparison\service\EraComparisonService.java") -Dest (Join-Path $db "comparison\service\EraComparisonService.java") -Tag $mod
Write-Host ""

# 5. parallel-optimizer
$mod = "parallel-optimizer"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-Dir -Source (Join-Path $sourceBase "optimization") -Dest (Join-Path $db "optimization") -Tag $mod
Write-Host ""

# 6. vr-chain-pump
$mod = "vr-chain-pump"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-Dir -Source (Join-Path $sourceBase "virtualoperation") -Dest (Join-Path $db "virtualoperation") -Tag $mod
Write-Host ""

# 7. waterwheel-app
$mod = "waterwheel-app"
$md = Join-Path $backendDir $mod
$db = Join-Path $md "src\main\java\$pkgPath"
Write-Host "--- $mod ---" -ForegroundColor Magenta
Copy-F -Source (Join-Path $sourceBase "WaterwheelChainTransmissionApplication.java") -Dest (Join-Path $db "WaterwheelChainTransmissionApplication.java") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "controller") -Dest (Join-Path $db "controller") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "service") -Dest (Join-Path $db "service") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "dtu_receiver\service") -Dest (Join-Path $db "dtu_receiver\service") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "alarm_mqtt\service") -Dest (Join-Path $db "alarm_mqtt\service") -Tag $mod
Copy-Dir -Source (Join-Path $sourceBase "efficiency_optimizer\service") -Dest (Join-Path $db "efficiency_optimizer\service") -Tag $mod
Write-Host ""
Write-Host "--- $mod (test+resources) ---" -ForegroundColor Magenta
Copy-Dir -Source (Join-Path $backendDir "src\test") -Dest (Join-Path $md "src\test") -Tag ($mod + "-test")
Copy-Dir -Source (Join-Path $backendDir "src\main\resources") -Dest (Join-Path $md "src\main\resources") -Tag ($mod + "-res")
Write-Host ""

Write-Host "========== DONE ==========" -ForegroundColor Cyan
Write-Host ""
Write-Host "========== FILE LIST ==========" -ForegroundColor Cyan

$mods = @("core-common", "core-simulation", "chain-comparator", "era-comparator", "parallel-optimizer", "vr-chain-pump", "waterwheel-app")
foreach ($m in $mods) {
    $mdir = Join-Path $backendDir $m
    $fs = List-ModuleFiles -ModDir $mdir
    Write-Host ""
    Write-Host ("[" + $m + "] " + $fs.Count + " files:") -ForegroundColor Yellow
    foreach ($f in $fs) {
        Write-Host ("  - " + $f)
    }
}
