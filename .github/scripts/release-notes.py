#!/usr/bin/env python3
"""Write release notes for VERSION to OUTPUT.

Uses the matching `## [x.y.z]` section of CHANGELOG.md (pre-release suffixes such as -rc1 fall back
to the base version). When the changelog has no entry, lists the commits since the previous tag.
"""
import re
import subprocess
import sys

version, output = sys.argv[1], sys.argv[2]
candidates = [version, version.split("-", 1)[0]]


def changelog_section() -> str | None:
    try:
        text = open("CHANGELOG.md", encoding="utf-8").read()
    except FileNotFoundError:
        return None
    for candidate in candidates:
        match = re.search(
            rf"^## \[{re.escape(candidate)}\][^\n]*\n(.*?)(?=^## |\Z)", text, flags=re.MULTILINE | re.DOTALL
        )
        if match and match.group(1).strip():
            heading = match.group(0).splitlines()[0].lstrip("# ").strip()
            return f"### {heading}\n\n{match.group(1).strip()}\n"
    return None


def git_log() -> str:
    def git(*args: str) -> str:
        return subprocess.run(["git", *args], capture_output=True, text=True).stdout.strip()

    previous = git("describe", "--tags", "--abbrev=0", "HEAD^")
    rev_range = f"{previous}..HEAD" if previous else "HEAD"
    commits = git("log", rev_range, "--no-merges", "--pretty=format:- %s (%h)")
    since = f" since {previous}" if previous else ""
    return f"### Changes{since}\n\n{commits or '- No commits found.'}\n"


notes = changelog_section() or git_log()
with open(output, "w", encoding="utf-8") as fh:
    fh.write(notes)
print(notes)
