"""
Cloudify Music Backend Server v6.0 - 100% PURE SOUNDCLOUD
Zero YouTube. Zero VPN needed. High-speed native SoundCloud streaming for Russia:
- Direct SoundCloud v2 API integration with auto-rotating client_id
- 100ms ultra-fast search directly from SoundCloud
- Native SoundCloud HLS-to-MP3 transcoding via server FFmpeg
- True HTTP 206 Partial Content (Range: bytes=) for instant seeking and scrubbing
- Cover image proxying to bypass Roskomnadzor sndcdn.com blocks
- Strict 250 MB disk quota with LRU auto-cleanup
"""

import asyncio
import glob
import hashlib
import os
import re
import shutil
import site
import subprocess
import sys
import time
import urllib.parse
from typing import Dict, List, Optional, Tuple

# 1. Ensure all user-installed site-packages are in sys.path
for path in glob.glob("/home/container/.local/lib/python*/site-packages"):
    if path not in sys.path:
        sys.path.insert(0, path)

user_site = site.getusersitepackages()
if isinstance(user_site, str) and user_site not in sys.path:
    sys.path.insert(0, user_site)

import httpx
import uvicorn
from fastapi import FastAPI, HTTPException, Query, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse, RedirectResponse

app = FastAPI(title="Cloudify SoundCloud Backend", version="6.8")

@app.on_event("startup")
async def on_startup():
    """Cleans up any orphaned temp/part files on server boot."""
    try:
        if os.path.exists(CACHE_DIR):
            for fname in os.listdir(CACHE_DIR):
                if fname.endswith(".tmp") or fname.endswith(".part") or "_temp" in fname:
                    try:
                        os.remove(os.path.join(CACHE_DIR, fname))
                    except Exception:
                        pass
        await asyncio.to_thread(clean_lru_cache)
    except Exception as e:
        print(f"[Startup Cache Clean Error]: {e}")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Cache Directory Setup
CACHE_DIR = "/home/container/cache"
if not os.path.exists("/home/container"):
    CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")
os.makedirs(CACHE_DIR, exist_ok=True)

MAX_CACHE_SIZE_BYTES = 250 * 1024 * 1024  # 250 MB hard cap
TARGET_CACHE_SIZE_BYTES = 180 * 1024 * 1024  # Clean down to 180 MB

# SoundCloud Client ID management
KNOWN_CLIENT_IDS = [
    "39AVoiZVrdvJEMFLH8T0c5WGshQaAPRJ",
    "8UvlHph8Fhze3ZixMeSkFXj4Dwkblsjt",
    "a3dd183a357981fe3282973260a1f342",
    "b45b1aa10f1ac2941914220c8210292d"
]
CLIENT_ID_INDEX = 0
CURRENT_CLIENT_ID = KNOWN_CLIENT_IDS[0]
CLIENT_ID_LOCK = asyncio.Lock()
LAST_CLIENT_ID_FETCH = 0

SEARCH_CACHE: Dict[str, Tuple[float, List[dict]]] = {}
SEARCH_CACHE_TTL = 1800  # 30 min

DOWNLOAD_LOCKS: Dict[str, asyncio.Lock] = {}
DOWNLOAD_LOCKS_LOCK = asyncio.Lock()

# Concurrency limiter: Maximum 2 concurrent FFmpeg transcode processes to prevent container CPU/RAM lockup
TRANSCODE_SEMAPHORE = asyncio.Semaphore(2)

HAS_FFMPEG = shutil.which("ffmpeg") is not None


async def get_valid_client_id(force_refresh: bool = False) -> str:
    """Returns an active SoundCloud client_id, automatically scraping fresh ones or rotating candidates."""
    global CURRENT_CLIENT_ID, LAST_CLIENT_ID_FETCH, CLIENT_ID_INDEX

    now = time.time()
    if not force_refresh and CURRENT_CLIENT_ID and (now - LAST_CLIENT_ID_FETCH < 3600):
        return CURRENT_CLIENT_ID

    async with CLIENT_ID_LOCK:
        if not force_refresh and CURRENT_CLIENT_ID and (now - LAST_CLIENT_ID_FETCH < 3600):
            return CURRENT_CLIENT_ID

        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        }

        timeout_cfg = httpx.Timeout(15.0, connect=10.0)
        async with httpx.AsyncClient(timeout=timeout_cfg, follow_redirects=True, headers=headers) as client:
            # 1. Try scraping from soundcloud.com
            for page_url in ["https://soundcloud.com", "https://soundcloud.com/discover"]:
                try:
                    home_res = await client.get(page_url)
                    if home_res.status_code == 200:
                        script_urls = re.findall(r'<script crossorigin src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"', home_res.text)
                        # Check scripts in reverse order (newer main bundles are at the very end)
                        for s_url in list(reversed(script_urls))[:8]:
                            try:
                                s_res = await client.get(s_url, timeout=3.5)
                                if s_res.status_code == 200:
                                    m = re.search(r'client_id[:=]\s*["\']([a-zA-Z0-9]{32})["\']', s_res.text)
                                    if m:
                                        scraped_id = m.group(1)
                                        print(f"[SoundCloud Auth] Scraped active client_id: {scraped_id}")
                                        CURRENT_CLIENT_ID = scraped_id
                                        LAST_CLIENT_ID_FETCH = time.time()
                                        return CURRENT_CLIENT_ID
                            except Exception:
                                continue
                except Exception as e:
                    print(f"[SoundCloud Client ID Scrape Failed on {page_url}]: {type(e).__name__}: {e}")

        # Rotate to next known working candidate
        CLIENT_ID_INDEX = (CLIENT_ID_INDEX + 1) % len(KNOWN_CLIENT_IDS)
        CURRENT_CLIENT_ID = KNOWN_CLIENT_IDS[CLIENT_ID_INDEX]
        LAST_CLIENT_ID_FETCH = time.time()
        print(f"[SoundCloud Auth] Rotated to fallback client_id: {CURRENT_CLIENT_ID}")
        return CURRENT_CLIENT_ID


