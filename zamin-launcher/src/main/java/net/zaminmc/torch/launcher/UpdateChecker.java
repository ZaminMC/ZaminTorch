package net.zaminmc.torch.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The update prompt: on boot, a background thread fetches the project's
 * latest published release tag from the GitHub API and compares it against
 * the running build's version. A newer tag logs a PROMPT-style banner with
 * the download page (the operator decides when to upgrade; the server never
 * self-updates mid-flight).
 *
 * <p>Design rules (§54, §121): the check never blocks boot, never kills the
 * server on failure, and every failure path is silent-at-FINE — an offline
 * server boots exactly like an online one, just without the banner. The
 * timeout is deliberately small (2s connect/read): a hung proxy must not
 * delay the "Server ready" line beyond a human's patience.</p>
 *
 * <p>The torch-1.8.8-N era (build 26 on): releases are prereleases cut
 * automatically from every develop push, tagged torch-1.8.8-N. The list
 * endpoint (not /releases/latest, which cannot see prereleases) carries
 * them; the atom feed remains the rate-limit-free fallback. The parser
 * still reads the legacy vX.Y.Z-dev.N ledger so a fresh build orders
 * itself correctly against any tag ever shipped (torch-1.8.8-26 &gt;
 * v0.2.0-dev.25).</p>
 */
final class UpdateChecker {

    /** The running build's version — stamped by the release workflow. */
    static final String BUILD_VERSION = "1.8.8-40";

    private static final Logger LOGGER = Logger.getLogger(UpdateChecker.class.getName());
    /** The first page of the release list — newest first, prereleases included
     *  (/releases/latest skips prereleases, and every automated release is one). */
    private static final String RELEASES_API =
            "https://api.github.com/repos/ZaminMC/ZaminTorch/releases?per_page=1";
    /** The atom feed carries prereleases too and carries no API rate limit. */
    private static final String RELEASES_ATOM =
            "https://github.com/ZaminMC/ZaminTorch/releases.atom";
    private static final String RELEASES_PAGE =
            "https://github.com/ZaminMC/ZaminTorch/releases";

    private final AtomicBoolean started = new AtomicBoolean(false);

    /** Kicks the background check once per process; safe to call twice. */
    void checkAsync() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info(() -> "Update check: looking for newer releases (" + BUILD_VERSION + ")");
        Thread worker = new Thread(this::run, "zamin-update-check");
        worker.setDaemon(true); // never keeps the JVM alive on its own
        worker.start();
    }

    private void run() {
        try {
            String latest = fetchLatestTag();
            if (latest == null) {
                latest = fetchLatestTagViaAtom();
            }
            if (latest == null || latest.isBlank()) {
                LOGGER.fine("Update check: no release information available");
                return;
            }
            if (isNewer(latest, BUILD_VERSION)) {
                LOGGER.warning("");
                LOGGER.warning("==========================================================");
                LOGGER.warning(" An update is available: " + latest
                        + " (this build: " + BUILD_VERSION + ")");
                LOGGER.warning(" Download: " + RELEASES_PAGE);
                LOGGER.warning(" Stop, replace the server jar, and start again to update.");
                LOGGER.warning("==========================================================");
            } else {
                LOGGER.info("Update check: this build (" + BUILD_VERSION
                        + ") is up to date (latest release " + latest + ")");
            }
        } catch (Exception e) {
            // Any failure (offline, rate-limited, DNS) is a fine-log footnote,
            // never a boot problem (§54: broken observers must not starve).
            LOGGER.log(Level.FINE, "Update check skipped: " + e.getMessage(), e);
        }
    }

    private String fetchLatestTag() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(RELEASES_API)
                .toURL().openConnection();
        connection.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        connection.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "ZaminTorch/" + BUILD_VERSION);
        int status = connection.getResponseCode();
        if (status != 200) {
            LOGGER.fine(() -> "Update check: API answered " + status);
            return null;
        }
        String body = readAll(connection);
        connection.disconnect();
        return extractTag(body);
    }

    /**
     * The fallback: the releases atom feed, whose first entry carries the
     * newest release's tag (prereleases included, no API rate limit) —
     * {@code <id>tag:github.com,2008:Repository/<repo>/<tag></id>}.
     */
    private String fetchLatestTagViaAtom() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(RELEASES_ATOM)
                    .toURL().openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
            connection.setRequestProperty("User-Agent", "ZaminTorch/" + BUILD_VERSION);
            int status = connection.getResponseCode();
            if (status != 200) {
                LOGGER.fine(() -> "Update check: atom feed answered " + status);
                connection.disconnect();
                return null;
            }
            String feed = readAll(connection);
            connection.disconnect();
            int entryAt = feed.indexOf("<entry>");
            if (entryAt < 0) {
                return null;
            }
            int idAt = feed.indexOf("<id>tag:github.com,2008:Repository/", entryAt);
            if (idAt < 0) {
                return null;
            }
            // tag:github.com,2008:Repository/<repoId>/<tag> — the tag is the
            // LAST segment, not everything after the first slash.
            int from = idAt + "<id>".length();
            int to = feed.indexOf('<', from);
            if (to < 0) {
                return null;
            }
            String id = feed.substring(from, to);
            int tagAt = id.lastIndexOf('/');
            return tagAt < 0 ? null : id.substring(tagAt + 1);
        } catch (Exception e) {
            LOGGER.fine(() -> "Update check: atom probe failed: " + e.getMessage());
            return null;
        }
    }

    private String readAll(HttpURLConnection connection) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }

    /** Pulls the first {@code "tag_name":"..."} out of the release JSON (no
     *  parser dependency) — the list endpoint orders newest-first, so the
     *  first hit is the latest release, prereleases included. */
    private String extractTag(String body) {
        String marker = "\"tag_name\":\"";
        int at = body.indexOf(marker);
        if (at < 0) {
            return null;
        }
        int from = at + marker.length();
        int to = body.indexOf('"', from);
        return to < 0 ? null : body.substring(from, to);
    }

    /**
     * Compares {@code torch-1.8.8-N} release tags against the running
     * {@code 1.8.8-N} build (the tag carries a "torch-" prefix the constant
     * does not), with the legacy {@code vX.Y.Z-dev.N} ledger still readable
     * so any torch build orders above every legacy one:
     * {@code torch-1.8.8-26 > v0.2.0-dev.25 > v0.2.0-dev.24}. Numeric
     * awareness over string order. Any parse trouble degrades to a
     * conservative "not newer" so a weird tag never nags.
     */
    static boolean isNewer(String candidate, String current) {
        long[] a = parse(candidate);
        long[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return a[i] > b[i];
            }
        }
        return false;
    }

    /**
     * {@code torch-1.8.8-N} (release tags), {@code 1.8.8-N} (BUILD_VERSION)
     * and the legacy {@code vX.Y.Z-dev.N} all map to a comparable
     * (major, minor, patch, build) quadruple; null when unrecognized.
     */
    private static long[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        // The release tag carries a prefix the build constant does not.
        if (version.startsWith("torch-")) {
            version = version.substring("torch-".length());
        }
        String core;
        if (version.charAt(0) == 'v') {
            core = version.substring(1).replace("-dev.", "."); // legacy: v0.2.0-dev.25 -> 0.2.0.25
        } else {
            core = version.replace('-', '.'); // torch era: 1.8.8-26 -> 1.8.8.26
        }
        if (!core.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return null;
        }
        String[] parts = core.split("\\.");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Long.parseLong(parts[i]);
        }
        return out;
    }
}
