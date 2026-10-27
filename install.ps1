# Install the latest cljp release on Windows.
#
#   irm https://raw.githubusercontent.com/thiagooak/cljp/main/install.ps1 | iex
#
# Environment:
#   CLJP_VERSION  release tag to install (default: latest), e.g. v0.1.0
#   CLJP_HOME     install root (default: %USERPROFILE%\.cljp); the binary goes in
#                 %CLJP_HOME%\bin, which is added to your user PATH

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue' # the progress bar makes Invoke-WebRequest very slow on 5.1

# Windows PowerShell 5.1 may default to TLS 1.0/1.1, which GitHub rejects.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Repo = 'thiagooak/cljp'
$Asset = 'cljp-windows-x86_64.exe'
$Version = if ($env:CLJP_VERSION) { $env:CLJP_VERSION } else { 'latest' }
$CljpHome = if ($env:CLJP_HOME) { $env:CLJP_HOME } else { Join-Path $env:USERPROFILE '.cljp' }
$BinDir = Join-Path $CljpHome 'bin'
$Target = Join-Path $BinDir 'cljp.exe'

$BaseUrl = if ($Version -eq 'latest') {
    "https://github.com/$Repo/releases/latest/download"
} else {
    "https://github.com/$Repo/releases/download/$Version"
}

# Only an x64 build is published; Windows on ARM runs it under emulation.
if ($env:PROCESSOR_ARCHITECTURE -notin 'AMD64', 'ARM64') {
    throw "no pre-built binary for $env:PROCESSOR_ARCHITECTURE; see https://github.com/$Repo#installation"
}

# Remember any cljp that is already reachable so we can warn about it later.
$Other = Get-Command cljp -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Other -and ($Other.Source -eq $Target)) { $Other = $null }

$Tmp = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $Tmp | Out-Null
try {
    Write-Host "Downloading $Asset ($Version)..."
    Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/$Asset" -OutFile (Join-Path $Tmp $Asset)
    Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/SHA256SUMS" -OutFile (Join-Path $Tmp 'SHA256SUMS')

    # SHA256SUMS lines look like: <64 hex chars>  <file name>
    $Expected = $null
    foreach ($Line in Get-Content (Join-Path $Tmp 'SHA256SUMS')) {
        if ($Line -match '^([0-9a-fA-F]{64})\s+\*?(.+)$' -and $Matches[2] -eq $Asset) {
            $Expected = $Matches[1]
        }
    }
    if (-not $Expected) { throw "$Asset not listed in SHA256SUMS" }
    $Actual = (Get-FileHash -Algorithm SHA256 -Path (Join-Path $Tmp $Asset)).Hash
    if ($Actual -ne $Expected) {
        throw "checksum mismatch for $Asset (expected $Expected, got $Actual)"
    }
    Write-Host 'Checksum OK.'

    New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
    Move-Item -Force -Path (Join-Path $Tmp $Asset) -Destination $Target
} finally {
    Remove-Item -Recurse -Force $Tmp -ErrorAction SilentlyContinue
}

Write-Host "Installed cljp to $Target"

# Add $BinDir to the *user* PATH via the registry. [Environment]::SetEnvironmentVariable
# would flatten an expandable (REG_EXPAND_SZ) PATH into a plain string, so write it directly.
function Test-SamePath($A, $B) {
    $A.TrimEnd('\') -ieq $B.TrimEnd('\')
}

$EnvKey = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true)
try {
    $UserPath = $EnvKey.GetValue('Path', '', 'DoNotExpandEnvironmentNames')
    $Entries = @($UserPath -split ';' | Where-Object { $_ })
    $OnUserPath = $false
    foreach ($Entry in $Entries) {
        if (Test-SamePath ([Environment]::ExpandEnvironmentVariables($Entry)) $BinDir) { $OnUserPath = $true }
    }

    if (-not $OnUserPath) {
        $NewPath = (@($BinDir) + $Entries) -join ';'
        $EnvKey.SetValue('Path', $NewPath, [Microsoft.Win32.RegistryValueKind]::ExpandString)

        # Tell running programs (Explorer, new terminals) that the environment changed.
        try {
            Add-Type -Namespace Cljp -Name Native -MemberDefinition @'
[DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint fuFlags, uint uTimeout, out UIntPtr lpdwResult);
'@
            $Result = [UIntPtr]::Zero
            [Cljp.Native]::SendMessageTimeout([IntPtr]0xffff, 0x1A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$Result) | Out-Null
        } catch { }

        Write-Host "Added $BinDir to your user PATH."
        Write-Host 'Open a new terminal to use cljp.'
    }
} finally {
    $EnvKey.Close()
}
$env:Path = "$BinDir;$env:Path" # make it work in this session too

# Windows searches the system PATH before the user PATH, and entries earlier in
# either win, so we can't promise this install takes precedence over another one.
if ($Other) {
    Write-Warning "another cljp exists at $($Other.Source) and may shadow this install. Run 'where.exe cljp' in a new terminal to see which one wins."
}

if (-not (Get-Command clojure -ErrorAction SilentlyContinue)) {
    Write-Host 'note: cljp requires the Clojure CLI (https://clojure.org/guides/install_clojure), which was not found on PATH.'
}
