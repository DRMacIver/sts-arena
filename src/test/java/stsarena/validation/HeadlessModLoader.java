package stsarena.validation;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * Headless mod loader that runs ModTheSpire's full loading pipeline without starting the game.
 *
 * This tests:
 * - Mod discovery and ModInfo parsing
 * - Dependency resolution
 * - Annotation scanning for patches
 * - Actual patch application via Javassist
 *
 * Unlike the simpler HeadlessPatchTest, this actually runs ModTheSpire's code.
 */
public class HeadlessModLoader {

    private static final String[] REQUIRED_JARS = {
        "lib/desktop-1.0.jar",
        "lib/ModTheSpire.jar",
        "lib/BaseMod.jar",
        "lib/CommunicationMod.jar"
    };

    private boolean verbose = false;
    private boolean skipCompile = false;
    private boolean stsArenaOnly = false;

    public static void main(String[] args) throws Exception {
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

        loader.run();
    }

    public void run() throws Exception {
        // Configure java.util.logging to stderr
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }
        ConsoleHandler handler = new ConsoleHandler() {
            @Override
            protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
                super.setOutputStream(System.err);
            }
        };
        handler.setLevel(Level.ALL);
        rootLogger.addHandler(handler);

        // Configure Log4j2 (used by BaseMod) to stderr
        // Set system properties before Log4j2 initializes
        System.setProperty("log4j2.level", "DEBUG");
        System.setProperty("log4j.configurationFile", "");  // Disable file config

        System.out.println("=== STS Arena Headless Mod Loader ===\n");

        // Check prerequisites
        for (String jar : REQUIRED_JARS) {
            if (!new File(jar).exists()) {
                throw new FileNotFoundException("Required JAR not found: " + jar);
            }
            System.out.println("Found: " + jar);
        }

        // Check our mod JAR
        File modJar = new File("target/STSArena.jar");
        if (!modJar.exists()) {
            System.out.println("\nMod JAR not found, building...");
            ProcessBuilder pb = new ProcessBuilder("mvn", "package", "-DskipTests", "-q");
            pb.inheritIO();
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                throw new RuntimeException("Maven build failed");
            }
        }

        if (!modJar.exists()) {
            throw new FileNotFoundException("Mod JAR not found after build");
        }
        System.out.println("Found: " + modJar);

        System.out.println("\nLoading mods through ModTheSpire pipeline...\n");
        runModLoading();

        System.out.println("\n=== MOD LOADING PASSED ===");
    }

    private void runModLoading() throws Exception {
        // Set up classpath with all dependencies
        // Include test resources for log4j2.xml config
        URL[] urls = new URL[] {
            new File("src/test/resources").toURI().toURL(),
            new File("lib/desktop-1.0.jar").toURI().toURL(),
            new File("lib/ModTheSpire.jar").toURI().toURL(),
            new File("lib/BaseMod.jar").toURI().toURL(),
            new File("lib/CommunicationMod.jar").toURI().toURL(),
            new File("target/STSArena.jar").toURI().toURL()
        };

        URLClassLoader mainLoader = new URLClassLoader(urls, getClass().getClassLoader());

        try {
            // Step 1: Load ModTheSpire's Loader class
            System.out.println("Step 1: Loading ModTheSpire classes...");
            Class<?> loaderClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.Loader");
            Class<?> modInfoClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.ModInfo");

            // Enable debug logging
            Field debugField = loaderClass.getDeclaredField("DEBUG");
            debugField.setAccessible(true);
            debugField.set(null, true);
            System.out.println("  Debug logging enabled");

            // Initialize MTS version
            Method loadMTSVersion = loaderClass.getDeclaredMethod("loadMTSVersion");
            loadMTSVersion.setAccessible(true);
            loadMTSVersion.invoke(null);
            System.out.println("  ModTheSpire version loaded");

            // Step 2: Read mod info
            System.out.println("\nStep 2: Reading mod info...");
            Method readModInfo = modInfoClass.getDeclaredMethod("ReadModInfo", File.class);

            Object baseMod = readModInfo.invoke(null, new File("lib/BaseMod.jar"));
            Object commMod = readModInfo.invoke(null, new File("lib/CommunicationMod.jar"));
            Object stsArena = readModInfo.invoke(null, new File("target/STSArena.jar"));

            if (baseMod == null) {
                throw new RuntimeException("Failed to read BaseMod.jar ModInfo");
            }
            if (commMod == null) {
                throw new RuntimeException("Failed to read CommunicationMod.jar ModInfo");
            }
            if (stsArena == null) {
                throw new RuntimeException("Failed to read STSArena.jar ModInfo");
            }

            printModInfo(modInfoClass, baseMod, "BaseMod");
            printModInfo(modInfoClass, commMod, "CommunicationMod");
            printModInfo(modInfoClass, stsArena, "STSArena");

            // Step 3: Set up class pool with ModTheSpire
            System.out.println("\nStep 3: Setting up class pool...");
            Class<?> mtsClassPoolClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.MTSClassPool");
            Class<?> mtsClassLoaderClass = mainLoader.loadClass("com.evacipated.cardcrawl.modthespire.MTSClassLoader");

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
                System.out.println("  (STSArena only mode - skipping other mod patches)");
                modInfoArray = Array.newInstance(modInfoClass, 1);
                Array.set(modInfoArray, 0, stsArena);
            } else {
                modInfoArray = Array.newInstance(modInfoClass, 3);
                Array.set(modInfoArray, 0, baseMod);
                Array.set(modInfoArray, 1, commMod);
                Array.set(modInfoArray, 2, stsArena);
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
            System.out.flush();
            Class<?> classPoolClass = mainLoader.loadClass("javassist.ClassPool");
            Method injectPatches = patcherClass.getDeclaredMethod("injectPatches",
                ClassLoader.class, classPoolClass, List.class);
            injectPatches.invoke(null, mtsLoader, pool, patchSets);
            System.out.println("  Patches injected successfully");

            // Step 6: Finalize patches
            System.out.println("\nStep 6: Finalizing patches...");
            Method finalizePatches = patcherClass.getDeclaredMethod("finalizePatches", ClassLoader.class);
            finalizePatches.invoke(null, mtsLoader);
            System.out.println("  Patches finalized");

            // Step 7: Compile patched classes
            if (skipCompile) {
                System.out.println("\nStep 7: Compiling patched classes... SKIPPED (--skip-compile)");
            } else {
                System.out.println("\nStep 7: Compiling patched classes...");
                Method compilePatches = patcherClass.getDeclaredMethod("compilePatches",
                    mtsClassLoaderClass, mtsClassPoolClass);
                compilePatches.invoke(null, mtsLoader, pool);
                System.out.println("  Classes compiled");
            }

            // Step 8: Test that we can load a patched class
            if (skipCompile) {
                System.out.println("\nStep 8: Testing patched class loading... SKIPPED (depends on Step 7)");
            } else {
                System.out.println("\nStep 8: Testing patched class loading...");
                Class<?> menuButtonClass = Class.forName(
                    "com.megacrit.cardcrawl.screens.mainMenu.MenuButton",
                    true,
                    (ClassLoader) mtsLoader);
                System.out.println("  Loaded MenuButton class: " + menuButtonClass.getName());
                System.out.println("  Class loader: " + menuButtonClass.getClassLoader().getClass().getSimpleName());
            }

            // Step 9: Initialize mods (runs @SpireInitializer methods - this is where logging happens)
            if (skipCompile) {
                System.out.println("\nStep 9: Initializing mods... SKIPPED (depends on Step 7)");
            } else {
                System.out.println("\nStep 9: Initializing mods...");
                System.out.flush();
                Method initializeMods = patcherClass.getDeclaredMethod("initializeMods", ClassLoader.class);
                initializeMods.invoke(null, mtsLoader);
                System.out.println("  Mods initialized");
            }

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
}
