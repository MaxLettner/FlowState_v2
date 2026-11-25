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

The Maven wrapper works too: `./mvnw javafx:run`. `./mvnw test` runs the unit tests (JUnit 5, in `src/test`).

`FLOWSTATE_FPS=1 ./mvnw javafx:run` prints the frame rate and the game speed (game time vs real time) every 2 seconds.

Testing switches (combine freely):

|Variable|Effect|
|---|---|
|`FLOWSTATE_DEMONS=imp,bat`|Only these demons spawn (imp, hound, brute, hurler, bat, cultist, warden, gargoyle, watcher, gatekeeper, priest, drake, void_caller), from the start, with a full danger budget; `lord` brings the Pit Lord right away, `mimic` puts mimics on the ground|
|`FLOWSTATE_ELITES=1`|Every single demon is an elite|
|`FLOWSTATE_MIMICS=1`|Every chest is a Mimic|
|`FLOWSTATE_AUTOSTART=1`|Skips the main menu|
|`FLOWSTATE_INFINITE_SP=1`|Unlimited skill points (the HUD shows 999)|
|`FLOWSTATE_SHOTS=<dir>`|Saves a screenshot every `FLOWSTATE_SHOTS_EVERY` s (default 1), at most `FLOWSTATE_SHOTS_MAX` (default 30)|

### Standalone jar (Linux + Windows)

```bash
./mvnw -Pdist clean package
```

This builds `target/FlowState-0.7.0.jar`, a single jar with JavaFX and its native libraries for Linux and Windows (x64) bundled. Run it on either OS with a Java 25+ runtime installed:

```bash
java -jar FlowState-0.7.0.jar
```

On Windows, double-clicking the jar also works when Java is associated with `.jar` files. Ignore `target/original-FlowState-0.7.0.jar`; it is the jar without dependencies.

---

## Controls

|Key / Button|Action|
|---|---|
|A / D|Move left / right|
|W / Space|Jump (hold for higher jump; coyote time) / fly up while levitating|
|S|Fly down while levitating|
|1 – 4|Hotbar: select an attack, or switch a toggle (Mana Shield, enchantments) / use an instant (Levitation)|
|Q / E|Hotbar: switch to the next attack slot on the left / right (wraps around; skips empty slots and toggles)|
|Left click|Use the active attack (hold to charge Zweihander, Fire Ball; hold for Morningstar spin). Bows: hold to draw, release to shoot; a partial draw is weaker and doesn't fly as far. Crossbows: hold to crank a bolt in (letting go early resets it); it stays loaded until the next click fires it. You move slower while drawing or cranking.|
|Right click|Weapon secondary: dodge (Leather Gloves), block (Metal Gloves), throw / recall / wind up (tridents)|
|I|Open / close skill tree (hover a skill + 1–4 to put it on the hotbar)|
|Escape|Game menu (Resume / Exit)|
|F11|Toggle fullscreen / windowed (the game starts in fullscreen)|
|M|Mute / unmute all sound|

---

## Project structure

