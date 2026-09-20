#!/usr/bin/env python3
"""
Generate release notes with a cumulative business-interpretation changelog
for PebbleTasker releases.

Resolves commits since the previous release, formats a business/user-facing
summary using Gemini (gemini-3.8-flash with low reasoning) if GEMINI_API_KEY
is available, or falls back to a categorized semantic parser.
Also provides monotonic tag resolution for continuous releases.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

DEFAULT_MODEL = "gemini-3.8-flash"


def run_cmd(cmd, cwd=None, check=True):
    res = subprocess.run(
        cmd,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed ({res.returncode}): {' '.join(cmd)}\n{res.stderr}")
    return res.stdout.strip()


def determine_next_tag(base_version, repo=None):
    """
    Determine next monotonic tag for base_version (e.g. 0.9.0 -> v0.9.0.1).
    Inspects existing releases/tags.
    If v{base_version} or v{base_version}.N exists, finds max N and returns v{base_version}.{max+1}.
    If none exists, returns v{base_version}.1.
    """
    tags = []
    # 1. Query GitHub releases
    try:
        gh_cmd = ["gh", "release", "list", "--limit", "50", "--json", "tagName"]
        if repo:
            gh_cmd.extend(["-R", repo])
        out = run_cmd(gh_cmd, check=False)
        if out:
            releases = json.loads(out)
            for r in releases:
                t = r.get("tagName", "")
                if t and t not in tags:
                    tags.append(t)
    except Exception as e:
        print(f"Notice: Could not list releases via gh CLI: {e}", file=sys.stderr)

    # 2. Query git tags
    try:
        git_tags = run_cmd(["git", "tag"]).splitlines()
        for t in git_tags:
            t = t.strip()
            if t and t not in tags:
                tags.append(t)
    except Exception:
        pass

    prefix = f"v{base_version}"
    pattern = re.compile(rf"^{re.escape(prefix)}(?:\.(\d+))?$")

    max_counter = 0
    matched = False
    for t in tags:
        m = pattern.match(t)
        if m:
            matched = True
            counter_str = m.group(1)
            if counter_str is not None:
                max_counter = max(max_counter, int(counter_str))

    if not matched:
        return f"{prefix}.1"
    else:
        return f"{prefix}.{max_counter + 1}"


def get_previous_release_tag(current_tag=None, repo=None):
    """Find the most recent release tag before current_tag."""
    # 1. Try gh CLI if available
    try:
        gh_cmd = ["gh", "release", "list", "--limit", "20", "--json", "tagName,isDraft,isPrerelease"]
        if repo:
            gh_cmd.extend(["-R", repo])
        out = run_cmd(gh_cmd, check=False)
        if out:
            releases = json.loads(out)
            for r in releases:
                tag = r.get("tagName", "")
                if not r.get("isDraft") and not r.get("isPrerelease"):
                    if current_tag and tag == current_tag:
                        continue
                    return tag
    except Exception as e:
        print(f"Notice: Could not list releases via gh CLI: {e}", file=sys.stderr)

    # 2. Fallback to git tags
    try:
        if current_tag:
            tags = run_cmd(["git", "tag", "--sort=-creatordate"]).splitlines()
            for t in tags:
                t = t.strip()
                if t and t != current_tag:
                    return t
        else:
            latest = run_cmd(["git", "describe", "--tags", "--abbrev=0", "HEAD^"], check=False)
            if latest:
                return latest
    except Exception:
        pass

    return None


def get_commits_since(prev_tag=None):
    """Get commit list since prev_tag up to HEAD."""
    rev_range = f"{prev_tag}..HEAD" if prev_tag else "HEAD"
    cmd = [
        "git",
        "log",
        rev_range,
        "--pretty=format:%h%x1f%an%x1f%s%x1f%b%x1e",
    ]
    raw = run_cmd(cmd, check=False)
    if not raw:
        return []

    commits = []
    for entry in raw.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split("\x1f")
        if len(parts) >= 3:
            sha = parts[0].strip()
            author = parts[1].strip()
            subject = parts[2].strip()
            body = parts[3].strip() if len(parts) > 3 else ""

            # Filter out merge commits
            if subject.startswith("Merge branch") or subject.startswith("Merge pull request"):
                continue

            commits.append({
                "sha": sha,
                "author": author,
                "subject": subject,
                "body": body,
            })
    return commits


def generate_with_gemini(commits, api_key, model=DEFAULT_MODEL):
    """Generate business changelog using Gemini API with low reasoning."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

    commit_lines = []
    for c in commits:
        line = f"- {c['subject']}"
        if c['body']:
            clean_body = re.sub(r'\n+', ' ', c['body']).strip()
            line += f": {clean_body}"
        commit_lines.append(line)
    commit_text = "\n".join(commit_lines)

    prompt = (
        "You are a release manager writing user-facing, business-oriented release notes "
        "for PebbleTasker (an Android Tasker automation plugin for Pebble smartwatches).\n\n"
        "Given the following list of git commits since the last release, generate a concise, "
        "cumulative business-interpretation changelog.\n"
        "Translate technical developer commits into clear, user-focused value (for example: "
        "translate Binder sessions, event cache loops, or signing tweaks into connectivity reliability, "
        "seamless setup, and stability improvements).\n\n"
        "Guidelines:\n"
        "- Group items into relevant sections such as:\n"
        "  ### 🚀 New Features & Capabilities\n"
        "  ### 🛠️ Improvements & Reliability\n"
        "  ### 🐛 Bug Fixes\n"
        "- Only include sections that have entries.\n"
        "- Write clear, professional bullet points highlighting user/automation benefit.\n"
        "- Do NOT include raw commit hashes, SHAs, or internal refactoring noise.\n"
        "- Do NOT wrap your output in triple backtick markdown code blocks.\n\n"
        f"Commits:\n{commit_text}"
    )

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.2,
            "thinkingConfig": {
                "thinkingBudget": 1024
            }
        }
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            candidates = data.get("candidates", [])
            if candidates:
                parts = candidates[0].get("content", {}).get("parts", [])
                # Exclude thinking/thought parts if present
                content_parts = [p.get("text", "") for p in parts if not p.get("thought")]
                if not content_parts:
                    content_parts = [p.get("text", "") for p in parts if "text" in p]
                text = "\n".join(content_parts).strip()
                # Strip outermost markdown fence if Gemini wrapped it
                if text.startswith("```markdown"):
                    text = text[len("```markdown"):].strip()
                elif text.startswith("```"):
                    text = text[3:].strip()
                if text.endswith("```"):
                    text = text[:-3].strip()
                return text
    except Exception as e:
        print(f"Warning: Gemini API call failed ({e}), falling back to semantic parser.", file=sys.stderr)
        return None