def get_cache_size() -> int:
    """Returns total size of cached files in bytes."""
    total = 0
    try:
        for entry in os.scandir(CACHE_DIR):
            if entry.is_file():
                total += entry.stat().st_size
    except Exception:
        pass
    return total


def clean_lru_cache():
    """Enforces disk usage quota. Deletes oldest files if cache exceeds limit."""
    try:
        total_size = get_cache_size()
        if total_size <= MAX_CACHE_SIZE_BYTES:
            return

        files = []
        for entry in os.scandir(CACHE_DIR):
            if entry.is_file() and not entry.name.endswith(".tmp") and not entry.name.endswith(".part") and not "_temp" in entry.name:
                files.append((entry.stat().st_atime, entry.stat().st_size, entry.path))

        files.sort(key=lambda x: x[0])

        for _, size, path in files:
            if total_size <= TARGET_CACHE_SIZE_BYTES:
                break
            try:
                os.remove(path)
                total_size -= size
                print(f"[Cache Clean] Removed {os.path.basename(path)}")
            except Exception:
                pass
    except Exception as e:
        print(f"[Cache Clean Error]: {e}")


def find_cached_file(track_hash: str) -> Optional[str]:
    """Finds any valid audio file matching the track hash in cache directory."""
    if not os.path.exists(CACHE_DIR):
        return None

    # Priority 1: .mp3
    mp3_path = os.path.join(CACHE_DIR, f"{track_hash}.mp3")
    if os.path.isfile(mp3_path) and os.path.getsize(mp3_path) > 10000:
        return mp3_path

    # Priority 2: Any matching file
    for fname in os.listdir(CACHE_DIR):
        if fname.startswith(track_hash):
            if not fname.endswith(".part") and not fname.endswith(".tmp") and not "_temp" in fname:
                candidate = os.path.join(CACHE_DIR, fname)
                if os.path.isfile(candidate) and os.path.getsize(candidate) > 10000:
                    return candidate
    return None


@app.api_route("/", methods=["GET", "HEAD"])
@app.api_route("/health", methods=["GET", "HEAD"])
async def health(request: Request):
    if request.method == "HEAD":
        return Response(status_code=200)

    current_cache_mb = round(get_cache_size() / (1024 * 1024), 2)
    cached_files = [f for f in os.listdir(CACHE_DIR) if os.path.isfile(os.path.join(CACHE_DIR, f))]

    return {
        "status": "ok",
        "service": "Cloudify Backend v6.8 (Pure SoundCloud Auto-Auth & Bulletproof Fallback)",
        "source": "100% SoundCloud",
        "ffmpeg": HAS_FFMPEG,
        "cache_mb": current_cache_mb,
        "cached_tracks": len(cached_files),
        "cached_searches": len(SEARCH_CACHE),
    }


def clean_title(title: str) -> str:
    """Cleans up snippet markers for clean display."""
    return title.strip() or "Без названия"


