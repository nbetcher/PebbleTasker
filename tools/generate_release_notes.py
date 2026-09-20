#!/usr/bin/env python3
"""
Generate release notes with a cumulative business-interpretation changelog
for PebbleTasker releases.

Resolves commits since the previous release, formats a business/user-facing
summary using Gemini (gemini-3.8-flash with low reasoning) if GEMINI_API_KEY
is available, or falls back to a categorized semantic parser.
Also provides monotonic tag resolution for continuous releases (-nb0, -nb1, etc.).
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
    Determine next monotonic tag for base_version: v{base_version}-nb{counter}
    starting at counter=0.
    """
    tags = []
    # 1. Query GitHub releases
    try:
        gh_cmd = ["gh", "release", "list", "--limit", "100", "--json", "tagName"]
        if repo:
            gh_cmd.extend(["-R", repo])
        out = run_cmd(gh_cmd, check=False)
        if out:
            releases = json.loads(out)
            for r in releases:
                t = r.get("tagName", "")
                if t and t not in tags:
                    tags.append(t)
    except Exception:
        pass

    # 2. Query git tags
    try:
        git_tags = run_cmd(["git", "tag"]).splitlines()
        for t in git_tags:
            t = t.strip()
            if t and t not in tags:
                tags.append(t)
    except Exception:
        pass

    prefix = f"v{base_version}-nb"
    pattern = re.compile(rf"^{re.escape(prefix)}(\d+)$")

    max_counter = -1
    for t in tags:
        m = pattern.match(t)
        if m:
            counter = int(m.group(1))
            if counter > max_counter:
                max_counter = counter

    next_counter = max_counter + 1
    return f"{prefix}{next_counter}"


def get_previous_release_tag(current_tag=None, repo=None):
    """Find the most recent release tag before current_tag."""
    # 1. Try gh CLI if available
    try:
        gh_cmd = ["gh", "release", "list", "--limit", "50", "--json", "tagName,isDraft,isPrerelease"]
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
    """Get commit list since prev_tag up to HEAD. If prev_tag is None, returns all commits."""
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
    """Generate consolidated business changelog using Gemini API with low reasoning."""
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
        "You are a product release manager writing concise, professional, user-facing release notes "
        "for PebbleTasker (a standalone Android Tasker automation plugin for Pebble smartwatches).\n\n"
        "Below is the list of git commits representing changes since the last release (or the full project history "
        "if this is the initial release).\n\n"
        "TASK:\n"
        "Produce a high-level, cohesive business-interpretation summary of the features and capabilities delivered.\n\n"
        "STRICT CONSOLIDATION AND EDITING RULES:\n"
        "1. Focus on USER and AUTOMATION value (e.g. watch events, state triggers, actions, UI configuration, "
        "reconnection reliability, companion integration).\n"
        "2. REMOVE DEVELOPMENT CHURN AND FIXES TO ENHANCEMENTS: When a capability was added and then followed by bug fixes, "
        "refactors, or adjustments to that same capability (or internal CI/build/signing fixes) during development, "
        "DO NOT list those fixes or intermediate tweaks separately. Present the capability in its final, working form.\n"
        "3. KEEP UNNECESSARY VERBOSITY DOWN: Keep bullet points clear, concise, and focused on outcomes. Avoid technical "
        "implementation minutiae like Binder method names, internal class names, license merges, Gradle wrappers, or CI secrets.\n"
        "4. Structure into clean markdown sections such as:\n"
        "   ### 🚀 Features & Capabilities\n"
        "   ### 🛠️ Reliability & System Enhancements\n"
        "   (Only create sections that have items; do not create a separate 'Bug Fixes' section if the fixes were merely "
        "internal fix-ups to features delivered in this release).\n"
        "5. Do NOT include raw commit hashes, SHAs, author names, or raw commit subjects.\n"
        "6. Do NOT wrap output in triple backtick markdown code blocks.\n\n"
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
    """Fallback rule-based semantic categorization with churn suppression."""
    features = []
    enhancements = []

    # Patterns indicating internal churn/fix-ups to suppress
    churn_patterns = [
        re.compile(r'(publish on main|executable bit|release signing|signing config|ci debug|preserve.*license|initial commit)', re.I),
        re.compile(r'(fix config ui|criteria fields|load the event serial|continuous release)', re.I),
    ]

    for c in commits:
        subj = c["subject"]
        if any(p.search(subj) for p in churn_patterns):
            continue

        clean_subj = re.sub(r'^[a-zA-Z]+(\([^\)]+\))?:\s*', '', subj)
        clean_subj = clean_subj[0].upper() + clean_subj[1:] if clean_subj else subj

        if any(k in subj.lower() for k in ["plugin", "getting-started", "example", "criteria", "output variables", "prepare"]):
            features.append(clean_subj)
        else:
            enhancements.append(clean_subj)

    lines = []
    if features:
        lines.append("### 🚀 Features & Capabilities")
        for item in features:
            lines.append(f"- {item}")
        lines.append("")

    if enhancements:
        lines.append("### 🛠️ Reliability & System Enhancements")
        for item in enhancements:
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
