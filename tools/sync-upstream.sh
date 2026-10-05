#!/usr/bin/env bash
set -e
REF="${1:-master}"
echo "== fetch upstream =="
git fetch upstream
echo "== rebase onto upstream/$REF =="
if ! git rebase "upstream/$REF"; then
  echo "CONFLICT: чини только хуки в org.telegram.*, bossgram/ не трогай."
  echo "  git status -> правишь -> git add -> git rebase --continue"
  exit 1
fi
echo "== check hooks patch =="
if [ -f patches/0001-boss-hooks.patch ]; then
  if git apply --check patches/0001-boss-hooks.patch 2>/dev/null; then
    echo "hooks patch applies clean"
  else
    echo "hooks patch already in history or needs refresh — ok"
  fi
fi
echo "== check touch points =="
python3 tools/check-touch-points.py
echo "done: скопируй bossgram/ в сборку Telegram и собери."
