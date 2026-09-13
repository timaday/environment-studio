#!/usr/bin/env python3
"""Independent fixed ZIP byte-layout oracle, using only invented shape strings."""
import hashlib, json, struct, zlib
from pathlib import Path
members=[('manifest.json',b'{}'),('payload.json',b'{}'),('transaction.sql',b'BEGIN\nEND;\n'),('instructions.txt',b'mock instructions\n')]
local=bytearray(); central=bytearray()
for name, body in members:
    name=name.encode('ascii'); crc=zlib.crc32(body); offset=len(local)
    local+=struct.pack('<IHHHHHIIIHH',0x04034b50,20,0x800,0,0,33,crc,len(body),len(body),len(name),0)+name+body
    central+=struct.pack('<IHHHHHHIIIHHHHHII',0x02014b50,0x314,20,0x800,0,0,33,crc,len(body),len(body),len(name),0,0,0,0,0o100600<<16,offset)+name
archive=local+central+struct.pack('<IHHHHIIH',0x06054b50,0,0,4,4,len(central),len(local),0)
expected={'archiveBytes':len(archive),'sha256':hashlib.sha256(archive).hexdigest()}
path=Path(__file__).with_name('zip-expected.json')
if path.exists(): assert json.loads(path.read_text())==expected
else:path.write_text(json.dumps(expected,indent=2)+'\n')
print(expected)
