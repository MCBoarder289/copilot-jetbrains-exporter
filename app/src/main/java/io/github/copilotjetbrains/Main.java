package io.github.copilotjetbrains;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import io.github.copilotjetbrains.model.AgentSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

@Command(
        name        = "copilot-jetbrains-exporter",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = {
                "Export JetBrains Copilot chat sessions from the Nitrite database.",
                "Supports agentsview-compatible JSONL output or standalone Markdown files.",
                "",
                "Default source directories (auto-detected by OS):",
                "  macOS:   ~/.config/github-copilot/  (or $XDG_CONFIG_HOME/github-copilot/)",
                "  Linux:   ~/.config/github-copilot/  (or $XDG_CONFIG_HOME/github-copilot/)",
                "  Windows: %%APPDATA%%\\github-copilot\\",
        },
        sortOptions = false
)
public class Main implements Callable<Integer> {

    public enum Format { JSONL, MARKDOWN }

    @Option(
            names       = {"-s", "--source"},
            description = "JetBrains config directory to scan for Nitrite DB files. "
                        + "May be repeated to scan multiple directories. "
                        + "Default: OS-specific auto-detect.",
            paramLabel  = "<dir>"
    )
    private List<Path> sourceDirs;

    @Option(
            names       = {"-o", "--output"},
            description = "Directory to write exported files into. "
                        + "Default depends on --format and OS (run --help for details).",
            paramLabel  = "<dir>"
    )
    private Path outputDir;

    @Option(
            names        = {"-f", "--format"},
            description  = "Output format: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.",
            defaultValue = "JSONL"
    )
    private Format format = Format.JSONL;

    @Option(
            names       = {"--dry-run"},
            description = "Show what would be exported without writing any files."
    )
    private boolean dryRun;

    @Option(
            names       = {"-v", "--verbose"},
            description = "Print additional detail during export."
    )
    private boolean verbose;

    static class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            var url = Main.class.getClassLoader().getResource("version.properties");
            if (url == null) return new String[]{"unknown"};
            var props = new Properties();
            try (var in = url.openStream()) { props.load(in); }
            return new String[]{props.getProperty("version", "unknown")};
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        List<Path> sources = (sourceDirs != null && !sourceDirs.isEmpty())
                ? sourceDirs
                : PlatformDefaults.defaultSourceDirs();

        Path output = (outputDir != null)
                ? outputDir
                : PlatformDefaults.defaultOutputDir(format);

        if (verbose) {
            System.out.println("Source directories:");
            sources.forEach(d -> System.out.println("  " + d));
            System.out.println("Output directory: " + output);
            System.out.println("Format:           " + format);
        }

        List<Path> dbFiles = NitriteReader.findDbFiles(sources);
        if (dbFiles.isEmpty()) {
            System.err.println("No " + NitriteReader.DB_FILENAME + " files found.");
            System.err.println("Use --source to specify the JetBrains config directory, e.g.:");
            System.err.println("  --source \"~/Library/Application Support/github-copilot\"");
            return 1;
        }

        if (verbose) {
            System.out.println("Found " + dbFiles.size() + " database file(s):");
            dbFiles.forEach(f -> System.out.println("  " + f));
        }

        List<AgentSession> allSessions = new ArrayList<>();
        for (Path dbFile : dbFiles) {
            if (verbose) System.out.println("Reading: " + dbFile);
            try (NitriteReader reader = new NitriteReader(dbFile)) {
                reader.readSessions().stream()
                        .filter(s -> !s.turns().isEmpty())
                        .forEach(allSessions::add);
            } catch (IOException e) {
                System.err.println("Error reading " + dbFile + ":");
                System.err.println(e.getMessage());
                return 1;
            }
        }

        if (allSessions.isEmpty()) {
            System.out.println("No sessions with conversation turns found.");
            return 0;
        }

        System.out.printf("Exporting %d session(s) to %s (%s format)%n",
                allSessions.size(), output, format);

        try {
            int count = switch (format) {
                case JSONL    -> JsonlExporter.export(allSessions, output, dryRun, verbose);
                case MARKDOWN -> MarkdownExporter.export(allSessions, output, dryRun, verbose);
            };
            System.out.printf("%s %d session(s).%n",
                    dryRun ? "Would export" : "Exported", count);

            if (!dryRun && format == Format.JSONL) {
                System.out.println();
                System.out.println("To view in agentsview, add this to your config or environment:");
                System.out.println("  COPILOT_DIR=" + output);
                System.out.println("  (or set copilot_dirs: [\"" + output + "\"] in agentsview's config.json)");
            }
        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
            return 1;
        }

        return 0;
    }
}
