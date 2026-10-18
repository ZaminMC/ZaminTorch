package net.zaminmc.torch.server.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * The world-session lock (architecture §31.6 "session ownership"): exactly
 * one LIVE writer may hold a world root at a time. The lock is an OS-level
 * {@link FileLock} over a marker file inside the world directory, held for
 * the server's whole run and released at shutdown.
 *
 * <p>Why an OS lock and not a lock FILE: the §31 rule is "reject a second
 * live writer... instead of relying only on a lock file's presence or
 * timestamp". A leftover lock file from a crashed process is meaningless
 * noise under this scheme — the operating system releases the lock itself
 * when the owning process dies, so a stale marker can never block a
 * legitimate restart (the deliberate, testable stale-lock story: there
 * isn't one to recover from). A genuinely live second writer — another
 * process, or a second boot attempt inside this JVM (which surfaces as
 * {@link OverlappingFileLockException}) — is refused LOUDLY, because two
 * authoritative writers on one world is exactly the corruption §3 forbids.</p>
 */
public final class WorldSessionLock implements AutoCloseable {

    /** The marker file's name inside the world root. */
    public static final String LOCK_FILE = "session.lock";

    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;

    private WorldSessionLock(Path file, FileChannel channel, FileLock lock) {
        this.file = file;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Acquires the exclusive world-session lock.
     *
     * @throws IllegalStateException when another live writer holds the world
     *         (the loud refusal — boot must not continue past it)
     */
    public static WorldSessionLock acquire(Path worldRoot) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        try {
            Files.createDirectories(worldRoot);
            Path file = worldRoot.resolve(LOCK_FILE);
            FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    channel.close();
                    throw secondWriter(worldRoot, null);
                }
                // The marker's content is diagnostics only — the OS lock is
                // the authority (a timestamp here would be the exact "lock
                // file presence" heuristic §31 rejects).
                channel.write(java.nio.ByteBuffer.wrap(
                        ("zamin-torch session lock, pid " + ProcessHandle.current().pid()
                                + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                channel.truncate(64);
                return new WorldSessionLock(file, channel, lock);
            } catch (OverlappingFileLockException overlapping) {
                channel.close();
                throw secondWriter(worldRoot, overlapping);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "World session lock could not be acquired for " + worldRoot
                            + " (I/O failure: " + e.getMessage() + ")", e);
        }
    }

    private static IllegalStateException secondWriter(Path worldRoot, Throwable cause) {
        IllegalStateException refusal = new IllegalStateException(
                "The world at " + worldRoot + " is already held by another live"
                        + " ZaminTorch session (session.lock is OS-locked). Stop the other"
                        + " server first — two authoritative writers on one world is the"
                        + " corruption the storage contract forbids (§31.6).");
        if (cause != null) {
            refusal.initCause(cause);
        }
        return refusal;
    }

    /** The marker file's path (diagnostics). */
    public Path file() {
        return file;
    }

    /** @return whether this handle still holds the OS lock. */
    public boolean held() {
        return lock.isValid();
    }

    /** Releases the OS lock and the channel. Idempotent. */
    @Override
    public void close() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // the channel close below still tears everything down
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // a closed channel releases its locks by contract
        }
    }
}