async def _search_soundcloud_api(query: str, limit: int = 30) -> List[dict]:
    """Direct, 150ms search on SoundCloud v2 API."""
    cache_key = f"{query.lower().strip()}_{limit}"
    if cache_key in SEARCH_CACHE:
        ts, data = SEARCH_CACHE[cache_key]
        if time.time() - ts < SEARCH_CACHE_TTL:
            return data

    client_id = await get_valid_client_id()
    results = []

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Referer": "https://soundcloud.com/",
    }

    timeout_cfg = httpx.Timeout(15.0, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout_cfg, follow_redirects=True, headers=headers) as client:
        for attempt in range(len(KNOWN_CLIENT_IDS) + 1):
            try:
                url = "https://api-v2.soundcloud.com/search/tracks"
                params = {
                    "q": query,
                    "client_id": client_id,
                    "limit": limit,
                    "offset": 0,
                }
                res = await client.get(url, params=params)
                if res.status_code in (401, 403):
                    # Token expired, rotate to next client_id
                    client_id = await get_valid_client_id(force_refresh=True)
                    continue

                if res.status_code == 200:
                    data = res.json()
                    collection = data.get("collection", [])
                    for item in collection:
                        if not item or item.get("kind") != "track":
                            continue

                        # Filter out tracks blocked by policy or snippets
                        if item.get("policy") == "BLOCK":
                            continue

                        duration_ms = item.get("duration") or item.get("full_duration") or 0
                        title = item.get("title") or "Без названия"
                        if 0 < duration_ms <= 35000 and "snippet" in title.lower():
                            continue

                        # Filter out DRM-protected tracks (FairPlay / Widevine encrypted) or tracks without playable streams
                        transcodings = (item.get("media") or {}).get("transcodings") or []
                        if not transcodings:
                            continue

                        has_playable_stream = any(
                            "encrypted" not in ((t.get("format") or {}).get("protocol") or "").lower()
                            for t in transcodings
                        )
                        if not has_playable_stream:
                            continue

                        track_id = str(item.get("id"))
                        user = item.get("user") or {}
                        uploader = user.get("username") or "SoundCloud Artist"

                        # High quality artwork (replace -large.jpg with -t500x500.jpg)
                        artwork = item.get("artwork_url") or user.get("avatar_url") or ""
                        if artwork and "-large." in artwork:
                            artwork = artwork.replace("-large.", "-t500x500.")

                        permalink_url = item.get("permalink_url") or f"https://soundcloud.com/track/{track_id}"

                        results.append({
                            "id": track_id,
                            "title": clean_title(title),
                            "uploader": uploader,
                            "thumbnail": artwork,
                            "url": permalink_url,
                            "duration_ms": duration_ms,
                        })
                    break
            except Exception as e:
                print(f"[SoundCloud Search Error]: {type(e).__name__}: {e}")

    if results:
        SEARCH_CACHE[cache_key] = (time.time(), results)
    return results


@app.get("/api/search")
async def search(q: str = Query(..., min_length=1)):
    """Search tracks natively from SoundCloud."""
    tracks = await _search_soundcloud_api(q, 30)
    return JSONResponse(content=tracks)


@app.get("/api/wave")
async def get_wave():
    """Returns diverse trending tracks from SoundCloud."""
    import random
    queries = [
        "russian rap hits 2026",
        "dabbackwood",
        "GONE.Fludd",
        "Miyagi Andy Panda",
        "phonk hits 2026",
        "cloud rap",
        "shadowraze",
        "kai angel 9mice",
        "toxis",
        "og buda"
    ]
    query = random.choice(queries)
    tracks = await _search_soundcloud_api(query, 30)
    return JSONResponse(content=tracks)


@app.get("/api/debug_auth")
async def debug_auth():
    """Diagnostic endpoint to inspect active client_id and test SoundCloud connectivity."""
    cid = await get_valid_client_id()
    async with httpx.AsyncClient(timeout=8.0) as client:
        test_url = "https://api-v2.soundcloud.com/search/tracks"
        res = await client.get(test_url, params={"q": "miyagi", "limit": 2, "client_id": cid})
        return {
            "client_id": cid,
            "status_code": res.status_code,
            "sample_results_count": len(res.json().get("collection", [])) if res.status_code == 200 else 0,
            "response_preview": res.text[:200]
        }


