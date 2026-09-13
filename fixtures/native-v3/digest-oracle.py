"""Independent contract oracle using invented fixtures; no runtime implementation imports."""
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
# The framing is frozen across versions; only normalized objects and domains change.
V2 = runpy.run_path(str(ROOT.parent / 'native-v2/digest-oracle.py'))
frame = V2['frame']
SEMANTICS = {
    'version': 1, 'profileMode': 'physical-only-v3',
    'maxDerivations': 32, 'maxCooccurrences': 32,
    'maxTotalNodes': 20000, 'maxTotalEdges': 50000,
    'maxContributorLinks': 100000, 'maxIdentityUtf8Bytes': 8388608,
}


def hashed(domain, value):
    return sha256(domain.encode('ascii') + b'\0' + frame(value)).hexdigest()


def expected(definition):
    logical = deepcopy(definition['logical'])
    for entity in logical['entityTypes']:
        del entity['label']
        entity['fields'].sort(key=lambda field: field['id'])
    for entity in logical['computedTypes']:
        del entity['label']
    for key in ('entityTypes', 'relations', 'rules', 'computedTypes', 'derivations',
                'cooccurrences', 'computedRules'):
        logical[key].sort(key=lambda item: item['id'])
    logical['operationCapabilities'].sort()
    logical_digest = hashed('ES-LOGICAL-3', {'logical': logical, 'derivedSemantics': SEMANTICS})
    bindings = {}
    vectors = {}
    for original in definition['bindings']:
        binding = deepcopy(original)
        identifier = binding.pop('id')
        dependencies = {'native-compiler-v3': 1, 'xml-path-v1': 1, 'xml-span-v1': 1,
                        'generic-graph-v1': 1, 'derived-graph-v1': 1}
        binding['documents'].sort(key=lambda item: item['id'])
        for document in binding['documents']:
            document['entities'].sort(key=lambda item: item['id'])
            for projection in document['entities']:
                projection['fields'].sort(key=lambda item: item['field'])
                projection['references'].sort(key=lambda item: item['relation'])
                if any('childProperty' in item for item in projection['fields']):
                    dependencies['xml-child-property-v1'] = 1
        vectors[identifier] = dependencies
        bindings[identifier] = hashed('ES-BINDING-3', {
            'logicalDigest': logical_digest, 'mechanisms': dependencies, 'binding': binding})
    return {'logicalDigest': logical_digest, 'bindingDigests': bindings, 'mechanisms': vectors}


def profile_digest(profile):
    value = deepcopy(profile)
    del value['revision']
    value['entities'].sort(key=lambda item: item['id'])
    for item in value['entities']:
        item['requiredInputs'].sort()
    value['relations'].sort(key=lambda edge: (edge['type'], edge['from'], edge['to']))
    return hashed('ES-PROFILE-3', value)


def controls(definition, profile):
    original = expected(definition)
    reordered = deepcopy(definition)
    reordered['id'] = 'another-neutral-id'
    reordered['revision'] = 10**100
    for key, values in reordered['logical'].items():
        values.reverse()
    for item in reordered['logical']['entityTypes'] + reordered['logical']['computedTypes']:
        item['label'] = 'Changed display label 𐀀'
        if 'fields' in item:
            item['fields'].reverse()
    assert expected(reordered) == original
    selector = deepcopy(definition)
    selector['bindings'][0]['documents'][0]['entities'][0]['fields'][1]['childProperty']['discriminatorValue'] = 'other-tone'
    changed = expected(selector)
    assert changed['logicalDigest'] == original['logicalDigest']
    assert changed['bindingDigests']['mock-oracle'] == original['bindingDigests']['mock-oracle']
    assert changed['bindingDigests']['mock-pg'] != original['bindingDigests']['mock-pg']
    for key in ('sourceField', 'membershipRelation'):
        changed = deepcopy(definition)
        changed['logical']['derivations'][0][key] = 'another-declaration'
        assert expected(changed)['logicalDigest'] != original['logicalDigest']
    changed = deepcopy(definition)
    changed['logical']['cooccurrences'][0]['maximum'] += 1
    assert expected(changed)['logicalDigest'] != original['logicalDigest']
    assert 'xml-child-property-v1' not in original['mechanisms']['mock-oracle']
    assert original['mechanisms']['mock-pg']['xml-child-property-v1'] == 1
    reordered_profile = deepcopy(profile)
    reordered_profile['revision'] += 1
    reordered_profile['entities'].reverse()
    reordered_profile['relations'].reverse()
    assert profile_digest(reordered_profile) == profile_digest(profile)
    historical = json.loads((ROOT.parent / 'native-v2/definition.json').read_text())
    assert V2['expected'](historical) == json.loads((ROOT.parent / 'native-v2/expected-digests.json').read_text())
    assert original['logicalDigest'] != V2['expected'](historical)['logicalDigest']
    try:
        frame('\ud800')
    except UnicodeEncodeError:
        pass
    else:
        raise AssertionError('Malformed Unicode must not be replacement-encoded')


if __name__ == '__main__':
    definition = json.loads((ROOT / 'definition.json').read_text())
    profile = json.loads((ROOT / 'profile.json').read_text())
    actual = expected(definition)
    assert profile['logicalDefinitionDigest'] == actual['logicalDigest']
    actual['profileDigest'] = profile_digest(profile)
    assert actual == json.loads((ROOT / 'expected-digests.json').read_text())
    controls(definition, profile)
    print(json.dumps(actual, indent=2))
