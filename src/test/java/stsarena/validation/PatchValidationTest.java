package stsarena.validation;

import org.junit.Test;
import org.junit.BeforeClass;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests that all patches in the stsarena.patches package are valid.
 * This test catches patch errors (wrong method names, missing classes, etc.)
 * before the mod is loaded into the game.
 */
public class PatchValidationTest {

    private static ClassLoader gameClassLoader;
    private static boolean gameJarAvailable = false;

    @BeforeClass
    public static void setUpClassLoader() {
        // Try to find the game JAR
        String[] possiblePaths = {
            "lib/desktop-1.0.jar",
            "../lib/desktop-1.0.jar",
            System.getProperty("user.home") + "/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/desktop-1.0.jar"
        };

        List<URL> urls = new ArrayList<>();

        for (String path : possiblePaths) {
            File gameJar = new File(path);
            if (gameJar.exists()) {
                try {
                    urls.add(gameJar.toURI().toURL());
                    gameJarAvailable = true;
                    System.out.println("Found game JAR at: " + gameJar.getAbsolutePath());
                    break;
                } catch (Exception e) {
                    System.err.println("Error loading game JAR: " + e.getMessage());
                }
            }
        }

        // Also add our compiled classes
        try {
            File targetClasses = new File("target/classes");
            if (targetClasses.exists()) {
                urls.add(targetClasses.toURI().toURL());
            }
        } catch (Exception e) {
            System.err.println("Error loading target classes: " + e.getMessage());
        }

        if (!urls.isEmpty()) {
            gameClassLoader = new URLClassLoader(urls.toArray(new URL[0]), PatchValidationTest.class.getClassLoader());
        } else {
            gameClassLoader = PatchValidationTest.class.getClassLoader();
        }
    }

    @Test
    public void testAllPatchesAreValid() {
        if (!gameJarAvailable) {
            System.out.println("WARNING: Game JAR not found. Skipping full patch validation.");
            System.out.println("To run full validation, ensure lib/desktop-1.0.jar exists.");
            return;
        }

        PatchValidator validator = new PatchValidator(gameClassLoader);

        // List of all patch classes to validate
        String[] patchClasses = {
            "stsarena.patches.ArenaDeathScreenButtonsPatch",
            "stsarena.patches.ArenaDefeatPatch",
            "stsarena.patches.ArenaMenuButton",
            "stsarena.patches.ArenaPauseButtonPatch",
            "stsarena.patches.ArenaPauseMenuPatch",
            "stsarena.patches.ArenaSkipRewardsPatch",
            "stsarena.patches.ArenaVictoryPatch",
            "stsarena.patches.ArenaVictoryScreenPatch",
            "stsarena.patches.BlockMenuButtonHoverPatch",
            "stsarena.patches.ClearArenaOnMainMenuPatch",
            "stsarena.patches.DisableArenaSavePatch",
            "stsarena.patches.MainMenuArenaPatch",
            "stsarena.patches.MainMenuUpdatePatch",
            "stsarena.patches.NormalDeathScreenButtonPatch",
            "stsarena.patches.NormalRunLoadoutSaver"
        };

        for (String className : patchClasses) {
            validator.validateClass(className);
        }

        validator.printReport();

        if (validator.hasErrors()) {
            fail("Patch validation failed with " + validator.getErrors().size() + " errors:\n" +
                 String.join("\n", validator.getErrors()));
        }
    }

    @Test
    public void testArenaSkipRewardsPatchTargets() {
        if (!gameJarAvailable) {
            System.out.println("WARNING: Game JAR not found. Skipping.");
            return;
        }

        // Specifically test the methods we're trying to patch
        try {
            Class<?> abstractMonster = gameClassLoader.loadClass("com.megacrit.cardcrawl.monsters.AbstractMonster");
            assertNotNull("AbstractMonster class should exist", abstractMonster);

            // Check for die method
            boolean hasDieMethod = false;
            for (java.lang.reflect.Method m : abstractMonster.getDeclaredMethods()) {
                if (m.getName().equals("die")) {
                    hasDieMethod = true;
                    System.out.println("Found die method: " + m);
                }
            }
            assertTrue("AbstractMonster should have die() method", hasDieMethod);

            // Check AbstractRoom methods
            Class<?> abstractRoom = gameClassLoader.loadClass("com.megacrit.cardcrawl.rooms.AbstractRoom");
            assertNotNull("AbstractRoom class should exist", abstractRoom);

            boolean hasAddGoldToRewards = false;
            boolean hasAddPotionToRewards = false;
            for (java.lang.reflect.Method m : abstractRoom.getDeclaredMethods()) {
                if (m.getName().equals("addGoldToRewards")) {
                    hasAddGoldToRewards = true;
                    System.out.println("Found addGoldToRewards method: " + m);
                }
                if (m.getName().equals("addPotionToRewards")) {
                    hasAddPotionToRewards = true;
                    System.out.println("Found addPotionToRewards method: " + m);
                }
            }
            assertTrue("AbstractRoom should have addGoldToRewards() method", hasAddGoldToRewards);
            assertTrue("AbstractRoom should have addPotionToRewards() method", hasAddPotionToRewards);

        } catch (ClassNotFoundException e) {
            fail("Could not load game class: " + e.getMessage());
        }
    }

    @Test
    public void testListAvailableMethodsOnAbstractDungeon() {
        if (!gameJarAvailable) {
            System.out.println("WARNING: Game JAR not found. Skipping.");
            return;
        }

        // This test helps us discover available methods for patching
        try {
            Class<?> abstractDungeon = gameClassLoader.loadClass("com.megacrit.cardcrawl.dungeons.AbstractDungeon");
            System.out.println("Available methods on AbstractDungeon:");
            for (java.lang.reflect.Method m : abstractDungeon.getDeclaredMethods()) {
                // Filter to interesting methods
                String name = m.getName().toLowerCase();
                if (name.contains("reward") || name.contains("combat") || name.contains("screen")) {
                    System.out.println("  " + m.getName() + "(" +
                        java.util.Arrays.toString(m.getParameterTypes()) + ")");
                }
            }
        } catch (ClassNotFoundException e) {
            fail("Could not load AbstractDungeon: " + e.getMessage());
        }
    }
}
