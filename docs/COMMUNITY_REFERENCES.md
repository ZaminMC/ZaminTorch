# Community references — what we study, what we own

The standing rule (from the project brief): **before building anything, check
whether the community has already solved it; study their solution, then write
our own implementation that fits ZaminTorch.** We never take a runtime
dependency on these projects — ZaminTorch is a self-contained engine, not a
Minestom or Bukkit platform — but their *designs* are proven maps of the same
territory we are exploring, and reinventing a map when a good one exists is
wasted effort.

When a repo below informs a slice, credit it in the slice's engineering-plan
entry. Implementations must be ours: our abstractions, our threading model,
our protocol layer — only the *ideas* are borrowed.

## The reference list

| Repository | What it is | What we take from it |
|---|---|---|
| [MinestomPvP](https://github.com/TogAr2/MinestomPvP) | Modern PvP mechanics on Minestom | **Adopted (slice 10u)**: the shape of melee combat — server-authoritative reach verdicts, the 10-tick hurt invulnerability window with the out-damage rule, knockback as a velocity packet to the victim. Our implementation lives in `EngineServer.attackPlayer`; 1.8 has no attack cooldown, and the wire packets are protocol 47's. |
| [BlueDragonMC/Server](https://github.com/BlueDragonMC/Server) | A from-scratch Minecraft server (1.8 era) | The closest community relative of ZaminTorch. Reference for 1.8 wire semantics we have not touched yet (signs, scoreboard, weather) and for pacing decisions of a small-team vanilla server. |
| [Revxrsal/Lamp](https://github.com/Revxrsal/Lamp), [MeveraStudios/Imperat](https://github.com/MeveraStudios/Imperat) | Command frameworks | When the console/chat command surface grows past a dozen commands, adopt the *annotation-free, dispatcher + resolver* pattern concept: commands as registered handlers with typed argument resolvers. Our `CommandService` grows into that; no library. |
| [Shynixn/MCCoroutine](https://github.com/Shynixn/MCCoroutine) | Coroutine bridge for plugin flows | Concepts only if we ever add scripting; the engine core stays plain Java 21 with the tick-thread model (§447). |
| [Combimagnetron/Sunscreen](https://github.com/Combimagnetron/Sunscreen), [Brikster/glyphs](https://github.com/Brikster/glyphs) | Cosmetic/viewport tricks | Deferred: cosmetic layers are not on the roadmap while survival semantics are unfinished. |
| [unnamed/hephaestus-engine](https://github.com/unnamed/hephaestus-engine), [AtlasEngineCa/WorldSeedEntityEngine](https://github.com/AtlasEngineCa/WorldSeedEntityEngine) | Entity model/animation engines | Reference when mobs gain real models/animations (pose metadata, equipment rendering). |
| [hollow-cube/polar](https://github.com/hollow-cube/polar), [hollow-cube/schem](https://github.com/hollow-cube/schem), [cody-quinn/SlimeLoader](https://github.com/cody-quinn/SlimeLoader) | World/schematic formats | Reference for the Anvil-import milestone: format readers are concept-portable; the on-disk ZMD/ZPD/ZCD formats stay ours. |
| [AtlasEngineCa/ParticleEmitter](https://github.com/AtlasEngineCa/ParticleEmitter), [TogAr2/MinestomParticles](https://github.com/TogAr2/MinestomParticles) | Particle systems | **Adopted in shape (slice 10v)**: the engine-side semantic emitter / version-bound translator split — our `FxManager` emits identifiers, the 1.8 adapter writes 0x2B with the community particles.json ids. |
| [4drian3d/SignedVelocity](https://github.com/4drian3d/SignedVelocity) | Signed chat result sync | Modern-chat concept; protocol 47 predates signed chat, so nothing to adopt now. |
| [mworzala/mc_debug_renderer](https://github.com/mworzala/mc_debug_renderer) | Debug visualization | A great idea for our own debugging (rendering pathfinding/light volumes); revisit when the client-side hooking approach fits. |
| [Mangolise/mango-anti-cheat](https://github.com/Mangolise/mango-anti-cheat) | Anti-cheat | Reference for the movement-validity hardening slice (our `isSaneMovement` grows into a real checker). |
| [TogAr2/MinestomFluids](https://github.com/TogAr2/MinestomFluids) | Fluid simulation | Reference for the fluids milestone (flow scheduling, source/spreading rules on our block-update queue). |
| [AtlasEngineCa/AtlasProjectiles](https://github.com/AtlasEngineCa/AtlasProjectiles) | Projectiles | **Adopted (slice 10v)**: the projectile-manager shape — one simulation-confined owner with launch/physics/removal events fanned to the wire — and the gravity-per-tick thrown-entity loop. Our physics constants, collision sampling and damage rules live in `ProjectileManager`. |
| [GoldenStack/window](https://github.com/GoldenStack/window), [GoldenStack/trove](https://github.com/GoldenStack/trove), [emortalmc/NBStom](https://github.com/emortalmc/NBStom) | Container/ADT/NBT utilities | `window`: container-layout ergonomics ideas for the remaining GUIs (enchanting, brewing). `trove`/`NBStom`: our SlotNbt stays internal; their readers are references for edge cases. |
| [LooFifteen/simple-voice-chat-minestom](https://github.com/LooFifteen/simple-voice-chat-minestom) | Voice chat | Out of scope for 1.8.8. |
| [oglassdev/KotStom](https://github.com/oglassdev/KotStom), [TropicalShadow/minestom-utils](https://github.com/TropicalShadow/minestom-utils), [GhostRider584/axiom-minestom](https://github.com/GhostRider584/axiom-minestom) | Utility layers | Generic utility patterns; nothing concrete until the corresponding features exist. |
| [aprilthepink/MinestomBasicLight](https://github.com/aprilthepink/MinestomBasicLight) | Light engine | Our light engine (§475/§476) already exceeds it; keep as a cross-check for propagation edge cases. |
| [kiip1/MineScreen](https://github.com/kiip1/MineScreen) | Screen rendering | Out of scope. |
| [Kanelucky/MobMind](https://github.com/Kanelucky/MobMind) | Mob AI | Reference for the mob-AI expansion (pathing goals, aggro tables). |
| [smoldermc/mirage](https://github.com/smoldermc/mirage), [everbuild-org/blocks-and-stuff](https://github.com/everbuild-org/blocks-and-stuff), [everbuild-org/minecraft-heads-minestom](https://github.com/everbuild-org/minecraft-heads-minestom) | Misc / cosmetic | Cosmetic layers, deferred. |

## Policy summary

1. **Search first** (the standing rule): every new subsystem opens with a
   survey of the list above plus a GitHub search for prior art.
2. **Ideas, not dependencies**: no external runtime libraries in the engine
   modules; the only third-party runtime is Netty (transport).
3. **Credit in the plan**: each engineering-plan slice names the references
   that shaped it, so provenance survives into the docs.
4. **Licensing**: code we *port conceptually* is reimplemented from the
   protocol/behavior spec; nothing is copied wholesale from MIT/Apache
   projects into the engine sources.
