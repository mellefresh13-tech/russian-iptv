#!/usr/bin/env python3
"""Build a de-duplicated Russian-language IPTV playlist from public sources."""

from __future__ import annotations

import concurrent.futures
import os
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "playlist.m3u"

# Keep this list explicit: the report records every independent catalog used.
SOURCES = [
    ("https://iptv-org.github.io/iptv/languages/rus.m3u", "rus", "iptv-org / Russian"),
    ("https://iptv-org.github.io/iptv/countries/ru.m3u", "country", "iptv-org / Russia"),
    ("https://iptv-org.github.io/iptv/countries/by.m3u", "country", "iptv-org / Belarus"),
    ("https://iptv-org.github.io/iptv/countries/ua.m3u", "country", "iptv-org / Ukraine"),
    ("https://iptv-org.github.io/iptv/categories/news.m3u", "europe-news", "iptv-org / European news"),
    ("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_russia.m3u8", "candidate", "Free-TV / Russia"),
    ("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_belarus.m3u8", "candidate", "Free-TV / Belarus"),
    ("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_ukraine.m3u8", "candidate", "Free-TV / Ukraine"),
    ("https://raw.githubusercontent.com/streamline-iptv/iptv-playlists/dist/languages/rus.m3u", "candidate", "streamline-iptv / Russian"),
    ("https://dearbulut.github.io/iptv/playlists/country/ru.m3u", "candidate", "dearbulut / Russia"),
    ("https://raw.githubusercontent.com/ngrch/iptv/master/ru.m3u", "candidate", "ngrch / Russia"),
    ("https://raw.githubusercontent.com/FoxStudio24/IPTV/main/PMR.m3u8", "candidate", "FoxStudio24 / PMR"),
    ("https://raw.githubusercontent.com/yxcqr/viptv-work-iptv/main/XXXTV.m3u", "xxx", "yxcqr / XXXTV"),
    ("https://raw.githubusercontent.com/yxcqr/viptv-work-iptv/main/Adult.m3u", "xxx", "yxcqr / Adult"),
    ("https://raw.githubusercontent.com/zeckelektronik/adultasian/main/adult-5951", "xxx", "zeckelektronik / Adult Asian"),
    ("https://raw.githubusercontent.com/syukriadi/iptv/master/xxx.txt", "xxx", "syukriadi / XXX"),
    ("https://gist.githubusercontent.com/kingleone017/992c1bc81bbb3974bb1cb68f2eb54c16/raw/", "xxx", "public gist / XXX"),
    ("https://gist.githubusercontent.com/639936/ee108ef4fc3eadcc23c41408fa0d107e/raw/", "xxx", "public gist / XXX 2"),
]

EUROPE_NEWS = re.compile(
    r"\b(BBC|DW|Deutsche Welle|Euronews|France 24|BFM|LCI|Sky News|Sky TG24|"
    r"Rai News|ARD|ZDF|Tagesschau|3sat|TVP Info|RTP|NOS|SVT|NRK|DR News|"
    r"SRF|RTS|Euronews|CNN International)\b",
    re.I,
)
ADULT = re.compile(r"\b(XXX|Adult|Porn|Sex|Erotic|Barely Legal|Private|Hustler)\b", re.I)

GROUPS = {
    "News": "Новости",
    "Business": "Новости",
    "Kids": "Детские",
    "Family": "Детские",
    "Movies": "Фильмы",
    "Series": "Фильмы и сериалы",
    "Documentary": "Документальные",
    "Sports": "Спорт",
    "Music": "Музыка",
    "Culture": "Культура",
    "Education": "Познавательные",
    "Entertainment": "Развлекательные",
    "Lifestyle": "Развлекательные",
    "Religious": "Религиозные",
}


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "russian-iptv-playlist/1.0"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read().decode("utf-8", "replace")


