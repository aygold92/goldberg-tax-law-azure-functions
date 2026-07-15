#!/usr/bin/env bash
#
# Package each agent's skill into a zip ready for MANUAL upload to Anthropic via the
# claude.ai UI (Settings > Capabilities > Skills). For automated/API publishing use
# `gradle updateSkill` instead — this script is only for the point-and-click path.
#
# Layout expected (one directory per agent, skill nested under skill/):
#   managed-agents/<agent>/skill/SKILL.md
#   managed-agents/<agent>/skill/references/...
#
# Each zip is named after the skill's frontmatter `name` and contains a single top-level
# folder of that name (so it unzips cleanly), with SKILL.md at its root. macOS junk
# (.DS_Store, ._* AppleDouble files, xattrs) is excluded so the upload validates.
#
# Usage:
#   ./managed-agents/package-skills.sh                            # build all agents' skills
#   ./managed-agents/package-skills.sh bank-statement-extraction  # build one (by agent dir name)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENTS_DIR="$SCRIPT_DIR"
OUT_DIR="$SCRIPT_DIR/dist"

mkdir -p "$OUT_DIR"

# Determine which agents to build.
if [[ $# -gt 0 ]]; then
  targets=("$@")
else
  targets=()
  for d in "$AGENTS_DIR"/*/; do
    [[ -f "${d}skill/SKILL.md" ]] && targets+=("$(basename "$d")")
  done
fi

for agent in "${targets[@]}"; do
  skill_src="$AGENTS_DIR/$agent/skill"
  if [[ ! -f "$skill_src/SKILL.md" ]]; then
    echo "skip: $agent (no skill/SKILL.md)" >&2
    continue
  fi

  # Read the `name:` field from the YAML frontmatter for the zip + top-level folder name.
  skill_name="$(awk -F': *' '/^name:/{print $2; exit}' "$skill_src/SKILL.md" | tr -d '\r')"
  skill_name="${skill_name:-$agent}"

  zip_path="$OUT_DIR/$skill_name.zip"
  rm -f "$zip_path"

  # Stage into a temp dir so the top-level folder inside the zip is <skill_name>, not "skill".
  stage="$(mktemp -d)"
  trap 'rm -rf "$stage"' EXIT
  cp -R "$skill_src" "$stage/$skill_name"

  ( cd "$stage" && zip -r -X -q "$zip_path" "$skill_name" \
      -x '*.DS_Store' -x '*/.*' )

  rm -rf "$stage"
  trap - EXIT

  echo "built: ${zip_path#$SCRIPT_DIR/}  (skill: $skill_name)"
done