async def _resolve_soundcloud_media(webpage_url: str, is_fallback: bool = False) -> Optional[dict]:
    """
    Resolves a SoundCloud track webpage URL to an authenticated media stream.
    Returns {"type": "progressive" | "hls", "url": media_url}
    """
    client_id = await get_valid_client_id()

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer": "https://soundcloud.com/",
    }

    timeout_cfg = httpx.Timeout(20.0, connect=12.0)
    async with httpx.AsyncClient(timeout=timeout_cfg, follow_redirects=True, headers=headers) as client:
        for attempt in range(2):
            try:
                # 1. Resolve webpage URL to track object
                resolve_url = "https://api-v2.soundcloud.com/resolve"
                res = await client.get(resolve_url, params={"url": webpage_url, "client_id": client_id})
                if res.status_code in (401, 403):
                    client_id = await get_valid_client_id(force_refresh=True)
                    continue

                if res.status_code != 200:
                    if not is_fallback:
                        try:
                            path_parts = [p for p in urllib.parse.urlparse(webpage_url).path.strip("/").split("/") if p]
                            if len(path_parts) >= 2:
                                uploader_slug = path_parts[0].replace("-", " ")
                                track_slug = path_parts[1].replace("-", " ")
                                q = f"{uploader_slug} {track_slug}".strip()
                                print(f"[SoundCloud Resolve {res.status_code}] Attempting slug search fallback: '{q}'")
                                fallback_tracks = await _search_soundcloud_api(q, limit=3)
                                for alt in fallback_tracks:
                                    alt_url = alt.get("url")
                                    if alt_url and alt_url != webpage_url:
                                        alt_media = await _resolve_soundcloud_media(alt_url, is_fallback=True)
                                        if alt_media:
                                            print(f"[SoundCloud Fallback Success] Using '{alt.get('title')}' -> {alt_url}")
                                            return alt_media
                        except Exception as slug_err:
                            print(f"[SoundCloud Slug Fallback Error]: {slug_err}")
                    return None

                track_data = res.json()
                media = track_data.get("media") or {}
                transcodings = media.get("transcodings") or []
                track_auth = track_data.get("track_authorization")

                # Priority 1: Check progressive stream (direct MP3 file from CDN)
                prog_transcoding = next(
                    (t for t in transcodings if t.get("format", {}).get("protocol") == "progressive"),
                    None
                )
                if prog_transcoding and prog_transcoding.get("url"):
                    params = {"client_id": client_id}
                    if track_auth:
                        params["track_authorization"] = track_auth
                    stream_res = await client.get(prog_transcoding["url"], params=params)
                    if stream_res.status_code == 200:
                        direct_url = stream_res.json().get("url")
                        if direct_url:
                            return {"type": "progressive", "url": direct_url}

                # Priority 2: Check HLS streams (ONLY unencrypted, playable streams)
                hls_candidates = [
                    t for t in transcodings 
                    if "hls" in t.get("format", {}).get("protocol", "").lower()
                    and "encrypted" not in t.get("format", {}).get("protocol", "").lower()
                ]

                # Helper to run fallback search if track is blocked/unplayable/DRM-locked
                async def try_fallback_search():
                    if is_fallback:
                        return None
                    title = clean_title(track_data.get("title", ""))
                    if not title or title == "Без названия":
                        title = (track_data.get("publisher_metadata") or {}).get("release_title", "")
                    
                    user = track_data.get("user") or {}
                    uploader = (track_data.get("publisher_metadata") or {}).get("artist") or user.get("username", "") or ""
                    clean_artist = re.sub(r'(-sc|-music|_sc|_music|-official|_official|\s+official|\s+topic|\s+-\s+Topic)$', '', uploader, flags=re.IGNORECASE).strip()
                    
                    if not clean_artist:
                        try:
                            parts = [p for p in urllib.parse.urlparse(webpage_url).path.strip("/").split("/") if p]
                            if parts:
                                clean_artist = parts[0].replace("-", " ")
                        except Exception:
                            pass

                    q = f"{clean_artist} {title}".strip() if clean_artist and title else title
                    if not q:
                        return None

                    print(f"[SoundCloud Unplayable Track] '{title}' by '{clean_artist}' (policy={track_data.get('policy')}). Searching fallback alternative: '{q}'...")
                    fallback_tracks = await _search_soundcloud_api(q, limit=4)
                    for alt in fallback_tracks:
                        alt_url = alt.get("url")
                        if alt_url and alt_url != webpage_url:
                            alt_media = await _resolve_soundcloud_media(alt_url, is_fallback=True)
                            if alt_media:
                                print(f"[SoundCloud Fallback Success] Using '{alt.get('title')}' -> {alt_url}")
                                return alt_media
                    return None

                # If no unencrypted streams exist on this track link:
                if not hls_candidates:
                    fallback_result = await try_fallback_search()
                    if fallback_result:
                        return fallback_result
                    return None

                def hls_score(t):
                    preset = t.get("preset", "").lower()
                    mime = ((t.get("format") or {}).get("mime_type") or "").lower()
                    score = 0
                    if "mp3" in preset or "mpeg" in mime:
                        score += 100
                    elif "160" in preset:
                        score += 50
                    elif "96" in preset:
                        score += 25
                    elif "abr" in preset:
                        score += 10
                    return score

                hls_candidates.sort(key=hls_score, reverse=True)

                params = {"client_id": client_id}
                if track_auth:
                    params["track_authorization"] = track_auth

                for cand in hls_candidates:
                    endpoint = cand.get("url")
                    if not endpoint:
                        continue
                    try:
                        stream_res = await client.get(endpoint, params=params)
                        if stream_res.status_code == 200:
                            m3u8_url = stream_res.json().get("url")
                            if m3u8_url:
                                print(f"[SoundCloud Resolve] Matched stream preset '{cand.get('preset')}' -> {m3u8_url[:60]}...")
                                return {"type": "hls", "url": m3u8_url}
                    except Exception as stream_err:
                        print(f"[SoundCloud Resolve Cand Error]: {type(stream_err).__name__}: {stream_err}")

                # If all candidates failed to provide a valid m3u8 URL, try search fallback
                fallback_result = await try_fallback_search()
                if fallback_result:
                    return fallback_result

            except Exception as e:
                print(f"[SoundCloud Resolve Error]: {type(e).__name__}: {e}")
                if attempt == 0:
                    client_id = await get_valid_client_id(force_refresh=True)
                    continue

    return None


