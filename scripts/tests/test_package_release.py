import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_release import create_release


class PackageReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        base = Path(self.temporary.name)
        self.project = base / "source"
        self.repository = base / "maven"
        self.output = base / "output"
        self.version = "1.2.3"
        self.parent = self.project / "pom.xml"
        self.write(self.parent, self.pom("de.drremote", "db-parent", "pom",
            "<modules><module>db-core</module><module>db-feature</module></modules>"))
        self.install("de.drremote", "db-parent", "pom", self.parent.read_text())
        for directory, artifact, packaging in [("db-core", "sqlite", "bundle"), ("db-feature", "sqlite-features", "feature")]:
            pom = self.pom("de.drremote.db", artifact, packaging)
            self.write(self.project / directory / "pom.xml", pom)
            self.install("de.drremote.db", artifact, "pom", pom)
            if packaging == "bundle":
                for suffix in ["jar", "sources.jar", "javadoc.jar"]:
                    self.install("de.drremote.db", artifact, suffix, "public bundle")
            else:
                self.install("de.drremote.db", artifact, "features.xml", "<features/> ")
        self.write(self.project / "LICENSE", "Apache-2.0")
        self.write(self.project / "CHANGELOG.md", "Release notes")
        self.write(self.project / "CONTRIBUTING.md", "Source build and contribution guide")
        self.write(self.project / "docs/binary-installation.md", "Binary installation")
        self.write(self.project / "docs/releases/1.2.3.md", "Versioned release notes")
        for name in ["notes-client", "json-client"]:
            self.write(self.project / f"examples/{name}/pom.xml", self.pom("de.drremote.db.examples", name, "bundle"))
            self.write(self.project / f"examples/{name}/README.md", "Example")
            self.write(self.project / f"examples/{name}/src/main/java/Client.java", "class Client {}")
            self.write(self.project / f"examples/{name}/target/{name}-{self.version}.jar", "example bundle")
        self.write(self.project / "examples/de.drremote.db.sqlite.cfg.example", "registerMbeans=false")

    def pom(self, group, artifact, packaging, extra=""):
        return (f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
                f'<groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{self.version}</version>'
                f'<packaging>{packaging}</packaging>{extra}</project>')

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def install(self, group, artifact, suffix, content):
        _, dot, _ = suffix.partition(".")
        ending = "-" + suffix if dot else "." + suffix
        path = self.repository / group.replace(".", "/") / artifact / self.version / f"{artifact}-{self.version}{ending}"
        self.write(path, content)
        return path

    def test_complete_archive_has_checksums_examples_and_only_expected_artifacts(self):
        self.write(self.repository / "de/drremote/private/secret.jar", "private cache content")
        self.write(self.project / "examples/json-client/target/unrelated-secret.txt", "unrelated build output")
        archive = create_release(self.project, self.repository, self.output)
        checksum = (self.output / "SHA256SUMS").read_text()
        self.assertEqual(checksum, hashlib.sha256(archive.read_bytes()).hexdigest() + "  " + archive.name + "\n")
        with zipfile.ZipFile(archive) as bundle:
            self.assertIsNone(bundle.testzip())
            self.assertFalse(any("secret" in name for name in bundle.namelist()))
            prefix = "drremote-db-1.2.3/"
            self.assertEqual(sum(name.startswith(prefix + "repository/") for name in bundle.namelist()), 21)
            artifact = prefix + "repository/de/drremote/db/sqlite/1.2.3/sqlite-1.2.3.jar"
            self.assertEqual(bundle.read(artifact + ".sha256").decode().strip(), hashlib.sha256(bundle.read(artifact)).hexdigest())
            manifest = json.loads(bundle.read(prefix + "manifest.json"))
            self.assertEqual(manifest["version"], self.version)
            for name, digest in manifest["files"].items():
                self.assertEqual(hashlib.sha256(bundle.read(prefix + name)).hexdigest(), digest)
            self.assertIn(prefix + "examples/json-client/src/main/java/Client.java", bundle.namelist())
            self.assertIn(prefix + "examples/notes-client/target/notes-client-1.2.3.jar", bundle.namelist())
            self.assertIn(prefix + "docs/releases/1.2.3.md", bundle.namelist())
            self.assertEqual((bundle.getinfo(artifact).external_attr >> 16) & 0o777, 0o644)
            self.assertIn(b"env.DRREMOTE_RELEASE_DIR", bundle.read(prefix + "maven-settings.xml"))

    def test_missing_required_artifact_fails_without_leaving_a_release(self):
        path = self.repository / "de/drremote/db/sqlite/1.2.3/sqlite-1.2.3-sources.jar"
        path.unlink()
        with self.assertRaisesRegex(ValueError, "sources.jar"):
            create_release(self.project, self.repository, self.output)
        self.assertFalse(self.output.exists())

    def test_snapshot_source_version_is_refused(self):
        self.parent.write_text(self.parent.read_text().replace("1.2.3", "1.2.3-SNAPSHOT"))
        with self.assertRaisesRegex(ValueError, "SNAPSHOT"):
            create_release(self.project, self.repository, self.output)
        self.assertFalse(self.output.exists())

    def test_wrong_installed_pom_is_refused(self):
        self.install("de.drremote.db", "sqlite", "pom", self.pom("de.drremote.db", "sqlite", "bundle").replace("1.2.3", "1.2.2"))
        with self.assertRaisesRegex(ValueError, "POM"):
            create_release(self.project, self.repository, self.output)
        self.assertFalse(self.output.exists())

    def test_missing_example_bundle_is_refused(self):
        (self.project / "examples/json-client/target/json-client-1.2.3.jar").unlink()
        with self.assertRaisesRegex(ValueError, "json-client"):
            create_release(self.project, self.repository, self.output)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
