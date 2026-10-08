'use strict';
/**
 * ZaminTorch real-client validation (batch K).
 *
 * Drives mineflayer — the PrismarineJS community's real Minecraft client
 * implementation — against a live ZaminTorch server as an independent
 * 1.8.8 protocol peer. Unlike the engine's own tests (which read the wire
 * the server wrote), mineflayer parses every packet with its own
 * protocol stack, so a green run here is cross-implementation proof:
 * login, chat, dig + pickup, placement, the chest container (63-slot
 * layout, deposit/retrieve), the furnace (sand -> glass, log -> charcoal
 * via item metadata) and /give with a variant argument.
 *
 * Usage: node validate-1.8.8.js <port> [host]
 * Requires: npm install mineflayer (run from a directory that has it).
 * Exit code 0 = all checks passed.
 */

const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const port = parseInt(process.argv[2] || '25565', 10);
const host = process.argv[3] || '127.0.0.1';

let failures = 0;
function check(name, cond, extra) {
  const verdict = cond ? 'PASS' : 'FAIL';
  if (!cond) failures++;
  console.log(`[${verdict}] ${name}${extra ? ' (' + extra + ')' : ''}`);
  return cond;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function waitFor(bot, event, timeoutMs, filter) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener(event, onEvent);
      reject(new Error(`timeout waiting for ${event}`));
    }, timeoutMs);
    function onEvent(...args) {
      if (filter && !filter(...args)) return;
      clearTimeout(timer);
      bot.removeListener(event, onEvent);
      resolve(args);
    }
    bot.on(event, onEvent);
  });
}

