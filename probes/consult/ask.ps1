<#
.SYNOPSIS
  Query a consultation AI provider inside a PERSISTENT, accumulating session.

.DESCRIPTION
  A bare chat-completions call is STATELESS: every request is an independent
  conversation with no memory of what was asked before. That breaks consultation
  in two ways:

    1. Cost/latency - the whole project briefing is restated per question.
    2. CORRECTNESS - the model cannot see its own earlier answers, so it can
       contradict itself across calls. A reviewer told "the app has no INTERNET
       permission" in turn 1 and "you added INTERNET" in turn 5 has no way to
       notice the transition.

  This script keeps the full message array per session on disk. A session is
  seeded once with PROJECT_CONTEXT.md, and every later question appends to the
  same history, so the model reasons against everything it was already told in
  that session.

  Secrets come from secrets.env (gitignored) and are never passed on a command
  line or placed in a URL. Error text is scrubbed of the key.

  Run detached: consultations outlive a 30s shell timeout.

.PARAMETER Session
  Session name. Reuse a name to continue that conversation. -New discards history.

.PARAMETER Question
  This turn's question. On a session's first turn this follows the project
  context, so it can be short.

.PARAMETER OutFile
  Where to write this turn's answer. Staged then moved so a polling caller
  never reads a partial file.

.EXAMPLE
  powershell -File probes\consult\ask.ps1 -Session m21 -Question "..."
  powershell -File probes\consult\ask.ps1 -Session m21 -Question "And API 27?"
