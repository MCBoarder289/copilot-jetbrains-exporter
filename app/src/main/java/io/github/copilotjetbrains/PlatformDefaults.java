package io.github.copilotjetbrains;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves OS-specific default paths for source directories and output.
 *
 * <p>JetBrains stores Copilot sessions in:
 * <ul>
 *   <li>macOS: {@code ~/.config/github-copilot/}</li>
 *   <li>Linux: {@code ~/.config/github-copilot/} (or {@code $XDG_CONFIG_HOME/github-copilot/})</li>
 *   <li>Windows: {@code %APPDATA%\github-copilot\}</li>
 * </ul>
 */
public final class PlatformDefaults {

    private PlatformDefaults() {}

    /**
     * Returns the default directories to scan for Nitrite DB files, based on the current OS.
     * These mirror the paths where the JetBrains GitHub Copilot plugin stores its data.
     */
    public static List<Path> defaultSourceDirs() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        List<Path> dirs = new ArrayList<>();

        if (os.contains("mac") || os.contains("darwin")) {
            // JetBrains uses ~/.config on macOS, same as Linux.
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isBlank()) {
                dirs.add(Path.of(xdgConfig, "github-copilot"));
            } else {
                dirs.add(Path.of(home, ".config", "github-copilot"));
            }
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = Path.of(home, "AppData", "Roaming").toString();
            }
            dirs.add(Path.of(appData, "github-copilot"));
        } else {
            // Linux and other Unix-like systems; respect XDG_CONFIG_HOME if set.
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isBlank()) {
                dirs.add(Path.of(xdgConfig, "github-copilot"));
            } else {
                dirs.add(Path.of(home, ".config", "github-copilot"));
            }
        }

        return dirs;
    }

    /**
     * Returns the default output directory for the given format, based on the current OS.
     *
     * <p>For JSONL format, the output lands in {@code ~/.copilot/jetbrains-sessions/} on all
     * platforms so that agentsview can pick it up with a single {@code COPILOT_DIR} config entry.
     *
     * <p>For Markdown format, a user-visible directory is chosen per OS convention.
     */
    public static Path defaultOutputDir(Main.Format format) {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        if (format == Main.Format.JSONL) {
            // agentsview-compatible: place alongside CLI Copilot sessions.
            return Path.of(home, ".copilot", "jetbrains-sessions");
        }

        // Markdown output: use the OS-conventional user data location.
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support",
                    "copilot-jetbrains-exporter", "export");
        } else if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isBlank()) {
                localAppData = Path.of(home, "AppData", "Local").toString();
            }
            return Path.of(localAppData, "copilot-jetbrains-exporter", "export");
        } else {
            return Path.of(home, ".local", "share", "copilot-jetbrains-exporter", "export");
        }
    }
}
