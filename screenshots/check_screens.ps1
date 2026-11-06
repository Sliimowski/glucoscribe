Add-Type -AssemblyName System.Windows.Forms
$screens = [System.Windows.Forms.Screen]::All
Write-Output "Screens count: $($screens.Count)"
foreach($s in $screens) {
    Write-Output "Screen bounds: $($s.Bounds)"
}
