param(
  [string]$BaseUrl = 'http://localhost:8055',
  [string]$EnvironmentFile = "$PSScriptRoot\..\.env"
)

$ErrorActionPreference = 'Stop'

function Get-EnvironmentMap {
  $values = @{}
  Get-Content $EnvironmentFile | Where-Object { $_ -match '^[^#=]+=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    $values[$key.Trim()] = $value.Trim()
  }
  return $values
}

$environment = Get-EnvironmentMap
$login = @{ email = $environment['DIRECTUS_ADMIN_EMAIL']; password = $environment['DIRECTUS_ADMIN_PASSWORD'] } | ConvertTo-Json
$accessToken = (Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType 'application/json' -Body $login).data.access_token
$headers = @{ Authorization = "Bearer $accessToken" }

function Invoke-Directus([string]$Method, [string]$Path, $Body = $null) {
  $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; ContentType = 'application/json' }
  if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 12 }
  return Invoke-RestMethod @parameters
}

$existingCollections = @((Invoke-Directus 'Get' '/collections').data.collection)
function Ensure-Collection([string]$Name, [string]$Icon, [string]$Note, [string]$Template = '{{id}}') {
  if ($existingCollections -contains $Name) { return }
  Invoke-Directus 'Post' '/collections' @{ collection = $Name; meta = @{ icon = $Icon; note = $Note; display_template = $Template; accountability = 'all' }; schema = @{ name = $Name } } | Out-Null
  $script:existingCollections += $Name
}

$existingFields = @{}
function Ensure-Field([string]$Collection, [string]$Name, [string]$Type, [hashtable]$Schema, [bool]$Required = $false, [string]$Note = '') {
  if (-not $existingFields.ContainsKey($Collection)) {
    $existingFields[$Collection] = @((Invoke-Directus 'Get' "/fields/$Collection").data.field)
  }
  if ($existingFields[$Collection] -contains $Name) { return }
  $meta = @{ interface = if ($Type -eq 'boolean') { 'boolean' } elseif ($Type -eq 'text') { 'input-multiline' } elseif ($Type -eq 'timestamp') { 'datetime' } elseif ($Type -eq 'json') { 'input-code' } elseif ($Type -eq 'decimal') { 'input' } else { 'input' }; required = $Required; width = 'full'; note = $Note }
  Invoke-Directus 'Post' "/fields/$Collection" @{ field = $Name; type = $Type; meta = $meta; schema = $Schema } | Out-Null
  $existingFields[$Collection] += $Name
}

function String-Schema([int]$Length = 255, [bool]$Unique = $false, [bool]$Nullable = $true) { @{ name = ''; table = ''; data_type = 'character varying'; max_length = $Length; is_nullable = $Nullable; is_unique = $Unique } }
function Integer-Schema([bool]$Nullable = $true) { @{ name = ''; table = ''; data_type = 'integer'; is_nullable = $Nullable; is_unique = $false } }
function Boolean-Schema([bool]$Default = $false) { @{ name = ''; table = ''; data_type = 'boolean'; default_value = $Default; is_nullable = $false; is_unique = $false } }
function Text-Schema([bool]$Nullable = $true) { @{ name = ''; table = ''; data_type = 'text'; is_nullable = $Nullable; is_unique = $false } }
function DateTime-Schema([bool]$Nullable = $true) { @{ name = ''; table = ''; data_type = 'timestamp with time zone'; is_nullable = $Nullable; is_unique = $false } }
function Json-Schema([bool]$Nullable = $true) { @{ name = ''; table = ''; data_type = 'json'; is_nullable = $Nullable; is_unique = $false } }

@(
  @('tavuno_profiles', 'person', 'Customer-facing profile attached to a Directus user.', '{{display_name}}'),
  @('tavuno_devices', 'tv', 'Registered playback devices.', '{{name}}'),
  @('tavuno_plans', 'workspace_premium', 'Subscription plans available to Tavuno profiles.', '{{name}}'),
  @('tavuno_subscriptions', 'card_membership', 'Plan assignments and validity windows.', '{{id}}'),
  @('tavuno_entitlements', 'verified_user', 'Explicit content and package grants.', '{{id}}'),
  @('tavuno_categories', 'category', 'Catalog categories.', '{{name}}'),
  @('tavuno_channels', 'live_tv', 'Tavuno live-TV catalog.', '{{name}}'),
  @('tavuno_channel_sources', 'link', 'Dispatcharr-backed channel-source mappings.', '{{external_id}}'),
  @('tavuno_epg_channels', 'calendar_month', 'EPG identities mapped to Tavuno channels.', '{{external_id}}'),
  @('tavuno_epg_programmes', 'event_note', 'Normalized EPG programmes.', '{{title}}'),
  @('tavuno_movies', 'movie', 'VOD movie metadata.', '{{title}}'),
  @('tavuno_series', 'theaters', 'Series metadata.', '{{title}}'),
  @('tavuno_seasons', 'view_week', 'Series seasons.', '{{title}}'),
  @('tavuno_episodes', 'slideshow', 'Series episodes.', '{{title}}'),
  @('tavuno_playback_sessions', 'play_circle', 'Short-lived authorized playback sessions.', '{{id}}'),
  @('tavuno_audit_logs', 'history', 'Application-level security and operator audit trail.', '{{event_type}}')
) | ForEach-Object { Ensure-Collection $_[0] $_[1] $_[2] $_[3] }

