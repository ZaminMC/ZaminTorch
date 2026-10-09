# Vanilla 1.8.8 mechanics reference

Decompiled vanilla Minecraft 1.8.8 source (`net.minecraft.*`, 1634 classes)
kept **in-repo** as the porting source for ZaminTorch's engine semantics.
Per the project's porting rule: when a behavior needs vanilla grade
correctness (AI, pathing, crafting, containers, enchanting, falling blocks,
liquids, portals...), port the **actual implementation from this tree** and
adapt it to ZaminTorch's own architecture — do not hand-approximate.

## Landmark packages

| Package | Contents |
|---------|----------|
| `entity/ai/goal` | 52 vanilla AI goals (AttackOnCollide, ArrowAttack, Panic, Wander, LookClosest, Breed, FollowParent, EatGrass, BondWithPlayer...) |
| `entity/ai/pathing` | vanilla A*: `PathFinder`, `Path`, `PathPoint`, `PathNavigate`, node evaluators |
| `entity/ai/control` | `MoveControl`, `JumpControl`, `LookControl`, `BodyControl` |
| `entity/living/mob/passive/animal` | `HorseBaseEntity`, `AnimalEntity` (breeding, temper, chest mounts) |
| `entity/living/mob/monster` | zombie/skeleton/creeper/spider families with their goal wiring |
| `crafting` | `CraftingManager`, `ShapedRecipes`, `ShapelessRecipes` — the real matcher (offset + mirror + multiset) |
| `inventory/menu` | vanilla container slot layouts: `MenuWorkbench`, `MenuChest`, `MenuFurnace`, `MenuEnchantment`, `HorseMenu`, `MenuMerchant` |
| `enchantment` | all 1.8 enchantments + `EnchantmentHelper` (the weight/level/table math) |
| `block` | 153 vanilla blocks incl. `BlockFalling`, `BlockPortal`, `BlockLiquid`, `BlockChest`, `BlockWorkbench` |
| `entity` | `FallingBlockEntity`, `ItemEntity`, `ExperienceOrbEntity` physics |
| `world` | `World`, `WorldServer`, `Explosion`, spawner/seed plumbing |

## Usage rule

Read the class here first, port the algorithm (constants, tolerances, edge
cases) into the matching ZaminTorch module, and cite the ported-from file in
the commit message. Never copy client/render/network classes — the engine has
its own wire layer.