async def _download_progressive_mp3(direct_url: str, target_path: str) -> bool:
    """Directly streams pre-encoded MP3 from SoundCloud CDN to disk."""
    temp_path = os.path.join(CACHE_DIR, f"{os.path.basename(target_path)}.temp.mp3")
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
    try:
        async with httpx.AsyncClient(timeout=25.0, follow_redirects=True, headers=headers) as client:
            async with client.stream("GET", direct_url) as resp:
                if resp.status_code == 200:
                    with open(temp_path, "wb") as f:
                        async for chunk in resp.aiter_bytes(chunk_size=65536):
                            f.write(chunk)
                    if os.path.exists(temp_path) and os.path.getsize(temp_path) > 10000:
                        os.replace(temp_path, target_path)
                        print(f"[Progressive Download Success] -> {target_path} ({os.path.getsize(target_path)} bytes)")
                        return True
    except Exception as e:
        print(f"[Progressive Download Error]: {type(e).__name__}: {e}")
    finally:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except Exception:
                pass
    return False


def _transcode_hls_to_mp3_sync(m3u8_url: str, target_mp3_path: str) -> bool:
    """
    Uses FFmpeg on the server to convert SoundCloud HLS stream to clean 192k MP3.
    Uses auto-reconnect flags, thread bounding (threads=2), and 25s timeout to prevent hanging.
    """
    temp_path = os.path.join(CACHE_DIR, f"{os.path.basename(target_mp3_path)}.temp.mp3")

    attempts = [
        # Attempt 1: Standard libmp3lame with auto-reconnect and bounded threads
        [
            "ffmpeg", "-y", "-loglevel", "error",
            "-user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "2",
            "-threads", "2",
            "-i", m3u8_url,
            "-vn",
            "-c:a", "libmp3lame",
            "-b:a", "192k",
            "-f", "mp3",
            temp_path
        ],
        # Attempt 2: built-in mp3 encoder as fallback
        [
            "ffmpeg", "-y", "-loglevel", "error",
            "-user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "2",
            "-threads", "2",
            "-i", m3u8_url,
            "-vn",
            "-c:a", "mp3",
            "-b:a", "192k",
            "-f", "mp3",
            temp_path
        ],
    ]

    for i, cmd in enumerate(attempts, 1):
        try:
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=25)
            if res.returncode == 0 and os.path.exists(temp_path) and os.path.getsize(temp_path) > 10000:
                os.replace(temp_path, target_mp3_path)
                print(f"[FFmpeg Transcode Success (attempt {i})] -> {target_mp3_path} ({os.path.getsize(target_mp3_path)} bytes)")
                return True
            else:
                err_msg = res.stderr.decode("utf-8", errors="ignore").strip()
                print(f"[FFmpeg Attempt {i} Failed]: {err_msg}")
                if any(x in err_msg.lower() for x in ["403", "forbidden", "unauthorized", "key", "encryption", "crypto"]):
                    break
        except subprocess.TimeoutExpired:
            print(f"[FFmpeg Attempt {i} Timeout]: Exceeded 25s limit")
        except Exception as e:
            print(f"[FFmpeg Attempt {i} Exception]: {e}")
        finally:
            if os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except Exception:
                    pass

    return False


async def _get_or_download_track(url: str, track_hash: str) -> Optional[str]:
    """Ensures track is downloaded to cache and returns its path."""
    cached = find_cached_file(track_hash)
    if cached:
        return cached

    await asyncio.to_thread(clean_lru_cache)

    # 1. Resolve SoundCloud media (progressive direct MP3 or HLS playlist)
    media_info = await _resolve_soundcloud_media(url)
    if not media_info or not media_info.get("url"):
        print(f"[Stream] Could not resolve SoundCloud URL: {url}")
        return None

    target_mp3 = os.path.join(CACHE_DIR, f"{track_hash}.mp3")

    # 2. If progressive direct MP3
    if media_info.get("type") == "progressive":
        success = await _download_progressive_mp3(media_info["url"], target_mp3)
        if success and os.path.exists(target_mp3):
            return target_mp3

    # 3. If HLS, transcode with server FFmpeg (concurrency limited to 2 processes)
    m3u8_url = media_info["url"]
    async with TRANSCODE_SEMAPHORE:
        success = await asyncio.to_thread(_transcode_hls_to_mp3_sync, m3u8_url, target_mp3)
    if success and os.path.exists(target_mp3):
        return target_mp3

    return None