Ensure-Field 'tavuno_profiles' 'directus_user' 'uuid' @{ name='directus_user'; table='tavuno_profiles'; data_type='uuid'; is_nullable=$false; is_unique=$true } $true 'The authenticated Directus account that owns this profile.'
Ensure-Field 'tavuno_profiles' 'display_name' 'string' (String-Schema 120 $false $false) $true 'Profile name shown in Tavuno clients.'
Ensure-Field 'tavuno_profiles' 'status' 'string' (String-Schema 32 $false $false) $true 'active, suspended, or deleted.'

Ensure-Field 'tavuno_devices' 'profile' 'integer' (Integer-Schema $false) $true 'Owning Tavuno profile.'
Ensure-Field 'tavuno_devices' 'name' 'string' (String-Schema 120 $false $false) $true 'Human-readable device name.'
Ensure-Field 'tavuno_devices' 'device_key' 'string' (String-Schema 255 $true $false) $true 'Stable, non-secret device identifier.'
Ensure-Field 'tavuno_devices' 'platform' 'string' (String-Schema 48 $false $false) $true 'Client platform, such as android_tv.'
Ensure-Field 'tavuno_devices' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether this device may start playback.'
Ensure-Field 'tavuno_devices' 'last_seen_at' 'timestamp' (DateTime-Schema $true) $false 'Last authenticated activity.'

Ensure-Field 'tavuno_plans' 'code' 'string' (String-Schema 64 $true $false) $true 'Stable internal plan code.'
Ensure-Field 'tavuno_plans' 'description' 'text' (Text-Schema $true) $false 'Operator-facing plan description.'
Ensure-Field 'tavuno_plans' 'max_devices' 'integer' (Integer-Schema $false) $true 'Maximum registered devices.'
Ensure-Field 'tavuno_plans' 'max_concurrent_streams' 'integer' (Integer-Schema $false) $true 'Maximum simultaneous playback sessions.'
Ensure-Field 'tavuno_plans' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the plan can be sold or assigned.'

Ensure-Field 'tavuno_subscriptions' 'profile' 'integer' (Integer-Schema $false) $true 'Subscribed profile.'
Ensure-Field 'tavuno_subscriptions' 'plan' 'integer' (Integer-Schema $false) $true 'Assigned plan.'
Ensure-Field 'tavuno_subscriptions' 'status' 'string' (String-Schema 32 $false $false) $true 'pending, active, cancelled, or expired.'
Ensure-Field 'tavuno_subscriptions' 'starts_at' 'timestamp' (DateTime-Schema $false) $true 'Subscription start.'
Ensure-Field 'tavuno_subscriptions' 'ends_at' 'timestamp' (DateTime-Schema $true) $false 'Subscription expiry, if any.'

Ensure-Field 'tavuno_entitlements' 'subscription' 'integer' (Integer-Schema $false) $true 'Granting subscription.'
Ensure-Field 'tavuno_entitlements' 'resource_type' 'string' (String-Schema 32 $false $false) $true 'channel, category, movie, series, or package.'
Ensure-Field 'tavuno_entitlements' 'resource_key' 'string' (String-Schema 128 $false $false) $true 'Stable resource identifier within the resource type.'
Ensure-Field 'tavuno_entitlements' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the entitlement is effective.'

Ensure-Field 'tavuno_categories' 'name' 'string' (String-Schema 120 $true $false) $true 'Category title.'
Ensure-Field 'tavuno_categories' 'kind' 'string' (String-Schema 32 $false $false) $true 'live, movie, series, or sports.'
Ensure-Field 'tavuno_categories' 'parent' 'integer' (Integer-Schema $true) $false 'Optional parent category.'
Ensure-Field 'tavuno_categories' 'sort_order' 'integer' (Integer-Schema $false) $true 'Catalog sort order.'
Ensure-Field 'tavuno_categories' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the category is published.'

