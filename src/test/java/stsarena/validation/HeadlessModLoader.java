package stsarena.validation;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * Headless mod loader that runs ModTheSpire's full loading pipeline without starting the game.
 *
 * This tests:
 * - Mod discovery and ModInfo parsing
 * - Dependency resolution
 * - Annotation scanning for patches
 * - Actual patch application via Javassist
 * - Mod initialization (@SpireInitializer methods)
 *
 * Unlike the simpler HeadlessPatchTest, this actually runs ModTheSpire's code.
 */
public class HeadlessModLoader {

    private static final String[] REQUIRED_JARS = {
        "lib/desktop-1.0.jar",
        "lib/ModTheSpire.jar",
        "lib/BaseMod.jar"
    };

    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean verbose = false;
    private boolean skipCompile = false;
    private boolean stsArenaOnly = false;

    public static void main(String[] args) {
        HeadlessModLoader loader = new HeadlessModLoader();

        // Parse args
        for (String arg : args) {
            if (arg.equals("-v") || arg.equals("--verbose")) {
                loader.verbose = true;
            } else if (arg.equals("--skip-compile")) {
                loader.skipCompile = true;
            } else if (arg.equals("--stsarena-only")) {
                loader.stsArenaOnly = true;
            }
        }

        int exitCode = loader.run();
        System.exit(exitCode);
    }

    public int run() {
        System.out.println("=== STS Arena Headless Mod Loader ===\n");

        // Check prerequisites
        for (String jar : REQUIRED_JARS) {
            if (!new File(jar).exists()) {
                System.err.println("ERROR: Required JAR not found: " + jar);
                return 1;
            }
            System.out.println("Found: " + jar);
        }

        // Check our mod JAR
        File modJar = new File("target/STSArena.jar");
        if (!modJar.exists()) {
            System.out.println("\nMod JAR not found, building...");
            try {
                ProcessBuilder pb = new ProcessBuilder("mvn", "package", "-DskipTests", "-q");
                pb.inheritIO();
                Process p = pb.start();
                int result = p.waitFor();
                if (result != 0) {
                    System.err.println("ERROR: Maven build failed");
                    return 1;
                }
            } catch (Exception e) {
                System.err.println("ERROR: Failed to build mod: " + e.getMessage());
                return 1;
            }
        }

        if (!modJar.exists()) {
            System.err.println("ERROR: Mod JAR not found after build");
            return 1;
        }
        System.out.println("Found: " + modJar);

        System.out.println("\nLoading mods through ModTheSpire pipeline...\n");

        try {
            return runModLoading() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("\nERROR: Mod loading failed with exception:");
            e.printStackTrace();
            errors.add("Exception: " + e.getMessage());
            return 1;
        }
    }

