# copilot-jetbrains-exporter

Export JetBrains IDE Copilot chat sessions (IntelliJ, PyCharm, etc.) for viewing in
[agentsview](https://github.com/wesm/agentsview) or as standalone Markdown files.

JetBrains stores Copilot sessions in a Nitrite/MVStore database
(`copilot-agent-sessions-nitrite.db`) rather than plain JSON. This tool reads that
database and converts sessions to formats other tools understand.

## Requirements

- **Native binary** (recommended): no Java required — download a pre-built binary for your OS.
- **JAR**: Java 21 or later. IntelliJ bundles a JDK so you likely already have it.  
  Verify with: `java -version`

## Installation

### Option A: Native binary (no Java needed)

Download the binary for your OS from the [Releases](../../releases) page:

| OS | File |
|----|------|
| macOS | `copilot-jetbrains-exporter-macos-amd64` |
| Linux | `copilot-jetbrains-exporter-linux-amd64` |
| Windows | `copilot-jetbrains-exporter-windows-amd64.exe` |

Make it executable (macOS/Linux):
```bash
chmod +x copilot-jetbrains-exporter-macos-amd64
# Optionally move it onto your PATH:
mv copilot-jetbrains-exporter-macos-amd64 /usr/local/bin/copilot-jetbrains-exporter
```

On macOS you may need to allow the binary in **System Settings → Privacy & Security** the
first time you run it (Gatekeeper prompt).

### Option B: Fat JAR (requires Java 21+)

Download `copilot-jetbrains-exporter-<version>.jar` from [Releases](../../releases).

## Usage

```bash
# Native binary
copilot-jetbrains-exporter

# Fat JAR
java -jar copilot-jetbrains-exporter.jar

# Specify where the exported files should go
copilot-jetbrains-exporter --output ~/.copilot/jetbrains-sessions

# Export as Markdown instead
copilot-jetbrains-exporter --format MARKDOWN --output ~/Documents/copilot-export

# Specify the JetBrains config directory explicitly (non-standard install or Linux)
copilot-jetbrains-exporter --source "~/.config/github-copilot"

# Preview without writing anything
copilot-jetbrains-exporter --dry-run --verbose

# Full help
copilot-jetbrains-exporter --help
```

### All options

| Flag | Default | Description |
|------|---------|-------------|
| `-s`, `--source <dir>` | OS auto-detect | JetBrains config dir to scan. Repeatable. |
| `-o`, `--output <dir>` | OS default (see below) | Where to write exported files. |
| `-f`, `--format` | `JSONL` | Output format: `JSONL` or `MARKDOWN`. |
| `--dry-run` | false | Show what would happen without writing files. |
| `-v`, `--verbose` | false | Print extra detail during export. |
| `-h`, `--help` | | Show help and exit. |
| `-V`, `--version` | | Show version and exit. |

## Default paths

### Source (where JetBrains stores sessions)

| OS | Auto-detected path |
|----|-------------------|
| macOS | `~/.config/github-copilot/` (or `$XDG_CONFIG_HOME/github-copilot/`) |
| Linux | `~/.config/github-copilot/` (or `$XDG_CONFIG_HOME/github-copilot/`) |
| Windows | `%APPDATA%\github-copilot\` |

Use `--source` to override, for example if your JetBrains IDE is installed in a
non-standard location or you have multiple profiles.

### Output (where exported files are written)

| Format | Default output path |
|--------|-------------------|
| JSONL  | `~/.copilot/jetbrains-sessions/` (all platforms) |
| Markdown | macOS: `~/Library/Application Support/copilot-jetbrains-exporter/export/` |
|          | Linux: `~/.local/share/copilot-jetbrains-exporter/export/` |
|          | Windows: `%LOCALAPPDATA%\copilot-jetbrains-exporter\export\` |

## Using with agentsview

After running the exporter, point agentsview at the output directory:

```bash
# Option 1: environment variable
export COPILOT_DIR=~/.copilot/jetbrains-sessions

# Option 2: agentsview config file (~/.agentsview/config.json)
{
  "copilot_dirs": ["~/.copilot/jetbrains-sessions"]
}
```

Re-run the exporter after each JetBrains Copilot session to pick up new conversations.

## Building from source

```bash
git clone https://github.com/<your-username>/copilot-jetbrains-exporter.git
cd copilot-jetbrains-exporter
./gradlew app:shadowJar
# Output: app/build/libs/copilot-jetbrains-exporter-<version>.jar
```

### Common development commands

```bash
# Run all tests
./gradlew app:test

# Run tests and show output even on success (useful when debugging a specific test)
./gradlew app:test --info

# Run a single test class
./gradlew app:test --tests "io.github.copilotjetbrains.NitriteReaderTest"

# Run a single test method
./gradlew app:test --tests "io.github.copilotjetbrains.NitriteReaderTest.testReadSessions"

# Build the fat JAR (all dependencies bundled)
./gradlew app:shadowJar
# Output: app/build/libs/copilot-jetbrains-exporter-<version>.jar

# Run the fat JAR directly (quick smoke test without installing)
java -jar app/build/libs/copilot-jetbrains-exporter-*.jar --dry-run

# Clean build outputs
./gradlew clean

# List all available tasks
./gradlew tasks
```

> **Java version:** The project requires Java 21. If your default `java` is older, set
> `JAVA_HOME` before running Gradle:
> ```bash
> # sdkman
> sdk use java 21.0.6-tem
> # or export directly
> export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.6-tem
> ```

### Building the native binary locally

Requires [GraalVM CE 21](https://www.graalvm.org/downloads/) on your PATH (or via sdkman:
`sdk install java 21.0.2-graalce && sdk use java 21.0.2-graalce`):

```bash
./gradlew app:nativeCompile
# Output: app/build/native/nativeCompile/copilot-jetbrains-exporter

# Smoke test the native binary
./app/build/native/nativeCompile/copilot-jetbrains-exporter --dry-run
```

### Running tests

```bash
./gradlew app:test
```

Tests use synthetic (fictional) conversation data — no real sessions are required or
included in the test suite.

### Testing CI workflows locally with `act`

[act](https://nektosact.com/) runs GitHub Actions workflows locally inside Docker. It is
useful for verifying the `test` job before pushing.

```bash
# Install (macOS)
brew install act

# Run only the ubuntu matrix leg (recommended — fast, avoids a known act/Windows quirk)
act -j test --container-architecture linux/amd64 --matrix os:ubuntu-latest

# Run the full matrix (windows-latest will likely fail with a JAVA_HOME path error
# in act — this is a known act limitation with Linux containers, not a real test failure)
act -j test --container-architecture linux/amd64

# Simulate a push to main
act push -j test --container-architecture linux/amd64 --matrix os:ubuntu-latest
```

**Important limitations with `act` for this project:**

- The `test` job runs fine locally — it just needs Java 21.
- The `native` job uses `graalvm/setup-graalvm@v1` which pulls a large Docker image and
  compiles a Linux binary inside a container. It will work but is slow (~10-15 min).
- The `release` job calls `softprops/action-gh-release` which requires a real
  `GITHUB_TOKEN` with write access. For local testing, skip it or use `--dry-run`.
- `act` runs Linux containers on all platforms; native binaries for macOS and Windows
  **must** be built on real GitHub-hosted runners — there is no local workaround.

For the native image, the best local test is `./gradlew app:nativeCompile` directly (see
above), which builds the binary for your current OS.

## Releasing a new version

All releases are cut by pushing a version tag to `main`. The CI pipeline handles
everything else.

### Step-by-step

1. **Bump the version** in `gradle.properties`:
   ```
   version=0.2.0
   ```

2. **Commit** the version bump:
   ```bash
   git add gradle/properties
   git commit -m "chore: bump version to 0.2.0"
   ```

3. **Push the commit** to `main` (or merge your PR first):
   ```bash
   git push origin main
   ```

4. **Tag the release** — the tag triggers all build and release jobs:
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```

5. **Watch the run** at `https://github.com/<owner>/copilot-jetbrains-exporter/actions`.
   The pipeline runs three jobs in parallel:
   - `build` — fat JAR on Ubuntu
   - `native` — native binaries on Ubuntu, macOS, and Windows
   Then `release` downloads all artifacts and creates the GitHub Release with
   auto-generated release notes.

### What gets published

| Artifact | Platform |
|----------|----------|
| `copilot-jetbrains-exporter-<version>.jar` | All (requires Java 21) |
| `copilot-jetbrains-exporter-linux-amd64` | Linux x86-64 |
| `copilot-jetbrains-exporter-macos-amd64` | macOS (Intel + Apple Silicon via Rosetta) |
| `copilot-jetbrains-exporter-windows-amd64.exe` | Windows x86-64 |

### If a release job fails

- Re-trigger only the failed job from the Actions UI (use "Re-run failed jobs").
- Do **not** delete and re-push the tag — this creates a duplicate release. Instead,
  delete the draft/broken release on GitHub, fix the issue, and re-run the workflow.
- If the native macOS or Windows job fails due to a reflection or serialization error,
  run `./gradlew app:nativeCompile` locally on that OS first to get the exact error,
  update `reflect-config.json` or `serialization-config.json`, and push a fix commit
  before re-running.

## A note on compatibility

JetBrains stores sessions using [Nitrite 4.x](https://github.com/nitrite/nitrite-java),
a Java-native embedded database. This tool bundles a compatible Nitrite version so no
extra dependencies are needed.

If the JetBrains Copilot plugin is updated to a significantly newer Nitrite version,
you may see an error like *"serial version mismatch"* when opening the database. If that
happens, please [open an issue](../../issues) and include the error output — a version
bump in the build file is usually all that's needed.

**Tip:** Close IntelliJ/PyCharm before running the exporter if you get a "file locked"
error — the IDE holds an exclusive lock on the database while running.

## License

MIT
