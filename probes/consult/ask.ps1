<#
.SYNOPSIS
  Query a consultation AI provider and write the answer to a file.

.DESCRIPTION
  One entry point for every consultation provider. Credentials are read from
  probes/consult/secrets.env, which is gitignored. The key is never printed,
  never written into a URL, and never passed on a command line.

  Runs in the background by design: a long consultation outlives a 30s shell
  timeout, so the script is launched detached and the caller polls for the
  output file.

.PARAMETER Provider
  claude | qwen   (matches the prefix in secrets.env)

.PARAMETER InFile
  File containing the prompt.

.PARAMETER OutFile
  Where to write the answer. Written atomically at the end.

.PARAMETER MaxTokens
  Response cap. Default 4000.

.EXAMPLE
  powershell -File probes\consult\ask.ps1 -Provider qwen -InFile q.txt -OutFile a.txt
#>
param(
    [Parameter(Mandatory=$true)][ValidateSet('claude','qwen')][string]$Provider,
    [Parameter(Mandatory=$true)][string]$InFile,
    [Parameter(Mandatory=$true)][string]$OutFile,
    [int]$MaxTokens = 4000
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot
$secretsPath = Join-Path $PSScriptRoot 'secrets.env'

if (-not (Test-Path $InFile))   { Write-Error "Prompt file not found: $InFile"; exit 2 }
if (-not (Test-Path $secretsPath)) {
    Write-Error "secrets.env not found at $secretsPath"; exit 2
}

# Parse KEY=VALUE. Never echo the values.
$cfg = @{}
Get-Content $secretsPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $k, $v = $line.Split('=', 2)
        $cfg[$k.Trim()] = $v.Trim()
    }
}

$baseUrl = $cfg["${Provider}_BASE_URL"]
$apiKey  = $cfg["${Provider}_API_KEY"]
$model   = $cfg["${Provider}_MODEL"]

if (-not $baseUrl -or -not $apiKey -or -not $model) {
    Write-Error "Provider '$Provider' is not fully configured in secrets.env"
    exit 2
}

$prompt = [IO.File]::ReadAllText((Resolve-Path $InFile))

$payload = @{
    model       = $model
    messages    = @(@{ role = 'user'; content = $prompt })
    max_tokens  = $MaxTokens
    temperature = 0.3
}
$json = $payload | ConvertTo-Json -Depth 6 -Compress

$tmp = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($tmp, $json, (New-Object Text.UTF8Encoding $false))

$endpoint = "$baseUrl/chat/completions"
$utf8 = New-Object Text.UTF8Encoding $false

try {
    $r = Invoke-RestMethod -Uri $endpoint -Method Post `
        -Headers @{ Authorization = "Bearer $apiKey"; 'Content-Type' = 'application/json' } `
        -Body ([IO.File]::ReadAllText($tmp)) -TimeoutSec 900

    $content = $r.choices[0].message.content
    # Write to a temp sibling first, then move into place, so a poller never
    # observes a half-written answer file.
    $staging = "$OutFile.part"
    [IO.File]::WriteAllText($staging, $content, $utf8)
    Move-Item -Force $staging $OutFile
    Write-Output "OK $Provider/$model $($content.Length) chars -> $OutFile"
}
catch {
    $msg = $_.Exception.Message
    # Scrub the key from any error text before it is persisted or printed.
    if ($apiKey) { $msg = $msg.Replace($apiKey, '***') }
    [IO.File]::WriteAllText("$OutFile.part", "ERROR: $msg", $utf8)
    Move-Item -Force "$OutFile.part" $OutFile
    Write-Output "ERROR: $msg"
    exit 1
}
finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}