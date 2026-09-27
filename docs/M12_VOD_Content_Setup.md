# M12 VOD Content Setup Runbook

This guide explains how to populate realistic VOD (movies and series) content in Dispatcharr and sync it into Tavuno's catalog.

## Prerequisites

- Tavuno backend running with Dispatcharr integration configured
- Access to Dispatcharr admin UI or API
- Dispatcharr version 0.28.0 (matching Tavuno's expected contract)

## Step 1: Configure VOD Categories in Dispatcharr

1. Log in to Dispatcharr admin UI
2. Navigate to **VOD** > **Categories**
3. Create categories for your content types:
   - **Action** (type: movie)
   - **Drama** (type: series)
   - **Comedy** (type: movie)
   - Add any other categories you need

The category type determines whether content under it is treated as movies or series in Tavuno.

## Step 2: Add Movies to Dispatcharr

### Option A: Via Dispatcharr Admin UI

1. Navigate to **VOD** > **Movies**
2. Click **Add Movie**
3. Fill in the required fields:
   - **Name**: The movie title (e.g., "The Matrix")
   - **Year**: Release year (e.g., 1999)
   - **Category**: Select the appropriate category (e.g., "Action")
4. Optional fields (recommended for better UX):
   - **Description**: Synopsis or plot summary
   - **Poster**: Cover art URL
5. Add stream URL:
   - In the movie details, add a stream source
   - Stream URL format: `http://your-server/stream.m3u8` or similar
6. Save the movie

### Option B: Via Dispatcharr API

If Dispatcharr supports API-based movie creation, you can use the API endpoint:

```bash
curl -X POST http://dispatcharr:9191/api/vod/movies/ \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "The Matrix",
    "year": 1999,
    "category_id": 10,
    "description": "A computer hacker learns about the true nature of reality."
  }'
```

## Step 3: Add Series to Dispatcharr

### Option A: Via Dispatcharr Admin UI

1. Navigate to **VOD** > **Series**
2. Click **Add Series**
3. Fill in the required fields:
   - **Name**: The series title (e.g., "Breaking Bad")
   - **Category**: Select the appropriate category (e.g., "Drama")
4. Optional fields:
   - **Description**: Series synopsis
   - **Poster**: Cover art URL
5. Save the series

### Option B: Via Dispatcharr API

```bash
curl -X POST http://dispatcharr:9191/api/vod/series/ \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Breaking Bad",
    "category_id": 11,
    "description": "A chemistry teacher turned methamphetamine manufacturer."
  }'
```

## Step 4: Add Episodes to Series

For each series you added:

1. Navigate to **VOD** > **Episodes**
2. Click **Add Episode**
3. Fill in the required fields:
   - **Series**: Select the series (e.g., "Breaking Bad")
   - **Season Number**: The season (e.g., 1)
   - **Episode Number**: The episode within the season (e.g., 1)
   - **Name**: Episode title (e.g., "Pilot")
4. Optional fields:
   - **Description**: Episode synopsis
5. Add stream URL for the episode
6. Save the episode

Repeat for all episodes across all seasons.

### Via API Example

```bash
curl -X POST http://dispatcharr:9191/api/vod/episodes/ \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Pilot",
    "series_id": 200,
    "season_number": 1,
    "episode_number": 1,
    "description": "The first episode"
  }'
```

## Step 5: Trigger Dispatcharr Sync in Tavuno

Once your content is added to Dispatcharr, sync it to Tavuno:

### Option A: Via Admin API

```bash
curl -X POST http://tavuno-backend:8000/v1/admin/sync/dispatcharr \
  -H "Authorization: Bearer your-admin-jwt"
```

### Option B: Via Tavuno Admin UI (if available)

Navigate to the admin panel and click **Sync Dispatcharr**.

## Step 6: Verify Content Synced Correctly

### Check Movies

```bash
curl http://tavuno-backend:8000/v1/movies \
  -H "Authorization: Bearer your-user-jwt"
```

Expected response:
```json
{
  "count": 4,
  "results": [
    {
      "id": 1,
      "title": "The Matrix",
      "slug": "darr-movie-100",
      "release_year": 1999,
      "synopsis": "A computer hacker learns about the true nature of reality.",
      "is_active": true
    },
    ...
  ]
}
```

### Check Series

```bash
curl http://tavuno-backend:8000/v1/series \
  -H "Authorization: Bearer your-user-jwt"
```

Expected response:
```json
{
  "count": 2,
  "results": [
    {
      "id": 1,
      "title": "Breaking Bad",
      "slug": "darr-series-200",
      "synopsis": "A chemistry teacher turned methamphetamine manufacturer.",
      "is_active": true
    },
    ...
  ]
}
```

### Check Episodes

```bash
curl http://tavuno-backend:8000/v1/series/1/seasons/1/episodes \
  -H "Authorization: Bearer your-user-jwt"
```

Expected response:
```json
{
  "results": [
    {
      "id": 1,
      "episode_number": 1,
      "title": "Pilot",
      "synopsis": "The first episode",
      "is_active": true
    },
    ...
  ]
}
```

## Step 7: Check Sync Status

Get the last sync summary:

```bash
curl http://tavuno-backend:8000/v1/admin/sync/dispatcharr \
  -H "Authorization: Bearer your-admin-jwt"
```

Expected response:
```json
{
  "status": "success",
  "dispatcharr_version": "0.28.0",
  "movies_synced": 4,
  "series_synced": 2,
  "episodes_synced": 7,
  "vod_categories_synced": 3
}
```

## Troubleshooting

### Content doesn't appear after sync

1. **Check sync logs**: Look for errors in the Tavuno backend logs
2. **Verify Dispatcharr version**: Ensure it matches the expected 0.28.0
3. **Check API connectivity**: Ensure Tavuno can reach Dispatcharr at the configured URL
4. **Verify categories**: Make sure VOD categories exist in Dispatcharr before adding content
5. **Check required fields**: Ensure movies/series have at least a name or title

### Sync returns 0 for all counts

- Verify Dispatcharr has content added (check via Dispatcharr UI)
- Check the Tavuno `DISPATCHARR_BASE_URL` and `DISPATCHARR_API_KEY` configuration
- Ensure the Dispatcharr API key has read permissions

### Episodes not syncing

- Ensure the parent series exists and has been synced first
- Check that `series_id` in episodes matches a Dispatcharr series ID
- Verify season_number and episode_number are valid integers

### Duplicate content after re-sync

- This should not happen; the sync uses upsert (update-if-exists, insert-if-new)
- If duplicates appear, check that the slug generation logic is consistent
- The sync uses `darr-movie-{id}` and `darr-series-{id}` as slugs, which should be unique

### Stream URLs missing

- Stream URLs are fetched separately via `get_movie_streams` and `get_episode_streams`
- Ensure stream sources are added in Dispatcharr for each movie/episode
- Check that the stream URL field is properly mapped in Dispatcharr

## Best Practices

1. **Sync order**: Always sync categories first, then series, then episodes
2. **Idempotency**: Re-running sync should be safe and not create duplicates
3. **Required fields**: Always provide at least a name/title for all content
4. **Optional fields**: Description and poster are optional but recommended for UX
5. **Stream URLs**: Add stream URLs after content is added to Dispatcharr
6. **Testing**: Test with a small sample of content before bulk loading

## Example Minimal Content Set

For testing purposes, add this minimal content set:

**Categories:**
- Action (movie)
- Drama (series)

**Movies:**
- Test Movie (2020, Action category)

**Series:**
- Test Series (Drama category)

**Episodes:**
- S1E1: "Pilot" (Test Series)
- S1E2: "Episode Two" (Test Series)

Then trigger sync and verify all items appear in Tavuno's catalog endpoints.