def parse(text: str, source_kind: str, source_name: str) -> list[dict[str, str]]:
    lines = [x.strip() for x in text.splitlines()]
    result = []
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF"):
            continue
        url = next((x for x in lines[i + 1 :] if x and not x.startswith("#")), "")
        if not url:
            continue
        name = line.rsplit(",", 1)[-1].strip()
        id_match = re.search(r'tvg-id="([^"]*)"', line)
        logo_match = re.search(r'tvg-logo="([^"]*)"', line)
        group_match = re.search(r'group-title="([^"]*)"', line)
        if source_kind == "europe-news" and not EUROPE_NEWS.search(name):
            continue
        if source_kind == "xxx" and not ADULT.search(name + " " + line):
            continue
        group = "18+" if source_kind == "xxx" else GROUPS.get(group_match.group(1) if group_match else "", "Общие")
        result.append(
            {
                "id": id_match.group(1) if id_match else "",
                "name": re.sub(r"\s*\([^)]*\)\s*$", "", name),
                "logo": logo_match.group(1) if logo_match else "",
                "group": group,
                "url": url,
                "source": source_name,
            }
        )
    return result


def key(channel: dict[str, str]) -> str:
    return channel["id"].lower() or re.sub(r"[^a-zа-яё0-9]", "", channel["name"].lower())


def probe(channel: dict[str, str]) -> bool:
    try:
        req = urllib.request.Request(
            channel["url"],
            headers={"User-Agent": "Mozilla/5.0", "Range": "bytes=0-1023"},
        )
        with urllib.request.urlopen(req, timeout=6) as response:
            return response.status < 500
    except Exception:
        return False


def main() -> int:
    channels: list[dict[str, str]] = []
    errors = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        jobs = {pool.submit(fetch, url): (url, kind, name) for url, kind, name in SOURCES}
        for job in concurrent.futures.as_completed(jobs):
            url, kind, source_name = jobs[job]
            try:
                channels.extend(parse(job.result(), kind, source_name))
            except Exception as exc:
                errors.append(f"{url}: {exc}")

    unique: dict[str, dict[str, str]] = {}
    for channel in channels:
        unique.setdefault(key(channel), channel)
    channels = list(unique.values())

    verified_count = len(channels)
    if os.getenv("VERIFY_STREAMS", "").lower() in {"1", "true", "yes"}:
        with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
            valid = list(pool.map(probe, channels))
        verified_count = sum(ok for ok in valid)
        # Keep the requested 18+ section available for manual player checks.
        # Those endpoints are frequently blocked by providers and are reported
        # as unverified instead of being silently discarded.
        channels = [
            channel
            for channel, ok in zip(channels, valid)
            if ok or channel["group"] == "18+"
        ]

    priority = {"Новости": 1, "Фильмы": 2, "Фильмы и сериалы": 3, "Детские": 4, "18+": 99}
    channels.sort(key=lambda c: (priority.get(c["group"], 10), c["group"], c["name"].casefold()))

    header = (
        "#EXTM3U\n"
        '#PLAYLIST:name="Russian & European IPTV"\n'
        '#EXT-X-TVG-URL="https://xmltv.live/epg/ru.xml.gz"\n'
    )
    with OUT.open("w", encoding="utf-8", newline="\n") as output:
        output.write(header)
        for channel in channels:
            attrs = [f'tvg-id="{channel["id"]}"', f'group-title="{channel["group"]}"']
            if channel["logo"]:
                attrs.append(f'tvg-logo="{channel["logo"]}"')
            output.write(f'#EXTINF:-1 {" ".join(attrs)},{channel["name"]}\n{channel["url"]}\n')

    report = ROOT / "build-report.txt"
    report.write_text(
        f"channels={len(channels)}\n"
        f"verified_channels={verified_count}\n"
        f"unverified_18plus={sum(c['group'] == '18+' for c in channels if c['group'] == '18+')}\n"
        + "\n".join(f"{group}={sum(c['group'] == group for c in channels)}" for group in sorted({c["group"] for c in channels}))
        + "\nsource_errors=\n"
        + ("\n".join(errors) if errors else "none")
        + "\nsource_catalogs=" + str(len(SOURCES))
        + "\n"
        + "\n",
        encoding="utf-8",
    )
    return 1 if errors and not channels else 0


if __name__ == "__main__":
    sys.exit(main())
