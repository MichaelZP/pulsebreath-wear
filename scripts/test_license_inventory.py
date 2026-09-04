import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

from license_inventory import licenses_for, embedded_notices, digest


class LicenseInventoryTest(unittest.TestCase):
    def test_parent_license_is_inherited_with_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache = Path(tmp)
            for artifact, body in {
                'parent': '<licenses><license><name>Apache-2.0</name></license></licenses>',
                'child': '<parent><groupId>example</groupId><artifactId>parent</artifactId><version>1</version></parent>',
            }.items():
                path = cache / 'example' / artifact / '1' / 'hash' / (artifact + '.pom')
                path.parent.mkdir(parents=True)
                path.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">' + body + '</project>')
            licenses, evidence, issues = licenses_for('example:child:1', cache)
            self.assertEqual(licenses[0]['name'], 'Apache-2.0')
            self.assertEqual(len(evidence), 2)
            self.assertEqual(issues, [])

    def test_missing_pom_is_not_assumed_apache(self):
        with tempfile.TemporaryDirectory() as tmp:
            licenses, _, issues = licenses_for('missing:library:1', Path(tmp))
            self.assertEqual(licenses, [])
            self.assertTrue(issues)

    def test_nested_aar_notices_preserve_bytes(self):
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, 'w') as out:
            out.writestr('META-INF/NOTICE', b'Original copyright\r\n')
        aar = io.BytesIO()
        with zipfile.ZipFile(aar, 'w') as out:
            out.writestr('classes.jar', jar.getvalue())
        self.assertEqual(list(embedded_notices(aar.getvalue())),
                         [('classes.jar!/META-INF/NOTICE', b'Original copyright\r\n')])

    def test_current_inventory_has_no_missing_maven_licenses_or_private_paths(self):
        root = Path(__file__).resolve().parents[1]
        raw = (root / 'docs/licenses/dependencies.json').read_text(encoding='utf-8')
        data = json.loads(raw)
        self.assertEqual(data['unresolvedLicenses'], [])
        self.assertTrue(all(row['licenses'] for row in data['modules']))
        self.assertNotIn('C:\\Users\\', raw)
        self.assertNotIn('192.168.', raw)
        for row in data['modules']:
            for notice in row['notices']:
                path = root / 'docs/licenses' / notice['file']
                self.assertTrue(path.is_file())
                self.assertEqual(digest(path.read_bytes()), path.stem)


class LogoTest(unittest.TestCase):
    def test_svg_matches_android_vector_geometry(self):
        root = Path(__file__).resolve().parents[1]
        svg = ET.parse(root / 'docs/assets/pulsebreath-logo.svg').getroot()
        vector = ET.parse(root / 'app/src/main/res/drawable/ic_launcher_foreground.xml').getroot()
        ns = '{http://schemas.android.com/apk/res/android}'
        self.assertEqual([(p.get('d'), p.get('fill')) for p in svg.findall('{http://www.w3.org/2000/svg}path')],
                         [(p.get(ns + 'pathData'), p.get(ns + 'fillColor')) for p in vector.findall('path')])

    def test_no_old_robot_rasters_and_consistent_monochrome_geometry(self):
        root = Path(__file__).resolve().parents[1]
        res = root / 'app/src/main/res'
        self.assertEqual(list(res.glob('mipmap-*/ic_launcher*.webp')), [])
        ns = '{http://schemas.android.com/apk/res/android}'
        foreground = ET.parse(res / 'drawable/ic_launcher_foreground.xml').getroot()
        mono = ET.parse(res / 'drawable/ic_launcher_monochrome.xml').getroot()
        self.assertEqual([p.get(ns + 'pathData') for p in foreground],
                         [p.get(ns + 'pathData') for p in mono])
        self.assertTrue(all(p.get(ns + 'fillColor') == '#FFFFFF' for p in mono))


if __name__ == '__main__':
    unittest.main()
