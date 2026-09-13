#!/usr/bin/env bash
set -euo pipefail
studio_image="${1:?Usage: container_smoke.sh IMAGE}"
studio_container="environment-studio-check-${RANDOM}"
trap 'docker rm -f "$studio_container" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$studio_container" --read-only --cap-drop ALL \
  --security-opt no-new-privileges --user 10001:10001 --memory 1g \
  --tmpfs /tmp:rw,noexec,nosuid,size=128m -p 127.0.0.1::8080 "$studio_image" >/dev/null
studio_port="$(docker port "$studio_container" 8080/tcp | cut -d: -f2)"
export STUDIO_SMOKE_URL="http://127.0.0.1:$studio_port"
python3 - <<'PY'
import json, os, time
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from http.client import RemoteDisconnected
base=os.environ['STUDIO_SMOKE_URL']
deadline=time.monotonic()+60
while True:
    try:
        with urlopen(base+'/actuator/health/readiness',timeout=3) as response:
            assert response.status == 200
        break
    except (URLError, TimeoutError, ConnectionResetError, RemoteDisconnected):
        if time.monotonic() >= deadline:
            raise SystemExit('Container readiness timed out')
        time.sleep(1)
with urlopen(base+'/api/v1/capabilities',timeout=3) as response:
    data=json.load(response)
    assert data['mode']=='demo' and data['exportEnabled'] is False and data['inspectionEnabled'] is False
    assert data['inspectionUiEnabled'] is False and data['inspectionApiConfigured'] is False
with urlopen(base+'/',timeout=3) as response:
    assert 'Environment Studio' in response.read().decode()
try:
    urlopen(Request(base+'/api/v1/plans/demo/artifacts',data=b'{}',method='POST'),timeout=3)
    raise AssertionError('Mutation endpoint was not denied')
except HTTPError as error:
    assert error.code==403
print('Container startup, static UI, health, demo capability and denial checks passed')
PY
test "$(docker inspect --format '{{.Config.User}}' "$studio_container")" = "10001:10001"
python3 scripts/workspace_smoke.py "$studio_image"
