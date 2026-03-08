# Changelog

All notable changes to this project will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - Initial release

### Added
- Read JetBrains Copilot sessions from `copilot-agent-sessions-nitrite.db`
- JSONL output format compatible with [agentsview](https://github.com/wesm/agentsview) Copilot parser
- Markdown output format for standalone human-readable export
- Auto-detection of JetBrains config directories on macOS, Linux, and Windows
- OS-specific default output directories
- `--source` flag to specify custom JetBrains config directories (repeatable)
- `--output` flag to specify the output directory
- `--format` flag to choose between `JSONL` and `MARKDOWN`
- `--dry-run` flag to preview exports without writing files
- `--verbose` flag for detailed output
- Handles both inline turns (stored in session document) and standalone turns (separate collection)
- Skips deleted turns automatically
- Content extraction from nested JSON-in-JSON `contents` field with fallback to `stringContent`
- Fat JAR distribution — single file, no external dependencies required
- GitHub Actions CI: build and test on macOS, Linux, and Windows
- GitHub Actions release workflow: attaches fat JAR to tagged releases