@app.api_route("/api/stream", methods=["GET", "HEAD"])
async def stream_audio(request: Request, url: str = Query(...), quality: str = Query("high")):
    """
    Streams pure SoundCloud audio with full HTTP 206 Partial Content (Range support).
    ExoPlayer can buffer, seek, pause, and resume instantly.
    """
    track_hash = hashlib.md5(url.encode()).hexdigest()

    # Pre-flight HEAD request
    if request.method == "HEAD":
        cached = find_cached_file(track_hash)
        content_len = str(os.path.getsize(cached)) if cached else "3500000"
        return Response(
            status_code=200,
            headers={
                "Accept-Ranges": "bytes",
                "Content-Type": "audio/mpeg",
                "Content-Length": content_len,
                "Access-Control-Allow-Origin": "*",
            },
        )

    # Acquire lock for this track hash so concurrent requests don't duplicate transcode
    async with DOWNLOAD_LOCKS_LOCK:
        if track_hash not in DOWNLOAD_LOCKS:
            DOWNLOAD_LOCKS[track_hash] = asyncio.Lock()
        lock = DOWNLOAD_LOCKS[track_hash]

    async with lock:
        cached_file = find_cached_file(track_hash)
        if not cached_file:
            cached_file = await _get_or_download_track(url, track_hash)

    if not cached_file or not os.path.exists(cached_file):
        raise HTTPException(status_code=404, detail="SoundCloud audio track could not be loaded")

    file_size = os.path.getsize(cached_file)

    range_header = request.headers.get("range")
    if range_header:
        range_match = re.match(r"bytes=(\d+)-(\d*)", range_header.strip())
        if range_match:
            start = int(range_match.group(1))
            end = int(range_match.group(2)) if range_match.group(2) else file_size - 1
            if start >= file_size:
                return Response(
                    status_code=416,
                    headers={"Content-Range": f"bytes */{file_size}"}
                )
            end = min(end, file_size - 1)
            chunk_length = end - start + 1

            def range_generator(start_pos: int, bytes_to_read: int):
                with open(cached_file, "rb") as f:
                    f.seek(start_pos)
                    remaining = bytes_to_read
                    while remaining > 0:
                        chunk = f.read(min(65536, remaining))
                        if not chunk:
                            break
                        remaining -= len(chunk)
                        yield chunk

            return StreamingResponse(
                range_generator(start, chunk_length),
                status_code=206,
                headers={
                    "Content-Range": f"bytes {start}-{end}/{file_size}",
                    "Accept-Ranges": "bytes",
                    "Content-Length": str(chunk_length),
                    "Content-Type": "audio/mpeg",
                    "Access-Control-Allow-Origin": "*",
                },
            )

    # Full file response
    def full_generator():
        with open(cached_file, "rb") as f:
            while chunk := f.read(65536):
                yield chunk

    return StreamingResponse(
        full_generator(),
        status_code=200,
        headers={
            "Accept-Ranges": "bytes",
            "Content-Length": str(file_size),
            "Content-Type": "audio/mpeg",
            "Access-Control-Allow-Origin": "*",
        },
    )


@app.get("/api/download")
async def download_audio(url: str = Query(...)):
    """Downloads audio file directly for offline storage in Android app."""
    track_hash = hashlib.md5(url.encode()).hexdigest()

    async with DOWNLOAD_LOCKS_LOCK:
        if track_hash not in DOWNLOAD_LOCKS:
            DOWNLOAD_LOCKS[track_hash] = asyncio.Lock()
        lock = DOWNLOAD_LOCKS[track_hash]

    async with lock:
        cached_file = find_cached_file(track_hash)
        if not cached_file:
            cached_file = await _get_or_download_track(url, track_hash)

    if not cached_file or not os.path.exists(cached_file):
        raise HTTPException(status_code=404, detail="Audio file could not be generated")

    return FileResponse(
        cached_file,
        media_type="audio/mpeg",
        filename=f"track_{track_hash}.mp3",
        headers={"Access-Control-Allow-Origin": "*"}
    )