#>
param(
    [string]$Provider = 'claude',
    [string]$Session = 'default',
    [string]$Question,
    [Parameter(Mandatory=$true)][string]$OutFile,
    [int]$MaxTokens = 4000,
    [switch]$New,
    [switch]$NoContext,
    [switch]$NoTranscript,
    [string]$QuestionFile
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

# ---------------------------------------------------------------------------
# SESSION CONTEXT BUDGETING
# ---------------------------------------------------------------------------
# Both endpoints have an input ceiling and signal it differently:
#   qwen   -> HTTP 400
#   claude -> HTTP 400 / 502 (measured: ~5 KB ceiling, fails from 7 KB)
# A session that grows past the ceiling fails on the NEXT turn, which looks
# like a provider outage rather than our own bug. So replayed history is
# capped: the oldest turns are dropped first, the project context is pinned.
# ---------------------------------------------------------------------------

function Get-ReplayBudget {
    if ($Provider -eq 'claude') { return 4000 }
    return 24000
}

function Select-ReplayWindow([object[]]$all) {
    $budget = Get-ReplayBudget
    $keep = New-Object System.Collections.ArrayList
    $used = 0
    for ($i = $all.Count - 1; $i -ge 0; $i--) {
        $len = ([string]$all[$i].content).Length
        if ($used + $len -gt $budget) { break }
        [void]$keep.Insert(0, $all[$i])
        $used += $len
    }
    if ($all.Count -gt 0 -and -not $keep.Contains($all[0])) {
        $ctxLen = ([string]$all[0].content).Length
        if ($ctxLen -le $budget) { [void]$keep.Insert(0, $all[0]) }
    }
    return $keep
}
$secretsPath = Join-Path $PSScriptRoot 'secrets.env'
$stateDir    = Join-Path $PSScriptRoot 'sessions'
$stateFile   = Join-Path $stateDir "$Session.json"

if ($QuestionFile) {
    if (-not (Test-Path $QuestionFile)) { Write-Error "QuestionFile not found: $QuestionFile"; exit 2 }
    $Question = [IO.File]::ReadAllText((Resolve-Path $QuestionFile))
}
if (-not $Question) { Write-Error '-Question or -QuestionFile is required'; exit 2 }
if (-not (Test-Path $secretsPath)) { Write-Error 'secrets.env not found'; exit 2 }
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

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


# ---- load or start the conversation --------------------------------------
$utf8 = New-Object System.Text.UTF8Encoding $false

if ($New -or -not (Test-Path $stateFile)) {
    $messages = New-Object System.Collections.ArrayList
    if (-not $NoContext) {
        $ctxPath = Join-Path $PSScriptRoot 'PROJECT_CONTEXT.md'
        if (Test-Path $ctxPath) {
            $ctx = [IO.File]::ReadAllText($ctxPath)
            [void]$messages.Add(@{ role = 'user'; content = $ctx })
            [void]$messages.Add(@{ role = 'assistant'; content =
                'Context loaded. I have the project briefing. Ask your first question.' })
        }
    }
} else {
    $loaded = Get-Content $stateFile -Raw | ConvertFrom-Json
    $messages = New-Object System.Collections.ArrayList
    foreach ($m in $loaded.messages) {
        [void]$messages.Add(@{ role = $m.role; content = $m.content })
    }
}

# The claude endpoint rejects multi-message payloads (502, reproduced 5/5).
# Its session continuity therefore has to be delivered INSIDE one user message
# as a labelled transcript. Verified: the model answers correctly from it, so
# this is a transport workaround, not a capability compromise.
if ($Provider -eq 'claude') {
    $parts = @()
    foreach ($m in $messages) {
        $body = [string]$m.content
        if ($m.role -eq 'system') { continue }
        $parts += ("### {0}`n{1}" -f $m.role.ToUpper(), $body)
    }
    $merged = ($parts -join "`n`n") + "`n`n### CURRENT QUESTION (most recent USER block above may already contain it)`n$Question"
    $messages = New-Object System.Collections.ArrayList
    [void]$messages.Add(@{ role = 'user'; content = $merged })
} else {
    [void]$messages.Add(@{ role = 'user'; content = $Question })
}

# Persist the request BEFORE calling, so a crash cannot lose history.
$save = @{ provider = $Provider; model = $model; messages = $messages }
[IO.File]::WriteAllText($stateFile, ($save | ConvertTo-Json -Depth 8), $utf8)

# Trim replayed history to the provider's budget before building the payload.
$replay = Select-ReplayWindow @($messages)
if ($replay.Count -lt $messages.Count) {
    Write-Output "NOTE: history trimmed $($messages.Count) -> $($replay.Count) turns (budget $(Get-ReplayBudget))"
    $messages = New-Object System.Collections.ArrayList
    foreach ($m in $replay) { [void]$messages.Add($m) }
}
$payload = @{
    model       = $model
    messages    = $messages
    max_tokens  = $MaxTokens
    temperature = 0.3
}
$json = $payload | ConvertTo-Json -Depth 8 -Compress

$tmp = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($tmp, $json, $utf8)
$endpoint = "$baseUrl/chat/completions"

# ---------------------------------------------------------------------------
# RETRY WITH BACKOFF
# ---------------------------------------------------------------------------
# Measured: the Qwen endpoint returns HTTP 400 for a payload that succeeds on
# the next attempt, and a tiny payload timed out once out of six calls. The 400s
# are transient, not a context-limit signal - a real context limit is
# reproducible, and 48 KB was observed failing and succeeding on the same day.
#
# So a single 400 must be retried before it is believed. Retrying is safe: the
# session file is written before the call, and a duplicate question turn is
# harmless to the model's understanding.
# ---------------------------------------------------------------------------

$maxAttempts = 4
$attempt = 0
$r = $null
$failure = $null

while ($attempt -lt $maxAttempts) {
    $attempt++
    try {
        $r = Invoke-RestMethod -Uri $endpoint -Method Post `
            -Headers @{ Authorization = "Bearer $apiKey"; 'Content-Type' = 'application/json' } `
            -Body ([IO.File]::ReadAllText($tmp)) -TimeoutSec 900
        break
    } catch {
        $failure = $_
        if ($attempt -lt $maxAttempts) {
            $waitSec = [Math]::Min(20, 2 * $attempt)
            Write-Output "attempt $attempt failed, retrying in ${waitSec}s"
            Start-Sleep -Seconds $waitSec
        }
    }
}

if ($null -eq $r) { throw $failure }
try {
    $content = $r.choices[0].message.content

    # Append the answer so the NEXT turn sees it.
    [void]$messages.Add(@{ role = 'assistant'; content = $content })
    $save = @{ provider = $Provider; model = $model; messages = $messages }
    [IO.File]::WriteAllText($stateFile, ($save | ConvertTo-Json -Depth 8), $utf8)

    $staging = "$OutFile.part"
    [IO.File]::WriteAllText($staging, $content, $utf8)
    Move-Item -Force $staging $OutFile

    $turns = @($messages | Where-Object { $_.role -eq 'user' }).Count
    Write-Output "OK session=$Session turn=$turns provider=$Provider chars=$($content.Length)"
}
catch {
    $msg = $_.Exception.Message
    # A 400 from a chat-completions endpoint names the offending field in the
    # response BODY; the exception message alone ("Bad Request") hides it.
    try {
        $resp = $_.Exception.Response
        if ($null -ne $resp) {
            $reader = New-Object IO.StreamReader($resp.GetResponseStream())
            $body = $reader.ReadToEnd()
            if ($body) { $msg = "HTTP $([int]$resp.StatusCode): $body" }
        }
    } catch { }
    if ($apiKey) { $msg = $msg.Replace($apiKey, '***') }
    [IO.File]::WriteAllText("$OutFile.part", "ERROR: $msg", $utf8)
    Move-Item -Force "$OutFile.part" $OutFile
    Write-Output "ERROR: $msg"
    exit 1
}
finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}