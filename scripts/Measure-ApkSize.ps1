param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Baseline
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Measure-Apk([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    $archive = [IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $entries = @($archive.Entries | Where-Object { $_.Length -gt 0 })
        $groups = @($entries | Group-Object {
            if ($_.FullName -match '^classes\d*\.dex$') { 'dex' }
            elseif ($_.FullName.StartsWith('assets/')) { 'assets' }
            elseif ($_.FullName.StartsWith('res/')) { 'resources' }
            elseif ($_.FullName.StartsWith('lib/')) { 'native' }
            else { 'metadata' }
        } | ForEach-Object {
            [PSCustomObject]@{
                Category = $_.Name
                Files = $_.Count
                CompressedBytes = ($_.Group | Measure-Object CompressedLength -Sum).Sum
                RawBytes = ($_.Group | Measure-Object Length -Sum).Sum
            }
        } | Sort-Object Category)
        $fingerprints = @($entries | Where-Object {
            $_.FullName.StartsWith('res/') -or $_.FullName.StartsWith('assets/')
        } | ForEach-Object {
            $stream = $_.Open()
            try { $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
            finally { $stream.Dispose() }
            [PSCustomObject]@{ Name = $_.FullName; Bytes = $_.CompressedLength; Hash = $hash }
        })
        $duplicates = @($fingerprints | Group-Object Hash | Where-Object Count -gt 1 | ForEach-Object {
            # Keep the smallest encoded copy when calculating potential savings.
            $sizes = $_.Group | Measure-Object Bytes -Sum -Minimum
            [PSCustomObject]@{ Files = $_.Group.Name; RedundantBytes = $sizes.Sum - $sizes.Minimum }
        } | Sort-Object RedundantBytes -Descending)
        [PSCustomObject]@{
            File = $file.Name
            Bytes = $file.Length
            Sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            Groups = $groups
            ZipAndSigningOverheadBytes = $file.Length - ($groups | Measure-Object CompressedBytes -Sum).Sum
            RedundantBytes = [long]($duplicates | Measure-Object RedundantBytes -Sum).Sum
            Duplicates = $duplicates
            LargestEntries = @($entries | Sort-Object CompressedLength -Descending |
                Select-Object -First 12 FullName, Length, CompressedLength)
        }
    } finally { $archive.Dispose() }
}

$current = Measure-Apk $Apk
if ($Baseline) {
    $previous = Measure-Apk $Baseline
    $categories = @($current.Groups.Category) + @($previous.Groups.Category) | Sort-Object -Unique
    [PSCustomObject]@{
        Current = $current
        Baseline = $previous
        DeltaBytes = $current.Bytes - $previous.Bytes
        CategoryDelta = @($categories | ForEach-Object {
            $name = $_
            $after = ($current.Groups | Where-Object Category -eq $name | Measure-Object CompressedBytes -Sum).Sum
            $before = ($previous.Groups | Where-Object Category -eq $name | Measure-Object CompressedBytes -Sum).Sum
            [PSCustomObject]@{ Category = $name; DeltaBytes = $after - $before }
        })
    } | ConvertTo-Json -Depth 6
} else { $current | ConvertTo-Json -Depth 6 }