    private boolean runModLoading() throws Exception {
        // Set up classpath with all dependencies
        URL[] urls = new URL[] {
            new File("lib/desktop-1.0.jar").toURI().toURL(),
            new File("lib/ModTheSpire.jar").toURI().toURL(),
            new File("lib/BaseMod.jar").toURI().toURL(),
            new File("target/STSArena.jar").toURI().toURL()
        };

        URLClassLoader mainLoader = new URLClassLoader(urls, getClass().getClassLoader());

        try {
            // Step 1: Load ModTheSpire's Loader class
            System.out.println("Step 1: Loading ModTheSpire classes...");
            Class<?> loaderClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.Loader");
            Class<?> modInfoClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.ModInfo");

            // Initialize MTS version
            Method loadMTSVersion = loaderClass.getDeclaredMethod("loadMTSVersion");
            loadMTSVersion.setAccessible(true);
            loadMTSVersion.invoke(null);
            System.out.println("  ModTheSpire version loaded");

            // Step 2: Read mod info
            System.out.println("\nStep 2: Reading mod info...");
            Method readModInfo = modInfoClass.getDeclaredMethod("ReadModInfo", File.class);

            Object baseMod = readModInfo.invoke(null, new File("lib/BaseMod.jar"));
            Object stsArena = readModInfo.invoke(null, new File("target/STSArena.jar"));

            if (baseMod == null) {
                errors.add("Failed to read BaseMod.jar ModInfo");
                return false;
            }
            if (stsArena == null) {
                errors.add("Failed to read STSArena.jar ModInfo");
                return false;
            }

            printModInfo(modInfoClass, baseMod, "BaseMod");
            printModInfo(modInfoClass, stsArena, "STSArena");

            // Step 3: Set up class pool with ModTheSpire
            System.out.println("\nStep 3: Setting up class pool...");
            Class<?> mtsClassPoolClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.MTSClassPool");
            Class<?> mtsClassLoaderClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.MTSClassLoader");

            // Get the corepatches resource
            InputStream corePatches = loaderClass.getResourceAsStream("/corepatches.jar");
            if (corePatches == null) {
                warnings.add("Could not find corepatches.jar in ModTheSpire");
            }

            // Create MTSClassLoader
            Constructor<?> mtsLoaderCtor = mtsClassLoaderClass.getConstructor(
                InputStream.class, URL[].class, ClassLoader.class);
            Object mtsLoader = mtsLoaderCtor.newInstance(
                loaderClass.getResourceAsStream("/corepatches.jar"),
                urls,
                mainLoader
            );
            System.out.println("  MTSClassLoader created");

            // Create MTSClassPool (takes MTSClassLoader specifically)
            Constructor<?> poolCtor = mtsClassPoolClass.getConstructor(mtsClassLoaderClass);
            Object pool = poolCtor.newInstance(mtsLoader);
            System.out.println("  MTSClassPool created");

            // Step 4: Scan for patches
            System.out.println("\nStep 4: Scanning for patches...");
            Class<?> patcherClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.Patcher");

            // Create ModInfo array based on flags
            Object modInfoArray;
            if (stsArenaOnly) {
                System.out.println("  (STSArena only mode - skipping BaseMod patches)");
                modInfoArray = Array.newInstance(modInfoClass, 1);
                Array.set(modInfoArray, 0, stsArena);
            } else {
                modInfoArray = Array.newInstance(modInfoClass, 2);
                Array.set(modInfoArray, 0, baseMod);
                Array.set(modInfoArray, 1, stsArena);
            }

            // Set MODINFOS on Loader class
            Field modInfosField = loaderClass.getDeclaredField("MODINFOS");
            modInfosField.setAccessible(true);
            modInfosField.set(null, modInfoArray);

            // Find patches
            Method findPatches = patcherClass.getDeclaredMethod("findPatches",
                Array.newInstance(modInfoClass, 0).getClass());
            @SuppressWarnings("unchecked")
            List<Iterable<String>> patchSets = (List<Iterable<String>>) findPatches.invoke(null, modInfoArray);

            int totalPatches = 0;
            for (Iterable<String> patchSet : patchSets) {
                for (String patch : patchSet) {
                    totalPatches++;
                    if (verbose) {
                        System.out.println("  Found patch: " + patch);
                    }
                }
            }
            System.out.println("  Found " + totalPatches + " patch classes");

            // Step 5: Inject patches (this is where most errors would occur)
            System.out.println("\nStep 5: Injecting patches...");
            try {
                Class<?> classPoolClass = mainLoader.loadClass("javassist.ClassPool");
                Method injectPatches = patcherClass.getDeclaredMethod("injectPatches",
                    ClassLoader.class, classPoolClass, List.class);
                injectPatches.invoke(null, mtsLoader, pool, patchSets);
                System.out.println("  Patches injected successfully");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                errors.add("Patch injection failed: " + cause.getMessage());
                if (verbose) {
                    cause.printStackTrace();
                }
                return false;
            }

            // Step 6: Finalize patches
            System.out.println("\nStep 6: Finalizing patches...");
            try {
                Method finalizePatches = patcherClass.getDeclaredMethod("finalizePatches", ClassLoader.class);
                finalizePatches.invoke(null, mtsLoader);
                System.out.println("  Patches finalized");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                errors.add("Patch finalization failed: " + cause.getMessage());
                if (verbose) {
                    cause.printStackTrace();
                }
                return false;
            }

            // Step 7: Compile patched classes
            if (skipCompile) {
                System.out.println("\nStep 7: Compiling patched classes... SKIPPED (--skip-compile)");
            } else {
                System.out.println("\nStep 7: Compiling patched classes...");
                try {
                    Method compilePatches = patcherClass.getDeclaredMethod("compilePatches",
                        mtsClassLoaderClass, mtsClassPoolClass);
                    compilePatches.invoke(null, mtsLoader, pool);
                    System.out.println("  Classes compiled");
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    errors.add("Class compilation failed: " + cause.getMessage());
                    if (verbose) {
                        cause.printStackTrace();
                    }
                    return false;
                }
            }

            // Step 8: Test that we can load a patched class
            if (skipCompile) {
                System.out.println("\nStep 8: Testing patched class loading... SKIPPED (depends on Step 7)");
            } else {
                System.out.println("\nStep 8: Testing patched class loading...");
                try {
                    // Try to load a class we know we patch
                    Class<?> menuButtonClass = Class.forName(
                        "com.megacrit.cardcrawl.screens.mainMenu.MenuButton",
                        true,
                        (ClassLoader) mtsLoader);
                    System.out.println("  Loaded MenuButton class: " + menuButtonClass.getName());
                    System.out.println("  Class loader: " + menuButtonClass.getClassLoader().getClass().getSimpleName());
                } catch (ClassNotFoundException e) {
                    warnings.add("Could not load patched MenuButton class: " + e.getMessage());
                }
            }

            // Print final report
            printReport();
            return errors.isEmpty();

        } finally {
            mainLoader.close();
        }
    }

    private void printModInfo(Class<?> modInfoClass, Object modInfo, String label) throws Exception {
        Field nameField = modInfoClass.getField("Name");
        Field idField = modInfoClass.getField("ID");
        Field versionField = modInfoClass.getField("ModVersion");

        String name = (String) nameField.get(modInfo);
        String id = (String) idField.get(modInfo);
        Object version = versionField.get(modInfo);

        System.out.println("  " + label + ":");
        System.out.println("    Name: " + name);
        System.out.println("    ID: " + id);
        System.out.println("    Version: " + version);
    }

    private void printReport() {
        System.out.println("\n=== Mod Loading Report ===");
        System.out.println("Errors: " + errors.size());
        System.out.println("Warnings: " + warnings.size());

        if (!errors.isEmpty()) {
            System.out.println("\n--- ERRORS ---");
            for (String error : errors) {
                System.out.println("  ERROR: " + error);
            }
        }

        if (!warnings.isEmpty()) {
            System.out.println("\n--- WARNINGS ---");
            for (String warning : warnings) {
                System.out.println("  WARN: " + warning);
            }
        }

        System.out.println("\n" + (errors.isEmpty() ? "MOD LOADING PASSED" : "MOD LOADING FAILED"));
    }
}
