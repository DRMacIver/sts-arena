# STS Arena

A Slay the Spire mod that adds an Arena mode for practicing fights with custom loadouts.

## Features

### Arena Mode
Fight any encounter in the game with a randomly generated or saved loadout. Access Arena Mode from the main menu.

**Loadout Selection:**
- **New Random Loadout** - Generates a random character with random deck, relics, HP, and ascension level (0-20)
- **Saved Loadouts** - Replay loadouts from previous runs (both arena and normal runs)

**Encounter Selection:**
- Choose any encounter from Acts 1-4, organized by act and type (normal, elite, boss)
- Select "Random" for a random encounter
- Encounters you've beaten with the current loadout show in green (bold)
- Encounters you've lost show in red (bold)

### Auto-Save from Normal Runs
When you complete a normal run (victory or defeat), your loadout is automatically saved to the arena. This lets you replay your favorite builds against any encounter.

- **Victory saves** capture your final state
- **Defeat saves** capture your state *before* the fatal fight (HP and potions you had going in)
- Loadout names include character, ascension, floor, outcome, and timestamp
  - Example: "Ironclad A15 F51 Victory (01-01 20:47)"

### Arena History
View your arena fight history from the main menu, including:
- Win/loss statistics
- Recent fights with loadout, encounter, outcome, HP, and date
- **Replay button** - Instantly replay any previous fight with the same loadout (new seed)

### Full State Preservation
Loadouts preserve your complete build:
- Character class
- Full deck (with upgrades)
- All relics
- All potions
- Current and max HP
- Ascension level

## Installation

1. Install [ModTheSpire](https://github.com/kiooeht/ModTheSpire) and [BaseMod](https://github.com/daviscook477/BaseMod)
2. Download `STSArena-0.1.0.jar` from releases
3. Place the JAR in your `SlayTheSpire/mods/` folder
4. Launch the game with ModTheSpire and enable STS Arena

## Usage

1. From the main menu, click **Arena Mode**
2. Select a loadout (new random or a saved one)
3. Select an encounter to fight
4. After the fight, you're returned to encounter selection to fight again

To view history and statistics, click **Arena History** from the main menu.

## Building from Source

Requires Java 8+ and Maven.

```bash
# Build the mod
mvn clean package

# Output JAR is in target/STSArena-0.1.0.jar
```

## Data Storage

Arena data (loadouts, fight history) is stored in a SQLite database at:
- **macOS:** `~/Library/Preferences/ModTheSpire/stsarena/arena.db`
- **Windows:** `%LOCALAPPDATA%/ModTheSpire/stsarena/arena.db`
- **Linux:** `~/.config/ModTheSpire/stsarena/arena.db`

## License

MIT
