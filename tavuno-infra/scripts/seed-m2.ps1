param(
  [string]$BaseUrl = 'http://localhost:8055',
  [string]$EnvironmentFile = "$PSScriptRoot\..\.env"
)

$ErrorActionPreference = 'Stop'
$environment = @{}
Get-Content $EnvironmentFile | Where-Object { $_ -match '^[^#=]+=' } | ForEach-Object {
  $key, $value = $_ -split '=', 2
  $environment[$key.Trim()] = $value.Trim()
}

$login = @{ email = $environment['DIRECTUS_ADMIN_EMAIL']; password = $environment['DIRECTUS_ADMIN_PASSWORD'] } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType 'application/json' -Body $login).data.access_token
$headers = @{ Authorization = "Bearer $token" }

function Get-OrCreate([string]$Collection, [hashtable]$Filter, [hashtable]$Item) {
  $query = ($Filter.GetEnumerator() | ForEach-Object { "filter[$($_.Key)][_eq]=$([uri]::EscapeDataString($_.Value))" }) -join '&'
  $existing = (Invoke-RestMethod -Method Get -Uri "$BaseUrl/items/$Collection`?$query" -Headers $headers).data
  if ($existing.Count -gt 0) { return $existing[0] }
  return (Invoke-RestMethod -Method Post -Uri "$BaseUrl/items/$Collection" -Headers $headers -ContentType 'application/json' -Body ($Item | ConvertTo-Json -Depth 8)).data
}

$plan = Get-OrCreate 'tavuno_plans' @{ code = 'M2_DEMO' } @{ code = 'M2_DEMO'; name = 'M2 Demo'; description = 'Local verification plan only.'; max_devices = 3; max_concurrent_streams = 1; is_active = $true }
$category = Get-OrCreate 'tavuno_categories' @{ name = 'M2 Demo Live' } @{ name = 'M2 Demo Live'; kind = 'live'; sort_order = 1000; is_active = $true }
$channel = Get-OrCreate 'tavuno_channels' @{ slug = 'm2-demo-channel' } @{ name = 'M2 Demo Channel'; slug = 'm2-demo-channel'; category = $category.id; is_active = $true }
$source = Get-OrCreate 'tavuno_channel_sources' @{ external_id = 'm2-demo-source' } @{ channel = $channel.id; provider = 'dispatcharr'; external_id = 'm2-demo-source'; priority = 1; is_active = $true }
$epgChannel = Get-OrCreate 'tavuno_epg_channels' @{ external_id = 'm2-demo-epg' } @{ channel = $channel.id; external_id = 'm2-demo-epg' }
$null = Get-OrCreate 'tavuno_epg_programmes' @{ title = 'M2 Demo Programme' } @{ epg_channel = $epgChannel.id; title = 'M2 Demo Programme'; starts_at = '2026-09-18T18:00:00Z'; ends_at = '2026-09-18T19:00:00Z'; description = 'Non-production M2 seed programme.' }

Write-Host "M2 seed complete: plan=$($plan.id), category=$($category.id), channel=$($channel.id), source=$($source.id)."
