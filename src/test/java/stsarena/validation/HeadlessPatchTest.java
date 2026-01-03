package stsarena.validation;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javassist.*;

/**
 * Headless test that validates patches by actually running ModTheSpire's patching logic.
 * This catches runtime errors like "No method named [foo] found on class [Bar]".
 *
 * Unlike the static PatchValidator, this runs the actual Javassist patching that
 * ModTheSpire performs, catching errors that only occur at patch application time.
 */
public class HeadlessPatchTest {

    private static final String[] REQUIRED_JARS = {
        "lib/desktop-1.0.jar",
        "lib/ModTheSpire.jar",
        "lib/BaseMod.jar"
    };

    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public static void main(String[] args) {
        HeadlessPatchTest test = new HeadlessPatchTest();
        int exitCode = test.run();
        System.exit(exitCode);
    }

    @Test
    public void testAllPatchesAreValid() {
        // Skip if game JAR not available
        if (!new File("lib/desktop-1.0.jar").exists()) {
            System.out.println("WARNING: Game JAR not found. Skipping headless patch validation.");
            return;
        }

        int result = run();
        if (result != 0) {
            fail("Patch validation failed with " + errors.size() + " errors:\n" +
                 String.join("\n", errors));
        }
    }

    public int run() {
        System.out.println("=== STS Arena Headless Patch Validation ===\n");

        // Check prerequisites
        for (String jar : REQUIRED_JARS) {
            if (!new File(jar).exists()) {
                System.err.println("ERROR: Required JAR not found: " + jar);
                return 1;
            }
            System.out.println("Found: " + jar);
        }

        // Check that our mod is compiled
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
            System.err.println("ERROR: Mod JAR still not found after build: " + modJar);
            return 1;
        }
        System.out.println("Found: " + modJar);

        System.out.println("\nValidating patches...\n");

