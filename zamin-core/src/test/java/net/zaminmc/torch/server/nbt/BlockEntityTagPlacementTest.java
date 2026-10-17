package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave H2/H3 — the BlockEntityTag placement arm over the live engine
 * (BlockItem.setTileNbt lines 70-100, the 1:1 port): the held stack's raw
 * compound merged into the freshly-placed tile's rows — the chest family's
 * Items/Lock/CustomName, the spawner's cadence rows for an op, the
 * command-block family's op-gate refusal for a non-op (the placement stays
 * committed, the rows never apply), THE 1.8.8 QUIRK (the pristine copy
 * precedes the write — the no-change refusal is unreachable, the read
 * always runs), and the /give dataTag arm feeding the whole loop.
 */
class BlockEntityTagPlacementTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice; // opped
    PlayerSession bob;   // never opped
    final List<String> chatLog = new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bet" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice", new Position(0.5, 5, 0.5));
        await(() -> alice.state() == PlayerState.PLAYING, "Alice playing");
        bob = join("Bob", new Position(4.5, 5, 0.5));
        await(() -> bob.state() == PlayerState.PLAYING, "Bob playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
        // The system-chat listener (the give reply's path): the engine
        // delivers system lines through the chat event bus, the wire
        // bridge converts them — a test records them here.
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override public void onChatMessage(PlayerSession sender, String content) { }
            @Override public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient == alice || recipient == bob) {
                    chatLog.add(content);
                }
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // The helpers
    // ------------------------------------------------------------------

    private PlayerSession join(String name, Position at) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new RecordingLink(chatLog), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(() -> {
            session.markPlaying();
            session.applyMovement(at, Rotation.ZERO, true);
        });
        return session;
    }
    /** A parseable BlockEntityTag tree, built boxed (the model arm's form). */
    private static Map<String, Object> chestTag() {
        Map<String, Object> row0 = new LinkedHashMap<>();
        row0.put("Slot", (byte) 0);
        row0.put("id", "minecraft:diamond");
        row0.put("Count", (byte) 2);
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("Slot", (byte) 1);
        row1.put("id", "minecraft:emerald");
        row1.put("Count", (byte) 3);
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("Items", List.of(row0, row1));
        tag.put("Lock", "Secret");
        tag.put("CustomName", "Vault");
        return tag;
    }

    /** One held stack set into the hotbar's held slot (index 0). */
    private void hold(PlayerSession player, ItemStack stack) throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<ItemStack> seen =
                new java.util.concurrent.atomic.AtomicReference<>();
        tick(() -> {
            player.inventory().setSlot(0, stack);
            seen.set(player.inventory().held());
        });
        await(() -> seen.get() == stack, "the held stack landed");
    }

    /** The deterministic clicked/target pair beside the spawn columns. */
    private BlockPosition groundAt() {
        return new BlockPosition(2, 4, 2);
    }

    private BlockPosition targetAt() {
        return new BlockPosition(2, 5, 2);
    }

    private void layGround() throws InterruptedException {
        tick(() -> server.world().setBlock(groundAt(), BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(groundAt()).equals(BuiltinBlocks.STONE),
                "the ground stone laid");
    }

    /** Runs the body on the tick lane and waits it out. */
    private void tick(Runnable body) throws InterruptedException {
        AtomicBoolean done = new AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                body.run();
            } finally {
                done.set(true);
            }
        });
        await(done::get, "the tick task ran");
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        private final List<String> sink;
        RecordingLink(List<String> sink) { this.sink = sink; }
        @Override public boolean isActive() { return true; }
        @Override public void kick(String reason) { }
        @Override public void updateSpawnPosition(int x, int y, int z) { }
        @Override public void playSound(String name, double x, double y, double z,
                                        float volume, float pitch) { }
        @Override public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                             float offsetX, float offsetY, float offsetZ,
                                             float particleData, int count, int[] params) { }
        @Override public void sendChatComponent(String json, int position) {
            sink.add(position + ":" + json);
        }
    }

    // ------------------------------------------------------------------
    // The model arm (the unit rows)
    // ------------------------------------------------------------------

    @Test
    void theModelArmCarriesTheRawCompoundVerbatimAndDeeplyImmutable() {
        Map<String, Object> source = chestTag();
        source.put("Bonus", new int[]{1, 2, 3});
        ItemStack stack = ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow(),
                1).withBlockEntityTag(source);
        // The arm is a deep copy: mutating the source never reaches it.
        source.put("Lock", "Changed");
        ((int[]) source.get("Bonus"))[0] = 99;
        source.put("Items", List.of());
        assertEquals("Secret", stack.blockEntityTag().get("Lock"));
        assertEquals(1, ((int[]) stack.blockEntityTag().get("Bonus"))[0]);
        assertEquals(2, ((List<?>) stack.blockEntityTag().get("Items")).size());
        // The empty map clears (the absent convention).
        assertNull(stack.withBlockEntityTag(new LinkedHashMap<>()).blockEntityTag());
        assertNull(stack.withBlockEntityTag(null).blockEntityTag());
    }

    @Test
    void theWholeTagIdentityIncludesTheArm() {
        var chest = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();
        ItemStack bare = ItemStack.of(chest, 1);
        ItemStack tagged = ItemStack.of(chest, 1).withBlockEntityTag(chestTag());
        // Two differently-tagged stacks never merge (areItemStacksEqual's
        // whole-tag compare); identical trees do — including int[] by
        // VALUE (Java's array identity would refuse them).
        assertFalse(ItemStack.mergeable(bare, tagged));
        assertFalse(ItemStack.mergeable(tagged, bare));
        assertTrue(ItemStack.mergeable(tagged,
                ItemStack.of(chest, 1).withBlockEntityTag(chestTag())));
        Map<String, Object> withArray = new LinkedHashMap<>();
        withArray.put("Rows", new int[]{7, 8});
        Map<String, Object> sameArray = new LinkedHashMap<>();
        sameArray.put("Rows", new int[]{7, 8});
        assertTrue(ItemStack.mergeable(
                ItemStack.of(chest, 1).withBlockEntityTag(withArray),
                ItemStack.of(chest, 1).withBlockEntityTag(sameArray)));
        Map<String, Object> otherArray = new LinkedHashMap<>();
        otherArray.put("Rows", new int[]{7, 9});
        assertFalse(ItemStack.mergeable(
                ItemStack.of(chest, 1).withBlockEntityTag(withArray),
                ItemStack.of(chest, 1).withBlockEntityTag(otherArray)));
    }

    @Test
    void theNonCompoundRowNeverLandsOnTheModel() {
        // contains("BlockEntityTag", 10): a string-typed row is not the
        // compound — getCompound answers empty, the arm stays null.
        Map<String, Snbt.Tag> tag = new LinkedHashMap<>();
        NbtOps.putString(tag, "BlockEntityTag", "not a compound");
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:chest");
        NbtOps.putByte(rows, "Count", (byte) 1);
        NbtOps.putShort(rows, "Damage", (short) 0);
        NbtOps.putCompound(rows, "tag", tag);
        ItemStack read = ItemNbt.read(rows);
        assertNull(read.blockEntityTag());
        // The compound row lands, the boxed tree round-trips the rows.
        NbtOps.putCompound(tag, "BlockEntityTag", NbtOps.fromBoxed(chestTag()));
        read = ItemNbt.read(rows);
        assertEquals("Secret", read.blockEntityTag().get("Lock"));
        // And the row writer emits it back verbatim.
        Map<String, Snbt.Tag> written = new LinkedHashMap<>();
        ItemNbt.write(read, written);
        Map<String, Snbt.Tag> tagRows = NbtOps.getCompound(written, "tag");
        Map<String, Snbt.Tag> blockEntityTag = NbtOps.getCompound(tagRows, "BlockEntityTag");
        assertEquals("Secret", NbtOps.getString(blockEntityTag, "Lock"));
        assertEquals("Vault", NbtOps.getString(blockEntityTag, "CustomName"));
        List<Snbt.Tag> items = NbtOps.getList(blockEntityTag, "Items", Snbt.Tag.Type.COMPOUND);
        assertEquals(2, items.size());
        assertEquals(2, NbtOps.getByte(NbtOps.listCompound(items, 0), "Count"));
    }

    // ------------------------------------------------------------------
    // The placement consult (the live engine)
    // ------------------------------------------------------------------

    @Test
    void theChestPlacementAppliesTheStacksTag() throws Exception {
        layGround();
        var chestItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();
        hold(alice, ItemStack.of(chestItem, 1).withBlockEntityTag(chestTag()));
        AtomicInteger tables = new AtomicInteger();
        server.useItemOnBlock(alice, groundAt(), 1, Optional.empty(), id -> tables.incrementAndGet());
        BlockPosition target = targetAt();
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.CHEST),
                "the chest placed");
        var placed = server.chests().getOrCreate(target);
        assertEquals("Secret", placed.lockKey());
        assertEquals("Vault", placed.customName());
        ItemStack[] slots = placed.snapshotSlots();
        assertEquals("minecraft:diamond", slots[0].type().identifier().toString());
        assertEquals(2, slots[0].count());
        assertEquals("minecraft:emerald", slots[1].type().identifier().toString());
        assertEquals(3, slots[1].count());
        // The stack consumed, exactly the reference's own order (the consult
        // before the consume; both committed).
        await(() -> alice.inventory().held().isEmpty(), "the held stack consumed");
    }

    @Test
    void theSpawnerCadenceAppliesForAnOpAndRefusesANonOp() throws Exception {
        // The reference's setTileNbt is STATIC — the consult keys off the
        // BLOCK family at the position, never the held item's identity
        // (the engine has no mob_spawner item yet; the direct call walks
        // the same merge the placement site would).
        layGround();
        BlockPosition target = targetAt();
        tick(() -> server.world().setBlock(target, BuiltinBlocks.MOB_SPAWNER));
        await(() -> server.world().getBlock(target).identifier().value().equals("mob_spawner"),
                "the spawner block placed");
        var chestItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();
        Map<String, Object> cadence = new LinkedHashMap<>();
        cadence.put("EntityId", "Zombie");
        cadence.put("MinSpawnDelay", (short) 100);
        cadence.put("MaxSpawnDelay", (short) 200);
        cadence.put("SpawnCount", (short) 4);
        ItemStack stack = ItemStack.of(chestItem, 1).withBlockEntityTag(cadence);

        // The non-op consult first (Bob's level): the refusal skips the
        // merge, the spawner keeps the fresh cadence.
        AtomicBoolean refused = new AtomicBoolean();
        tick(() -> refused.set(!server.tileNbt().applyPlacementTag(
                server.world(), 0, target, stack, 0)));
        await(refused::get, "the non-op consult refused");
        var spawner = server.spawners().getOrCreate(0, target);
        assertTrue(spawner.minSpawnDelay() != 100,
                "the refused spawner kept its own cadence: " + spawner.minSpawnDelay());

        // The op consult: the cadence rows land.
        AtomicBoolean applied = new AtomicBoolean();
        tick(() -> applied.set(server.tileNbt().applyPlacementTag(
                server.world(), 0, target, stack, 2)));
        await(applied::get, "the op consult applied");
        assertEquals(100, spawner.minSpawnDelay());
        assertEquals(200, spawner.maxSpawnDelay());
        assertEquals(4, spawner.spawnCount());
    }

    @Test
    void theCommandBlockRefusesANonOpButAnOpApplies() throws Exception {
        layGround();
        var commandItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:command_block")).orElseThrow();
        Map<String, Object> commandTag = new LinkedHashMap<>();
        commandTag.put("Command", "say hi");
        commandTag.put("SuccessCount", 5);

        // Bob (never opped): the block still places — the refusal only
        // skips the NBT apply (the reference's own order: setTileNbt runs
        // after setBlockState already succeeded).
        hold(bob, ItemStack.of(commandItem, 1).withBlockEntityTag(commandTag));
        AtomicInteger tables = new AtomicInteger();
        server.useItemOnBlock(bob, groundAt(), 1, Optional.empty(), id -> tables.incrementAndGet());
        BlockPosition target = targetAt();
        await(() -> server.world().getBlock(target).identifier().value().equals("command_block"),
                "the command block placed for the non-op");
        var refused = server.commandBlocks().at(target);
        if (refused == null) {
            refused = server.commandBlocks().createAt(target, true);
        }
        assertEquals("", refused.command());
        assertEquals(0, refused.successCount());
        await(() -> bob.inventory().held().isEmpty(), "the non-op's stack consumed too");

        // Alice (op level 2): the same stack's rows land. The second
        // ground sits INSIDE the reach (the eye-to-center 4.5-squared
        // budget — the worklog's own REACH DISCOVERY: column 4 was out).
        BlockPosition second = new BlockPosition(2, 5, -2);
        tick(() -> server.world().setBlock(new BlockPosition(2, 4, -2), BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(new BlockPosition(2, 4, -2)).equals(BuiltinBlocks.STONE),
                "the second ground laid");
        hold(alice, ItemStack.of(commandItem, 1).withBlockEntityTag(commandTag));
        server.useItemOnBlock(alice, new BlockPosition(2, 4, -2), 1, Optional.empty(),
                id -> tables.incrementAndGet());
        await(() -> server.world().getBlock(second).identifier().value().equals("command_block"),
                "the op's command block placed");
        var applied = server.commandBlocks().at(second);
        assertEquals("say hi", applied.command());
        assertEquals(5, applied.successCount());
    }

    @Test
    void theNoChangeRefusalIsDeadCodeInThePort() throws Exception {
        // THE 1.8.8 QUIRK (BlockItem lines 83-84): the pristine copy
        // precedes the write, so x/y/z always land and the !equals is
        // always true — even a merge that changes NOTHING still re-reads
        // (and answers true). A chest whose fresh rows already carry the
        // stack's every row: the consult still returns true. The 1.9
        // semantics (copy after the write) would answer false here.
        layGround();
        BlockPosition target = targetAt();
        tick(() -> server.world().setBlock(target, BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.CHEST),
                "the chest placed bare");
        var chestItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();
        // A no-op tree: the fresh chest writes Lock:"" (ALWAYS, even for
        // NONE) and no CustomName; a tag carrying the same empty lock
        // merges nothing new.
        Map<String, Object> noChange = new LinkedHashMap<>();
        noChange.put("Lock", "");
        ItemStack stack = ItemStack.of(chestItem, 1).withBlockEntityTag(noChange);
        AtomicBoolean applied = new AtomicBoolean();
        tick(() -> applied.set(server.tileNbt().applyPlacementTag(
                server.world(), 0, target, stack, 2)));
        await(applied::get, "the consult answered");
        assertTrue(applied.get(), "the dead refusal never skips the read (1.8.8 verbatim)");
    }

    // ------------------------------------------------------------------
    // The /give dataTag arm (the whole loop)
    // ------------------------------------------------------------------

    @Test
    void theGiveDataTagFeedsTheArmThroughToThePlacedChest() throws Exception {
        // /give with a dataTag: the granted stack carries the arm (and the
        // display name), and placing THAT stack lands the lock on the
        // chest — the full loop the wave exists for. The unquoted values
        // ride the SNBT fall-to-string shape (the reference's own
        // NumberFormatException arm) — no quotes, no splitter ambiguity.
        server.chatService().submitChat(alice,
                "/give chest 1 0 {BlockEntityTag:{Lock:Keyed},display:{Name:Stash}}");
        await(() -> {
            ItemStack held = alice.inventory().held();
            return !held.isEmpty()
                    && "Stash".equals(held.displayName())
                    && held.blockEntityTag() != null
                    && "Keyed".equals(held.blockEntityTag().get("Lock"));
        }, "the granted stack carries the arm");
        layGround();
        AtomicInteger tables = new AtomicInteger();
        server.useItemOnBlock(alice, groundAt(), 1, Optional.empty(), id -> tables.incrementAndGet());
        BlockPosition target = targetAt();
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.CHEST),
                "the granted stack placed its chest");
        assertEquals("Keyed", server.chests().getOrCreate(target).lockKey());
        // A malformed dataTag is the command's own tag error.
        int replies = chatLog.size();
        server.chatService().submitChat(alice, "/give chest 1 0 {not a compound");
        await(() -> chatLog.size() > replies, "the malformed parse replied");
        String last = chatLog.get(chatLog.size() - 1);
        assertTrue(last.contains("Invalid dataTag"),
                "the tag error message: " + last);
    }
}
