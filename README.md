# FlowState

A 2D platformer / action RPG built with **Java 25**, **FXGL 17.3**, and **Box2D physics**. The world generates infinitely to the right, enemies chase and attack the player, and a three-branch skill tree (Melee · Ranged · Magic) lets you shape how you fight.

---

## Tech stack

|Layer|Choice|
|---|---|
|Language|Java 25|
|Framework|FXGL 17.3|
|Physics|Box2D (via FXGL)|
|Build|Maven + JPMS (`module-info.java`)|
|Version|0.7.0|
|Package root|`at.htl.flowstate`|

---

## Getting started

### Prerequisites

- Java 25 JDK
- Maven 3.9+

### Run

```bash
mvn javafx:run
```

The Maven wrapper works too: `./mvnw javafx:run`.

### Standalone jar (Linux + Windows)

```bash
./mvnw -Pdist clean package
```

This builds `target/FlowState-1.0.jar`, a single jar with JavaFX and its native libraries for Linux and Windows (x64) bundled. Run it on either OS with a Java 25+ runtime installed:

```bash
java -jar FlowState-1.0.jar
```

On Windows, double-clicking the jar also works when Java is associated with `.jar` files. Ignore `target/original-FlowState-1.0.jar`; it is the jar without dependencies.

---

## Controls

|Key / Button|Action|
|---|---|
|A / D|Move left / right|
|W|Jump (hold for higher jump; coyote time) / fly up while levitating|
|S|Fly down while levitating|
|1 – 4|Hotbar: select an attack, or switch a toggle (Mana Shield, enchantments) / use an instant (Levitation)|
|Left click|Use the active attack (hold to charge Zweihander, War Bow, Fire Ball; hold for Morningstar spin).|
|Right click|Weapon secondary: dodge (Leather Gloves), block (Metal Gloves), throw / recall / wind up (tridents)|
|I|Open / close skill tree (hover a skill + 1–4 to put it on the hotbar)|
|Escape|Game menu (Resume / Exit)|
|F11|Toggle fullscreen / windowed (the game starts in fullscreen)|

---

## Project structure

```
src/main/java/
├── module-info.java
└── at/htl/flowstate/
    ├── Game.java                         # Entry point, input, camera, background, death/pit checks
    ├── Assets.java                       # Texture loading + cache
    ├── Combat/
    │   ├── Combat                        # Central damage pipeline (multipliers, crits, passives, imbue, statuses)
    │   ├── Hit                           # Fluent description of one hit
    │   ├── DamageKind, Element
    ├── Skills/
    │   ├── SkillType                     # Every tree node with branch, parent, slot kind, description
    │   ├── SkillManager                  # Stat ranks, unlocks, rising costs, requirements, hotbar
    │   ├── Branch, NodeKind, SlotKind
    ├── Components/
    │   ├── Chests/ChestComponent         # +1 skill point on touch
    │   ├── Enemies/
    │   │   ├── EnemyBehaviourComponent   # Chase, step-up, jump-over; respects stun/freeze/pin/slow
    │   │   ├── Melee/RangedEnemyBehaviourComponent
    │   │   ├── EnemyStatsComponent       # HP, death, XP, stun/freeze/slow/burn/poison/bleed/pin, knockback, bounce
    │   │   ├── EnemyProjectileComponent, BallisticProjectileComponent
    │   ├── Identifier/                   # Marker components (enemy, platform)
    │   ├── SpriteComponents/SpriteComponent
    │   └── Player/
    │       ├── PlayerMovementComponent   # Run, jump, coyote, step-up, levitation, dash, slam
    │       ├── PlayerStatsComponent      # HP, mana, XP, SP, damage multipliers, block/shield/i-frames
    │       ├── LoadoutComponent          # Hotbar routing, cooldowns, toggles, levitation, combo meter
    │       ├── Abilities/                # One class per weapon family (+ AbilityFactory)
    │       ├── Attacks/                  # Swing, thrust, spin, projectile, thrown trident, blast, burning ground
    │       └── Helpers/DeleteAfterTimeComponent
    ├── Factories/                        # LevelFactory (platform, chest), EnemyFactory
    ├── Generation/                       # Level, structure and enemy generation
    └── Menu/
        ├── GameMenu                      # Pause menu
        ├── Hud                           # Bars, skill points, hotbar, combo meter, charge bar
        └── SkillTree/SkillTreeView       # Skill tree overlay
```

---

## Core systems

### Procedural world generation