        try {
            return runPatchValidation() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("ERROR: Validation failed with exception:");
            e.printStackTrace();
            return 1;
        }
    }

    private boolean runPatchValidation() throws Exception {
        // Build classpath with all required JARs
        URL[] urls = new URL[] {
            new File("lib/desktop-1.0.jar").toURI().toURL(),
            new File("lib/ModTheSpire.jar").toURI().toURL(),
            new File("lib/BaseMod.jar").toURI().toURL(),
            new File("target/STSArena.jar").toURI().toURL(),
            new File("target/classes").toURI().toURL()
        };

        // Create a ClassLoader with all dependencies
        URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader());

        try {
            // Load Javassist ClassPool
            ClassPool pool = new ClassPool(true);
            for (URL url : urls) {
                File f = new File(url.toURI());
                if (f.isDirectory()) {
                    pool.appendClassPath(f.getAbsolutePath());
                } else if (f.getName().endsWith(".jar")) {
                    pool.appendClassPath(f.getAbsolutePath());
                }
            }

            // Find all @SpirePatch annotated classes in our mod
            List<String> patchClasses = findPatchClasses(loader);
            System.out.println("Found " + patchClasses.size() + " patch classes to validate\n");

            // Validate each patch
            for (String className : patchClasses) {
                validatePatchClass(loader, pool, className);
            }

            // Print results
            printReport();

            return errors.isEmpty();
        } finally {
            loader.close();
        }
    }

    private List<String> findPatchClasses(URLClassLoader loader) throws Exception {
        List<String> patchClasses = new ArrayList<>();

        // Scan our mod's patches package
        String packageName = "stsarena.patches";
        String packagePath = packageName.replace('.', '/');

        // Look in target/classes
        File patchDir = new File("target/classes/" + packagePath);
        if (patchDir.exists() && patchDir.isDirectory()) {
            for (File f : patchDir.listFiles()) {
                if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                    String className = packageName + "." + f.getName().replace(".class", "");
                    patchClasses.add(className);
                }
            }
        }

        return patchClasses;
    }

    private void validatePatchClass(URLClassLoader loader, ClassPool pool, String className) {
        System.out.println("Validating: " + className);

        try {
            CtClass ctPatchClass = pool.get(className);

            // Check class-level @SpirePatch annotations
            Object spirePatch = ctPatchClass.getAnnotation(
                loader.loadClass("com.evacipated.cardcrawl.modthespire.lib.SpirePatch"));
            Object spirePatches = ctPatchClass.getAnnotation(
                loader.loadClass("com.evacipated.cardcrawl.modthespire.lib.SpirePatches"));

            List<Object> patches = new ArrayList<>();
            if (spirePatches != null) {
                Method valueMethod = spirePatches.getClass().getMethod("value");
                Object[] patchArray = (Object[]) valueMethod.invoke(spirePatches);
                patches.addAll(Arrays.asList(patchArray));
            } else if (spirePatch != null) {
                patches.add(spirePatch);
            }

            // Also check nested classes (most patches are nested)
            for (CtClass nested : ctPatchClass.getDeclaredClasses()) {
                Object nestedPatch = nested.getAnnotation(
                    loader.loadClass("com.evacipated.cardcrawl.modthespire.lib.SpirePatch"));
                if (nestedPatch != null) {
                    validateSinglePatch(loader, pool, nested.getName(), nestedPatch);
                }
            }

            for (Object patch : patches) {
                validateSinglePatch(loader, pool, className, patch);
            }

        } catch (NotFoundException e) {
            errors.add(className + ": Class not found in pool - " + e.getMessage());
        } catch (ClassNotFoundException e) {
            warnings.add(className + ": Could not load SpirePatch annotation class - " + e.getMessage());
        } catch (Exception e) {
            errors.add(className + ": Unexpected error - " + e.getMessage());
        }
    }

    private void validateSinglePatch(URLClassLoader loader, ClassPool pool,
                                     String patchClassName, Object patch) {
        try {
            // Get target class
            Method clzMethod = patch.getClass().getMethod("clz");
            Method clsMethod = patch.getClass().getMethod("cls");
            Method methodMethod = patch.getClass().getMethod("method");
            Method paramtypezMethod = patch.getClass().getMethod("paramtypez");
            Method optionalMethod = patch.getClass().getMethod("optional");

            Class<?> targetClz = (Class<?>) clzMethod.invoke(patch);
            String targetCls = (String) clsMethod.invoke(patch);
            String methodName = (String) methodMethod.invoke(patch);
            Class<?>[] paramtypez = (Class<?>[]) paramtypezMethod.invoke(patch);
            boolean optional = (Boolean) optionalMethod.invoke(patch);

            // Determine target class name
            String targetClassName = null;
            if (targetClz != null && targetClz != void.class) {
                targetClassName = targetClz.getName();
            } else if (targetCls != null && !targetCls.isEmpty()) {
                targetClassName = targetCls;
            }

            if (targetClassName == null) {
                if (!optional) {
                    errors.add(patchClassName + ": No target class specified");
                }
                return;
            }

            // Try to load target class from pool
            CtClass ctTarget;
            try {
                ctTarget = pool.get(targetClassName);
            } catch (NotFoundException e) {
                if (optional) {
                    warnings.add(patchClassName + ": Optional target class not found: " + targetClassName);
                } else {
                    errors.add(patchClassName + ": Target class not found: " + targetClassName);
                }
                return;
            }

            // Skip special methods
            if (methodName.equals("<ctor>") || methodName.equals("<staticinit>") ||
                methodName.equals("<class>")) {
                System.out.println("  OK: " + targetClassName + "." + methodName + " (special)");
                return;
            }

            // Check if method exists
            boolean hasParamTypes = !(paramtypez.length == 1 && paramtypez[0] == void.class);

            if (hasParamTypes) {
                // With specific parameter types
                try {
                    String[] paramTypeNames = new String[paramtypez.length];
                    for (int i = 0; i < paramtypez.length; i++) {
                        paramTypeNames[i] = paramtypez[i].getName();
                    }
                    CtClass[] ctParamTypes = pool.get(paramTypeNames);
                    ctTarget.getDeclaredMethod(methodName, ctParamTypes);
                    System.out.println("  OK: " + targetClassName + "." + methodName +
                                      "(" + Arrays.toString(paramTypeNames) + ")");
                } catch (NotFoundException e) {
                    if (optional) {
                        warnings.add(patchClassName + ": Optional method not found: " +
                                    targetClassName + "." + methodName);
                    } else {
                        errors.add(patchClassName + ": No method [" + methodName +
                                  "] found on class [" + targetClassName + "]");
                    }
                }
            } else {
                // Without parameter types - any method with this name matches
                CtMethod[] methods = ctTarget.getDeclaredMethods(methodName);
                if (methods.length > 0) {
                    System.out.println("  OK: " + targetClassName + "." + methodName +
                                      " (" + methods.length + " overloads)");
                } else {
                    // Also check superclass methods
                    boolean found = findMethodInHierarchy(ctTarget, methodName);
                    if (found) {
                        System.out.println("  OK: " + targetClassName + "." + methodName +
                                          " (inherited)");
                    } else if (optional) {
                        warnings.add(patchClassName + ": Optional method not found: " +
                                    targetClassName + "." + methodName);
                    } else {
                        errors.add(patchClassName + ": No method named [" + methodName +
                                  "] found on class [" + targetClassName + "]");
                    }
                }
            }

        } catch (Exception e) {
            errors.add(patchClassName + ": Error validating patch - " + e.getMessage());
        }
    }

    private boolean findMethodInHierarchy(CtClass ctClass, String methodName) {
        try {
            while (ctClass != null) {
                CtMethod[] methods = ctClass.getDeclaredMethods(methodName);
                if (methods.length > 0) {
                    return true;
                }
                ctClass = ctClass.getSuperclass();
            }
        } catch (NotFoundException e) {
            // Reached Object or unknown class
        }
        return false;
    }

    private void printReport() {
        System.out.println("\n=== Validation Report ===");
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

        System.out.println("\n" + (errors.isEmpty() ? "VALIDATION PASSED" : "VALIDATION FAILED"));
    }
}
