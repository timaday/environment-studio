#!/usr/bin/env python3
"""Independent JSON and native-framing oracle over the already invented public shape family."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parent
source=root.parent/'guarded-package-v1'
execution=json.loads((source/'manifest.json').read_text())['execution']
payload=json.loads((source/'payload.json').read_text())
def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf-8')
def frame(value):
    if isinstance(value,str):
        encoded=value.encode('utf-8');return b'S'+str(len(encoded)).encode()+b':'+encoded
    if type(value)is int:return b'I'+str(value).encode()+b';'
    if isinstance(value,list):return b'A'+str(len(value)).encode()+b':'+b''.join(map(frame,value))
    if isinstance(value,dict):return b'O'+str(len(value)).encode()+b':'+b''.join(frame(k)+frame(value[k])for k in sorted(value,key=lambda s:s.encode('utf-8')))
    raise ValueError('Unexpected oracle value')
payload_digest=hashlib.sha256(canonical(payload)).hexdigest()
expected={'payloadDigest':payload_digest,'programDigest':hashlib.sha256(b'ES-EXECUTION-1\0'+frame({'execution':execution,'payloadDigest':payload_digest})).hexdigest()}
for name,data in [('canonical-execution.json',canonical(execution)),('canonical-payload.json',canonical(payload)),('canonical-digest.json',json.dumps(expected,indent=2).encode()+b'\n')]:
    path=root/name
    if path.exists():assert path.read_bytes()==data
    else:path.write_bytes(data)
print(expected)
