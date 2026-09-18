# M2 — Tavuno Data Foundation

## Status

COMPLETE — validated locally on 2026-09-18.

## Scope

M2 defines Tavuno-owned business data in Directus/PostgreSQL. It does not add IPTV sources, stream playback, a custom backend, or StreamVault changes.

## Schema

The reproducible Directus schema is exported to `tavuno-infra/schema/M2_Directus_Schema.yaml`.

Core collections:

- Profiles and devices: `tavuno_profiles`, `tavuno_devices`
- Commerce and access: `tavuno_plans`, `tavuno_subscriptions`, `tavuno_entitlements`
- Live catalog: `tavuno_categories`, `tavuno_channels`, `tavuno_channel_sources`
- EPG: `tavuno_epg_channels`, `tavuno_epg_programmes`
- VOD catalog: `tavuno_movies`, `tavuno_series`, `tavuno_seasons`, `tavuno_episodes`
- Authorization and audit: `tavuno_playback_sessions`, `tavuno_audit_logs`

Tavuno tables own their own IDs. Middleware references are stored only as `external_id` mappings; no client should depend on a Dispatcharr internal ID.

## Reproducibility

Apply the schema to the local Directus project:

```powershell
.\tavuno-infra\scripts\apply-m2-schema.ps1
```

Add safe local verification data:

```powershell
.\tavuno-infra\scripts\seed-m2.ps1
```

Both scripts are idempotent. The seed contains only `M2 Demo` records and no provider URLs, credentials, or media content.

## Permissions

The administrator role retains full Directus management capability for M2. Public access has not been granted to Tavuno collections. Client/API role policies belong with the Tavuno Control service in the next backend milestone.

## Validation

- [x] All 16 Tavuno collections created in Directus.
- [x] Required foreign-key relationships created.
- [x] Directus administrator profile remains present after schema application.
- [x] Demo plan, live category, channel, Dispatcharr mapping, and EPG programme seed successfully.
- [x] Schema snapshot exported for repeatable deployment.
