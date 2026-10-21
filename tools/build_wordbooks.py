#!/usr/bin/env python3
"""Build WordSpring's built-in word books from ECDICT exam tags.

Each built-in book is exactly the set of ECDICT entries carrying the matching
exam tag (zk / gk / cet4 / cet6 / ky / ielts / toefl / gre). No padding words
from other lists are added, so "大学英语四级" really is the CET-4 list.

Usage:
    python tools/build_wordbooks.py --csv .cache/ecdict.csv \
        --out app/src/main/assets/books

ECDICT: https://github.com/skywind3000/ECDICT (MIT License, Copyright (c) 2025 Linwei).
The script never downloads data; get ecdict.csv yourself.

Cleaning rules (see docs/ecdict-import.md):
  * translation is split on literal "\\n" / "\\r\\n" and real newlines;
  * domain-only lines such as "[计] 运行" / "[医] 库" are dropped;
  * inflection notes ("run的过去式和过去分词") are dropped; forms come from `exchange`;
  * senses are grouped by part of speech ("a." -> "adj."), separated by "；",
    capped at MAX_SENSES per part of speech;
  * phonetics are normalised to IPA (ә->ə, ε/є->ɛ, '->ˈ, .->ˌ, :->ː) and wrapped in /.../;
  * `exchange` is reduced to the codes p d i 3 s r t (past, past participle,
    present participle, third person, plural, comparative, superlative).
Words are ordered by corpus frequency (BNC/COCA rank, most common first) so
learners meet high-value words earlier.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

VERSION = "3.0.0"
UPDATED_AT = "2026-09-27"
MAX_SENSES = 8
FORM_CODES = ["p", "d", "i", "3", "s", "r", "t"]


@dataclass(frozen=True)
class BookSpec:
    key: str
    tag: str
    name: str
    description: str
    daily_new_limit: int


BOOKS = [
    BookSpec("zhongkao", "zk", "中考英语词汇", "初中毕业与中考考纲词汇", 10),
    BookSpec("gaokao", "gk", "高考英语词汇", "普通高中课程标准与高考考纲词汇", 15),
    BookSpec("cet4", "cet4", "大学英语四级词汇", "CET-4 考试大纲词汇", 15),
    BookSpec("cet6", "cet6", "大学英语六级词汇", "CET-6 考试大纲词汇（含四级基础词）", 20),
    BookSpec("kaoyan", "ky", "考研英语词汇", "全国硕士研究生入学考试英语大纲词汇", 20),
    BookSpec("ielts", "ielts", "雅思 IELTS 词汇", "IELTS 备考常用词汇", 20),
    BookSpec("toefl", "toefl", "托福 TOEFL 词汇", "TOEFL 备考常用词汇", 20),
    BookSpec("gre", "gre", "GRE 词汇", "GRE 备考词汇", 20),
]

POS_ALIASES = {"a.": "adj.", "int.": "interj."}
KNOWN_POS = {
    "n.", "v.", "vt.", "vi.", "adj.", "adv.", "prep.", "pron.", "num.", "conj.",
    "interj.", "pl.", "aux.", "abbr.", "art.", "pref.", "suf.",
}
INFLECTION_NOTE = re.compile(
    r"^[A-Za-z' .-]+的(过去式|过去分词|现在分词|复数|第三人称单数|比较级|最高级)"
)
DOMAIN_LINE = re.compile(r"^\s*\[[^\]]{1,6}\]")
# Technical-domain labels. Usage labels such as [口] [俚] [美] are kept (label stripped).
TECH_LINE = re.compile(r"^\s*\[(计|医|经|化|电|法|物|数|机|网络|建|地|生|农|军|植|动|解|药|天|矿|核|冶|电子|化工|纺|船|航|工|劳经|贸易|交通)\]")
POS_PREFIX = re.compile(r"^([a-z]{1,7}\.)\s*(.*)$")
LINE_SPLIT = re.compile(r"\\r\\n|\\n|\r?\n")
# "vt. 偷窃；vi. 窃取；n. 便宜货" packed into one line.
EMBEDDED_POS = re.compile(r"[;,；，]\s*(?=(?:n|v|vt|vi|a|adj|adv|prep|conj|pron|num|interj|aux)\.\s)")
INLINE_TAG = re.compile(r"\[[^\]]{1,8}\]")
INFLECTION_PAREN = re.compile(r"[(（]\s*[A-Za-z' -]+\s*的[^)）]{0,12}[)）]")


def clean_phonetic(raw: str) -> str | None:
    text = raw.strip()
    if not text or re.search(r"[\\^@?]", text):
        return None
    text = (
        text.replace("ә", "ə")
        .replace("є", "ɛ")
        .replace("ε", "ɛ")
        .replace("'", "ˈ")
        .replace(":", "ː")
        .replace(".", "ˌ")
        .replace(",", "ˌ")
    )
    text = text.strip("/[] ")
    return f"/{text}/" if text else None


def split_senses(body: str) -> list[str]:
    parts = re.split(r"\s*[,;，；]\s*", body.strip())
    seen: list[str] = []
    for part in parts:
        part = INFLECTION_PAREN.sub("", INLINE_TAG.sub("", part))
        part = part.strip(" 　.。")
        if part and part not in seen:
            seen.append(part)
    return seen


def parse_translation(raw: str) -> list[dict]:
    lines = [
        piece.strip()
        for line in LINE_SPLIT.split(raw)
        for piece in EMBEDDED_POS.split(line)
        if piece.strip()
    ]
    grouped: dict[str | None, list[str]] = {}
    order: list[str | None] = []
    fallback: list[tuple[str | None, list[str]]] = []
    for line in lines:
        pos = None
        body = line
        match = POS_PREFIX.match(line)
        if match:
            candidate = POS_ALIASES.get(match.group(1), match.group(1))
            if candidate in KNOWN_POS:
                pos, body = candidate, match.group(2)
        # "[计] 运行" (no part of speech) is domain jargon; "n. [口]偷窃" is a real sense.
        noisy = (
            (pos is None and DOMAIN_LINE.match(body))
            or TECH_LINE.match(body)
            or INFLECTION_NOTE.match(body)
        )
        cleaned_body = DOMAIN_LINE.sub("", body).strip()
        senses = split_senses(cleaned_body)
        if not senses:
            continue
        if noisy:
            fallback.append((pos, senses))
            continue
        if pos not in grouped:
            grouped[pos] = []
            order.append(pos)
        for sense in senses:
            if sense not in grouped[pos]:
                grouped[pos].append(sense)
    # ECDICT appends computing/medical jargon without a part of speech after the
    # real senses ("sort" -> "对它以行为单位进行排序"). Drop it when real senses exist.
    if None in grouped and order[0] is not None:
        del grouped[None]
        order.remove(None)
    if not grouped and fallback:
        # Only noisy lines exist (e.g. "ran" -> "run的过去式"); keep the first one.
        pos, senses = fallback[0]
        grouped[pos] = senses
        order.append(pos)
    return [
        {"pos": pos, "zh": "；".join(grouped[pos][:MAX_SENSES])}
        for pos in order
    ]


def clean_exchange(raw: str, word: str) -> str | None:
    forms: dict[str, str] = {}
    for item in raw.split("/"):
        if ":" not in item:
            continue
        code, value = item.split(":", 1)
        value = value.strip()
        if code not in FORM_CODES or not value:
            continue
        # "run" -> past participle "run" is real information; "can" -> 3rd person "can" is noise.
        if value.lower() == word.lower() and code not in ("p", "d"):
            continue
        forms[code] = value
    if not forms:
        return None
    return "/".join(f"{code}:{forms[code]}" for code in FORM_CODES if code in forms)


def frequency_rank(row: dict) -> int:
    ranks = [int(row[key]) for key in ("frq", "bnc") if row.get(key, "0").isdigit() and int(row[key]) > 0]
    return min(ranks) if ranks else 10**9


def normalize(word: str) -> str:
    return unicodedata.normalize("NFKC", word).strip().lower()


def build(csv_path: Path, out_dir: Path) -> None:
    buckets: dict[str, list[tuple[int, str, dict]]] = {spec.tag: [] for spec in BOOKS}
    with csv_path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            tags = set(row.get("tag", "").split())
            wanted = [spec.tag for spec in BOOKS if spec.tag in tags]
            if not wanted:
                continue
            word = row["word"].strip()
            senses = parse_translation(row.get("translation", ""))
            if not word or not senses:
                continue
            entry = {"word": word, "phonetic": clean_phonetic(row.get("phonetic", ""))}
            entry["senses"] = senses
            forms = clean_exchange(row.get("exchange", ""), word)
            if forms:
                entry["forms"] = forms
            for tag in wanted:
                buckets[tag].append((frequency_rank(row), word.lower(), entry))

    out_dir.mkdir(parents=True, exist_ok=True)
    license_text = (Path(__file__).parent / "ECDICT_LICENSE.txt").read_text(encoding="utf-8")
    for spec in BOOKS:
        rows = sorted(buckets[spec.tag], key=lambda item: (item[0], item[1]))
        seen: set[str] = set()
        entries = []
        for _, _, entry in rows:
            key = normalize(entry["word"])
            if key in seen:
                continue
            seen.add(key)
            entries.append({k: v for k, v in entry.items() if v is not None})
        folder = out_dir / spec.key
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "entries.json").write_text(
            json.dumps(entries, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
        )
        metadata = {
            "id": spec.key,
            "name": spec.name,
            "description": spec.description,
            "sourceType": "BUILT_IN",
            "dailyNewLimit": spec.daily_new_limit,
            "license": "MIT License - ECDICT (Copyright (c) 2025 Linwei; see LICENSE.txt)",
            "version": VERSION,
            "updatedAt": UPDATED_AT,
            "source": f"ECDICT entries tagged '{spec.tag}', ordered by word frequency",
            "wordCount": len(entries),
            "sourceLanguage": "en",
            "targetLanguage": "zh-CN",
        }
        (folder / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        (folder / "LICENSE.txt").write_text(license_text, encoding="utf-8")
        print(f"{spec.key:9s} {spec.name:12s} {len(entries):5d} words")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--csv", type=Path, default=Path(".cache/ecdict.csv"))
    parser.add_argument("--out", type=Path, default=Path("app/src/main/assets/books"))
    args = parser.parse_args()
    build(args.csv, args.out)


if __name__ == "__main__":
    main()
