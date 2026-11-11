package net.bigyous.gptgodmc.god.vision;

import java.lang.reflect.Method;

import net.bigyous.gptgodmc.god.build.Plan;

/** Reads Plan's packed coordinates from tests in another package. */
final class PlanAccess {
    private static final Method KX;
    private static final Method KY;
    private static final Method KZ;

    static {
        try {
            KX = Plan.class.getDeclaredMethod("kx", long.class);
            KY = Plan.class.getDeclaredMethod("ky", long.class);
            KZ = Plan.class.getDeclaredMethod("kz", long.class);
            KX.setAccessible(true);
            KY.setAccessible(true);
            KZ.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private PlanAccess() {
    }

    static int x(long k) {
        return call(KX, k);
    }

    static int y(long k) {
        return call(KY, k);
    }

    static int z(long k) {
        return call(KZ, k);
    }

    private static int call(Method m, long k) {
        try {
            return (int) m.invoke(null, k);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
