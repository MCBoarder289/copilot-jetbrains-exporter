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

### Building the native binary locally

Requires [GraalVM CE 21](https://www.graalvm.org/downloads/) on your PATH (or via sdkman:
`sdk install java 21.0.2-graalce && sdk use java 21.0.2-graalce`):

```bash
./gradlew app:nativeCompile
# Output: app/build/native/nativeCompile/copilot-jetbrains-exporter
```

### Running tests

```bash
./gradlew app:test
```

Tests use synthetic (fictional) conversation data — no real sessions are required or
included in the test suite.

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
