# STS Arena - Claude Development Guide

## Project Overview

STS Arena is a Slay the Spire mod that enables "arena" style gameplay - isolated single fights separate from the normal game run. This allows players to practice specific encounters with custom decks.

## Tech Stack

- **Language**: Java 8 (required by ModTheSpire - do NOT use Java 9+)
- **Build System**: Maven
- **Mod Framework**: BaseMod (modding API) + ModTheSpire (mod loader)
- **Database**: SQLite (bundled via maven-shade-plugin)
- **Game**: Slay the Spire

## Project Structure

```
sts-arena/
├── pom.xml                          # Maven build configuration
├── lib/                             # Dependencies (not committed)
│   ├── desktop-1.0.jar             # Slay the Spire game jar
│   ├── ModTheSpire.jar             # Mod loader
│   └── BaseMod.jar                 # Modding API
├── src/main/java/stsarena/
│   ├── STSArena.java               # Main mod entry point
│   ├── arena/                      # Arena fight logic
│   │   ├── ArenaRunner.java        # Starts arena fights
│   │   └── RandomLoadoutGenerator.java  # Generates random decks/relics
│   ├── data/                       # Data layer
│   │   ├── ArenaDatabase.java      # SQLite connection & schema
│   │   ├── Loadout.java            # Saved deck/relic configurations
│   │   └── FightRecord.java        # Historical fight records
│   └── patches/                    # SpirePatch classes
│       ├── ArenaMenuButton.java    # Menu button enum & handlers
│       └── MainMenuArenaPatch.java # Adds button to main menu
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

## macOS Paths

Steam library (default):
```
~/Library/Application Support/Steam/steamapps/
```

Game JAR location:
```
.../common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/desktop-1.0.jar
```

Mods folder:
```
.../common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/mods/
```

Workshop mods:
```
.../workshop/content/646570/1605060445/ModTheSpire.jar
.../workshop/content/646570/1605833019/BaseMod.jar
```

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
- `OptionsPanel` - Pause menu settings panel
- `AbandonRunButton` - The abandon run button in pause menu

## UI Positioning Notes

### Key Settings Fields
- `Settings.WIDTH`, `Settings.HEIGHT` - Screen dimensions
- `Settings.scale` - General UI scale factor
- `Settings.xScale`, `Settings.yScale` - Directional scale factors
- `Settings.OPTION_Y` = `HEIGHT / 2.0f - 32.0f * yScale` - Options panel reference Y

### OptionsPanel Layout
- Panel center Y: `Settings.HEIGHT / 2.0f - 64.0f * Settings.scale`
- Panel top boundary: approximately 250 pixels above center (scaled)
- AbandonRunButton original position: approximately `OPTION_Y + 340 * scale`

### Button Dimensions (OPTION_ABANDON style)
- Image dimensions: W=440, H=100 (raw pixels)
- Hitbox dimensions: 340x70 (scaled)
- **Visual height is ~70 pixels** (hitbox height), not 100 - the image has padding
- When positioning buttons to touch, use the visual/hitbox height (70), not image height (100)

### Button Positioning Pattern
```java
// Button positions are at CENTER, not edge
// To place button with bottom edge at Y:
float buttonCenterY = Y + buttonHeight / 2.0f;

// To stack buttons touching (use visual height for spacing):
float button1Y = baseY;
float button2Y = button1Y + visualButtonHeight;  // 70 * scale, not 100
```

### Detecting Run Abandonment
- `CardCrawlGame.startOver` is `true` when player confirms abandoning a run
- Use this to skip saving loadouts or showing arena retry buttons on abandon

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

## Data Layer

SQLite database stored at `~/Library/Preferences/ModTheSpire/stsarena/arena.db` (macOS).

### Tables

**loadouts** - Saved deck/relic/potion configurations
- `id`, `name`, `character_class`, `ascension_level`
- `max_hp`, `current_hp`, `gold`
- `deck_json`, `relics_json`, `potions_json` (JSON blobs)
- `created_at`, `updated_at` (timestamps in millis)

**fight_history** - Records of completed arena fights
- `id`, `loadout_id` (nullable FK), `encounter_id`, `encounter_name`
- `character_class`, `ascension_level`, `floor_num`
- `deck_json`, `relics_json`, `potions_json` (snapshot at fight time)
- `result` (WIN/LOSS), `turns_taken`, `damage_dealt`, `damage_taken`
- `fought_at` (timestamp)

### Usage

```java
// Save a loadout
Loadout loadout = new Loadout();
loadout.setName("My Heart Killer");
loadout.setCharacterClass("IRONCLAD");
loadout.setDeckJson(gson.toJson(cardIds));
loadout.save();

