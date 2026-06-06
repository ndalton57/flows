package dev.flows;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.function.Predicate;

/**
 * Rewrites the body of {@code getTickDelay} in Minecraft's water and lava fluid
 * classes to {@code return <constant>}, using the JDK's built-in Class-File API
 * ({@code java.lang.classfile}, standard since JDK 24 / JEP 484). No third-party
 * bytecode library is needed.
 *
 * <p>Only two classes are ever touched; every other class is returned unchanged
 * ({@code null}). If the expected method isn't found (e.g. a Mojang mapping
 * rename in a future MC version), the class is left untouched and a clear
 * warning is logged so the failure is visible rather than silent.
 */
public final class GetTickDelayTransformer implements ClassFileTransformer {

    // Mojang-mapped internal names (modern Paper runs Mojang mappings at runtime).
    private static final String WATER_FLUID = "net/minecraft/world/level/material/WaterFluid";
    private static final String LAVA_FLUID = "net/minecraft/world/level/material/LavaFluid";
    private static final String TARGET_METHOD = "getTickDelay";

    private final int waterDelay;
    private final int lavaDelay;

    GetTickDelayTransformer(int waterDelay, int lavaDelay) {
        this.waterDelay = waterDelay;
        this.lavaDelay = lavaDelay;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }

        final int value;
        if (className.equals(WATER_FLUID)) {
            value = waterDelay;
        } else if (className.equals(LAVA_FLUID)) {
            value = lavaDelay;
        } else {
            return null; // not our class - leave unchanged
        }

        try {
            ClassFile classFile = ClassFile.of();

            // Count how many methods matched so we can warn if the mapping changed.
            final int[] matched = {0};
            Predicate<MethodModel> isTarget = method -> {
                if (method.methodName().equalsString(TARGET_METHOD)) {
                    matched[0]++;
                    return true;
                }
                return false;
            };

            // For the matched method(s): drop the original Code attribute and emit
            // `loadConstant(value); ireturn`; forward all other method elements
            // (access flags, signature, etc.) unchanged.
            MethodTransform replaceBody = (methodBuilder, element) -> {
                if (element instanceof CodeModel) {
                    methodBuilder.withCode(code -> code.loadConstant(Integer.valueOf(value)).ireturn());
                } else {
                    methodBuilder.with(element);
                }
            };

            byte[] result = classFile.transformClass(
                    classFile.parse(classfileBuffer),
                    ClassTransform.transformingMethods(isTarget, replaceBody));

            if (matched[0] == 0) {
                System.err.println("[Flows] WARNING: method '" + TARGET_METHOD + "' not found in "
                        + className + " - the Mojang mapping may have changed for this Minecraft "
                        + "version. Fluid flow speed was NOT patched for this fluid.");
                return null; // leave class unchanged
            }

            System.out.println("[Flows] Patched " + className + "." + TARGET_METHOD
                    + " -> return " + value + " (" + matched[0] + " method(s)).");
            return result;
        } catch (Throwable t) {
            // Never break the server: on any failure, leave the original class as-is.
            System.err.println("[Flows] ERROR patching " + className + "; leaving it unchanged: " + t);
            return null;
        }
    }
}