@app.api_route("/api/cover", methods=["GET", "HEAD"])
async def proxy_cover(request: Request, url: str = Query(...)):
    """Proxies cover image to bypass Roskomnadzor blocking of sndcdn.com in Russia."""
    if request.method == "HEAD":
        return Response(status_code=200, headers={"Content-Type": "image/jpeg", "Accept-Ranges": "bytes"})

    decoded_url = urllib.parse.unquote(url)
    async with httpx.AsyncClient(timeout=8.0, follow_redirects=True) as client:
        try:
            res = await client.get(
                decoded_url,
                headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"},
            )
            if res.status_code == 200:
                return Response(
                    content=res.content,
                    media_type=res.headers.get("Content-Type", "image/jpeg"),
                    headers={
                        "Cache-Control": "public, max-age=86400",
                        "Access-Control-Allow-Origin": "*",
                    },
                )
        except Exception as e:
            print(f"[Cover Proxy Error]: {type(e).__name__}: {e}")

    # Fallback to default music artwork instead of 404 to keep UI clean
    return RedirectResponse(
        url="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500",
        status_code=307,
        headers={"Access-Control-Allow-Origin": "*"}
    )


# ---------------------------------------------------------------------------
# Cloud User Profile & Playlist Sync System (Persistent Server Database)
# ---------------------------------------------------------------------------
import sqlite3

USER_DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cloudify_users.db")

def init_user_db():
    try:
        conn = sqlite3.connect(USER_DB_PATH)
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS users (
                user_id TEXT PRIMARY KEY,
                email TEXT,
                name TEXT,
                avatar_url TEXT,
                updated_at INTEGER
            )
        """)
        cur.execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)")
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_favorites (
                user_id TEXT,
                track_id TEXT,
                title TEXT,
                artist TEXT,
                artwork_url TEXT,
                stream_url TEXT,
                duration_ms INTEGER,
                genre TEXT,
                lyrics TEXT,
                added_at INTEGER,
                PRIMARY KEY (user_id, track_id)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_playlists (
                playlist_id TEXT PRIMARY KEY,
                user_id TEXT,
                name TEXT,
                artwork_url TEXT,
                created_at INTEGER
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_playlist_tracks (
                playlist_id TEXT,
                track_id TEXT,
                added_at INTEGER,
                PRIMARY KEY (playlist_id, track_id)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_artists (
                user_id TEXT,
                name TEXT,
                artwork_url TEXT,
                genre TEXT,
                monthly_listeners TEXT,
                added_at INTEGER,
                PRIMARY KEY (user_id, name)
            )
        """)
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"[UserDB Init Error]: {e}")

init_user_db()


