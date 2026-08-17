# kura-log-filesystem

Eclipse Kura™ Filesystem log addon.

The addon provides the **`FilesystemLogProvider`**, a Kura `LogProvider`
implementation that tails a log file, parses each line and hands the resulting
`LogEntry` objects to the registered listeners. It is what backs the log view of
the Kura web console when the logs live on the filesystem rather than in the
journal.

The provider is a factory component, so more than one instance can be
configured, each one watching a different file.

The bundle used to live in the [Eclipse Kura](https://github.com/eclipse-kura/kura)
monorepo (`kura/org.eclipse.kura.log.filesystem.provider`) and used to be
installed by the `kura-core` package. It now lives here and is released as a
standalone Debian package.

## Contents

| Module | Artifact | Description |
|---|---|---|
| `org.eclipse.kura.log.filesystem.provider` | bundle | the `LogProvider` and the Kura log line parser |
| `bom` | `kura-log-filesystem-bom` | the bundles released by this project |
| `distrib` | `kura-log-filesystem-distrib` | Debian packaging (`jdeb`) |
| `tests` | `kura-log-filesystem-tests` | unit tests and OSGi integration tests |

The build is **Maven + [bnd](https://bnd.bndtools.org/)** targeting Java 21 —
there is no Tycho and no target definition. The project was bootstrapped with
[`kura-archetype`](https://github.com/eclipse-kura/kura-archetype).

Declarative Services and Metatype descriptors are hand-written and kept in
source control under `org.eclipse.kura.log.filesystem.provider/OSGI-INF/`.

## Prerequisites

| | |
|---|---|
| **JDK 21** | the project sets `maven.compiler.release=21` |
| **Maven 3.9.x** | |
| **git** | `git-commit-id-maven-plugin` stamps the commit hash into the snapshot Debian version |

## Building

```bash
mvn clean install
```

Add `-Presolve-integration-tests` whenever `-runrequires` or the bundle imports
change: the profile runs `bnd-resolver-maven-plugin:resolve`, which recomputes
the `-runbundles` list of
`tests/org.eclipse.kura.log.filesystem.provider.test/integration-test.bndrun`.
Commit the resolved `.bndrun`.

```bash
mvn clean install -Presolve-integration-tests
```

### Tests

Both test kinds run as part of `mvn verify`/`mvn install`:

- **unit tests** — `maven-surefire-plugin`, from
  `tests/org.eclipse.kura.log.filesystem.provider.test/src/test/java`;
- **OSGi integration test** — `bnd-testing-maven-plugin`, from
  `.../src/main/java` (it is part of the test bundle). It starts an embedded
  Kura framework, creates a provider instance through the Configuration Service
  pointing at a temporary file, then appends a line to that file and checks that
  the parsed entry reaches a registered `LogListener`.

Reports land in `tests/org.eclipse.kura.log.filesystem.provider.test/target/surefire-reports/`
(unit) and `.../surefire-reports/integration-test/` (OSGi); JaCoCo writes to
`.../target/site/jacoco-aggregate/`.

## Debian package

`jdeb` is bound to the `package` phase, so every `mvn package`/`install`
produces `distrib/target/deb/kura-log-filesystem_<version>-<revision>_all.deb`.

| Build | Version | Command |
|---|---|---|
| development (default) | `2.0.0~git202608170906.b1c2c21-1` | `mvn clean install` |
| release | `2.0.0-1` | `mvn clean install -DreleaseBuild` |

`-DreleaseBuild` also activates the enforcer rule that fails the build if the
project version is still a `-SNAPSHOT`.

The package depends on `kura-core (>= 6.0.0~), kura-core (<< 7.0.0~)` and
installs the bundle in `/opt/eclipse/kura/plugins/4s/` — the same start level
`kura-core` used to install it at, so log consumers still find a provider as
early as they did.

Install it on a device and restart Kura:

```bash
apt install ./kura-log-filesystem_<version>_all.deb
systemctl restart kura
```

## Configuring a provider

Create an instance from the Kura web console selecting the
`org.eclipse.kura.log.filesystem.provider.FilesystemLogProvider` factory:

| Property | Description | Default |
|---|---|---|
| `logFilePath` | file the provider reads the log entries from | `/var/log/kura.log` |

The file is read from its beginning and then followed, with a 100 ms poll
interval. A missing file is logged and leaves the provider running, so
configuring a path that does not exist yet is not an error.

### Restoring the instances kura-core used to create

Up to Kura 6, `kura-core` shipped this bundle and its default snapshot
pre-configured two instances. It no longer does, so after installing this package
create them again — with these exact `kura.service.pid` values, because
`kura.default.log.manager` in `kura.properties` still points at the first one:

| `kura.service.pid` | `logFilePath` |
|---|---|
| `filesystem-kura-log` | `/var/log/kura.log` |
| `filesystem-kura-audit-log` | `/var/log/kura-audit.log` |

### Line parsing

The parser recognises two formats, and picks one based on the **file name**:

| Path contains | Format |
|---|---|
| `kura.log` | `TIMESTAMP [PID] PRIORITY MESSAGE` |
| `kura-audit.log` | the Kura audit syslog format |

For any other file name the line is passed through as the `MESSAGE` property,
with the default timestamp, PID and priority. Keep that in mind when pointing a
provider at a renamed or rotated file: `kura.log.1` still parses, `kura-old.txt`
does not.

## Contributing

See the [Kura contribution guide](https://github.com/eclipse-kura/kura/blob/develop/CONTRIBUTING.md).
Pull request titles must follow the
[Conventional Commits](https://www.conventionalcommits.org/) format, and signing
the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php) is
required.

## License

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
