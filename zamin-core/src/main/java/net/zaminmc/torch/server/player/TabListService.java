package net.zaminmc.torch.server.player;

import net.zaminmc.torch.server.net.ClientLink;

import java.util.List;
import java.util.UUID;

/**
 * The tab-list family (Slice 84 — the reference's PlayerInfoS2CPacket sends):
 * the engine-owned walks that announce joins, quits, game-mode changes, and
 * the periodic ping refresh on every player's tab list.
 *
 * <p>Stateless over the registry (the sessions themselves are the state); the
 * only owned counter is the ping cadence clock, the reference's own
 * {@code ++pingUpdateTime > 600} walk (PlayerManager.tick lines 504-508 — the
 * counter fires every 601 ticks, the historical off-by-one preserved).</p>
 *
 * <p>Wire shape (the reference's add/remove walks, PlayerManager lines
 * 261-273 + 298):</p>
 * <ul>
 *   <li>the joiner lands on every OTHER player's wire once (the ADD
 *       broadcast);</li>
 *   <li>the joiner's own wire carries their entry TWICE — the broadcast's
 *       self copy, then the per-player loop's final iteration (the joiner is
 *       already in the list when the loop runs) — the reference's own
 *       double-add quirk, ported verbatim;</li>
 *   <li>each ADD packet carries exactly one entry (the reference sends one
 *       packet per player), while the ping refresh is ONE packet carrying
 *       every entry.</li>
 * </ul>
 */
public final class TabListService {

    /** The reference's cadence threshold: fires on the 601st tick, then every 601. */
    private static final int PING_UPDATE_INTERVAL = 600;

    private int pingUpdateTime;

    /** The wire entry for a session (the offline profile: no properties, no display name). */
    private static ClientLink.TabListEntry entryOf(PlayerSession session) {
        return new ClientLink.TabListEntry(session.uuid(), session.name(),
                session.gamemode().legacyId(), session.ping());
    }

    /**
     * The join walk (PlayerManager.add lines 261-273): the ADD broadcast to
     * everyone else, then the joiner's own flush — the broadcast's self copy
     * first, then one packet per registered session (the joiner included,
     * their own entry a second time — the reference's wire shape).
     */
    public void joinAnnounce(PlayerRegistry players, PlayerSession joiner) {
        ClientLink.TabListEntry joinerEntry = entryOf(joiner);
        // (A) the broadcast: every other player's wire learns the joiner.
        for (PlayerSession other : players.all()) {
            if (other == joiner) {
                continue; // the joiner's own copy flushes below (their link
                // is mid-join here — the adapter calls this between the
                // pre-position packets and the teleport).
            }
            other.link().tabListAdd(List.of(joinerEntry));
        }
        // (B) the joiner's own wire: the broadcast's self copy, then the
        // per-player loop (one packet per player, the joiner last — the
        // reference's iteration over a list that already contains them).
        joiner.link().tabListAdd(List.of(joinerEntry));
        for (PlayerSession other : players.all()) {
            joiner.link().tabListAdd(List.of(entryOf(other)));
        }
    }

    /**
     * The quit walk (PlayerManager.remove line 298): the REMOVE broadcast to
     * every player who stays. One packet per recipient, one entry — the
     * leaver's uuid.
     */
    public void announceQuit(PlayerRegistry players, PlayerSession leaver) {
        UUID leaverId = leaver.uuid();
        for (PlayerSession remaining : players.all()) {
            if (remaining == leaver) {
                continue;
            }
            remaining.link().tabListRemove(List.of(leaverId));
        }
    }

    /**
     * The game-mode walk (ServerPlayerInteractionManager.setGameMode line
     * 44): the UPDATE_GAME_MODE broadcast to everyone INCLUDING the subject
     * (the reference's sendPacket has no self-skip).
     */
    public void broadcastGamemode(PlayerRegistry players, PlayerSession subject) {
        UUID subjectId = subject.uuid();
        int gamemodeId = subject.gamemode().legacyId();
        for (PlayerSession online : players.all()) {
            online.link().tabListUpdateGamemode(subjectId, gamemodeId);
        }
    }

    /**
     * The per-tick cadence (PlayerManager.tick lines 504-508): every 601
     * ticks, ONE batched UPDATE_PING packet — every session's uuid + smoothed
     * ping — to every player.
     */
    public void tick(PlayerRegistry players) {
        if (++pingUpdateTime > PING_UPDATE_INTERVAL) {
            pingUpdateTime = 0;
            List<ClientLink.TabListEntry> entries = players.all().stream()
                    .map(TabListService::entryOf)
                    .toList();
            for (PlayerSession online : players.all()) {
                online.link().tabListUpdatePing(entries);
            }
        }
    }
}
