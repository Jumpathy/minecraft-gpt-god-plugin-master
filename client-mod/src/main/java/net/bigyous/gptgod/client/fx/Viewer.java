package net.bigyous.gptgod.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * What a formula may know of the player watching (d, look, speed, ex ey ez in {@link FxExpr}): read from the
 * game once a tick, then handed to each layer relative to its own place. Client thread only.
 */
public final class Viewer {
    private Viewer() {
    }

    private static boolean here;
    private static double x, y, z, lx, ly, lz, speed;

    /** Once a client tick, before the scenes are advanced. */
    public static void update(Minecraft mc) {
        here = mc.player != null;
        if (!here) return;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        x = eye.x;
        y = eye.y;
        z = eye.z;
        lx = look.x;
        ly = look.y;
        lz = look.z;
        speed = mc.player.getDeltaMovement().length();
    }

    /** Fills in the viewer as seen from a layer's place (world coordinates); scale is the scene's. */
    public static void fill(double[] vars, double px, double py, double pz, float scale) {
        if (!here || scale <= 0) {
            FxExpr.nobody(vars);
            return;
        }
        FxExpr.viewer(vars, (x - px) / scale, (y - py) / scale, (z - pz) / scale, lx, ly, lz, speed);
    }

    /** The blocks of the world as a free particle of a layer at this place meets them. */
    public static final class Blocks implements FxField.Blocks {
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ClientLevel level;
        double x, y, z;
        float scale = 1;

        @Override
        public boolean solid(double dx, double dy, double dz) {
            if (level == null) return false;
            return level.getBlockState(pos.set(Mth.floor(x + dx * scale), Mth.floor(y + dy * scale), Mth.floor(z + dz * scale)))
                    .isSolidRender();
        }
    }
}
