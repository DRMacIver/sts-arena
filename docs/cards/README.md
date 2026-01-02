# Slay the Spire Card Reference

This directory contains reference documentation for all cards in Slay the Spire, extracted from the decompiled game source code.

## Files

- [**watcher.md**](./watcher.md) - Watcher (Purple) cards
- [**ironclad.md**](./ironclad.md) - Ironclad (Red) cards
- [**silent.md**](./silent.md) - Silent (Green) cards
- [**defect.md**](./defect.md) - Defect (Blue) cards

## Important: Card ID Format

**Different characters use different ID formats:**

| Character | ID Format | Example |
|-----------|-----------|---------|
| **Watcher** | CamelCase, no spaces | `TalkToTheHand`, `FlurryOfBlows` |
| **Ironclad** | Spaces between words | `Battle Trance`, `Blood for Blood` |
| **Silent** | Spaces between words | `Blade Dance`, `Cloak And Dagger` |
| **Defect** | Spaces between words | `Ball Lightning`, `Cold Snap` |

### Starter Card IDs

| Character | Strike ID | Defend ID |
|-----------|-----------|-----------|
| Ironclad | `Strike_R` | `Defend_R` |
| Silent | `Strike_G` | `Defend_G` |
| Defect | `Strike_B` | `Defend_B` |
| Watcher | `Strike_P` | `Defend_P` |

## Watcher ID Gotchas

Watcher has several cards where the internal ID differs significantly from the display name:

| Display Name | Internal ID |
|--------------|-------------|
| Tranquility | `ClearTheMind` |
| Foresight | `Wireheading` |
| Rushdown | `Adaptation` |
| Pressure Points | `PathToVictory` |
| Simmering Fury | `Vengeance` |
| Fasting | `Fasting2` |

## Card Types

- **ATTACK** - Deal damage to enemies
- **SKILL** - Utility effects, block, draw, etc.
- **POWER** - Permanent effects that persist for the combat

## Common Tags

- `STRIKE` - Synergizes with Strike-related effects (Perfected Strike, etc.)
- `STARTER_STRIKE` - The basic Strike card
- `STARTER_DEFEND` - The basic Defend card
- `HEALING` - Cards that can restore HP
- `EMPTY` (Watcher) - Cards that exit to neutral stance

## Usage in LoadoutConfig

When adding cards to `LoadoutConfig.java`, always use the **internal card ID**, not the display name:

```java
// WRONG - uses display name
"Talk to the Hand"

// CORRECT - uses internal ID
"TalkToTheHand"
```

The game's `CardLibrary.getCard()` method requires the exact internal ID.
