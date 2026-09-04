"""Generate a reproducible license inventory from Gradle's local resolution evidence.

Uses only the standard library, cached Maven POMs and resolved artifacts; no network.
Outputs are generated artifacts. Never copies SDK binaries, private paths or keys.
"""
import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def licenses_for(coordinate, cache, visited=None):
    visited = set() if visited is None else visited
    if coordinate in visited:
        return [], [], ['Parent POM cycle: ' + coordinate]
    visited.add(coordinate)
    group, artifact, version = coordinate.split(':')
    candidates = sorted((cache / group / artifact / version).glob('*/*.pom'))
    if not candidates:
        return [], [], ['Missing cached POM: ' + coordinate]
    payloads = {digest(p.read_bytes()) for p in candidates}
    if len(payloads) != 1:
        return [], [], ['Ambiguous cached POM: ' + coordinate]
    raw = candidates[0].read_bytes()
    xml = ET.fromstring(raw)
    # Older Maven POMs can omit the XML namespace (for example JDOM).
    namespaces = {'m': ''} if xml.tag == 'project' else NS
    evidence = [{'coordinate': coordinate, 'pomSha256': digest(raw)}]
    found = []
    for entry in xml.findall('m:licenses/m:license', namespaces):
        found.append({'name': entry.findtext('m:name', '', namespaces),
                      'url': entry.findtext('m:url', '', namespaces)})
    if found:
        return found, evidence, []
    parent = xml.find('m:parent', namespaces)
    if parent is not None:
        key = ':'.join(parent.findtext('m:' + part, '', namespaces)
                       for part in ('groupId', 'artifactId', 'version'))
        inherited, parents, issues = licenses_for(key, cache, visited)
        return inherited, evidence + parents, issues
    return [], evidence, ['No license declaration: ' + coordinate]


def embedded_notices(payload, prefix='', depth=0):
    """Retain upstream legal texts, including those nested in an AAR classes.jar."""
    if depth > 3:
        return
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for entry in sorted(archive.infolist(), key=lambda e: e.filename):
            name = entry.filename
            base = name.rsplit('/', 1)[-1]
            legal = re.match(r'(?i)^(license|licence|notice|copying|copyright)([._-].*)?$', base)
            if legal and not entry.is_dir() and entry.file_size <= 2_000_000:
                raw = archive.read(entry)
                if b'\x00' not in raw:
                    yield prefix + name, raw
            elif name.endswith('.jar') and entry.file_size <= 100_000_000:
                yield from embedded_notices(archive.read(entry), prefix + name + '!/', depth + 1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resolution', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--assets', type=Path)
    parser.add_argument('--overrides', type=Path, default=Path(__file__).with_name('license-overrides.json'))
    args = parser.parse_args()
    source = json.loads(args.resolution.read_text(encoding='utf-8'))
    overrides = json.loads(args.overrides.read_text(encoding='utf-8'))
    if source['failures']:
        raise SystemExit('Resolve Gradle failures first; inventory would be incomplete.')
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    notice_dir = out / 'upstream-notices'
    notice_dir.mkdir(exist_ok=True)
    rows, issues = [], []
    for module in source['modules']:
        key = module['coordinate']
        licenses, evidence, gaps = licenses_for(key, args.cache)
        review = overrides.get(key) if not licenses else None
        if review:
            licenses = review['licenses']
            gaps = []
        issues.extend(gaps)
        row = {'coordinate': key, 'scopes': sorted(module['scopes']),
               'licenses': licenses, 'evidence': evidence, 'artifacts': [], 'notices': []}
        if review:
            row['reviewedEvidence'] = review
        for name in sorted(module['artifacts']):
            path = Path(name)
            # Do not publish paths; files below are public Maven artifacts only.
            raw = path.read_bytes()
            row['artifacts'].append({'name': path.name, 'sha256': digest(raw)})
            if zipfile.is_zipfile(path):
                for entry, text in embedded_notices(raw):
                    filename = digest(text) + '.txt'
                    (notice_dir / filename).write_bytes(text)
                    row['notices'].append({'artifact': path.name, 'entry': entry,
                                           'file': 'upstream-notices/' + filename})
        rows.append(row)
    report = {'schema': 1, 'gradle': source['gradle'], 'scopes': source['scopes'],
              'modules': rows, 'unresolvedLicenses': sorted(set(issues)),
              'limitations': ['POM declarations and embedded legal texts are evidence, not legal clearance.',
                              'Local Samsung AAR and host tools are documented separately.',
                              'No vulnerability scan, trademark clearance or binary distribution approval.']}
    (out / 'dependencies.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    lines = ['# Resolved Maven dependency licenses', '',
             'Generated with `scripts/license_inventory.py`; see `dependencies.json` for exact scopes,',
             'POM provenance, artifact SHA-256 and extracted upstream legal texts.', '',
             f'{len(rows)} unique module coordinates; {len(source["scopes"])} resolved configurations.', '',
             'Coordinates include platform/BOM metadata, not just packaged binaries.',
             'Build and test dependencies are not necessarily shipped in an APK.', '',
             '| Coordinate | Declared license | Scope groups |', '| --- | --- | --- |']
    for row in rows:
        categories = set()
        for scope in row['scopes']:
            categories.add('build' if scope.startswith('build:') else
                           'test' if 'Test' in scope else 'app/compile/tooling')
        names = '; '.join(lic['name'].replace('|', '/') for lic in row['licenses']) or 'UNRESOLVED'
        lines.append(f'| `{row["coordinate"]}` | {names} | {", ".join(sorted(categories))} |')
    lines += ['', '## Unresolved license metadata', '']
    lines += ['- ' + item for item in sorted(set(issues))] or ['None for these Maven coordinates.']
    (out / 'DEPENDENCIES.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    if args.assets:
        runtime = [row for row in rows if any(
            scope.endswith('RuntimeClasspath') and 'Test' not in scope and
            not scope.startswith('build:') for scope in row['scopes'])]
        args.assets.mkdir(parents=True, exist_ok=True)
        (args.assets / 'dependencies.json').write_text(
            json.dumps({'scope': 'Union of demo/samsung debug/release runtime modules; not a binary SBOM.',
                        'modules': runtime}, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        for row in runtime:
            for notice in row['notices']:
                target = args.assets / notice['file']
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((out / notice['file']).read_bytes())
        (args.assets / 'Apache-2.0.txt').write_bytes(Path(__file__).resolve().parents[1].joinpath('LICENSE').read_bytes())
    print(json.dumps({'modules': len(rows), 'scopes': len(source['scopes']),
                      'unresolved': sorted(set(issues)),
                      'noticeFiles': len(list(notice_dir.glob('*.txt')))}, indent=2))


if __name__ == '__main__':
    main()
