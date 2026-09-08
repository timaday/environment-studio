#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for studio_tool in python3 npm java javac mvn; do
  command -v "$studio_tool" >/dev/null || { echo "BLOCKED: missing $studio_tool" >&2; exit 2; }
done
python3 scripts/check_repository_content.py
python3 scripts/check_repository.py
python3 -m unittest discover -s scripts -p 'test_*.py'
npm ci --prefix frontend
npm run check --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
mvn -B -ntp -f backend/pom.xml verify
echo "Starter gates passed. Product release qualification is a separate gate."