```
src/main/java/
├── module-info.java
└── at/htl/flowstate/
    ├── Game.java                         # Entry point, input, camera, background, death/pit checks
    ├── Assets.java                       # Texture loading + cache
    ├── Sfx.java                          # Every sound effect with its mix level; play / positional / loops / mute
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
    │   │   ├── DemonType                 # Per-demon data: HP, hitbox, sprite rig, XP, knockback/stun resistance
    │   │   ├── DemonBehaviour            # AI base: think vs. disabled (stun/freeze/pin/knockback interrupts attacks)
    │   │   ├── GroundDemonBehaviour      # Walking: step-up, jump-over
    │   │   ├── Imp/Hound/Brute/Hurler/Cultist/BatBehaviour
    │   │   ├── Warden/Gargoyle/Watcher/Gatekeeper/Priest/PitLordBehaviour
    │   │   ├── Drake/VoidCaller/MimicBehaviour, GravityWellComponent
    │   │   ├── DemonNature               # Elemental matchups: flesh, fire (hates ice), void (hates fire)
    │   │   ├── EliteAffix, EliteComponent, PortalComponent, BossGateComponent
    │   │   ├── EnemyStatsComponent       # HP, death, XP, stun/freeze/slow/burn/poison/bleed/pin, knockback, bounce
    │   │   ├── EnemyProjectileComponent, BallisticProjectileComponent, Ballistics, HomingOrbComponent
    │   │   ├── ShockwaveComponent, MagmaPoolComponent
    │   ├── Identifier/                   # Marker components (enemy, platform)
    │   ├── SpriteComponents/             # SpriteComponent (player), DemonSpriteComponent (demons)
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

**Enemy generation** — a danger budget: every ground segment past x = 1200 adds danger in proportion to its width (+100 % every 12 000 px). When the budget covers the next group, picked by weight from the groups unlocked at that distance, the group spawns on the segment:

|Group|Cost|From x|
|---|---|---|
|3–5 Imps|2.5|start|
|Hellhound|2|2 000|
|Brimstone Hurler|2.5|4 000|
|4–6 Cinderbats|3|7 000|
|Pit Brute|5.5|10 000|
|Hellfire Cultist|4|13 000|
|6–8 Imps, Hellhound pair|4|15 000|
|Brute + Hurler, Cultist + Imps, Bats + Hurler|6.5–8|18 000–22 000|
|Chainwarden|3.5|8 000|
|1–2 Gargoyles|4|9 000|
|Watcher|3.5|11 000|
|Gatekeeper; Hex Priest + Imps|5–6|14 000|
|Hex Priest + Chainwarden|7.5|16 000|
|2 Wardens + Hurler, 2 Watchers + Gargoyle|8–8.5|20 000–21 000|
|Ash Drake|4.5|12 000|
|Void Caller|4|13 000|
|Void Caller + Imps, Drake + Hurler, Void Caller + 2 Hounds|7–8.5|17 000–23 000|

Single demons can be **elites** (chance grows from 0 at x = 9 000 to 22 %): ×1.8 HP, ×2 XP, a coloured aura and one affix: *Infernal* (sets you on fire, can't be burned), *Frostbound* (chills you, can't be frozen or slowed), *Vampiric* (heals 15 % on every hit), *Swift* (+35 % speed and attack speed).

About one chest in six past x = 6 000 is a **Mimic** (it takes the chest's place; killing it pays out the skill point).

The **Pit Lord** mini-boss comes at x = 18 000 and every 24 000 px after. A gate of hellfire bars the way on until it dies, nothing else spawns during the fight, and its health bar shows at the top of the screen. Killing it grants 2 skill points.

Entities more than 3500 px behind the viewport are removed each frame to keep Box2D stable.

### Art

Every sprite is drawn by `python tools/art/generate_all.py` into `src/main/resources/assets/textures`. It needs
[Pillow](https://pypi.org/project/Pillow/) (`pip install pillow`); `tools/art/pixel.py` holds the shared pixel-art
helpers and `tools/art/materials.py` the colour ramps. Randomness is seeded, so the output is the same every run.

### Sound

Every sound is synthesized by `python tools/sound/generate_all.py` (stdlib only, no samples) into
`src/main/resources/assets/sounds` as WAV; `python tools/sound/generate_all.py jump land` regenerates single ones.
The files are peak-normalized, and `Sfx.java` sets how loud each one plays. World sounds (enemies, impacts,
explosions) are panned by screen position and fade out off screen; every sound gets a small random pitch so
repeats don't sound identical. Ambience: a wind-and-drone loop in the abyss and torch fire in the rune menu.

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

### Enemy AI (demons)

Every demon extends `DemonBehaviour`: each frame it either thinks (moves and attacks) or, while stunned, frozen, pinned or knocked back, is disabled. Becoming disabled interrupts any wind-up, so a stun always cancels an attack. Slow scales movement and attack timers alike. Attacks are telegraphed by a pose and a pulsing glow (red melee, orange thrown, green spell). Walkers (`GroundDemonBehaviour`) step up ledges ≤ 20 px and jump obstacles up to 300 px; flyers have kinematic bodies (no gravity).

|Demon|HP|Kind|Behaviour|Counter|
|---|---|---|---|---|
|Imp|34|ground melee, packs|Runs at you (each aims a bit to the side), crouches and hops claws-first|Wide swings, pierce|
|Hellhound|70|ground melee|Fast; crouches growling, then lunges ~400 px; pants for 0.9 s after|Jump the lunge, punish the pant|
|Pit Brute|520|ground heavy|Raises both fists, slams: fists in front plus a shockwave rolling both ways along the floor. Half knockback, half stun duration|Jump the shockwave; freeze + shatter|
|Brimstone Hurler|80|ground ranged|Keeps 330–650 px away, lobs magma that leaves a burning pool|Close the gap|
|Cinderbat|18|flying, swarms|Flutters around its own spot above you, shrieks, dives at where you were, climbs back|Any hit kills; sinks when stunned|
|Hellfire Cultist|90|ground mage|Keeps its distance, casts 3 slow homing orbs; teleports 450–600 px away when you get close|Cut the orbs (swings deflect them), catch it after a teleport, pin it|
|Chainwarden|150|ground shield|Tower shield always faces you: blocks arrows and spells from the front, melee only scratches through (25 %). Flail swing with ~190 px reach|Hit from behind or above; blunt hits and heavy knockback break the shield (it staggers, then fights faster without it)|
|Gargoyle|190|flying, big|A big, slow-swooping beast: an invulnerable statue on the ground until you come within ~330 px; circles, swoops, lands and turns back to stone for 2.6 s|Hit it while it flies|
|Watcher|60|flying ranged|Hovers ~420 px away at your height, traces an aim line (1.2 s), locks it (0.35 s), fires a beam stopped by terrain|Step out of the locked line, use terrain, stun it|
|Gatekeeper|200|summoner|Stays put and opens portals that let imps through (up to 4 of its own; they give no XP)|Kill it first|
|Hex Priest|85|support|Wards up to 3 nearby demons (soaks 30 damage), hexes you (lower jumps, no mana regen for 4 s)|Kill it first, knock it away|
|Ash Drake|150|flying bomber|Flies passes high over you (~400 px up), dropping fire bombs timed to land on you; each leaves a fire pool. Never comes down by itself, climbs away after 5 passes|Bows, crossbows, tridents, spells; freeze or stun it and it sinks within reach|
|Void Caller|110|ground mage|Drifts 280–620 px away, casts for 1.15 s (violet glow), then opens a gravity well where you stand for 3.6 s: drags you toward a biting core (slower than walking; a dash breaks free) while the others close in|Freeze or stun it to stop the cast; walk or dash out of the well|
|Mimic|220|ambush|Sleeps as a chest (the lid twitches now and then); wakes when you come close or hit it, hops after you and bites (18). Pays out the chest's skill point when it dies|Watch for the twitch; hit it before you reach for it|
|Pit Lord|1600|boss|Cleaver slam (direct hit + shockwaves), magma volleys; from 60 % HP also summons imps, below 30 % enraged (faster, double shockwaves, 5-ball volleys). No knockback, stuns cut to a third|Jump the shockwaves, keep moving under the volleys|

**Elemental weaknesses** (`DemonNature`): every demon is flesh, fire or void.

|Nature|Demons|Matchup|
|---|---|---|
|Flesh|Imp, Hellhound, Pit Brute, Chainwarden, Gargoyle, Mimic|none|
|Fire|Brimstone Hurler, Cinderbat, Hellfire Cultist, Ash Drake, Pit Lord|can't be burned (a puff of smoke), ice hits deal ×2, a freeze shatters for ×3 instead of ×2|
|Void|Watcher, Gatekeeper, Hex Priest, Void Caller|half knockback, fire hits and burn deal ×1.5|

Ice hits are Icicle, Ice Trident and anything that freezes or slows, plus weapons imbued with ice; fire hits are Spark, Fire Ball, anything that burns and weapons imbued with fire. A hit on a weakness throws a burst in the element's colour with a ringing crit sound.

The lob is solved analytically in `Ballistics.velocity` (discriminant check, minimum-time solution). **Do not modify that math unless you fully understand it.**

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
│   ├── Shortbow        fast draw (0.4 s), short range
│   ├── Bone Bow        arrows shatter into bone shards
│   └── War Bow         hold to draw (damage, speed, pierce up to 3)
├── Crossbow    Basic Crossbow · Quick Reload: -15 % crank time
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