// Query fight history
List<FightRecord> recent = FightRecord.findRecent(10);
FightRecord.FightStats stats = FightRecord.getStatsForEncounter("TheHeart");
```

## Coding Standards

- Use Java 8 features (lambdas OK, but no var keyword from Java 10)
- Follow existing game code style where interfacing with it
- Log important events using the Logger
- Handle null cases defensively (game state can be unpredictable)

## Testing

**IMPORTANT: Run ALL relevant tests before committing changes!**

### Test Types

| Test Type | Command | What It Tests |
|-----------|---------|---------------|
| Unit Tests | `mvn test` | Database, LoadoutConfig, patch validation |
| Headless Mod Load (fast) | `./scripts/headless-mod-load-test.sh --fast` | Patch discovery and injection |
| Headless Mod Load (full) | `./scripts/headless-mod-load-test.sh --stsarena-only` | Full patch compilation |
| Acceptance Tests | `./scripts/run_agent.py acceptance_tests/` | Full game integration with CommunicationMod |

### When to Run Each

- **After ANY Java changes**: `mvn test && ./scripts/headless-mod-load-test.sh --fast`
- **After changing patches**: Full headless test: `./scripts/headless-mod-load-test.sh --stsarena-only`
- **After changing arena/combat logic**: Acceptance tests (requires game running)

### Headless Test Notes

The headless mod load test runs ModTheSpire's patching pipeline without starting the game:
- `--fast` skips patch compilation (quick validation)
- `--stsarena-only` tests only STSArena patches (compiles successfully)
- Without flags: tests all mods including BaseMod (may fail due to external mod issues)

**IMPORTANT**: When writing patches, use `cls = "fully.qualified.ClassName"` instead of `clz = ClassName.class`. The `clz` form forces class loading during annotation resolution, causing LinkageError during headless patch compilation.

### Test Locations

```
src/test/java/stsarena/
├── arena/
│   ├── ArenaIntegrationTest.java    # Arena system tests
│   └── LoadoutConfigTest.java       # Loadout config unit tests
├── data/
│   └── ArenaDatabaseTest.java       # SQLite database tests
└── validation/
    ├── HeadlessModLoader.java       # Tests mod loads without game
    ├── HeadlessPatchTest.java       # Validates patch targets exist
    └── PatchValidator.java          # Checks patch annotations

acceptance_tests/
├── conftest.py                      # Pytest fixtures and helpers
├── test_user_stories.py             # User story acceptance tests
└── test_hypothesis_stateful.py      # Property-based Hypothesis tests
```

### Key Testing Considerations

1. **CommunicationMod is optional** - The mod must load without it. Use `Class.forName()` to check before loading command classes.
2. **CardLibrary/RelicLibrary may not be loaded** - Catch `NoClassDefFoundError` when using game libraries.
3. **Use `wait_for_stable()` not `time.sleep()`** - For acceptance tests, wait for game state, not arbitrary time.

## Debugging Mod Problems

### Attitude and Approach

**You are the sole author of this codebase. Every bug is your bug. Every broken test is something you broke.**

- **Never explain away errors** - If something fails, fix it. Don't claim it "can't work" or "isn't important."
- **If the user says it worked before, it did** - Your job is to find what you broke, not explain why it's impossible.
- **Don't dig for spurious explanations** - When something breaks, identify the specific change that broke it.
- **Run the actual tests** - Don't assume tests pass. Run them and verify.
- **Use git bisect** - When a test breaks, use `git bisect run ./test-command` to find the breaking commit.

### Consulting Mod Sources

**Clone source repos from GitHub. Never decompile JARs when source is available.**

Key mod repositories:
- **ModTheSpire**: https://github.com/kiooeht/ModTheSpire
- **BaseMod**: https://github.com/daviscook477/BaseMod
- **CommunicationMod**: (in external/CommunicationMod submodule)

```bash
# Clone for reference (don't commit to project)
cd external && git clone --depth 1 https://github.com/kiooeht/ModTheSpire.git
```

Read the actual source to understand how mods work. The decompiled code is harder to read and may have decompilation artifacts.

### ClassLoader Issues

ModTheSpire uses a complex classloader hierarchy. Common issues:

**LinkageError: duplicate class definition**
- Caused by a class being loaded by multiple classloaders
- MTS uses TWO classloaders: `tmpPatchingLoader` (for patching) and `loader` (for compilation)
- If you're testing patching, mirror this architecture

**Classes loaded too early**
- Check script classpaths - game jars on JVM classpath get loaded by system classloader
- Game jars should be loaded dynamically via URLClassLoader so MTSClassLoader can patch first
- Use `cls = "fully.qualified.Name"` not `clz = ClassName.class` in patches (prevents early loading)

**Debugging classloader issues**:
```java
// Check which classloader loaded a class
System.out.println(someClass.getClassLoader().getClass().getSimpleName());
// MTSClassLoader = good (patched)
// AppClassLoader = bad (loaded before patching)
```

### Script and Classpath Problems

**Check script classpaths carefully:**
```bash
# Bad - game jar on JVM classpath, can't be patched
CLASSPATH="$CLASSPATH:lib/desktop-1.0.jar"

# Good - only loader classes on classpath, game jars loaded dynamically
CLASSPATH="target/test-classes:lib/ModTheSpire.jar"
```

**Check for alternative jar versions:**
```bash
ls -la lib/*.orig lib/*.new lib/*.backup 2>/dev/null
```

### HeadlessModLoader Architecture

The headless test must mirror MTS's two-classloader approach:

```java
// 1. tmpPatchingLoader - used during inject/finalize
Object tmpPatchingLoader = mtsLoaderCtor.newInstance(...);
pool = new MTSClassPool(tmpPatchingLoader);
injectPatches(tmpPatchingLoader, pool, patches);
finalizePatches(tmpPatchingLoader);

// 2. loader - fresh classloader for compilation (no classes pre-loaded)
Object loader = mtsLoaderCtor.newInstance(...);
compilePatches(loader, pool);  // Uses fresh loader!
```

### Common Debugging Workflow

1. **Reproduce the failure**: Run the exact failing command
2. **Check recent changes**: `git diff HEAD~5 --name-only`
3. **Isolate the issue**: Use flags like `--stsarena-only` to narrow scope
4. **Find breaking commit**: `git bisect run ./test-script.sh`
5. **Read upstream source**: Clone the mod repo and understand the expected behavior
6. **Fix and verify**: Run ALL relevant tests, not just the one that failed

# Agent Instructions

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>         # Complete work
bd sync               # Sync with git
```

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