def generate_fallback_changelog(commits):
    """Fallback rule-based semantic categorization."""
    features = []
    improvements = []
    fixes = []

    feat_pattern = re.compile(r'^(feat|add|support|allow|enable|prepare|implement)\b', re.IGNORECASE)
    fix_pattern = re.compile(r'^(fix|resolve|correct|patch|handle|prevent|avoid|guard)\b', re.IGNORECASE)

    for c in commits:
        subj = c["subject"]
        # Strip conventional commit prefixes if present
        clean_subj = re.sub(r'^[a-zA-Z]+(\([^\)]+\))?:\s*', '', subj)
        clean_subj = clean_subj[0].upper() + clean_subj[1:] if clean_subj else subj

        if fix_pattern.search(subj) or "fix" in subj.lower():
            fixes.append(clean_subj)
        elif feat_pattern.search(subj):
            features.append(clean_subj)
        else:
            improvements.append(clean_subj)

    lines = []
    if features:
        lines.append("### 🚀 New Features & Capabilities")
        for item in features:
            lines.append(f"- {item}")
        lines.append("")

    if improvements:
        lines.append("### 🛠️ Improvements & Reliability")
        for item in improvements:
            lines.append(f"- {item}")
        lines.append("")

    if fixes:
        lines.append("### 🐛 Bug Fixes")
        for item in fixes:
            lines.append(f"- {item}")
        lines.append("")

    if not lines:
        lines.append("- Maintenance updates and stability improvements.")
        lines.append("")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate PebbleTasker release notes and manage release tags.")
    parser.add_argument("--determine-tag", action="store_true", help="Determine next monotonic release tag and print it")
    parser.add_argument("--base-version", help="Base application version (e.g. 0.9.0)")
    parser.add_argument("--tag", help="Target release tag being cut")
    parser.add_argument("--prev-tag", help="Explicit previous release tag (optional)")
    parser.add_argument("--repo", help="Repository in owner/repo format")
    parser.add_argument("--output", default="release-notes.md", help="Output file path")
    parser.add_argument("--version-name", help="Human-readable version name")
    args = parser.parse_args()

    repo = args.repo or os.environ.get("GITHUB_REPOSITORY")

    if args.determine_tag:
        if not args.base_version:
            print("Error: --base-version is required with --determine-tag", file=sys.stderr)
            sys.exit(1)
        next_tag = determine_next_tag(args.base_version, repo=repo)
        print(next_tag)
        return

    prev_tag = args.prev_tag or get_previous_release_tag(current_tag=args.tag, repo=repo)

    print(f"Generating changelog since: {prev_tag or 'initial commit'}")
    commits = get_commits_since(prev_tag)
    print(f"Found {len(commits)} commits since last release.")

    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    changelog = None

    if commits:
        if api_key:
            print(f"Invoking Gemini ({DEFAULT_MODEL}, low reasoning) for business changelog...")
            changelog = generate_with_gemini(commits, api_key, model=DEFAULT_MODEL)

        if not changelog:
            print("Using semantic rule-based changelog parser...")
            changelog = generate_fallback_changelog(commits)
    else:
        changelog = "Maintenance release with dependency and build updates."

    version_title = args.version_name or args.tag or "Latest Release"
    notes = [
        f"## PebbleTasker {version_title}",
        "",
        changelog.strip(),
        "",
        "---",
        "### 📦 Companion & Documentation",
        "Install the release APK with the matching companion fork:",
        "https://github.com/nbetcher/mobileapp/releases",
        "",
        "Setup and features: https://github.com/nbetcher/PebbleTasker#readme",
        "Support development: https://buymeacoffee.com/nbetcher",
    ]

    content = "\n".join(notes) + "\n"

    with open(args.output, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Release notes written to {args.output}")


if __name__ == "__main__":
    main()
