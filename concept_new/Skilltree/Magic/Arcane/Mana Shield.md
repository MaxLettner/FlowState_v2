# Mana Shield

Sub-tree: [[Arcane]] · Slot type: **Toggle**

*Old: "Reduces damage taken for time active, continuous mana use"*

## Design
- While active, **70 % of every hit is taken from mana** (1 mana per 1 damage) instead of HP. The other 30 % hits HP.
- Small idle drain while active. Mana regen is paused.
- When mana reaches 0, the shield **breaks** and cannot be reactivated for 3 s.

## Starting values
| Idle drain | Absorb | Break cooldown |
|---|---|---|
| 1 mana/s | 70 % | 3 s |
