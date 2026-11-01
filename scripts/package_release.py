#!/usr/bin/env python3
"""Package built public artifacts as a portable Maven repository, without deploying."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom_identity(path):
    root = ET.parse(path).getroot()
    def value(name):
        return root.findtext("m:" + name, namespaces=NS) or root.findtext("m:parent/m:" + name, namespaces=NS)
    return root, (value("groupId"), value("artifactId"), value("version")), value("packaging") or "jar"


def required_file(path):
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"Missing release input or unsupported symlink: {path}")
    return path.read_bytes()


def create_release(project, repository, output):
    project, repository, output = Path(project), Path(repository), Path(output)
    root, parent, _ = pom_identity(project / "pom.xml")
    version = parent[2]
    if not version or "SNAPSHOT" in version:
        raise ValueError("A release version is required; SNAPSHOT artifacts cannot be packaged")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError(f"Expected a stable major.minor.patch release version, got {version}")

    files = {}
    projects = [project / "pom.xml"] + [
        project / module.text / "pom.xml" for module in root.findall("m:modules/m:module", NS)]
    for source in projects:
        _, identity, packaging = pom_identity(source)
        group, artifact, artifact_version = identity
        if not group or not artifact or artifact_version != version:
            raise ValueError(f"Project POM coordinates/version do not match release: {source}")
        relative = Path(group.replace(".", "/")) / artifact / version
        base = f"{artifact}-{version}"
        installed_pom = repository / relative / (base + ".pom")
        required_file(installed_pom)
        if pom_identity(installed_pom)[1] != identity:
            raise ValueError(f"Installed POM coordinates/version do not match source: {installed_pom}")
        suffixes = [".pom"]
        if packaging == "bundle":
            suffixes += [".jar", "-sources.jar", "-javadoc.jar"]
        elif packaging == "feature":
            suffixes += ["-features.xml"]
        elif packaging != "pom":
            raise ValueError(f"Unsupported release packaging {packaging}: {source}")
        for suffix in suffixes:
            path = relative / (base + suffix)
            data = required_file(repository / path)
            name = "repository/" + path.as_posix()
            files[name] = data
            files[name + ".sha1"] = (hashlib.sha1(data, usedforsecurity=False).hexdigest() + "\n").encode()
            files[name + ".sha256"] = (hashlib.sha256(data).hexdigest() + "\n").encode()

    for name in ["LICENSE", "CHANGELOG.md", "CONTRIBUTING.md"]:
        files[name] = required_file(project / name)
    required_file(project / "docs/binary-installation.md")
    for path in sorted((project / "docs").glob("*.md")):
        files[path.relative_to(project).as_posix()] = required_file(path)
    release_notes = project / "docs/releases" / (version + ".md")
    files[release_notes.relative_to(project).as_posix()] = required_file(release_notes)
    for path in sorted((project / "examples").glob("de.drremote.db.*.cfg.example")):
        files[path.relative_to(project).as_posix()] = required_file(path)
    for client in ["notes-client", "json-client"]:
        directory = project / "examples" / client
        for path in [directory / "pom.xml", directory / "README.md",
                     directory / "target" / f"{client}-{version}.jar"]:
            files[path.relative_to(project).as_posix()] = required_file(path)
        for path in sorted((directory / "src").rglob("*")):
            if path.is_file():
                files[path.relative_to(project).as_posix()] = required_file(path)

    files["README.md"] = (f"# DrRemote DB {version}\n\n"
        "This archive contains the project's Maven artifacts, examples, and documentation.\n"
        "Start with [binary installation](docs/binary-installation.md).\n"
        "Third-party dependencies resolve from Maven Central. PostgreSQL ScriptMigration\n"
        "requires an installed psql client; complex psql scripts remain supported.\n"
        "Source: https://github.com/DoctorRemoteControl/drremote-db-postgresql-sqlite\n").encode()
    files["maven-settings.xml"] = b'''<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <profiles><profile><id>drremote-binary</id><repositories><repository>
    <id>drremote-release</id><url>file://${env.DRREMOTE_RELEASE_DIR}/repository</url>
    <releases><enabled>true</enabled><checksumPolicy>fail</checksumPolicy></releases>
    <snapshots><enabled>false</enabled></snapshots>
  </repository></repositories></profile></profiles>
  <activeProfiles><activeProfile>drremote-binary</activeProfile></activeProfiles>
</settings>
'''
    manifest = {"version": version, "files": {
        name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())}}
    files["manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()

    # Validate and collect every input before creating output. Never glob a Maven
    # cache into the archive: it may contain unrelated or private artifacts.
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f"drremote-db-{version}.zip"
    prefix = f"drremote-db-{version}/"
    with tempfile.NamedTemporaryFile(dir=output, suffix=".zip", delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(prefix + name)
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                bundle.writestr(entry, data)
        temporary_path.replace(archive)
    finally:
        temporary_path.unlink(missing_ok=True)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(digest + "  " + archive.name + "\n")
    return archive


def main():
    project = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True, help="Maven cache used for the completed clean install")
    parser.add_argument("--output", type=Path, default=project / "target/release")
    args = parser.parse_args()
    try:
        archive = create_release(project, args.repository, args.output)
    except (ValueError, OSError, ET.ParseError) as failure:
        print(f"Release packaging failed: {failure}", file=sys.stderr)
        return 1
    print(archive)
    print(archive.parent / "SHA256SUMS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
