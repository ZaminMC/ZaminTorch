package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The horse's spawn-time attribute rolls (HorseBaseEntity.initialize lines
 * 1278-1284, through getChildHealthBonus line 1386 + getMovementSpeedBonus
 * line 1390): the MAX_HEALTH band {@code 15 + nextInt(8) + nextInt(9)}
 * (line 1278), the MOVEMENT_SPEED roll (line 1281 — {@code (0.45 + 3x
 * nextDouble()*0.3) * 0.25} on the reference's 0.225 registered base,
 * landed on the engine's own base by the ratio) with the donkey/mule flat
 * (line 1283) read against the LIVE subtype, the JUMP_STRENGTH roll
 * (Wave PE-30 — initialize lines 1298-1303 over getChildJumpStrength
 * Bonus line 1389: {@code 0.4 + 3x nextDouble()*0.2} on the 0.7
 * registered base, the donkey/mule 0.5 flat), the current-health law
 * (Wave PE-30's repair — initialize line 1306's own tail re-fill: the
 * spawn sets the CURRENT health to the rolled ceiling, the pre-fix
 * engine's no-refill reading retired), and the Attributes NBT row's
 * precedence.
 */
class HorseSpawnAttributeRollTest {

    private static MobEntity.WorldQuery flatWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 4;

    private MobManager manager(long seed) {
        return new MobManager(flatWorld(), new Random(seed),
                new Loot(), 1_100_000, SURFACE);
    }

    private static final class Loot implements MobManager.LootSink {
        @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack, int dimension) {
        }
    }

    private static MobEntity horseAt(MobManager manager, double x, double z) {
        return manager.spawnAt(MobType.HORSE, new Position(x, 4.0, z));
    }

    @Test
    void theHealthRollCarriesTheReferenceBand() {
        // getChildHealthBonus line 1386: 15 + nextInt(8) + nextInt(9) — the
        // band [15, 30], mean 22.5 (15 + 3.5 + 4.0), both extremes live
        // (each end of the band is 1/72 per roll — ~139 per 10k).
        MobEntity body = new MobEntity(1, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        Random dice = new Random(20261021L);
        int sawFloor = 0;
        int sawCeiling = 0;
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            MobManager.rollHorseSpawnAttributes(body, dice);
            float ceiling = body.maxHealth();
            assertTrue(ceiling >= 15.0f && ceiling <= 30.0f,
                    "the ceiling stays in the band: " + ceiling);
            if (ceiling == 15.0f) {
                sawFloor++;
            }
            if (ceiling == 30.0f) {
                sawCeiling++;
            }
            sum += ceiling;
        }
        assertTrue(sawFloor > 50, "the band's floor is live: " + sawFloor);
        assertTrue(sawCeiling > 50, "the band's ceiling is live: " + sawCeiling);
        assertEquals(22.5, sum / 10_000, 0.15, "the band's mean (15 + 3.5 + 4.0)");
    }

    @Test
    void theSpeedRollCarriesTheReferenceBandOnTheEngineBase() {
        // getMovementSpeedBonus line 1390: (0.45 + 3x nextDouble()*0.3)*0.25
        // — the band [0.1125, 0.3375] on the reference's 0.225 registered
        // base, mean exactly the base. The consult lands it on the engine's
        // own base (the MobType row's 0.045) by the ratio: [0.0225, 0.0675],
        // mean 0.045.
        MobEntity body = new MobEntity(2, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        Random dice = new Random(20261022L);
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            MobManager.rollHorseSpawnAttributes(body, dice);
            double speed = body.walkSpeed();
            assertTrue(speed >= 0.0225 - 1e-12 && speed <= 0.0675 + 1e-12,
                    "the speed stays in the band: " + speed);
            min = Math.min(min, speed);
            max = Math.max(max, speed);
            sum += speed;
        }
        assertEquals(0.045, sum / 10_000, 0.0015, "the band's mean is the engine base");
        assertTrue(min < 0.03, "the band's low end is live: " + min);
        assertTrue(max > 0.06, "the band's high end is live: " + max);
    }

    @Test
    void theDonkeyAndTheMuleAnswerTheFlat() {
        // initialize line 1283 (the else arm of the i == 0 speed branch):
        // 0.175 on the reference's 0.225 base — 0.035 on the engine's 0.045.
        MobEntity body = new MobEntity(3, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        MobManager.rollHorseSpawnAttributes(body, new Random(20261023L));
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        assertEquals(0.035, body.walkSpeed(), 1e-9, "the donkey's flat");
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_MULE);
        assertEquals(0.035, body.walkSpeed(), 1e-9, "the mule's flat");
        // The roll is retained, not discarded: the horse branch answers the
        // band again the moment the subtype returns.
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        double speed = body.walkSpeed();
        assertTrue(speed >= 0.0225 - 1e-12 && speed <= 0.0675 + 1e-12,
                "the retained roll answers the horse band: " + speed);
    }

    @Test
    void theSpawnedHorseCarriesTheRolledAttributes() {
        // The spawn-site landing (spawnAt's horse arm — every construction
        // path rides it): the ceiling and the speed both carry their bands,
        // and the CURRENT health reads the ROLLED CEILING (Wave PE-30's
        // repair — initialize line 1306's own tail re-fill,
        // {@code setHealth(getMaxHealth())}: a freshly spawned horse
        // stands at its own full rolled health, exactly as the reference
        // spawns it — the pre-fix engine left the ctor's kind-row value
        // standing above or below the ceiling).
        MobManager manager = manager(20261024L);
        int above = 0;
        int below = 0;
        int donkeys = 0;
        for (int i = 0; i < 300; i++) {
            MobEntity horse = horseAt(manager, 0.5, 0.5);
            float ceiling = horse.maxHealth();
            double speed = horse.walkSpeed();
            assertTrue(ceiling >= 15.0f && ceiling <= 30.0f,
                    "the spawned ceiling: " + ceiling);
            assertEquals(ceiling, horse.health(), 1e-6f,
                    "the current health reads the rolled ceiling (initialize line 1306)");
            if (horse.horseSubtype() == MobEntity.HORSE_SUBTYPE_DONKEY) {
                // The ctor's 1-in-10 (initialize line 1270) landed a donkey:
                // the flat (line 1283) — itself inside the horse band, but
                // pinned exactly.
                assertEquals(0.035, speed, 1e-9, "the spawned donkey's flat");
                donkeys++;
            } else {
                assertTrue(speed >= 0.0225 - 1e-12 && speed <= 0.0675 + 1e-12,
                        "the spawned horse's speed: " + speed);
            }
            if (ceiling > 22.0f) {
                above++;
            } else if (ceiling < 22.0f) {
                below++;
            }
            // The heal clamps into the rolled ceiling (heal's own ceiling
            // read is maxHealth()).
            horse.heal(1000.0f);
            assertEquals(horse.maxHealth(), horse.health(), 1e-6f,
                    "the heal fills to the rolled ceiling");
            manager.remove(horse, "test");
        }
        assertTrue(above > 5, "ceilings above the kind row are live: " + above);
        assertTrue(below > 5, "ceilings below the kind row are live: " + below);
        assertTrue(donkeys > 0, "the ctor's 1-in-10 donkey is live: " + donkeys);
    }

    @Test
    void theAttributesRowWinsOverTheRoll() {
        // The /summon Attributes NBT row (Slice 78b — EntityAttributes
        // .readNbt): the row's Base replaces the attribute's base — the
        // explicit row beats the spawn roll.
        MobManager manager = manager(20261025L);
        MobEntity horse = horseAt(manager, 0.5, 0.5);
        horse.setMovementSpeedOverride(0.05);
        assertEquals(0.05, horse.walkSpeed(), 1e-12,
                "the Attributes row's override wins");
        // Clearing the row is not a state the NBT path produces (the row
        // only ever lands); the roll's shape is pinned by the band tests.
    }

    @Test
    void theJumpRollCarriesTheReferenceBand() {
        // getChildJumpStrengthBonus line 1389: 0.4 + 3x nextDouble()*0.2 —
        // the band [0.4, 1.0], mean exactly the 0.7 registered base (the
        // reference's own registered default, initAttributes line 49).
        MobEntity body = new MobEntity(5, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        Random dice = new Random(20261027L);
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            MobManager.rollHorseSpawnAttributes(body, dice);
            double jump = body.customJumpStrength();
            assertTrue(jump >= 0.4 - 1e-12 && jump <= 1.0 + 1e-12,
                    "the jump stays in the band: " + jump);
            min = Math.min(min, jump);
            max = Math.max(max, jump);
            sum += jump;
        }
        assertEquals(0.7, sum / 10_000, 0.005, "the band's mean is the registered base");
        assertTrue(min < 0.5, "the band's low end is live: " + min);
        assertTrue(max > 0.9, "the band's high end is live: " + max);
        // The donkey/mule flat (initialize line 1303): 0.5 read against
        // the LIVE subtype — the same convention the speed consult
        // carries, the retained roll answering the band again the moment
        // the subtype returns.
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        assertEquals(0.5, body.customJumpStrength(), 1e-9, "the donkey's flat");
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_MULE);
        assertEquals(0.5, body.customJumpStrength(), 1e-9, "the mule's flat");
        body.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        assertTrue(body.customJumpStrength() >= 0.4 - 1e-12,
                "the retained roll answers the horse band again");
        // The UNROLLED consult answers the registered base (an imported
        // horse with no Attributes row keeps the 0.7 the ctor registered).
        MobEntity fresh = new MobEntity(6, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        assertEquals(MobEntity.HORSE_JUMP_ATTRIBUTE_BASE, fresh.customJumpStrength(),
                1e-12, "the unrolled consult answers the registered base");
        // The Attributes row's precedence + the ranged band's own clamp
        // (the 0.0-2.0 RangedEntityAttribute, line 49).
        fresh.setHorseJumpOverride(5.0);
        assertEquals(2.0, fresh.customJumpStrength(), 1e-9,
                "the ranged clamp's ceiling");
        fresh.setHorseJumpOverride(1.3);
        assertEquals(1.3, fresh.customJumpStrength(), 1e-9,
                "the in-band row lands whole");
    }

    @Test
    void theRollIsRefusedOutsideTheHorseFamily() {
        // The family guards (the zombie leader's own shape): the rolls land
        // on no other kind.
        MobEntity cow = new MobEntity(4, MobType.COW,
                new Position(0.5, 4.0, 0.5), new Random(7), flatWorld());
        float beforeCeiling = cow.maxHealth();
        double beforeSpeed = cow.walkSpeed();
        cow.applyHorseSpawnHealth(15.0f);
        cow.rollHorseSpawnSpeed(new Random(20261026L));
        cow.rollHorseSpawnJump(new Random(20261028L));
        assertEquals(beforeCeiling, cow.maxHealth(), 1e-6f,
                "the cow's ceiling is untouched");
        assertEquals(beforeSpeed, cow.walkSpeed(), 1e-12,
                "the cow's speed is untouched");
        assertEquals(MobType.COW.maxHealth, cow.maxHealth(), 1e-6f);
        assertEquals(MobType.COW.walkSpeed, cow.walkSpeed(), 1e-12);
    }
}
