# ============================================================================
# Production Build Script for Vaulten App
# ============================================================================
# This script helps you build a production-ready APK
# Run from the project root directory
# ============================================================================

param(
    [switch]$GenerateKeystore,
    [switch]$BuildRelease,
    [switch]$BuildDebug,
    [switch]$Clean,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

function Show-Help {
    Write-Host ""
    Write-Host "Vaulten App - Production Build Script" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\build_prod.ps1 [options]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Options:" -ForegroundColor Green
    Write-Host "  -GenerateKeystore  Generate a new release keystore"
    Write-Host "  -BuildRelease      Build production APK (requires keystore)"
    Write-Host "  -BuildDebug        Build debug APK"
    Write-Host "  -Clean             Clean build directories"
    Write-Host "  -Help              Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Green
    Write-Host "  .\build_prod.ps1 -GenerateKeystore  # First time setup"
    Write-Host "  .\build_prod.ps1 -BuildRelease      # Build production APK"
    Write-Host "  .\build_prod.ps1 -Clean -BuildRelease  # Clean and build"
    Write-Host ""
}

function Test-JavaInstalled {
    try {
        $javaVersion = java -version 2>&1 | Select-String "version"
        Write-Host "Java found: $javaVersion" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "ERROR: Java is not installed or not in PATH" -ForegroundColor Red
        Write-Host "Please install JDK 17 or later" -ForegroundColor Yellow
        return $false
    }
}

function New-ReleaseKeystore {
    Write-Host ""
    Write-Host "Generating Release Keystore" -ForegroundColor Cyan
    Write-Host "============================" -ForegroundColor Cyan
    Write-Host ""
    
    if (Test-Path "vaulten-release.jks") {
        Write-Host "WARNING: vaulten-release.jks already exists!" -ForegroundColor Yellow
        $confirm = Read-Host "Do you want to overwrite it? (y/N)"
        if ($confirm -ne "y" -and $confirm -ne "Y") {
            Write-Host "Aborted." -ForegroundColor Red
            return
        }
        Remove-Item "vaulten-release.jks" -Force
    }
    
    Write-Host "Please provide the following information:" -ForegroundColor Yellow
    Write-Host ""
    
    $storePassword = Read-Host "Enter keystore password (min 6 chars)" -AsSecureString
    $storePasswordText = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword))
    
    $keyPassword = Read-Host "Enter key password (min 6 chars)" -AsSecureString
    $keyPasswordText = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword))
    
    $cn = Read-Host "Your name (CN)"
    $ou = Read-Host "Organization unit (OU)"
    $o = Read-Host "Organization (O)"
    $l = Read-Host "City (L)"
    $st = Read-Host "State (ST)"
    $c = Read-Host "Country code (C, e.g., US)"
    
    $dname = "CN=$cn, OU=$ou, O=$o, L=$l, ST=$st, C=$c"
    
    Write-Host ""
    Write-Host "Generating keystore..." -ForegroundColor Cyan
    
    $keytoolArgs = @(
        "-genkey",
        "-v",
        "-keystore", "vaulten-release.jks",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-alias", "vaulten",
        "-storepass", $storePasswordText,
        "-keypass", $keyPasswordText,
        "-dname", $dname
    )
    
    & keytool $keytoolArgs
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "Keystore generated successfully!" -ForegroundColor Green
        Write-Host ""
        
        # Create keystore.properties
        $propsContent = @"
# Keystore configuration for release signing
# Generated on $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
# DO NOT COMMIT THIS FILE TO VERSION CONTROL

storeFile=vaulten-release.jks
storePassword=$storePasswordText
keyAlias=vaulten
keyPassword=$keyPasswordText
"@
        
        Set-Content -Path "keystore.properties" -Value $propsContent
        
        Write-Host "Created keystore.properties" -ForegroundColor Green
        Write-Host ""
        Write-Host "IMPORTANT: Keep these files safe and backed up!" -ForegroundColor Yellow
        Write-Host "  - vaulten-release.jks (keystore file)" -ForegroundColor Yellow
        Write-Host "  - keystore.properties (passwords)" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "These files are in .gitignore and will NOT be committed." -ForegroundColor Cyan
    } else {
        Write-Host "ERROR: Failed to generate keystore" -ForegroundColor Red
    }
}

function Build-Release {
    Write-Host ""
    Write-Host "Building Release APK" -ForegroundColor Cyan
    Write-Host "====================" -ForegroundColor Cyan
    Write-Host ""
    
    # Check for keystore
    if (-not (Test-Path "keystore.properties")) {
        Write-Host "ERROR: keystore.properties not found!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Run with -GenerateKeystore first to create signing keys" -ForegroundColor Yellow
        Write-Host "Or copy keystore.properties.template to keystore.properties and fill in values" -ForegroundColor Yellow
        return
    }
    
    if (-not (Test-Path "vaulten-release.jks")) {
        Write-Host "ERROR: vaulten-release.jks not found!" -ForegroundColor Red
        Write-Host "Run with -GenerateKeystore first to create the keystore" -ForegroundColor Yellow
        return
    }
    
    Write-Host "Starting release build..." -ForegroundColor Cyan
    Write-Host ""
    
    & .\gradlew assembleRelease --no-daemon
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "Build completed successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "APK location:" -ForegroundColor Cyan
        Write-Host "  app/build/outputs/apk/release/app-release.apk" -ForegroundColor Yellow
        Write-Host ""
        
        # Show APK info
        $apkPath = "app/build/outputs/apk/release/app-release.apk"
        if (Test-Path $apkPath) {
            $apkSize = (Get-Item $apkPath).Length / 1MB
            Write-Host "APK Size: $([math]::Round($apkSize, 2)) MB" -ForegroundColor Green
        }
    } else {
        Write-Host ""
        Write-Host "ERROR: Build failed!" -ForegroundColor Red
    }
}

function Build-Debug {
    Write-Host ""
    Write-Host "Building Debug APK" -ForegroundColor Cyan
    Write-Host "==================" -ForegroundColor Cyan
    Write-Host ""
    
    & .\gradlew assembleDebug --no-daemon
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "Build completed successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "APK location:" -ForegroundColor Cyan
        Write-Host "  app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "ERROR: Build failed!" -ForegroundColor Red
    }
}

function Clean-Build {
    Write-Host ""
    Write-Host "Cleaning build directories..." -ForegroundColor Cyan
    
    & .\gradlew clean --no-daemon
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Clean completed!" -ForegroundColor Green
    }
}

# ============================================================================
# MAIN
# ============================================================================

if ($Help -or (-not $GenerateKeystore -and -not $BuildRelease -and -not $BuildDebug -and -not $Clean)) {
    Show-Help
    exit 0
}

# Check Java
if (-not (Test-JavaInstalled)) {
    exit 1
}

# Execute requested actions
if ($Clean) {
    Clean-Build
}

if ($GenerateKeystore) {
    New-ReleaseKeystore
}

if ($BuildRelease) {
    Build-Release
}

if ($BuildDebug) {
    Build-Debug
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
