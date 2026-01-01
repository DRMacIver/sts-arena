# STS Arena - Claude Development Guide

## Project Overview

STS Arena is a Slay the Spire mod that enables "arena" style gameplay - isolated single fights separate from the normal game run. This allows players to practice specific encounters with custom decks.

## Tech Stack

- **Language**: Java 8 (required by ModTheSpire - do NOT use Java 9+)
- **Build System**: Maven
- **Mod Framework**: BaseMod (modding API) + ModTheSpire (mod loader)
- **Game**: Slay the Spire

## Project Structure

```
sts-arena/
├── pom.xml                          # Maven build configuration
├── lib/                             # Dependencies (not committed)
│   ├── desktop-1.0.jar             # Slay the Spire game jar
│   ├── ModTheSpire.jar             # Mod loader
│   └── BaseMod.jar                 # Modding API
├── src/main/java/stsarena/         # Java source files
│   └── STSArena.java               # Main mod entry point
└── src/main/resources/
    ├── ModTheSpire.json            # Mod metadata for ModTheSpire
    └── stsarena/                   # Mod resources (images, localization)
```

## Key Files

- `STSArena.java` - Main mod class, annotated with `@SpireInitializer`
- `ModTheSpire.json` - Mod metadata (name, version, dependencies)
- `pom.xml` - Maven configuration with system-scoped dependencies

## Building

```bash
mvn package
```

The built JAR goes to `target/STSArena.jar` and is automatically copied to the game's mods folder.

## BaseMod Interfaces

Subscribe to these interfaces by implementing them and calling `BaseMod.subscribe(this)`:

- `PostInitializeSubscriber` - After game init, for UI setup
- `EditCardsSubscriber` - Add custom cards
- `EditRelicsSubscriber` - Add custom relics
- `EditStringsSubscriber` - Add localization
- `OnStartBattleSubscriber` - When combat begins
- `PostBattleSubscriber` - After combat ends

## SpirePatch Annotations

For modifying game code directly:

- `@SpirePatch` - Target a method to modify
- `@SpireInsertPatch` - Insert code at specific line
- `@SpirePrefixPatch` - Run before method
- `@SpirePostfixPatch` - Run after method

## Common Game Classes

Combat-related:
- `AbstractDungeon` - Current game state, player, monsters
- `AbstractMonster` - Enemy base class
- `AbstractCard` - Card base class
- `AbstractRelic` - Relic base class
- `AbstractRoom` - Room types (MonsterRoom, BossRoom, etc.)
- `MonsterGroup` - Group of enemies in a fight

UI-related:
- `AbstractScreen` - Base for custom screens
- `MainMenuScreen` - Main menu
- `CustomModeScreen` - Custom run configuration

## Development Tips

1. **Decompile the game**: Open `desktop-1.0.jar` in IntelliJ or use JD-GUI to see game source
2. **Check existing mods**: Many open-source mods on GitHub show patterns
3. **Use the dev console**: BaseMod provides a console (press ` in-game with BaseMod enabled)
4. **Test incrementally**: The mod hot-reloads when you restart the game

## Similar Mods for Reference

- **Practice Mode** (Steam Workshop) - Similar concept but unmaintained
- **Downfall** - Large mod with custom content, good architecture reference
- **StSLib** - Common utilities used by many mods

## Naming Conventions

- Package: `stsarena`
- Mod ID: `stsarena`
- Class prefix: `Arena` (e.g., `ArenaScreen`, `ArenaConfig`)
- Resource paths: `stsarena/images/`, `stsarena/localization/`

## Coding Standards

- Use Java 8 features (lambdas OK, but no var keyword from Java 10)
- Follow existing game code style where interfacing with it
- Log important events using the Logger
- Handle null cases defensively (game state can be unpredictable)