@app.post("/api/user/sync")
async def sync_user_data(request: Request):
    """Saves or updates user profile, favorite tracks, playlists, and artists to server SQLite database."""
    try:
        data = await request.json()
        user_info = data.get("user", {})
        user_id = user_info.get("id") or ""
        email = user_info.get("email") or ""
        name = user_info.get("name") or "Пользователь"
        avatar_url = user_info.get("avatarUrl") or ""

        if not user_id and not email:
            raise HTTPException(status_code=400, detail="Missing user_id or email")

        primary_id = user_id if user_id else f"email_{hashlib.md5(email.encode()).hexdigest()}"
        now_ts = int(time.time() * 1000)

        conn = sqlite3.connect(USER_DB_PATH)
        cur = conn.cursor()

        # Check existing user by email if primary_id doesn't match
        if email:
            cur.execute("SELECT user_id FROM users WHERE email = ? LIMIT 1", (email,))
            row = cur.fetchone()
            if row:
                primary_id = row[0]

        # 1. Upsert User
        cur.execute("""
            INSERT INTO users (user_id, email, name, avatar_url, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                email = excluded.email,
                name = excluded.name,
                avatar_url = excluded.avatar_url,
                updated_at = excluded.updated_at
        """, (primary_id, email, name, avatar_url, now_ts))

        # 2. Insert/Update Favorites
        favorites = data.get("favorites", [])
        saved_favs = 0
        for f in favorites:
            t_id = f.get("id")
            if not t_id:
                continue
            cur.execute("""
                INSERT INTO user_favorites (user_id, track_id, title, artist, artwork_url, stream_url, duration_ms, genre, lyrics, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, track_id) DO UPDATE SET
                    title = excluded.title,
                    artist = excluded.artist,
                    artwork_url = excluded.artwork_url,
                    stream_url = excluded.stream_url,
                    duration_ms = excluded.duration_ms,
                    genre = excluded.genre,
                    lyrics = excluded.lyrics
            """, (
                primary_id,
                t_id,
                f.get("title", ""),
                f.get("artist", ""),
                f.get("artworkUrl", ""),
                f.get("streamUrl", ""),
                int(f.get("durationMs", 180000)),
                f.get("genre", "SoundCloud"),
                f.get("lyrics", ""),
                int(f.get("addedAt", now_ts))
            ))
            saved_favs += 1

        # 3. Insert/Update Playlists
        playlists = data.get("playlists", [])
        saved_playlists = 0
        for pl in playlists:
            pl_id = pl.get("id")
            if not pl_id:
                continue
            cur.execute("""
                INSERT INTO user_playlists (playlist_id, user_id, name, artwork_url, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(playlist_id) DO UPDATE SET
                    name = excluded.name,
                    artwork_url = excluded.artwork_url
            """, (
                pl_id,
                primary_id,
                pl.get("name", "Плейлист"),
                pl.get("artworkUrl", ""),
                int(pl.get("createdAt", now_ts))
            ))

            # Tracks inside playlist
            track_ids = pl.get("trackIds", [])
            for tid in track_ids:
                cur.execute("""
                    INSERT OR IGNORE INTO user_playlist_tracks (playlist_id, track_id, added_at)
                    VALUES (?, ?, ?)
                """, (pl_id, tid, now_ts))

            saved_playlists += 1

        # 4. Insert/Update Favorite Artists
        artists = data.get("favoriteArtists", [])
        for a in artists:
            a_name = a.get("name")
            if not a_name:
                continue
            cur.execute("""
                INSERT INTO user_artists (user_id, name, artwork_url, genre, monthly_listeners, added_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, name) DO UPDATE SET
                    artwork_url = excluded.artwork_url,
                    genre = excluded.genre,
                    monthly_listeners = excluded.monthly_listeners
            """, (
                primary_id,
                a_name,
                a.get("artworkUrl", ""),
                a.get("genre", "Исполнитель"),
                a.get("monthlyListeners", ""),
                int(a.get("addedAt", now_ts))
            ))

        conn.commit()
        conn.close()

        return {
            "status": "ok",
            "user_id": primary_id,
            "savedFavorites": saved_favs,
            "savedPlaylists": saved_playlists,
            "updatedAt": now_ts
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/user/sync")
async def get_user_data(user_id: Optional[str] = Query(None), email: Optional[str] = Query(None)):
    """Retrieves all saved favorites, playlists, and artists for the user to restore their library after reinstallation."""
    if not user_id and not email:
        raise HTTPException(status_code=400, detail="Missing user_id or email parameter")

    conn = sqlite3.connect(USER_DB_PATH)
    cur = conn.cursor()

    target_user_id = None
    user_row = None

    if email:
        cur.execute("SELECT user_id, email, name, avatar_url, updated_at FROM users WHERE email = ? LIMIT 1", (email,))
        user_row = cur.fetchone()

    if not user_row and user_id:
        cur.execute("SELECT user_id, email, name, avatar_url, updated_at FROM users WHERE user_id = ? LIMIT 1", (user_id,))
        user_row = cur.fetchone()

    if not user_row:
        conn.close()
        return {
            "status": "not_found",
            "favorites": [],
            "playlists": [],
            "favoriteArtists": []
        }

    target_user_id = user_row[0]
    user_dict = {
        "id": user_row[0],
        "email": user_row[1],
        "name": user_row[2],
        "avatarUrl": user_row[3],
        "updatedAt": user_row[4]
    }

    # Fetch favorites
    cur.execute("""
        SELECT track_id, title, artist, artwork_url, stream_url, duration_ms, genre, lyrics, added_at
        FROM user_favorites
        WHERE user_id = ?
        ORDER BY added_at DESC
    """, (target_user_id,))
    favorites = [
        {
            "id": r[0],
            "title": r[1],
            "artist": r[2],
            "artworkUrl": r[3],
            "streamUrl": r[4],
            "durationMs": r[5],
            "genre": r[6],
            "lyrics": r[7],
            "addedAt": r[8]
        }
        for r in cur.fetchall()
    ]

    # Fetch playlists
    cur.execute("""
        SELECT playlist_id, name, artwork_url, created_at
        FROM user_playlists
        WHERE user_id = ?
        ORDER BY created_at DESC
    """, (target_user_id,))
    playlists = []
    for r in cur.fetchall():
        pl_id = r[0]
        cur.execute("SELECT track_id FROM user_playlist_tracks WHERE playlist_id = ? ORDER BY added_at ASC", (pl_id,))
        tids = [tr[0] for tr in cur.fetchall()]
        playlists.append({
            "id": pl_id,
            "name": r[1],
            "artworkUrl": r[2],
            "createdAt": r[3],
            "trackIds": tids
        })

    # Fetch artists
    cur.execute("""
        SELECT name, artwork_url, genre, monthly_listeners, added_at
        FROM user_artists
        WHERE user_id = ?
        ORDER BY added_at DESC
    """, (target_user_id,))
    artists = [
        {
            "name": r[0],
            "artworkUrl": r[1],
            "genre": r[2],
            "monthlyListeners": r[3],
            "addedAt": r[4]
        }
        for r in cur.fetchall()
    ]

    conn.close()

    return {
        "status": "ok",
        "user": user_dict,
        "favorites": favorites,
        "playlists": playlists,
        "favoriteArtists": artists
    }


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=25066, reload=False, log_level="info")
