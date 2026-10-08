# Manual 1.8.8 Client Validation Checklist

The engine already validates itself three ways: unit tests, integration tests
over a real socket, and mineflayer (the PrismarineJS community client
implementation) driving a live server as an independent protocol peer —
see `scripts/validate-1.8.8.js` (17/17 checks, `REAL_CLIENT_VALIDATION_PASSED`).
This checklist is the fourth, final layer: **you**, with the real
Minecraft 1.8.8 client. It exists because only a human with the actual
client can judge rendering, sounds and feel.

## Setup

1. Build and start a server:
   ```
   ./gradlew :zamin-launcher:runServer
   ```
   (or launch `zamin-launcher` with a `zamin.properties` — the default port
   is 25565). The server runs offline-mode, so no Mojang account is needed
   and the client connects directly.
2. Launch Minecraft **1.8.8** (the exact version — protocol 47) and add a
   server: `127.0.0.1` (or the host's IP). Multiplayer -> Direct Connect also
   works.
3. The world is a flat fixture: bedrock at y=0, dirt to y=3, grass at y=4,
   spawn at (0.5, 5, 0.5). You join in survival on easy difficulty.

## Join and world

- [ ] The client reaches the multiplayer screen and joins without errors
      (no "internal exception", no malformed-packet kick).
- [ ] The world **renders correctly**: grass surface, dirt below, bedrock at
      the bottom of holes — not floating chunks, not black/missing terrain.
      (This exercises the chunk payload: LE u16 packed block states, grouped
      light arrays — the layout mineflayer's parser verified.)
- [ ] The sky is bright (skylight is sent fully lit).
- [ ] Walking feels normal: no rubber-banding, no falling through the floor.
- [ ] F3 shows your position tracking your movement; the chat shows
      "Player joined".

## Survival loop

- [ ] Mining grass/dirt by hand takes a sensible moment and the block
      disappears with the dig animation; a dirt item pops out and follows you.
- [ ] Walking over the drop collects it (the inventory fills; the item
      entity disappears with the collect animation).
- [ ] Dropping with **Q** throws one item in the look direction; **Ctrl+Q**
      throws the whole stack. A dropped charcoal looks like charcoal (not
      coal) — see the variant section below.
- [ ] Placing dirt/cobblestone against a surface works; placing inside
      yourself is refused.
- [ ] Falls hurt (climb ~4+ blocks and jump down), hearts drop, and health
      regenerates while fed. Starvation never kills below 5 hearts (easy).
- [ ] Eating (hold right-click with food) consumes the item and restores
      hunger.
- [ ] Dying (fall far enough) shows the death screen with the combat event;
      Respawn puts you back at spawn with full stats and an empty inventory
      (the old items scattered where you died).

## Containers (the trio)

- [ ] `/give crafting_table 1` (press T, type the command) -> place it ->
      right-click opens the **3x3 crafting table**. Craft planks from a log,
      sticks from planks, a wooden pickaxe from the 3x3 grid.
- [ ] `/give furnace 1` -> place -> right-click opens the furnace. Put sand
      in the top, planks in the bottom: after ~10 s a **glass** item appears
      (watch the flame and arrow animate — Window Property sync).
- [ ] Put a **log** in the input of a fresh furnace: the output is
      **charcoal** — the item renders with charcoal's texture and tooltip.
- [ ] `/give chest 1` -> place -> right-click opens the **27-slot chest**.
      Shift-click moves stacks both ways; closing and reopening keeps the
      contents; breaking the chest spills everything (the chest item itself
      drops first, then the contents).
- [ ] All container interactions feel responsive (one-tick latency) and
      never desync: what you see is what the server holds.

## Item variants (metadata)

- [ ] `/give coal 5 1` gives **charcoal** (the tooltip says Charcoal; the
      texture is the charcoal variant). `/give coal 5` gives normal coal.
      The two stacks **do not merge** in the inventory.
- [ ] `/give sand 1 1` gives **red sand**. `/give sand` gives normal sand.
- [ ] Smelt a log: the charcoal output stacks with other charcoal but never
      with plain coal.

## Persistence

- [ ] Place a chest, put items in, type `stop` in the server console (or
      Ctrl+C). Restart the server, rejoin: the chest contents, your
      inventory, health/food and position are exactly where you left them.
- [ ] A furnace mid-smelt keeps its fuel/cook state across the restart.

## Falling blocks and block updates

- [ ] Place a sand column two high, dig the block under it: both sands turn
      into falling entities (you see them fall), land one step down, and the
      world keeps no ghosts. Red sand (the `/give sand 1 1` variant) falls the
      same way.
- [ ] Gravel does it too; digging gravel drops flint about one time in ten.
- [ ] Break the block under a floor torch: the torch pops as a pickup-able
      item.
- [ ] Cover a grass block with stone: within seconds it decays to dirt. Clear
      a patch to dirt near living grass and wait: grass creeps back.
- [ ] While a fall is in progress, type `stop`: after a restart the sand is
      where it was falling to (blocks persist, mid-air entities do not).

## Known limitations (expected, not bugs)

- No mobs, no day/night lighting simulation (skylight is constant), no
  hunger-driven sprint restrictions beyond the food bar, no double chests,
  no sneaking semantics, no creative inventory UI management beyond direct
  placement, no sound effects of mining/placing, and single-player-scale
  world size. These are later slices per the engineering plan.

## Reporting a failure

Note the checklist line, what you did, what you saw, and the server console
output (the last ~20 lines). If a packet-level suspicion arises,
`scripts/validate-1.8.8.js` reproduces most flows against a live server
without the GUI and prints per-check PASS/FAIL lines.
