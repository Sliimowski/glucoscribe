Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$baseDir = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$path = Join-Path $baseDir "screen_capture.png"

try {
    Write-Output "Checking for connected ADB devices..."
    Write-Output "Checking for connected ADB devices..."
    $adb_output = adb devices
    $adb_device = $null

    foreach ($line in ($adb_output -split "`n")) {
        $line = $line.Trim()
        if ($line -match "^(\S+)\s+device") {
            $adb_device = $matches[1]
            break
        }
    }


    if ($null -ne $adb_device) {
        Write-Output "Connected device found: $adb_device. Using ADB to capture screen."
        
        # Take screenshot on device. Using -p to save as png.
        adb -s $adb_device shell "screencap -p /sdcard/screen_temp.png"
        
        # Pull screenshot to host
        # Using a temporary file on the device first to ensure successful pull
        adb -s $adb_device pull /sdcard/screen_temp.png $path
        
        if ($LASTEXITCODE -eq 0) {
            Write-Output "SUCCESS: Saved via ADB to $path"
            return # Exit early if success
        } else {
            Write-Output "ADB pull/capture failed. Falling back to Windows GUI method."
        }
    }

    # --- Windows GUI Fallback ---
    Write-Output "Attempting Windows GUI capture..."
    $screens = [System.Windows.Forms.Screen]::All
    
    if ($screens.Count -eq 0) {
        Write-Output "Warning: System.Windows.Forms.Screen.All returned 0. Attempting to find primary screen..."
        $screen = [System.Windows.Forms.Screen]::Primary
    } else {
        $screen = $screens[0]
    }

    if ($null -eq $screen -or $null -eq $screen.Bounds) {
        Write-Output "ERROR: No screen detected. The current session may not have access to a display."
        exit 1
    }

    $width = $screen.Bounds.Width
    $height = $screen.Bounds.Height
    $left = $screen.Bounds.Left
    $top = $screen.Bounds.Top

    Write-Output "Targeting resolution: $width x $height at $left, $top"

    $bmp = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($left, $top, 0, 0, $bmp.Size)
    
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    
    $g.Dispose()
    $bmp.Dispose()
    
    Write-Output "SUCCESS: Saved via Windows GUI to $path"
}
catch {
    Write-Output "ERROR: $($_.Exception.Message)"
    exit 1
}
