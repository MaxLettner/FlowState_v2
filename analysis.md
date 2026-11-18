# FlowState v2 — Project Analysis

> Scope: every file in the project was read: Java sources, `pom.xml`, `module-info.java`, `README.md`, resources, the `Texture/` and `system/` folders, the Obsidian vault in `Concept/` and `Concept/todo.md`.
> Date of analysis: 2026-11-18. The source snapshot is version `0.6.1` (set in `Game.initSettings`). `pom.xml` says `1.0`.

---

## 1. What the project is

FlowState is a 2D side-scrolling action platformer with RPG progression, built on **FXGL 17.3** (a JavaFX game engine) with its bundled **Box2D** physics. The player runs to the right through endless, procedurally generated terrain and fights melee and ranged enemies. Killing enemies gives XP, and XP gives skill points. Chests also give skill points. Skill points are spent in a three-branch skill tree (**Melee / Ranged / Magic**). Each branch has three sub-trees with three leaf skills each. Selecting a node changes what the left mouse button does.

There is no win condition. The run ends (the application **exits**) when the player falls into a pit or their HP drops below zero.

### Repository-level facts

| Item | Value |
|---|---|
| Language | Java 25 (uses Java 25 *flexible constructor bodies*: statements before `super(...)` in `ExplosionBlastComponent`, `IceBlastComponent`, `KnockbackBlastComponent`, `SwordAnimationComponent`, `IceTridentComponent`) |
| Engine | FXGL 17.3 (JavaFX 21.0.6 controls/fxml/media, with FXGL's own OpenJFX dependencies excluded) |
| Build | Maven (`mvnw` wrapper pins Maven **3.8.5**, while the README asks for 3.9+), JPMS module `at.htl.flowstate` |
| Run | `mvn javafx:run` (main class `at.htl.flowstate/at.htl.flowstate.Game`) |
| Tests | None. JUnit 5.12.1 is declared, but there is no `src/test` directory |
| Size | 50 Java files, ~4,200 lines |
| License | MIT, © 2026 MaxLettner |
| VCS | This folder is **not** a git repository, although the README points to GitHub `MaxLettner/FlowState` |
| Authors (from todo.md) | "Max" (gameplay/systems) and "John" (textures, some skills, enemy spear) |

### Top-level layout

```
FlowState_v2/
├── pom.xml, mvnw, mvnw.cmd, .mvn/        Maven build + wrapper
├── README.md                             Detailed (partly outdated/aspirational) docs
├── LICENSE                               MIT
├── .gitignore, .idea/                    IntelliJ project files (JDK "openjdk-25")
├── system/                               FXGL runtime data (fxgl.bundle = serialized settings, auto-generated)
├── Texture/                              Loose source art NOT on the classpath (BasicSword, Boulder/boulder, MagicMissile)
├── Concept/                              Obsidian vault: skill-tree design notes + todo.md
└── src/main/
    ├── java/module-info.java
    ├── java/at/htl/flowstate/...         All game code
    └── resources/assets/textures/        45 PNG/JPG textures used via getResource()
```

---

## 2. Software architecture

### 2.1 Architectural style

The game follows FXGL's **Entity–Component** model:

- **Entities** are created through `EntityFactory` classes (`@Spawns("name")`) or directly with `entityBuilder()`.
- **Behaviour** lives in `Component` subclasses that override `onAdded()` / `onUpdate(tpf)` / `onRemoved()`.
- **Collision/hit detection** is done **manually** every frame with `entity.isColliding(other)` (bounding-box overlap). FXGL collision handlers are not used, and Box2D only handles movement against terrain.
- **Entity identification** uses empty *marker components* (`EnemyIdentifierComponent`, `PlatformIdentifierComponent`) that are queried with `getGameWorld().getEntitiesFiltered(...)`.
- **Global access** goes through a static `Game.getPlayer()` and the singleton `SkillList.getInstance()`.

### 2.2 Package / class map

```
at.htl.flowstate
├── Game                                  GameApplication: settings, input, HUD, camera, parallax bg, death/pit checks, cleanup
│
├── Factories/
│   ├── LevelFactory                      @Spawns "platform" (static Box2D body, coloured Rectangle) and "chest"
│   └── EnemyFactory                      @Spawns "meleeEnemy", "rangedEnemy" (dynamic body, 40×80, HP 100)
│
├── Generation/
│   ├── LevelGeneration                   Endless terrain generator (drift-based hills, pits), drives the two below
│   ├── StructureGeneration               Chest room / challenge tower
│   └── EnemyGeneration                   Per-segment enemy roll
│
├── Skills/                               Skill *data model*
│   ├── SkillType      (enum)             43 identifiers incl. START, display names
│   ├── SubTreeType    (enum)             MAGIC / MELEE / RANGED
│   ├── Skill                             name, type, cost, subtree, unlocked flag, canAfford()
│   └── SkillList      (singleton)        Array of 39 Skills, unlock/select, pushes selection to PlayerRouterComponent
│
├── Menu/
│   ├── GameMenu                          Custom FXGL pause menu (Resume / Exit)
│   └── SkillTree/
│       ├── SkillTreeParent               Overlay VBox, open/close, (unused) createStyledButton helper
│       ├── SkillTree                     Two-level UI: category buttons → full category tree
│       └── Components/SkillTreeNode      Tree node: pay cost → unlock → raise stat; or select if already unlocked
│
└── Components/
    ├── Chests/ChestComponent             Closed → opening → collected textures; +1 skill point on touch
    ├── Identifier/                       Marker components (Enemy, Platform)
    ├── SpriteComponents/SpriteComponent  8 textures (idle/walk/jump/land × L/R), swapped by callers
    ├── Enemies/
    │   ├── EnemyBehaviourComponent (abs) Chase, step-up, jump-over, stun/knockback freeze, sprite state
    │   ├── MeleeEnemyBehaviourComponent  Contact damage 5
    │   ├── RangedEnemyBehaviourComponent Keeps 500 px distance, throws ballistic spear (20 dmg, 2 s cooldown)
    │   ├── EnemyStatsComponent           Death → XP, poison DOT, stun, knockback decay
    │   ├── EnemyProjectileComponent      Hits player / despawns on terrain
    │   └── BallisticProjectileComponent  Manual gravity integration + rotation to velocity
    └── Player/
        ├── PlayerMovementComponent       Run ramp, jump, coyote time, variable jump, step-up, levitation, sprites, screen-lock
        ├── PlayerStatsComponent          HP/Mana/XP/level/skill points/strength/dexterity, regen, i-frames
        ├── PlayerRouterComponent         Maps the selected SkillType to a method on Melee/Ranged/MagicSkillComponent
        ├── Skills/
        │   ├── SkillComponent (abs)      doDefault, doSub1..3, doSubXSkillY (13 abstract methods)
        │   ├── MeleeSkillComponent       weaponBuilder(...) + attack-weight pool
        │   ├── RangedSkillComponent      tridentBuilder(...) + attack-weight pool
        │   ├── MagicSkillComponent       projectileBuilder(...), enchantments, levitation, shield, HUD icons
        │   └── IconType (enum)           Crit, Speed, Lifesteal, Piercing, Levitation, Shield
        ├── Melee/
        │   ├── WeaponDamageComponent     Damage×strength, crit (×5), lifesteal, stun, knockback
        │   └── AttackAnimations/
        │       ├── AnimationComponent (abs) returns attack weight on end
        │       └── SwordAnimationComponent  220°→45° eased arc swing attached to the player
        ├── MagicProjectiles/
        │   ├── PlayerProjectileComponent Hit list, pierce counter, platform despawn, hitEnemy/hitGround hooks
        │   ├── FireballProjectileComponent  Spawns ExplosionBlast on enemy or ground hit
        │   ├── IcecicleProjectileComponent  Stun 0.5 s on hit
        │   └── PoisonDartProjectileComponent Poison 5 s on hit
        ├── Tridents/
        │   ├── TridentComponent          Pierces all enemies, sticks in ground, picked up by touch, returns weight onRemoved
        │   ├── RecallTridentComponent    Starts HomingProjectileComponent toward the player on ground hit
        │   ├── IceTridentComponent       Stun on hit + IceBlast on ground hit
        │   └── HeavyTridentComponent     KnockbackBlast on ground hit
        ├── Blasts/
        │   ├── BlastComponent (abs)      Growing circle (via scale), radial hit test once per enemy, fade-out
        │   ├── ExplosionBlastComponent   Damage
        │   ├── IceBlastComponent         Stun
        │   └── KnockbackBlastComponent   Push away from trident
        └── Helpers/
            ├── HomingProjectileComponent Turn-rate-limited steering toward a target entity
            └── DeleteAfterTimeComponent  Removes entity after N seconds
```

`module-info.java` exports and opens most component packages to `com.almasb.fxgl.core`. `Skills`, `Menu`, `Chests` and `SpriteComponents` are not listed, which works today because FXGL doesn't reflect into them. It also `requires` `com.fasterxml.jackson.databind`, `java.desktop`, `jdk.security.jgss` and `annotations`, and nothing in the code uses the first three.

### 2.3 Runtime flow

```
main() → launch()
  initSettings   1920×1080, title "FlowState", version 0.6.1, custom GameMenu
  initPhysics    gravity (0, 800)
  initInput      A/D move, W jump/levitate-up, S levitate-down, I skill tree, LMB attack
  initGame       HUD bars → factories → LevelGeneration → player entity → viewport bounds
                 → SkillTree (creates SkillList singleton, needs player) → bars → parallax bg
  onUpdate(tpf)  pit check (y > 1180 → exit)
                 camera (only scrolls right, player kept at ≤75 % of screen width)
                 levelGeneration.generateLevel(playerX)   (keeps 1400 px generated ahead)
                 cleanup entities 3500 px behind the viewport
                 parallax scroll (5 %)
                 death check (hp < 0 → exit)
                 update HP / MP / XP bars
```

The **player entity** carries: `PhysicsComponent` (dynamic, fixed rotation, friction 0), `PlayerMovementComponent`, `MeleeSkillComponent`, `RangedSkillComponent`, `MagicSkillComponent`, `PlayerStatsComponent`, `PlayerRouterComponent`, and `SpriteComponent`.

### 2.4 Physics & collision

| Category | Bits | Mask | Used by |
|---|---|---|---|
| TERRAIN | `0x0001` | `0xFFFF` | Platforms |
| PLAYER | `0x0002` | TERRAIN | Player |
| ENEMY | `0x0004` | TERRAIN | Enemies |

Player and enemies pass through each other physically. Projectiles, weapons and blasts have no physics body. They move by `ProjectileComponent`, manual translation or homing, and test overlap themselves. Every damaging entity runs `getEntitiesFiltered(...)` over the whole world **each frame**, and so does the player's step-up check. That is O(entities²) per frame, which the 3500 px cleanup keeps manageable.

### 2.5 World generation (`Generation/`)

Each call to `spawnNextSegment()` picks one of three outcomes:

1. **Pit (6 %)**: advance 160–280 px with no ground and reset the drift.
2. **Structure (3 %)**: `FXGLMath.random(0,2)` is inclusive, so there is a ⅓ chance each of:
   - a **chest room** (100 px ground, chest on top),
   - a **challenge tower** (4 brown floating platforms zig-zagging up to 800 px, chest on top, 400 px ground),
   - **nothing** (default branch returns the same X, so the loop simply rolls again).
3. **Ground (~91 %)**: width 180–420 px. The new height comes from a *momentum drift* model:
   - 70 % near-flat (±12, pulled back toward BASE_Y 950), 20 % gentle (±30), 10 % hill/valley (±60), each kept for several segments.
   - Near the bounds the drift is forced back, and the result is hard-clamped to Y 800–1000.
   - Each segment extends down to Y 1500 so the bottom is never visible.
   - Each ground segment then rolls `EnemyGeneration.trySpawn`: 20 % chance of an enemy. Of those, 20 % are ranged and 80 % melee, and a melee roll has a 5 % chance to be a "gang" of 5.

### 2.6 Player systems

**Movement (`PlayerMovementComponent`)**
- Speed ramps from 60 % to 100 % of 400 px/s over 80 grounded frames. In the air it bleeds 0.5 frame/tick down to a floor of 20.
- The Speed enchantment multiplies speed by 1.3.
- Jump velocity is 600. Releasing W while rising multiplies vertical velocity by 0.45 (variable jump height).
- Coyote time is 5 frames. "Grounded" means `|vy| < 0.1`.
- Step-up: if the player is grounded and moving, an adjacent static body edge ≤ 20 px higher teleports the player on top with a 14 px nudge.
- Levitation overrides vertical velocity: W gives −300, S gives +300, otherwise +2.
- The player cannot walk left past the viewport edge, so there is no backtracking.
- Sprite state is chosen as landing (12 frames) > airborne > walking > idle, for each facing direction.

**Stats (`PlayerStatsComponent`)**
- HP 100, Mana 100, Strength 100, Dexterity 100.
- HP regenerates at 1 %/s. Mana regenerates at 5 %/s, but only when no enchantment, levitation or shield is active.
- 60 invincibility *frames* after each hit. These are counted per frame, not per second.
- XP: +1 per kill. When XP equals `maxExperience` (starting at 3), the player gains 1 skill point, `maxExperience` increases by 1, and XP resets.
- Unlocking a skill-tree node raises a stat by branch: Magic → +10 max mana, Melee → +5 strength, Ranged → `raiseDexterity()`.

**Attack routing**
`SkillList` keeps the "selected" index. On every unlock or select it calls `PlayerRouterComponent.setCurrentSelection(type)`. Left click then runs a large `switch` that calls the matching method on one of the three skill components. The initial selection is `START` (the "Rusty Sword"/club, `Knuppel.png`).

**Attack weight**
Melee and Ranged each keep their own pool (max 10). A melee weapon spends its weight and gets it back when `SwordAnimationComponent` ends. Tridents always cost 10 and give it back in `TridentComponent.onRemoved()`, so only one trident can be in the world at a time. Magic has no cooldown: it is limited only by mana.

### 2.7 Implemented attacks (actual numbers)

**Melee:** `weaponBuilder(texture, scale, offX, offY, w, h, dmg, weight, duration s, stun s, crit %, knockback)`

| Selection | Texture | Dmg | Weight | Swing s | Stun | Crit % | Knockback |
|---|---|---|---|---|---|---|---|
| START (Rusty Sword) | Knuppel.png | 5 | 10 | 1 | 0 | 0 | 200 (*debug=true → black hitbox rectangle is drawn*) |
| Melee (default) | RustySword.png | 10 | 10 | 1 | 0 | 1 | 200 |
| Swords (basic) | grey `Rectangle` placeholder | 10 | 10 | 1 | 0 | 0 | 0 |
| Shortsword | ShortSword.png | 5 | 10 | 0.5 | 0 | 3 | 250 |
| Dual Wielding | DualWieldingSword.png | 5 | **5** | 1.2 | 0 | 2 | 250 |
| Zweihander | Zweihander.png | 35 | 10 | 2 | 0 | 2 | 500 |
| Fisticuffs + Leather/Metal/Spike | — | *empty methods* | | | | | |
| Blunt (basic) | BasicHammer.png | 15 | 10 | 1.5 | 0.75 | 1 | 400 |
| Hammer | BigHammer.png | 15 | 10 | 2 | 1.5 | 2 | 500 |
| Morningstar | BigMace.png | 30 | 10 | 1.5 | 0 | 4 | 400 |
| Spring Hammer | SpringHammer.png | 1 | 10 | 1 | 0 | 0 | 1000 |

Damage is scaled by `strength/100` when the weapon entity is created. A crit deals ×5 damage, and the Crit enchantment doubles the crit chance. Note that `FXGLMath.random(0,100) <= critChance` is an inclusive integer roll, so even a 0 % weapon crits about 1/101 of the time.

**Ranged:** all tridents fly straight at 1300 px/s toward the mouse.

| Selection | Dmg | Special |
|---|---|---|
| Ranged (default), Bow, Crossbow, all 3 bows, all 3 crossbows | — | *empty methods* |
| Trident (basic) | 25 | Pierces everything, sticks in ground, touch to pick up |
| Recall Trident | 25 | After it sticks, it homes back to the player (600 px/s, 200°/s turn) |
| Ice Trident | 25 | Stuns 0.8 s on hit, plus an ice blast (radius 200) that stuns on ground hit |
| Heavy Trident | 45 | Knockback blast (strength 500, radius 200) on ground hit |

**Magic:** all projectiles fly straight toward the mouse and are removed when they hit terrain.

| Selection | Mana | Dmg | Pierce | Special |
|---|---|---|---|---|
| Magic (default) | 20 | 20 | 1 | "Magic missile" texture, aimed (not homing) |
| Arcane (basic), Elemental (basic), Magic Missile leaf | — | — | — | *empty methods* |
| Mana Shield | 2/s | — | — | Toggle with HUD icon; **has no effect on damage** |
| Levitation | 5/s | — | — | Toggle; flight |
| Fire Ball | 30 | 30 + 20 AOE | 1 | Explosion radius 200 on enemy *or* ground hit |
| Icicle | 15 | 20 | 5 | Stun 0.5 s per enemy hit |
| Poison Darts | 15 (for 3) | 10 each | 1 | 3 darts ±5° (rotation matrix), poison 5 dmg/s for 5 s |
| Enchanting (basic) | 2/s | — | — | **Crit** enchantment |
| Life Steal | 2/s | — | — | Heals 5 % of **melee** damage only |
| Piercing | 2/s | — | — | Shows icon and drains mana; **no gameplay effect** |
| Speed | 2/s | — | — | Movement ×1.3 |

Only one enchantment can be active at a time. Levitation and Shield are independent toggles. Right-clicking a HUD icon turns that effect off.

### 2.8 Enemies

- Both enemy types use the same textures (`Enemy*.png`), have HP 100 (`HealthDoubleComponent`), and get ±10 % random speed (base 200) and jump force (base 600).
- **AI:** move along X toward the player, step up ledges ≤ 20 px, and jump at obstacles 20–300 px high within 30 px.
- **Melee:** 5 damage on overlap. The player's 60 i-frames limit this to about one hit per second.
- **Ranged:** stops within 500 px, faces the player and throws a spear every 2 s for 20 damage. The launch velocity solves the projectile equation for fixed speed 800 under gravity 800, using the smaller positive time root (the flatter arc). The spear despawns on terrain, after 10 s, or off-screen.
- **Status effects (`EnemyStatsComponent`):**
  - Poison: a new poison *replaces* the remaining duration, so it does not stack.
  - Stun: it only counts down while no knockback is active.
  - Knockback: sets the X velocity and adds a small hop (−0.3 × strength), then decays at 600/s². No AI runs while stunned or knocked back.
- **Death:** when HP reaches zero, the player gets +1 XP and the enemy is removed.

### 2.9 UI

- **HUD:** HP bar (red), MP bar (blue) and XP bar (gold), each 300×15 at the top right. Active-effect icons (CRT/SPD/LST/PRC, LEV, SLD) appear at Y 145.
- **Skill tree (I key):** an overlay added to the game scene. **The game keeps running while it is open**; only attacking is blocked.
  - Top level shows Melee / Magic / Ranged buttons. The first click buys the branch (1 SP), and a click on an unlocked branch opens its tree.
  - The category tree shows three sub-tree boxes. Sub-tree buttons (2 SP) are enabled when the branch is unlocked, and leaf buttons (3 SP) when the sub-tree is unlocked.
  - Colours: grey = locked or unaffordable, orange = affordable, green = unlocked, blue = unlocked and selected.
  - Clicking an unlocked node selects it as the active attack. Buying a node selects it automatically.
  - The view redraws whenever the skill-point count changes.
- **Pause menu (Esc):** FXGL game menu with Resume and Exit.
- **Parallax:** two copies of `bg.png`, scrolled at 5 % of the player's X.

### 2.10 Skill costs and totals

| Level | Count | Cost each | Total |
|---|---|---|---|
| Branch (Melee/Ranged/Magic) | 3 | 1 | 3 |
| Sub-tree | 9 | 2 | 18 |
| Leaf | 27 | 3 | 81 |
| **Total** | 39 | | **102 SP** |

Leaves within a sub-tree are not mutually exclusive, so a player can eventually own every node.

---

## 3. Bugs, gaps and code-quality observations

**Functional bugs**
1. **`raiseDexterity()` raises `strength`** (`PlayerStatsComponent`). Dexterity stays at 100 forever, so `updateMaxMoveSpeed()` never changes anything, and Ranged unlocks buff melee damage instead.
2. **Mana Shield** drains mana and blocks mana regen but **never reduces damage**: `takeDamage` does not check `isShieldActive()`.
3. **Piercing enchantment** has no gameplay effect: `IconType.Piercing` is never read.
4. **Life Steal and Crit** only work for melee (`WeaponDamageComponent`), not for tridents or spells.
5. **Poison does not stack.** It resets the duration instead, which matches the open todo "fix poison".
6. **Initial selection mismatch:** the router starts on `START`, but `SkillList.index_of_selected_skill = 0` points at `MAGIC`. The tree therefore reports Magic as "selected" before anything is chosen, and `START` cannot be re-selected from the UI once something else is picked. This is probably what John's todo "fix basic not selectable" refers to.
7. **The START weapon is built with `debug = true`**, so a black hitbox rectangle is rendered behind the club.
8. **Death and pit falls call `getGameController().exit()`**, which closes the program. There is no game-over screen or restart. The death check is `health < 0`, so exactly 0 HP survives.
9. **Chest factory uses `buildAndAttach()`**, but `spawn()` attaches the entity again. FXGL normally expects `build()` here (as `newPlatform` does). Worth checking for duplicate-attach warnings or errors.
10. In `PlayerProjectileComponent` and `TridentComponent` the loops keep running after `removeFromWorld()`, so `hitGround()` can fire several times when a projectile touches two platforms in one frame (e.g. two blasts).

**Unimplemented (empty methods)**
- Fisticuffs: the basic sub-tree attack and all 3 gloves.
- Ranged: the default attack, Bow and Crossbow basics, and all 3 bows and all 3 crossbows (textures for most of them already exist).
- Magic: Arcane basic, Elemental basic, and the Magic Missile leaf.

**Code-quality notes**
- `SkillComponent` uses positional method names (`doSub2Skill3`), and the router's mapping order differs from the skill tree's order (for example, the tree lists Heavy/Ice/Recall while `doSub3Skill1` is Recall). This is easy to get wrong. A `Map<SkillType, Runnable>` or a per-skill strategy would be safer.
- The builders take 11–13 positional `double` parameters each, and the debug/non-debug branches are duplicated. Each builder could be a single builder chain with an optional view.
- Platform and enemy lookups and the hit loops are copy-pasted into roughly 7 classes.
- Textures are loaded with `MeleeSkillComponent.class.getResource(...)` and `new Image(...)` on every attack, instead of FXGL's `texture()` / asset cache.
- Timers are inconsistent: i-frames and coyote time count frames, while everything else uses `tpf` seconds.
- `SkillTreeParent.createStyledButton` and `ButtonState` are unused.
- `module-info` has unused `requires` entries.
- Unused resources: `BasicBow`, `BasicCrossbow`, `BoneBow`, `DualCrossbow`, `ShortBow`, `WarBow`, `MagicShield`, `EnemyRandomBenis`, `bg1/2/3.jpg`, `Painting_3.jpg`. The root `Texture/` folder (with case-duplicate `Boulder.png`/`boulder.png`) is not on the classpath at all.
- README inaccuracies:
  - It says Ice Trident "slows on melee contact", Mana Shield "reduces damage" and Piercing "adds pierce", but none of these are implemented.
  - It describes `java -jar target/flowstate.jar`, but there is no shade/assembly plugin, so no fat jar is produced.
  - It asks for Maven 3.9+, but the wrapper pins 3.8.5.
  - It places `module-info.java` inside the package folder.

---

## 4. The concept (`Concept/` folder)

The concept is a small **Obsidian vault** (`.obsidian/` config plus Markdown notes linked with `[[wikilinks]]`). It covers **only the skill tree** and a todo list. World generation, enemies, chests, XP, controls and HUD are not in the concept at all; they exist only in code.

### 4.1 Concept tree as written

```
SkillTree.md → [[Magic]] [[Melee]] [[Ranged]]

Magic/  (Magic.md → Arcane, Elemental, Enchanting;  Mana.md: "Gives more Mana, satisfies requirements")
  Arcane/     Levitation (continuous mana, no initial cost)
              Magic Missile (homing, low dmg)
              Mana Shield (reduces dmg taken while active, continuous mana)
  Elemental/  Fire Ball (straight, AOE, damage-over-time, high dmg, high mana)
              Icecicle (medium mana, "shoots icecle")
              Poison Darts (stackable DOT, low mana)
  Enchanting/ Enchanting.md → [[Fire Aspect]] [[Life Steal]] [[Piercing]]
              Life Steal (heal based on damage dealt)
              Piercing (adds pierce to projectiles)

Meele/  (Meele.md → [[One Handed]] [[Two Handed]] [[Spear]];  Strength.md: "More base dmg, satisfies requirements")
  One Handed/, Two Handed/, Spear/   → empty notes
  Swords/Weopons/     Shortsword (short range, med dmg, med speed, 3 %)
                      Dual Wielding (2 katanas, high speed, med range, low dmg, 1 % crit)
                      Zweihaender (low speed, high dmg, high range)
  Fisticuffs/         Fisticuff.md: "Stuns on Crit and double damage"
     Weopons/         Leather (high speed, low dmg, 10 % crit)
                      Metal Gloves (med speed/dmg, low range, 12 % crit)
                      Spike Gloves (low speed/range, high dmg, 15 % crit)
  Blunt/Weopons/      Hammer (2-hand, med range, slow, med dmg, 1 % crit, stun on hit, high KB)
                      Morningstar (high range, high KB, slow, 1 % crit)
                      Spring Hammer (high speed, very low dmg, very high KB)

Ranged/ (Ranged.md → Bow, Crossbow, Trident;  Dexterity.md: "More base dmg for ranged, satisfies requirements")
  Bow/        Shortbow (high speed, short range, low dmg, no pierce)
              Bone Bow (med range/dmg, low pierce)
              War-Bow (high dmg, slow, high range, pierce 3)
  Crossbow/   Dual Crossbow (2 shots then longer cooldown, low dmg, high speed, very low pierce)
              Heavy Crossbow (very slow, very high dmg, infinite pierce until wall, no bullet drop)
              Poison Crossbow (stackable DOT, low base dmg, med range/speed/pierce, only first hit poisons)
  Trident/    Trident.md: "Normal trident doesn't come back; works melee and ranged"
              Recall Trident (med ranged + close dmg, no pierce [1 with pierce buff], comes back)
              Ice Trident (AOE freeze on throw, slows enemies on melee hit, med dmg/speed)
              Heavy Trident (flies straight, infinite pierce, long windup, high dmg, high melee dmg, slow melee)
```

`todo.md` splits open work between Max and John (see §5.4).

### 4.2 Problems inside the concept itself
- `SkillTree.md` links `[[Melee]]`, but the folder and note are named `Meele`, so the link is broken.
- `Meele.md` links to **One Handed / Two Handed / Spear**. Those notes are empty, and the weapons actually designed are in **Swords / Fisticuffs / Blunt**, which have no index notes (only `Fisticuff.md` exists).
- `Enchanting.md` links `[[Fire Aspect]]`, which has no note.
- `Heavy Trident.md` ends with a stray `[[Recall Trident]]` link.
- `Bow.md` and `Crossbow.md` are empty, so there is no design for a basic bow or crossbow.
- Spelling varies: "Weopons", "Icecicle", "Zweihaender" vs. "Zweihander".

---

## 5. Differences between concept and implementation

### 5.1 Structural differences

| Topic | Concept | Implementation |
|---|---|---|
| Melee sub-trees | One Handed / Two Handed / Spear (index note), with weapon notes under Swords / Fisticuffs / Blunt | **Swords / Fisticuffs / Blunt**. The index note's categories were dropped; "Spear" only survives as the **ranged enemy's projectile** texture |
| Stat nodes | Separate **Mana / Strength / Dexterity** nodes that "satisfy requirements" (i.e. gate other nodes) | No stat nodes and no requirement system. Every unlock in a branch raises that branch's stat automatically (+10 mana / +5 strength / "dexterity", which actually raises strength) |
| Unlock rules | Not specified beyond "requirements" | Strict parent chain with costs 1 / 2 / 3 SP. Leaves are not exclusive |
| Enchanting leaves | Fire Aspect, Life Steal, Piercing | **Speed**, Life Steal, Piercing. Fire Aspect is gone. A **Crit** enchantment was added as the sub-tree's basic action |
| Sub-tree "basic" actions | Not in concept | Every branch and sub-tree node is itself selectable as an attack (Rusty Sword, basic sword, basic hammer, basic trident, magic default missile, Crit enchant) |
| Starting weapon | Not in concept | `START` = "Rusty Sword", which is actually drawn with `Knuppel.png` (a club) |
| Attack weight | Not in concept | Implemented (melee and ranged pools, max 10). todo.md: "attackweight is class specific" (done) |
| Everything outside the skill tree (world gen, enemies, chests, XP/levels, HUD, levitation controls, enemy spear) | Absent | Implemented only in code; the design is undocumented apart from the README |

### 5.2 Skill-by-skill comparison

Legend: ✅ matches · ⚠️ partially / deviates · ❌ missing · ➕ added beyond concept

**Magic**

| Skill | Concept | Code | Status |
|---|---|---|---|
| Levitation | Fly, continuous mana, no initial cost | Toggle, 5 mana/s, no initial cost, W/S control | ✅ |
| Magic Missile | **Homing**, low dmg | Leaf method empty. The *Magic branch default* fires a mouse-aimed, **non-homing** missile (20 dmg, 20 mana). `HomingProjectileComponent` exists but is only used by the Recall Trident | ⚠️/❌ |
| Mana Shield | Reduces damage while active, continuous mana | Toggle + 2 mana/s, but **no damage reduction** | ⚠️ |
| Fire Ball | Straight, AOE, **DOT**, high dmg, **high mana** | Straight, 30 dmg + 20 AOE (r 200), 30 mana. **No burn DOT**; cost is moderate | ⚠️ |
| Icicle | Medium mana | 15 mana, 20 dmg, ➕ pierce 5, ➕ 0.5 s stun | ✅➕ |
| Poison Darts | **Stackable** DOT, low mana | 3-dart spread ➕, 15 mana, 5 s poison, **not stackable** | ⚠️ |
| Fire Aspect | Enchantment | Not implemented, replaced by Speed | ❌ |
| Life Steal | Heal from damage dealt | 5 % of **melee** damage only | ⚠️ |
| Piercing | Adds pierce to projectiles | Toggle exists, **no effect** | ❌ |
| Speed (enchant) | — | ×1.3 move speed | ➕ |
| Crit (enchant) | — | Doubles crit chance | ➕ |

**Melee**

| Skill | Concept | Code | Status |
|---|---|---|---|
| Shortsword | Short range, med dmg, med speed, 3 % | 5 dmg, 0.5 s swing (fastest), 3 % crit, 85 px | ✅ (damage on the low side) |
| Dual Wielding | **2 katanas**, high speed, low dmg, 1 % | One swing entity with weight 5, so two can overlap. 5 dmg, but a **1.2 s** swing (slower than the shortsword), 2 % crit | ⚠️ |
| Zweihander | Slow, high dmg, high range | 2 s, 35 dmg, 160 px | ✅ |
| Fisticuffs (all) | Stun + double damage on crit; Leather 10 % / Metal 12 % / Spike 15 % | **All empty** | ❌ |
| Hammer | Med range, slow, med dmg, 1 %, stun, high KB | "Big Hammer": 2 s, 15 dmg, 1.5 s stun, KB 500, 2 % crit | ✅ (crit differs) |
| Morningstar | High range, high KB, slow, 1 % | 140 px, 1.5 s, 30 dmg, KB 400, **4 %** crit | ⚠️ (crit and damage higher than spec) |
| Spring Hammer | High speed, very low dmg, very high KB | 1 dmg, KB 1000, but a **1 s** swing (not especially fast) | ⚠️ |
| One Handed / Two Handed / Spear | Listed in index note | Not present | ❌ |

**Ranged**

| Skill | Concept | Code | Status |
|---|---|---|---|
| Shortbow / Bone Bow / War Bow | Fully specified (speed, range, pierce 0/low/3) | **Empty methods**, though textures exist | ❌ |
| Dual / Heavy / Poison Crossbow | Fully specified (double shot, infinite pierce, no drop, DOT) | **Empty methods** | ❌ |
| Trident (basic) | Doesn't come back; **melee and ranged** | Thrown, sticks, walk over to pick up. **No melee mode**. Pierces all | ⚠️ |
| Recall Trident | Comes back, **no pierce** (1 with buff) | Homes back to the player after landing, but **pierces all enemies** | ⚠️ |
| Ice Trident | AOE freeze on throw, **slow on melee hit** | Freeze blast when it lands + stun on direct hit. **No slow mechanic and no melee** | ⚠️ |
| Heavy Trident | Straight, infinite pierce, **long windup**, high dmg, high/slow melee | Straight, infinite pierce, 45 dmg ✅. **No windup, no melee**. ➕ Knockback blast on landing | ⚠️➕ |
| Dexterity node | More ranged base damage | No such node. Ranged damage is not scaled by any stat, and the dexterity stat never changes | ❌ |

### 5.3 Concept features that have no implementation hook at all
- A **requirements** system (stat nodes that "satisfy requirements").
- **Range** as a stat for bows (all projectiles fly until off-screen or terrain).
- **Slow** status effect (only stun exists).
- **Burn / fire DOT** (only poison DOT exists).
- **Weapon windup** times (attacks fire instantly).
- **Melee use of tridents**.
- **Bullet drop** choice per weapon (all player projectiles fly straight; only the enemy spear is ballistic).
- **Pierce buff** interacting with weapons (the Piercing enchant is never read).

### 5.4 todo.md vs. code status

| Todo (owner) | Marked | Actual state in code |
|---|---|---|
| fix poison (Max) | open | Still non-stacking ✔ consistent |
| basic sword (Max) | open | `doSub1` is a grey rectangle placeholder ✔ |
| magic missile (Max) | open | Leaf empty; John's entry is struck through because the *default* magic missile exists |
| basic arcane / basic elemental (Max) | open | Both empty ✔. In John's list "default arcane/elemental" are struck through with single `~`, which renders as strikethrough in Obsidian |
| shield (Max) | open | Toggle/UI done, effect missing ✔ |
| piercing (Max) | open | Toggle/UI done, effect missing ✔ |
| enemies textures / player textures (Max) | open | Textures exist (`Player*.png`, `Enemy*.png`); possibly placeholders or final art pending |
| fix basic not selectable (John) | open | Still present (see bug #6) |
| knockback, crit, lifesteal, levitation, tridents, attack weight, knueppel, enemy spear, bg | done | All present in code ✔ |

---

## 6. Summary

The implemented game has a solid core: endless drift-based terrain with pits and chest structures, two enemy archetypes (one with analytic ballistic aiming), a polished movement controller (acceleration, coyote time, variable jump, step-up, levitation), a working skill-point economy and a functional two-level skill-tree UI with live attack switching.

Measured against the concept, the **Magic** branch is the most complete. Its toggles and enchantments work, though Shield and Piercing have no effect and Fire Aspect was swapped for Speed. **Melee** has Swords and Blunt working and Fisticuffs entirely missing. **Ranged** has only the Trident sub-tree; Bows and Crossbows are empty stubs despite having textures. The concept's **requirements and stat-node system** was replaced by automatic per-branch stat increases, and the Ranged one is broken (`raiseDexterity` raises strength). Concept details such as homing missiles, burn and slow effects, weapon windups and trident melee have no counterpart in the code yet.

The concept vault itself is out of date. Its root index links to non-existent notes (`Melee`, `One Handed`, `Two Handed`, `Spear`, `Fire Aspect`). It never documents the systems that make up most of the code (world generation, enemies, chests, progression, HUD). Those are currently described only by the README, which also claims a few features (shield reduction, piercing, ice-trident slow, fat jar) that do not exist yet.
