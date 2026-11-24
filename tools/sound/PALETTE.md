# Sound palette: dark and heavy

FlowState is a hellish action platformer: a slime of tar and embers fights demons in a burning abyss. Every sound
should feel like it happens **in a huge dark stone cavern**, and every hit should feel like it has **mass**.
This file is the brief for all sound groups (`sounds/*.py`); follow it so the groups sound like one game.

## Rules
1. **Low and thick, not thin and bright.** Pitch things down (often an octave), use `lowpass2` (12 dB/oct) instead of
   leaving the top open, and keep most energy under ~2 kHz. High frequencies only as a short transient (a crack, a
   blade edge, grit) or where the sound *is* a hiss (acid, steam). Check with `analyze.py`: high share well under
   ~15% for anything that isn't meant to hiss.
2. **Weight comes from three layers:** a sub drop (`sub`, 30-60 Hz), a body/punch (80-250 Hz) and saturation
   (`saturate`) so the low end has harmonics. Laptop speakers can't play below ~80 Hz: the 80-250 Hz body and the
   saturation are what make it heavy there, the sub is a bonus on headphones. `impact()` in common.py does all three.
3. **One shared room.** Put most sounds through `cavern(sig, wet, decay)` (a big dark stone hall). Short wet
   (0.15-0.3) for gameplay sounds, long and wet (0.4+) for bosses, ambience, deaths, big rewards. **No reverb**, or
   very little, on sounds that repeat fast (steps, hover, ticks, crank, flow_ready): tails would turn to mud.
4. **No cheerful music.** No major arpeggios, no sparkly chimes, no cartoon boings/bloops (fast vibrato chirps,
   rising sine "pews"). Rewards and confirmations (crit, perfect, level up, flow, pickups, skill learn) become dark
   and *satisfying*: low tolls (`toll`), minor thirds, fifths and tritones, phrygian semitones, a swelling choir
   (`stack` through `formant`), a heavy resonant hit. Ominous ritual, not a fanfare. They must still feel rewarding.
5. **Organic and occult.** Creatures get throats: buzzy sources (`stack`, saw, noise) through `formant` with
   `shift < 1` for big ones, `resample(sig, 0.5-0.8)` to make anything larger. Magic sounds like dark power (deep
   hum, roaring furnace, cracking glacier, whispering void), not like a sci-fi laser.
6. **Readability stays.** Telegraphs (enemy windups, charges, boss attacks) must stay clearly audible and distinct
   from each other: give each a recognizable shape in the 300-2000 Hz band. Darker is not muddier.
7. **Variants for repeats.** Sounds that fire many times in a row (hits, swings, steps, impacts, enemy deaths)
   should use `@sound(variants=3)`: the function takes `v` and changes seeds/pitch a little per variant; the game
   picks one at random. Don't change the meaning between variants, only the details.

## Materials
- **Player slime (tar & ember):** viscous, heavy, wet gulps and slaps, molten sizzle and ember crackle. Low, not squeaky.
- **Steel and iron weapons:** heavy low whooshes, dark inharmonic metal ring (low `bell`/`toll` partials), blood
  and flesh thuds under every hit.
- **Fire:** furnace roar with sub, crackle low in the mix. **Ice:** groaning, cracking glacier, not tinkling glass.
  **Void/arcane:** deep pulsing hum, inward swells (`reverse`), whispers. **Poison/acid:** thick bubbling, hiss.
- **Demons:** throats, growls, bone and chitin, low and big. **Bosses:** enormous, layered, long tails.
- **UI:** stone grinding, iron latches, low muted tolls. Hover is a soft, short, dark tick.
- **Ambience:** deep layered drones in minor, distant rumbles, wind howl, faraway chains, groans, drips. Slow.

## Constraints
- **Keep every sound name** (they are `Sfx` enum constants in `src/main/java/at/htl/flowstate/Sfx.java`).
- **Keep the build-up length** of timed sounds within about 5%: the code scales their playback rate to a gameplay
  timer. `charge_weapon` 1.5 s, `charge_magic` 1.5 s, `bow_draw` 0.9 s, `watcher_charge` 1.5 s, `cultist_cast`
  0.85 s, `void_cast` 1.15 s, `well_hum` one 0.95 s beat. Loops (`@loop`) must loop seamlessly (`seamless()`, and
  any periodic modulation must divide the loop length).
- **Call sites pitch-shift some sounds** (`Sfx.HYDRA_ROAR.at(pos, 1, 0.5)` plays it an octave down). Check with
  `grep -rn "Sfx.NAME" src/main/java` before making a sound very low, or it ends up subsonic.
- Each file is peak-normalized: loudness in game is the volume in `Sfx.java`. Darker sounds read quieter at the
  same peak, so new volumes are part of the redesign.
- Stdlib-only Python, generation of a whole group should stay under ~2 minutes.

## Tools
- `python tools/sound/generate_all.py --group <group>` (or sound names) writes the WAVs.
- `python tools/sound/analyze.py --group <group>` prints length, rms, sub/low/mid/high energy shares and a rough
  centroid. Use it to check every sound against rules 1 and 2.
