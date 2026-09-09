"""Independent Python oracle for the frozen native v2 framing, using only invented input."""
from copy import deepcopy
from hashlib import sha256
import json
import sys
from pathlib import Path


def frame(value):
    if isinstance(value, str):
        encoded = value.encode('utf-8')
        return b'S' + str(len(encoded)).encode('ascii') + b':' + encoded
    if isinstance(value, bool):
        return b'T' if value else b'F'
    if isinstance(value, int):
        return b'I' + str(value).encode('ascii') + b';'
    if isinstance(value, list):
        return b'A' + str(len(value)).encode('ascii') + b':' + b''.join(map(frame, value))
    if isinstance(value, dict):
        pairs = sorted(value.items(), key=lambda item: item[0].encode('utf-8'))
        return b'O' + str(len(pairs)).encode('ascii') + b':' + b''.join(frame(key) + frame(item) for key, item in pairs)
    raise TypeError('Unsupported oracle input type')


def expected(definition):
    logical = deepcopy(definition['logical'])
    for entity in logical['entityTypes']:
        del entity['label']
        entity['fields'].sort(key=lambda field: field['id'])
    for collection in ('entityTypes', 'relations', 'rules'):
        logical[collection].sort(key=lambda item: item['id'])
    logical['operationCapabilities'].sort()
    logical_digest = sha256(b'ES-LOGICAL-2\0' + frame(logical)).hexdigest()
    bindings = {}
    for original in definition['bindings']:
        binding = deepcopy(original)
        identifier = binding.pop('id')
        binding['documents'].sort(key=lambda document: document['id'])
        for document in binding['documents']:
            document['entities'].sort(key=lambda projection: projection['id'])
            for projection in document['entities']:
                projection['fields'].sort(key=lambda mapping: mapping['field'])
                projection['references'].sort(key=lambda mapping: mapping['relation'])
        body = {'logicalDigest': logical_digest, 'mechanisms': {
            'native-compiler-v2': 2, 'xml-path-v1': 1, 'xml-span-v1': 1, 'generic-graph-v1': 1}, 'binding': binding}
        if any('childProperty' in field for doc in binding['documents'] for projection in doc['entities'] for field in projection['fields']):
            body['mechanisms']['xml-child-property-v1'] = 1
        bindings[identifier] = sha256(b'ES-BINDING-2\0' + frame(body)).hexdigest()
    return {'logicalDigest': logical_digest, 'bindingDigests': bindings}


if __name__ == '__main__':
    root = Path(__file__).parent
    definition = json.loads((root / 'definition.json').read_text())
    mapping = definition['bindings'][0]['documents'][0]['entities'][0]['fields'][1]
    mapping.pop('attribute')
    mapping['childProperty'] = {
        'element': {'namespaceUri': 'urn:mock:properties', 'localName': 'entry'},
        'discriminatorAttribute': {'namespaceUri': 'urn:mock:selector', 'localName': 'key'},
        'discriminatorValue': 'tone',
        'valueAttribute': {'namespaceUri': '', 'localName': 'value'}}
    print(json.dumps(expected(definition), indent=2))