`LevelGeneration` generates terrain ahead of the player on demand. A momentum-based **terrain drift** system produces smooth hills and valleys — 70 % of the time terrain is nearly flat, 30 % produces a real slope or hill/valley. Hard clamps keep the surface between Y 800 and Y 1000.

Segment types:

|Type|Chance|Details|
|---|---|---|
|Pit|6 %|Gap of 160–280 px; fall in and the game exits|
|Structure|3 %|Chest room or challenge tower (see below)|
|Ground segment|~91 %|Variable width 180–420 px; extends to Y 1500|

**Chest structure** — a 100 px flat segment with a chest on top that grants a skill point on contact.

**Challenge structure** — four floating platforms in a zigzag stack up to 800 px tall, chest at the summit.

**Enemy generation** — every normal ground segment has a 20 % chance to spawn enemies: 80 % chance of melee (with a 5 % gang-of-five roll), 20 % chance of a ranged enemy.

Entities more than 3500 px behind the viewport are removed each frame to keep Box2D stable.

### Physics & collision layers

Three Box2D category/mask layers prevent unwanted collisions:

|Layer|Bits|Collides with|
|---|---|---|
|`CATEGORY_TERRAIN`|`0x0001`|everything|
|`CATEGORY_PLAYER`|`0x0002`|terrain only|
|`CATEGORY_ENEMY`|`0x0004`|terrain only|

Player–enemy and enemy–enemy collisions are intentionally disabled at the physics layer. Damage is handled manually via `isColliding()` checks each frame.

### Player movement

`PlayerMovementComponent` handles all locomotion:

|Mechanic|Detail|
|---|---|
|Acceleration ramp|Speed builds from 60 % to 100 % over 80 frames; decelerates in the air|
|Coyote time|5-frame window to jump after walking off a ledge|
|Variable jump height|Releasing W early multiplies upward velocity by 0.45|
|Step-up|Automatically climbs ledges ≤ 20 px high; forward nudge prevents feedback loop|
|Sprite states|Idle · Walk · Jump · Land (with a 12-frame landing hold) for both directions|
|Levitation|Timed burst (3 s): gravity overridden, W up, S down, otherwise hover; then a 1 s glide|
|Dash|Leather Gloves dodge: 150 px in 0.2 s with brief invulnerability|
|Ground slam|Hammer mid-air attack: fast fall, shockwave on landing|

### Melee feel

Shared by every melee weapon (`MeleeAbility`, `MeleeSwingComponent`, `PunchComponent`, `Visuals/Impact`); weapon stats are unchanged.

