#!/usr/bin/env python3
"""Categorize Folia patch inventory into subsystem buckets + emit markdown tables."""
import json
import re
from collections import defaultdict

INV = "/home/z/my-project/scripts/folia_inventory.json"

CATEGORIES = [
    ("Regionizer / region structure", [
        r"ThreadedRegionizer", r"RegionizedData", r"RegionizedWorldData",
        r"RegionizedServer", r"TickRegions", r"Regionized" ,
    ]),
    ("Tick scheduling", [
        r"TickRegionScheduler", r"TickThread", r"EntityScheduler",
        r"FoliaRegionScheduler", r"RegionizedTaskQueue", r"ScheduledTask",
        r"GlobalRegionScheduler", r"AsyncScheduler", r"RegionScheduler",
        r"task/", r"scheduler",
    ]),
    ("Chunk system / world state", [
        r"ChunkMap", r"ServerChunkCache", r"ChunkHolder", r"NewChunkHolder",
        r"ChunkTaskScheduler", r"ChunkGenerator", r"ChunkSerializer",
        r"ChunkSystem", r"RegionizedPlayerChunkLoader", r"PlayerChunkLoader",
        r"chunk/", r"ChunkPyramid", r"ChunkStatus", r"ServerLevel$",
        r"generation/", r"lighting/", r"LightEngine", r"LevelLightEngine",
        r"PoasrChunk", r"ChunkAccess", r"LevelChunk", r"ChunkStorage",
        r"IOWorker", r"EntityPersistentStorage", r"SectionStorage",
        r"ChunkHolderManager", r"worldgen/",
    ]),
    ("Block updates / redstone / fluids", [
        r"RedStoneWireBlock", r"RedstoneWireTurbo", r"RedstoneTorchBlock",
        r"RedStoneWireBlock", r"DetectorRailBlock", r"PoweredRailBlock",
        r"TripWireBlock", r"TripWireHookBlock", r"CollectorNeighborUpdater",
        r"NeighborUpdater", r"PistonBaseBlock", r"PistonStructureResolver",
        r"HopperBlock", r"HopperBlockEntity", r"Level$", r"ServerLevel$",
        r"BlockState", r"fluid/", r"LiquidBlock", r"FlowingFluid",
        r"FireBlock", r"SaplingBlock", r"BlockBehaviour", r"Blocks",
        r"ObserverBlock", r"ComparatorBlock", r"RepeaterBlock", r"LecternBlock",
        r"BellBlock", r"SculkSensorBlock", r"ChestBlock", r"DoorBlock",
        r"TrapDoorBlock", r"FenceGateBlock", r"NoteBlock", r"PressurePlate",
        r"WeightedPressurePlate", r"RailState", r"BaseRailBlock",
    ]),
    ("Entity lifecycle / physics / movement", [
        r"Entity", r"LivingEntity", r"Player", r"ServerPlayer", r"Mob",
        r"entity/", r"VehicleEntity", r"AbstractHorse", r"Boat", r"Minecart",
        r"passenger", r"Teleport", r"teleport", r"Portal", r"Projectile",
        r"playerlist", r"PlayerList", r"ServerGamePacketListener",
        r"ServerCommonPacketListener", r"tracking", r"ChunkMap$TrackedEntity",
        r"boss/BossEvent", r"collision", r"PartitionedSection",
        r"EntityGetter", r"EntitySection", r"EntitySectionStorage",
        r"PersistentEntitySectionManager", r"LevelGetter", r"Level",
    ]),
    ("Global state / misc server", [
        r"MinecraftServer", r"ServerFunctionManager", r"CommandSourceStack",
        r"commands", r"scoreboard", r"Scoreboard", r"BossBar", r"boss",
        r"Weather", r"GameRules", r"ServerTickRateManager", r"TickRateManager",
        r"ticket", r"Ticket", r"DistanceManager", r"TickingTracker",
        r"SendingChunk", r"Spawn", r"spawning", r"NaturalSpawner",
        r"raid", r"Raid", r"WandererTrades", r"village", r"Village",
        r"SleepStatus", r"MapItemSavedData", r"worldMap", r"DerivedLevelData",
        r"LevelData", r"LevelStorageSource", r"PlayerDataStorage",
        r"CommandFunction", r"Timer", r"ProtocolPacket", r"Config",
    ]),
    ("Observability / profiler / watchdog", [
        r"Profiler", r"profiler", r"Watchdog", r"watchdog", r"TickTime",
        r"tps", r"TPS", r"RegionStats", r"CoarseTracker", r"SpikeFormatter",
        r"ChunkSystem", r"throughput", r"RegionizedProfiler",
    ]),
    ("Packet / network / protocol", [
        r"packet", r"Packet", r"PacketSendListener", r"ClientboundBundle",
        r"network", r"connection", r"Connection", r"Bundle",
        r"ServerCommonPacketListenerImpl", r"ServerConfigurationPacketListener",
        r"ServerLoginPacketListener", r"ServerGamePacketListener",
    ]),
    ("Bukkit/Paper API compatibility", [
        r"org/bukkit", r"craftbukkit", r"Craft", r"Bukkit", r"CraftServer",
        r"CraftWorld", r"CraftEntity", r"CraftPlayer", r"CachedRegistry",
        r"Registry", r"Main", r"ServerBuildInfo", r"WatchdogThread",
    ]),
]

def classify(path):
    for cat, patterns in CATEGORIES:
        for p in patterns:
            if re.search(p, path):
                return cat
    return "Other / misc"

def main():
    with open(INV) as f:
        inv = json.load(f)

    # Aggregate per side: base patch only for the main table; follow-ups separately
    rows = []
    for key, data in inv.items():
        side, patch = key.split("/", 1)
        for fe in data["files"]:
            rows.append({
                "side": side,
                "patch": patch,
                "path": fe["path"],
                "hunks": fe["hunks"],
                "added": fe["added"],
                "deleted": fe["deleted"],
                "category": classify(fe["path"]),
            })

    # Category counts for base patches only
    print("=== Base-patch category counts (minecraft + paper-server) ===")
    counts = defaultdict(int)
    for r in rows:
        if r["patch"] == "0001-Region-Threading-Base.patch":
            counts[r["category"]] += 1
    for c, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"{c}: {n}")

    # Output full table markdown (traceable, one row per path per patch)
    out_lines = []
    for side in ("minecraft", "paper-server", "paper-api"):
        out_lines.append(f"\n### {side}\n")
        out_lines.append("| Patch | Java source path | Hunks | +churn | -churn | Category |")
        out_lines.append("|---|---|---|---|---|---|")
        for r in rows:
            if r["side"] != side:
                continue
            out_lines.append(
                f"| {r['patch'].replace('.patch','')} | `{r['path']}` | {r['hunks']} | {r['added']} | {r['deleted']} | {r['category']} |")

    with open("/home/z/my-project/scripts/folia_inventory_table.md", "w") as f:
        f.write("\n".join(out_lines))
    print(f"\nWrote folia_inventory_table.md ({len(rows)} rows)")

if __name__ == "__main__":
    main()