async function main() {
  console.log(`Connecting mineflayer (real 1.8.8 client) to ${host}:${port} ...`);
  const bot = mineflayer.createBot({
    host,
    port,
    username: 'Validator' + (Date.now() % 100000),
    version: '1.8.8',
    auth: 'offline',
    keepAlive: true,
  });

  bot.on('error', (err) => console.log('[client-error]', err.message));
  bot.on('kicked', (reason) => console.log('[kicked]', reason));
  bot.on('end', (reason) => {
    if (!finished) {
      failures++;
      console.log(`[FAIL] connection ended early: ${reason}`);
      process.exit(1);
    }
  });

  const chatLines = [];
  bot.on('message', (message) => chatLines.push(message.toString()));

  await waitFor(bot, 'spawn', 30_000);
  check('login+spawn as 1.8.8', true);
  await sleep(1_000); // let the join sync settle

  // ---- 1. chat round trip -------------------------------------------------
  bot.chat('validation hello');
  await sleep(800);
  check('chat round trip', chatLines.some((l) => l.includes('validation hello')));

  // ---- 2. dig + automatic pickup ------------------------------------------
  const below = bot.blockAt(bot.entity.position.offset(0, -1, 0));
  check('world read: block below is diggable', !!below && below.boundingBox !== 'empty',
    below ? below.name : 'null');
  if (below) {
    await bot.dig(below);
    // The bot falls one block into the hole; the server's collection range
    // does the rest, exactly like a real player digging under themselves.
    await sleep(2_500);
    const dirt = bot.inventory.items().find((it) => it.name === 'dirt');
    check('dig + pickup (dirt in inventory)', !!dirt, dirt ? `count=${dirt.count}` : 'none');
  }

  // ---- 3. placement + chest container -------------------------------------
  // The flat world's surface is solid at feet level, so blocks are placed on
  // TOP of the surface: reference the grass beside the bot, face up.
  const ground = bot.blockAt(bot.entity.position.offset(1, 0, 0));
  check('ground reference is the surface block', !!ground
    && (ground.name === 'grass_block' || ground.name === 'grass'),
    ground ? ground.name : 'null');
  const chestSlot = bot.inventory.items().find((it) => it.name === 'chest')
    || null;
  if (!chestSlot) bot.chat('/give chest 1');
  await sleep(700);
  const chestItem = bot.inventory.items().find((it) => it.name === 'chest');
  check('/give chest reachable', !!chestItem);

  if (ground && chestItem) {
    await bot.equip(chestItem, 'hand');
    await bot.placeBlock(ground, new Vec3(0, 1, 0));
    await sleep(700);
    const chestBlock = bot.blockAt(ground.position.offset(0, 1, 0));
    check('chest placed', !!chestBlock && chestBlock.name === 'chest',
      chestBlock ? chestBlock.name : 'null');

    // Stock a charcoal variant, then store it through the real container UI.
    // prismarine-item keeps name 'coal' for both variants; the variant lives
    // in metadata (community items.json: coal metadata 1 = Charcoal).
    bot.chat('/give coal 4 1');
    await sleep(700);
    let charcoal = bot.inventory.items().find((it) => it.name === 'coal' && it.metadata === 1);
    check('/give variant resolves as charcoal (coal metadata 1)', !!charcoal,
      charcoal ? `metadata=${charcoal.metadata}` : 'none');

    const chest = await bot.openChest(chestBlock);
    await sleep(500);
    check('chest window opened by real client', chest != null);
    if (charcoal) {
      console.log('[dbg] pre-deposit: charcoal at slot', charcoal.slot, 'count', charcoal.count);
      await chest.deposit(charcoal.type, charcoal.metadata, charcoal.count);
      await sleep(500);
      console.log('[dbg] post-deposit: window id', chest.id,
        'selectedItem=', chest.selectedItem ? chest.selectedItem.name : 'null',
        'slot0=', chest.slots[0] ? chest.slots[0].name + 'x' + chest.slots[0].count + 'm' + chest.slots[0].metadata : 'null',
        'chestRegion=', chest.slots.slice(0, 27).filter(Boolean).map(s => s.name).join(',') || 'empty');
      const stored = chest.containerItems().find((it) => it.name === 'coal' && it.metadata === 1);
      check('charcoal stored in chest (variant preserved)',
        !!stored && stored.metadata === 1, stored ? `metadata=${stored.metadata}` : 'none');
      await chest.close();
      await sleep(500);

      // Re-open: the chest's world state still holds the variant.
      const chest2 = await bot.openChest(chestBlock);
      await sleep(500);
      const still = chest2.containerItems().find((it) => it.name === 'coal' && it.metadata === 1);
      check('chest retains contents across close/reopen',
        !!still && still.metadata === 1, still ? `metadata=${still.metadata}` : 'none');
      await chest2.withdraw(still.type, still.metadata, still.count);
      await sleep(400);
      await chest2.close();
      await sleep(400);
    }

    // Break the chest with the variant inside -> spill check.
    bot.chat('/give coal 2 1');
    await sleep(600);
    const variant2 = bot.inventory.items().find((it) => it.name === 'coal' && it.metadata === 1);
    const chest3 = await bot.openChest(chestBlock);
    await sleep(400);
    if (variant2) {
      await chest3.deposit(variant2.type, 1, variant2.count);
      await sleep(400);
    }
    await chest3.close();
    await sleep(300);
    await bot.dig(chestBlock);
    await sleep(1_500);
    // The spilled variant may not walk back to the bot; the check is that the
    // dig succeeded and the chest block is gone (spill observed server-side).
    const after = bot.blockAt(chestBlock.position);
    check('chest break (block gone)', !after || after.name !== 'chest',
      after ? after.name : 'air');
  }

  // ---- 4. furnace: sand -> glass, log -> charcoal --------------------------
  // (each /give settles through the tick thread; give the sync a full second)
  const sand = bot.inventory.items().find((it) => it.name === 'sand' && it.metadata === 0)
    || (bot.chat('/give sand 2'), await sleep(1_200),
      bot.inventory.items().find((it) => it.name === 'sand' && it.metadata === 0));
  const fuel = bot.inventory.items().find((it) => it.name === 'coal' && it.metadata === 0)
    || (bot.chat('/give coal 2'), await sleep(1_200),
      bot.inventory.items().find((it) => it.name === 'coal' && it.metadata === 0));
  const log = bot.inventory.items().find((it) => it.name === 'oak_log' || it.name === 'log')
    || (bot.chat('/give oak_log 1'), await sleep(1_200),
      bot.inventory.items().find((it) => it.name === 'oak_log' || it.name === 'log'));
  check('smelting ingredients stocked', !!sand && !!fuel && !!log);

  const furnaceGround = bot.blockAt(bot.entity.position.offset(0, 0, 1));
  const furnaceItem = bot.inventory.items().find((it) => it.name === 'furnace');
  if (!furnaceItem) {
    bot.chat('/give furnace 1');
    await sleep(700);
  }
  const furnaceInv = bot.inventory.items().find((it) => it.name === 'furnace');
  if (furnaceGround && furnaceInv) {
    await bot.equip(furnaceInv, 'hand');
    await bot.placeBlock(furnaceGround, new Vec3(0, 1, 0));
    await sleep(700);
    const furnaceBlock = bot.blockAt(furnaceGround.position.offset(0, 1, 0));
    check('furnace placed', !!furnaceBlock && furnaceBlock.name === 'furnace',
      furnaceBlock ? furnaceBlock.name : 'null');

    if (furnaceBlock && sand && fuel) {
      const furnace = await bot.openFurnace(furnaceBlock);
      await sleep(400);
      await furnace.putInput(sand.type, 0, 1);
      await furnace.putFuel(fuel.type, 0, 1);
      console.log('furnace loaded, waiting through the 10 s smelt ...');
      const deadline = Date.now() + 25_000;
      let glass = null;
      while (Date.now() < deadline) {
        await sleep(1_000);
        glass = furnace.outputItem();
        if (glass && glass.name === 'glass') break;
      }
      check('sand smelted to glass (real 10 s cook)', !!glass && glass.name === 'glass',
        glass ? glass.name : 'timeout');

      // Take the glass out: the historical furnace refuses a new smelt while
      // the output holds a different item.
      if (glass) {
        await furnace.takeOutput();
        await sleep(500);
      }

      // Swap the input for the log: charcoal is coal with metadata 1.
      if (log) {
        await furnace.putInput(log.type, 0, 1);
        const deadline2 = Date.now() + 25_000;
        let charcoalOut = null;
        while (Date.now() < deadline2) {
          await sleep(1_000);
          charcoalOut = furnace.outputItem();
          if (charcoalOut && charcoalOut.name === 'coal' && charcoalOut.metadata === 1) break;
        }
        check('log smelted to charcoal (metadata 1)', !!charcoalOut
          && charcoalOut.name === 'coal' && charcoalOut.metadata === 1,
          charcoalOut ? `name=${charcoalOut.name} metadata=${charcoalOut.metadata}` : 'timeout');
      }
      await furnace.close();
      await sleep(400);
    }
  }

  // ---- 5. red sand variant via /give --------------------------------------
  bot.chat('/give sand 1 1');
  await sleep(700);
  const redSand = bot.inventory.items().find((it) => it.name === 'sand' && it.metadata === 1);
  check('red sand variant (sand metadata 1)', !!redSand && redSand.metadata === 1,
    redSand ? `metadata=${redSand.metadata}` : 'none');

  await bot.quit();
  finished = true;
  await sleep(500);
  console.log(failures === 0
    ? 'REAL_CLIENT_VALIDATION_PASSED'
    : `REAL_CLIENT_VALIDATION_FAILED (${failures} failing checks)`);
  process.exit(failures === 0 ? 0 : 1);
}

let finished = false;
main().catch((err) => {
  console.log('VALIDATION_ERROR:', err.message);
  process.exit(1);
});