|Mechanic|Description|
|---|---|
|Direction|Slashes go in the facing direction (the player stays turned to the attack until it ends). S + attack in the air is a down-slash|
|Three phases|Windup (no hitbox) → fast strike (the only phase that hits, sub-stepped so it can't skip enemies) → follow-through and fade|
|Input buffer|A press up to 0.2 s before the weapon is ready is queued and fires on the first ready frame|
|Flow chain|Attacking on the beat (buffered, or ≤ 0.14 s after ready) right after a hit that connected raises flow (max x6): +8 % damage and stronger impact per level. Mashing early or attacking late resets it. A glint at the hand marks when the window opens|
|Pogo|A down-slash that connects bounces the player up|
|Deflect|Swung weapons destroy enemy spears they touch during the strike|
|Perfect release|Zweihander: releasing within 0.22 s after the charge completes (blade flash) adds flow and impact|
|Freeze frames|Every melee hit freezes the game briefly (more for damage, crits, kills), shakes the camera and makes the target tremble. Taking damage also freezes briefly|

### Enemy AI

Both enemy types share the `EnemyBehaviourComponent` base:

- Chase the player along the X axis at a randomised speed (±10 % variance)
- Automatically step up ledges ≤ 20 px
- Jump over obstacles between 20 px and 300 px tall
- Stop all movement while stunned, frozen or pinned; move and attack 40 % slower while slowed
- Knockback velocity decays via friction (600 units/s²)
- Die when falling into a pit (counts as a kill)
- Sprite states mirror the player's (idle / walk / jump)

`MeleeEnemyBehaviourComponent` deals contact damage on every overlapping frame.

`RangedEnemyBehaviourComponent` stops within 500 px, faces the player, and fires a spear on a 2-second cooldown. The launch velocity is solved analytically from a ballistic trajectory equation (discriminant check, minimum-time solution). **Do not modify the math in `calculateBallisticVelocity` unless you fully understand it.**

### HUD

- Health, mana and XP bars in the upper-right corner, with the current skill points below.
- A 4-slot **hotbar** at the bottom: the active attack has a gold border, active toggles are blue, and each slot shows a short status (cooldowns, "thrown", combo step, Imbue element).
- An orange **combo meter** above the hotbar (Fisticuffs).
- A **charge bar** above the player while charging, drawing, winding up or spinning.

### Parallax background

Two tiling `ImageView` instances (`bg1`, `bg2`) scroll at 5 % of the player's world X position, producing a subtle depth illusion without an additional physics body.

---

## Skill tree

The full design lives in [`concept_new/`](concept_new/SkillTree.md) (an Obsidian vault). Summary of what is implemented:

### Progression

- Skill points come from level-ups and chests.
- Each branch has a repeatable **stat node** (Strength / Dexterity / Mana, 1 SP per rank, max 10): +5 % melee damage, +5 % ranged damage, or +10 max mana and +0.5 %/s regen per rank.
- **Sub-trees** cost 2 SP, need rank 1, and grant a basic weapon/spell plus a passive.
- **Skills** cost 3 / 4 / 5 SP for the 1st / 2nd / 3rd skill in the same sub-tree and need rank 3 / 5 / 7.

### Tree

```
MELEE (Strength)
├── Swords      Iron Sword   · Keen Edge: sword crits bleed
│   ├── Shortsword      3-hit combo with finisher
│   ├── Dual Wielding   alternating blades, swings overlap
│   └── Zweihander      tap swing / hold for charged cleave
├── Fisticuffs  Hand Wraps   · Combo Meter: chained hits raise crit, crits x2 + stun
│   ├── Leather Gloves  fastest jabs, RMB dodge
│   ├── Metal Gloves    hold RMB to block (destroys projectiles)
│   └── Spike Gloves    heavy punches that bleed
└── Blunt       Club         · Concussion: longer stuns, +15 % vs stunned
    ├── Hammer          stun swing, mid-air ground slam
    ├── Morningstar     tap swing / hold for flail spin
    └── Spring Hammer   huge knockback, launched enemies bounce-damage others

RANGED (Dexterity)
├── Bow         Basic Bow    · Eagle Eye: faster, flatter arrows
│   ├── Shortbow        fast short-range shots
│   ├── Bone Bow        arrows shatter into bone shards
│   └── War Bow         hold to draw (damage, speed, pierce up to 3)
├── Crossbow    Basic Crossbow · Quick Reload: -15 % reload
│   ├── Dual Crossbow   burst of 2
│   ├── Heavy Crossbow  straight rail bolt, infinite pierce, pins to walls
│   └── Poison Crossbow first hit gets 2 poison stacks
└── Trident     Trident (LMB stab, RMB throw, pick it up) · Reunion: x2 stab after pickup
    ├── Recall Trident  RMB again recalls it, hits enemies on the way back
    ├── Ice Trident     stab slows, landing freezes an area, frozen enemies shatter
    └── Heavy Trident   hold RMB to wind up, infinite pierce

MAGIC (Mana)
├── Arcane      Arcane Bolt  · Arcane Flow: +50 % regen after 2 s without casting
│   ├── Magic Missile   homes on the enemy nearest the cursor
│   ├── Mana Shield     toggle: 70 % of damage is paid with mana
│   └── Levitation      25 mana: 3 s flight, glide, 10 s cooldown
├── Elemental   Spark (burn) · Elemental Mastery: statuses last 25 % longer
│   ├── Fire Ball       hold to charge: explosion + burning ground
│   ├── Icicle          slows; shatters slowed/frozen enemies
│   └── Poison Darts    3-dart fan, 1 poison stack each
└── Enchanting  Sharpen (+15 % weapon dmg) · enchantments drain 25 % less
    ├── Imbue           weapon hits apply Fire / Ice / Poison (press again to cycle)
    ├── Life Steal      heal 8 % max HP per kill
    └── Piercing        +2 pierce on all projectiles (+1 Recall Trident)
```

Only one enchantment can be active at a time. Mana Shield and enchantments pause mana regeneration while active.

### Status effects

Stun, knockback, slow, freeze, shatter, burn, poison (stacks up to 10), bleed (stacks up to 5) and pin. See [`concept_new/Mechanics/Status Effects.md`](concept_new/Mechanics/Status%20Effects.md).

### Cooldowns

Melee, Ranged and Magic each have their own attack cooldown, so switching weapons inside a branch never skips a cooldown.

---

## Repository

GitHub: [MaxLettner/FlowState](https://github.com/MaxLettner/FlowState)

Feature branches are developed independently and merged via pull request.
