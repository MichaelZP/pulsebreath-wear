"""Regression checks for the reviewed build-only dependency baseline."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BuildDependencySecurityTest(unittest.TestCase):
    def test_reviewed_fixed_versions_are_resolved_only_for_build_tools(self):
        modules = json.loads((ROOT / 'docs/licenses/dependencies.json').read_text(encoding='utf-8'))['modules']
        for name, expected in {
            'org.jdom:jdom2': '2.0.6.1',
            'org.bitbucket.b_c:jose4j': '0.9.6',
            'org.apache.commons:commons-lang3': '3.18.0',
        }.items():
            matches = [m for m in modules if m['coordinate'].rsplit(':', 1)[0] == name]
            self.assertEqual([m['coordinate'] for m in matches], [name + ':' + expected])
            self.assertTrue(all(s.startswith('build:') for m in matches for s in m['scopes']))

    def test_kapt_requires_new_security_review_before_enabling(self):
        # This is a tracked-configuration guard, not proof of runtime reachability.
        for path in ['build.gradle.kts', 'settings.gradle.kts', 'app/build.gradle.kts', 'gradle/libs.versions.toml']:
            text = (ROOT / path).read_text(encoding='utf-8')
            self.assertIsNone(re.search(r'\bkapt\b', text, re.IGNORECASE), path)


if __name__ == '__main__':
    unittest.main()
