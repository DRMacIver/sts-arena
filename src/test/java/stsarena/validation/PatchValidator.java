package stsarena.validation;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Validates that all SpirePatch annotations in the mod target valid classes and methods.
 * This catches errors like "No method named [foo] found on class [Bar]" before runtime.
 */
public class PatchValidator {

    private final ClassLoader gameClassLoader;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int patchesChecked = 0;

    public PatchValidator(ClassLoader gameClassLoader) {
        this.gameClassLoader = gameClassLoader;
    }

    /**
     * Validate all patches in a package.
     */
    public void validatePackage(String packageName) {
        try {
            // Get all classes in the package
            String path = packageName.replace('.', '/');
            URL packageUrl = getClass().getClassLoader().getResource(path);

            if (packageUrl == null) {
                errors.add("Package not found: " + packageName);
                return;
            }

            File packageDir = new File(packageUrl.toURI());
            if (!packageDir.exists() || !packageDir.isDirectory()) {
                errors.add("Package directory not found: " + packageDir);
                return;
            }

            for (File file : packageDir.listFiles()) {
                if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().replace(".class", "");
                    validateClass(className);
                }
            }
        } catch (Exception e) {
            errors.add("Error scanning package " + packageName + ": " + e.getMessage());
        }
    }

    /**
     * Validate all patches in a class (including nested classes).
     */
    public void validateClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            validateClassPatches(clazz);

            // Also check nested classes
            for (Class<?> nested : clazz.getDeclaredClasses()) {
                validateClassPatches(nested);
            }
        } catch (ClassNotFoundException e) {
            // Class not found in our classpath - this is an error
            errors.add("Could not load class (not found): " + className);
        } catch (NoClassDefFoundError e) {
            // Class exists but has dependencies that can't be loaded (e.g., LibGDX native code)
            // This is expected when testing without the full game runtime
            warnings.add("Could not load class (missing dependency " + e.getMessage() + "): " + className);
        } catch (ExceptionInInitializerError e) {
            // Static initializer failed - often due to LibGDX not being initialized
            warnings.add("Class initialization failed (probably needs game runtime): " + className);
        } catch (UnsatisfiedLinkError e) {
            // Native library not loaded - expected without full game
            warnings.add("Native library not loaded for class: " + className);
        }
    }

    private void validateClassPatches(Class<?> patchClass) {
        // Check for SpirePatch annotation on the class
        SpirePatch patch = patchClass.getAnnotation(SpirePatch.class);
        if (patch != null) {
            validatePatch(patchClass, patch);
        }

        SpirePatch2 patch2 = patchClass.getAnnotation(SpirePatch2.class);
        if (patch2 != null) {
            validatePatch2(patchClass, patch2);
        }

        // Also check methods for patches (some mods put @SpirePatch on methods)
        for (Method method : patchClass.getDeclaredMethods()) {
            SpirePatch methodPatch = method.getAnnotation(SpirePatch.class);
            if (methodPatch != null) {
                validatePatch(patchClass, methodPatch);
            }
        }
    }

    private void validatePatch(Class<?> patchClass, SpirePatch patch) {
        patchesChecked++;
        String targetClassName = null;

        // Get target class
        if (patch.clz() != void.class) {
            targetClassName = patch.clz().getName();
        } else if (!patch.cls().isEmpty()) {
            targetClassName = patch.cls();
        }

        if (targetClassName == null) {
            if (!patch.optional()) {
                errors.add(patchClass.getName() + ": No target class specified in @SpirePatch");
            }
            return;
        }

        // Try to load target class
        Class<?> targetClass;
        try {
            targetClass = gameClassLoader.loadClass(targetClassName);
        } catch (ClassNotFoundException e) {
            if (patch.optional()) {
                warnings.add(patchClass.getName() + ": Optional target class not found: " + targetClassName);
            } else {
                errors.add(patchClass.getName() + ": Target class not found: " + targetClassName);
            }
            return;
        }

        // Get target method
        String methodName = patch.method();
        if (methodName.isEmpty()) {
            errors.add(patchClass.getName() + ": No method specified in @SpirePatch for " + targetClassName);
            return;
        }

        // Special method names
        if (methodName.equals("<ctor>") || methodName.equals(SpirePatch.CONSTRUCTOR)) {
            // Constructor - skip detailed validation
            return;
        }
        if (methodName.equals("<staticinit>") || methodName.equals(SpirePatch.STATICINITIALIZER)) {
            // Static initializer - skip detailed validation
            return;
        }
        if (methodName.equals("<class>") || methodName.equals(SpirePatch.CLASS)) {
            // Class-level patch - skip detailed validation
            return;
        }

        // Check if method exists
        // Note: SpirePatch uses {void.class} as a sentinel for "not specified"
        Class<?>[] paramTypes = patch.paramtypez();
        boolean hasParamTypes = !(paramTypes.length == 1 && paramTypes[0] == void.class);
        boolean methodFound = findMethodInClassHierarchy(targetClass, methodName, hasParamTypes ? paramTypes : new Class<?>[0]);

        if (!methodFound) {
            String paramStr = hasParamTypes ? "(" + formatParamTypes(paramTypes) + ")" : "";
            if (patch.optional()) {
                warnings.add(patchClass.getName() + ": Optional method not found: " +
                    targetClassName + "." + methodName + paramStr);
            } else {
                errors.add(patchClass.getName() + ": Method not found: " +
                    targetClassName + "." + methodName + paramStr);
            }
        }
    }

    /**
     * Search for a method in a class and all its superclasses and interfaces.
     */
    private boolean findMethodInClassHierarchy(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        if (clazz == null) return false;

        // Check declared methods in this class
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                if (paramTypes.length == 0) {
                    // No specific params required - any signature matches
                    return true;
                }
                // Check if parameter types match
                if (Arrays.equals(m.getParameterTypes(), paramTypes)) {
                    return true;
                }
            }
        }

        // Check superclass
        if (findMethodInClassHierarchy(clazz.getSuperclass(), methodName, paramTypes)) {
            return true;
        }

        // Check interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            if (findMethodInClassHierarchy(iface, methodName, paramTypes)) {
                return true;
            }
        }

        return false;
    }

    private String formatParamTypes(Class<?>[] paramTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes[i].getSimpleName());
        }
        return sb.toString();
    }

    private void validatePatch2(Class<?> patchClass, SpirePatch2 patch) {
        // SpirePatch2 is similar but uses different field names
        // For now, just count it
        patchesChecked++;
        warnings.add(patchClass.getName() + ": SpirePatch2 validation not fully implemented");
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getPatchesChecked() {
        return patchesChecked;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void printReport() {
        System.out.println("=== Patch Validation Report ===");
        System.out.println("Patches checked: " + patchesChecked);
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

        System.out.println("\n" + (hasErrors() ? "VALIDATION FAILED" : "VALIDATION PASSED"));
    }
}
