#!/usr/bin/env python3
"""Convert an ECDICT CSV file into WordSpring's UTF-8 import format.

ECDICT: https://github.com/skywind3000/ECDICT (MIT License).
The original data must be obtained by the developer or user; this script never
downloads data so the Android application stays offline-first.
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


MIT_LICENSE = """MIT License

Copyright (c) 2025 Linwei

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the \"Software\"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""


def matches_tag(tags: str, requested: str) -> bool:
    if requested == "all":
        return True
    return requested.lower() in {tag.strip().lower() for tag in tags.split()}


def convert(source: Path, destination: Path, requested_tag: str) -> int:
    count = 0
    with source.open("r", encoding="utf-8-sig", newline="") as reader_file, destination.open(
        "w", encoding="utf-8", newline=""
    ) as writer_file:
        reader = csv.DictReader(reader_file)
        required = {"word", "translation", "tag"}
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"ECDICT CSV is missing columns: {', '.join(sorted(missing))}")

        writer = csv.DictWriter(
            writer_file,
            fieldnames=["word", "phonetic", "partOfSpeech", "definition", "example", "translation"],
        )
        writer.writeheader()
        for row in reader:
            word = (row.get("word") or "").strip()
            translation = (row.get("translation") or "").strip()
            if not word or not translation or not matches_tag(row.get("tag") or "", requested_tag):
                continue
            writer.writerow(
                {
                    "word": word,
                    "phonetic": (row.get("phonetic") or "").strip(),
                    "partOfSpeech": (row.get("pos") or "").strip(),
                    "definition": translation,
                    "example": "",
                    "translation": translation,
                }
            )
            count += 1
    return count


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert ECDICT CSV for WordSpring import")
    parser.add_argument("source", type=Path, help="Path to ECDICT's stardict.csv")
    parser.add_argument("destination", type=Path, help="Output CSV for WordSpring")
    parser.add_argument("--tag", choices=("gk", "cet4", "cet6", "all"), default="all")
    args = parser.parse_args()

    count = convert(args.source, args.destination, args.tag)
    license_path = args.destination.with_name("LICENSE-ECDICT.txt")
    license_path.write_text(MIT_LICENSE, encoding="utf-8")
    print(f"Wrote {count} words to {args.destination}")
    print(f"Wrote required attribution to {license_path}")


if __name__ == "__main__":
    main()
