"""Independent mock-profile digest oracle. No runtime/compiler imports or real model."""
import hashlib
import json
from pathlib import Path


def frame(value):
    if isinstance(value, str):
        encoded = value.encode('utf-8')
        return b'S' + str(len(encoded)).encode('ascii') + b':' + encoded
    if isinstance(value, list):
        return b'A' + str(len(value)).encode('ascii') + b':' + b''.join(map(frame, value))
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode('utf-8'))
        return b'O' + str(len(keys)).encode('ascii') + b':' + b''.join(frame(key) + frame(value[key]) for key in keys)
    raise TypeError('Unexpected mock shape')


def digest(value):
    value = {key: item for key, item in value.items() if key != 'revision'}
    value['entities'] = sorted((dict(entity, requiredInputs=sorted(entity['requiredInputs'])) for entity in value['entities']), key=lambda entity: entity['id'])
    value['relations'] = sorted(value['relations'], key=lambda edge: (edge['type'], edge['from'], edge['to']))
    return hashlib.sha256(b'ES-PROFILE-2\0' + frame(value)).hexdigest()


if __name__ == '__main__':
    directory = Path(__file__).parent
    actual = digest(json.loads((directory / 'profile.json').read_text()))
    expected = json.loads((directory / 'expected-digest.json').read_text())['contentDigest']
    assert actual == expected
    print(actual)