Ensure-Field 'tavuno_channels' 'name' 'string' (String-Schema 160 $false $false) $true 'Channel display name.'
Ensure-Field 'tavuno_channels' 'slug' 'string' (String-Schema 160 $true $false) $true 'Stable client-facing channel key.'
Ensure-Field 'tavuno_channels' 'category' 'integer' (Integer-Schema $true) $false 'Primary catalog category.'
Ensure-Field 'tavuno_channels' 'logo' 'uuid' @{ name='logo'; table='tavuno_channels'; data_type='uuid'; is_nullable=$true; is_unique=$false } $false 'Optional Directus File channel logo.'
Ensure-Field 'tavuno_channels' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the channel is available.'

Ensure-Field 'tavuno_channel_sources' 'channel' 'integer' (Integer-Schema $false) $true 'Parent Tavuno channel.'
Ensure-Field 'tavuno_channel_sources' 'provider' 'string' (String-Schema 64 $false $false) $true 'Source system; Dispatcharr for M2.'
Ensure-Field 'tavuno_channel_sources' 'external_id' 'string' (String-Schema 128 $false $false) $true 'Source-system identifier.'
Ensure-Field 'tavuno_channel_sources' 'priority' 'integer' (Integer-Schema $false) $true 'Failover priority.'
Ensure-Field 'tavuno_channel_sources' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the mapping is eligible.'

Ensure-Field 'tavuno_epg_channels' 'channel' 'integer' (Integer-Schema $false) $true 'Mapped Tavuno channel.'
Ensure-Field 'tavuno_epg_channels' 'external_id' 'string' (String-Schema 255 $true $false) $true 'XMLTV or provider channel identifier.'
Ensure-Field 'tavuno_epg_programmes' 'epg_channel' 'integer' (Integer-Schema $false) $true 'Mapped EPG channel.'
Ensure-Field 'tavuno_epg_programmes' 'title' 'string' (String-Schema 255 $false $false) $true 'Programme title.'
Ensure-Field 'tavuno_epg_programmes' 'starts_at' 'timestamp' (DateTime-Schema $false) $true 'Programme start.'
Ensure-Field 'tavuno_epg_programmes' 'ends_at' 'timestamp' (DateTime-Schema $false) $true 'Programme end.'
Ensure-Field 'tavuno_epg_programmes' 'description' 'text' (Text-Schema $true) $false 'Programme synopsis.'

Ensure-Field 'tavuno_movies' 'title' 'string' (String-Schema 255 $false $false) $true 'Movie title.'
Ensure-Field 'tavuno_movies' 'slug' 'string' (String-Schema 255 $true $false) $true 'Stable client-facing movie key.'
Ensure-Field 'tavuno_movies' 'category' 'integer' (Integer-Schema $true) $false 'Primary movie category.'
Ensure-Field 'tavuno_movies' 'synopsis' 'text' (Text-Schema $true) $false 'Movie synopsis.'
Ensure-Field 'tavuno_movies' 'release_year' 'integer' (Integer-Schema $true) $false 'Release year.'
Ensure-Field 'tavuno_movies' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the movie is published.'

Ensure-Field 'tavuno_series' 'title' 'string' (String-Schema 255 $false $false) $true 'Series title.'
Ensure-Field 'tavuno_series' 'slug' 'string' (String-Schema 255 $true $false) $true 'Stable client-facing series key.'
Ensure-Field 'tavuno_series' 'category' 'integer' (Integer-Schema $true) $false 'Primary series category.'
Ensure-Field 'tavuno_series' 'synopsis' 'text' (Text-Schema $true) $false 'Series synopsis.'
Ensure-Field 'tavuno_series' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the series is published.'
Ensure-Field 'tavuno_seasons' 'series' 'integer' (Integer-Schema $false) $true 'Parent series.'
Ensure-Field 'tavuno_seasons' 'season_number' 'integer' (Integer-Schema $false) $true 'One-based season number.'
Ensure-Field 'tavuno_seasons' 'title' 'string' (String-Schema 255 $false $false) $true 'Season title.'
Ensure-Field 'tavuno_episodes' 'season' 'integer' (Integer-Schema $false) $true 'Parent season.'
Ensure-Field 'tavuno_episodes' 'episode_number' 'integer' (Integer-Schema $false) $true 'One-based episode number.'
Ensure-Field 'tavuno_episodes' 'title' 'string' (String-Schema 255 $false $false) $true 'Episode title.'
Ensure-Field 'tavuno_episodes' 'synopsis' 'text' (Text-Schema $true) $false 'Episode synopsis.'
Ensure-Field 'tavuno_episodes' 'is_active' 'boolean' (Boolean-Schema $true) $true 'Whether the episode is published.'

