import unittest

import httpx

from app.dispatcharr_client import DispatcharrClient, redact_record


class DispatcharrClientTests(unittest.TestCase):
    def _client(self, handler) -> DispatcharrClient:
        transport = httpx.MockTransport(handler)
        client = DispatcharrClient("http://dispatcharr.test", api_key="secret-key", timeout=2.0)

        def _get_json(path, params=None, authenticated=True):
            request_headers = {"Accept": "application/json"}
            if authenticated:
                request_headers["X-API-Key"] = "secret-key"
            with httpx.Client(transport=transport, headers=request_headers) as http_client:
                response = http_client.get(f"http://dispatcharr.test{path}", params=params)
                response.raise_for_status()
                return response.json()

        client._get_json = _get_json  # type: ignore[method-assign]
        return client

    def test_redacts_stream_urls(self):
        cleaned = redact_record({"id": 9, "name": "News", "url": "http://secret/stream.ts", "local_file": "/tmp/a"})
        self.assertEqual(cleaned, {"id": 9, "name": "News"})

    def test_paginates_until_next_is_null(self):
        def handler(request: httpx.Request) -> httpx.Response:
            page = request.url.params.get("page", "1")
            if page == "1":
                return httpx.Response(
                    200,
                    json={
                        "count": 2,
                        "next": "http://dispatcharr.test/api/vod/movies/?page=2",
                        "results": [{"id": 1, "name": "Movie One", "url": "http://hidden"}],
                    },
                )
            return httpx.Response(200, json={"count": 2, "next": None, "results": [{"id": 2, "name": "Movie Two"}]})

        movies = self._client(handler).get_all_movies()
        self.assertEqual([movie["id"] for movie in movies], [1, 2])
        self.assertNotIn("url", movies[0])

    def test_list_endpoints_accept_bare_arrays(self):
        def handler(_: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json=[{"id": 4, "name": "Sports"}])

        groups = self._client(handler).get_channel_groups()
        self.assertEqual(groups[0]["name"], "Sports")

    def test_get_stream_by_id(self):
        """Test fetching a specific stream by ID."""
        def handler(request: httpx.Request) -> httpx.Response:
            if "/streams/123/" in str(request.url):
                return httpx.Response(
                    200,
                    json={
                        "id": 123,
                        "name": "Test Stream",
                        "url": "https://example.com/stream.m3u8",
                        "tvg_id": "test.tv"
                    }
                )
            return httpx.Response(404, json={"detail": "Not found"})

        client = self._client(handler)
        stream = client.get_stream_by_id(123)
        self.assertIsNotNone(stream)
        self.assertEqual(stream["id"], 123)
        self.assertEqual(stream["name"], "Test Stream")
        # URL should be redacted
        self.assertNotIn("url", stream)

    def test_get_stream_by_id_not_found(self):
        """Test get_stream_by_id returns None for non-existent stream."""
        def handler(_: httpx.Request) -> httpx.Response:
            return httpx.Response(404, json={"detail": "Not found"})

        client = self._client(handler)
        stream = client.get_stream_by_id(999)
        self.assertIsNone(stream)

    def test_get_stream_url_by_id_returns_unredacted_url(self):
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={"id": 123, "url": "https://example.com/live.m3u8"})

        url = self._client(handler).get_stream_url_by_id(123)
        self.assertEqual(url, "https://example.com/live.m3u8")
