# M5 — EPG System

## Status

COMPLETE — normalized guide schedule pipeline fully tested and validated.

## Purpose

Provides a clean, unified Electronic Program Guide (EPG) to Tavuno TV clients without requiring the client to parse raw XMLTV or interact with provider feeds.

## Architectural Pipeline

```
XMLTV / Provider Feed
        ↓
   Dispatcharr
        ↓
Tavuno Normalization (SyncService)
        ↓
PostgreSQL (tavuno_epg_channels + tavuno_epg_programmes)
        ↓
Tavuno Control API
        ↓
Android TV Client
```

## Implementation Details

1. **`app/sync_service.py`**:
   - Matches `tvg_id` across `tavuno_epg_channels` and maps to canonical `channel_id`.
   - Inserts and updates normalized programmes into `tavuno_epg_programmes` (`title`, `starts_at`, `ends_at`, `description`).
2. **Endpoints**:
   - `GET /v1/epg` — Retrieves programmes filtered by channel and timeline window.
   - `GET /v1/epg/channel/{channel_id}/now-next` — Resolves the current broadcast (`now`), immediate upcoming programme (`next`), and future programme (`later`).

## Real Testing Results

### Infrastructure Testing
- ✅ Docker infrastructure running with all services healthy
- ✅ Directus schema applied with all EPG tables
- ✅ Test data inserted: categories, channels, EPG mappings, programmes

### API Testing
- ✅ `GET /v1/epg/channel/2/now-next` returns NOW, NEXT, LATER programmes
- ✅ `GET /v1/epg` returns all programmes with channel filtering
- ✅ `GET /v1/epg?channel_id=2` correctly filters by channel
- ✅ EPG time-window resolution working with actual timestamps
- ✅ Channel to EPG programme relationships validated

### Validation

- [x] EPG channel relationship mapping verified.
- [x] Now / Next / Later time-window resolution tested with ascending timeline bounds.
- [x] Null safety handled when programmes are not scheduled.
- [x] Real API endpoints tested against live database.
- [x] Integration with PostgreSQL confirmed.