Ensure-Field 'tavuno_playback_sessions' 'profile' 'integer' (Integer-Schema $false) $true 'Profile initiating playback.'
Ensure-Field 'tavuno_playback_sessions' 'device' 'integer' (Integer-Schema $false) $true 'Device initiating playback.'
Ensure-Field 'tavuno_playback_sessions' 'content_type' 'string' (String-Schema 32 $false $false) $true 'channel, movie, or episode.'
Ensure-Field 'tavuno_playback_sessions' 'content_key' 'string' (String-Schema 128 $false $false) $true 'Stable content key.'
Ensure-Field 'tavuno_playback_sessions' 'status' 'string' (String-Schema 32 $false $false) $true 'created, playing, closed, or expired.'
Ensure-Field 'tavuno_playback_sessions' 'expires_at' 'timestamp' (DateTime-Schema $false) $true 'Authorization expiry.'
Ensure-Field 'tavuno_playback_sessions' 'last_seen_at' 'timestamp' (DateTime-Schema $true) $false 'Last client heartbeat.'

Ensure-Field 'tavuno_audit_logs' 'actor' 'uuid' @{ name='actor'; table='tavuno_audit_logs'; data_type='uuid'; is_nullable=$true; is_unique=$false } $false 'Directus user that initiated the event, if known.'
Ensure-Field 'tavuno_audit_logs' 'event_type' 'string' (String-Schema 120 $false $false) $true 'Machine-readable event name.'
Ensure-Field 'tavuno_audit_logs' 'subject_type' 'string' (String-Schema 64 $false $false) $true 'Type of affected resource.'
Ensure-Field 'tavuno_audit_logs' 'subject_key' 'string' (String-Schema 128 $false $false) $true 'Affected resource identifier.'
Ensure-Field 'tavuno_audit_logs' 'metadata' 'json' (Json-Schema $true) $false 'Non-secret structured event context.'
Ensure-Field 'tavuno_audit_logs' 'occurred_at' 'timestamp' (DateTime-Schema $false) $true 'Event timestamp.'

$existingRelations = @((Invoke-Directus 'Get' '/relations').data | ForEach-Object { "$($_.collection).$($_.field)" })
function Ensure-Relation([string]$Collection, [string]$Field, [string]$Related, [string]$OnDelete = 'SET NULL') {
  if ($existingRelations -contains "$Collection.$Field") { return }
  Invoke-Directus 'Post' '/relations' @{ collection = $Collection; field = $Field; related_collection = $Related; schema = @{ table = $Collection; column = $Field; foreign_key_table = $Related; foreign_key_column = 'id'; on_update = 'NO ACTION'; on_delete = $OnDelete }; meta = @{ many_collection = $Collection; many_field = $Field; one_collection = $Related; one_field = $null; one_deselect_action = if ($OnDelete -eq 'CASCADE') { 'delete' } else { 'nullify' } } } | Out-Null
  $script:existingRelations += "$Collection.$Field"
}

Ensure-Relation 'tavuno_profiles' 'directus_user' 'directus_users' 'CASCADE'
Ensure-Relation 'tavuno_devices' 'profile' 'tavuno_profiles' 'CASCADE'
Ensure-Relation 'tavuno_subscriptions' 'profile' 'tavuno_profiles' 'CASCADE'
Ensure-Relation 'tavuno_subscriptions' 'plan' 'tavuno_plans' 'RESTRICT'
Ensure-Relation 'tavuno_entitlements' 'subscription' 'tavuno_subscriptions' 'CASCADE'
Ensure-Relation 'tavuno_categories' 'parent' 'tavuno_categories'
Ensure-Relation 'tavuno_channels' 'category' 'tavuno_categories'
Ensure-Relation 'tavuno_channels' 'logo' 'directus_files'
Ensure-Relation 'tavuno_channel_sources' 'channel' 'tavuno_channels' 'CASCADE'
Ensure-Relation 'tavuno_epg_channels' 'channel' 'tavuno_channels' 'CASCADE'
Ensure-Relation 'tavuno_epg_programmes' 'epg_channel' 'tavuno_epg_channels' 'CASCADE'
Ensure-Relation 'tavuno_movies' 'category' 'tavuno_categories'
Ensure-Relation 'tavuno_series' 'category' 'tavuno_categories'
Ensure-Relation 'tavuno_seasons' 'series' 'tavuno_series' 'CASCADE'
Ensure-Relation 'tavuno_episodes' 'season' 'tavuno_seasons' 'CASCADE'
Ensure-Relation 'tavuno_playback_sessions' 'profile' 'tavuno_profiles' 'CASCADE'
Ensure-Relation 'tavuno_playback_sessions' 'device' 'tavuno_devices' 'CASCADE'
Ensure-Relation 'tavuno_audit_logs' 'actor' 'directus_users'

Write-Host 'M2 Directus schema applied successfully.'
