package net.bigyous.gptgod.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.bigyous.gptgod.client.fx.ScreenFx;
import net.bigyous.gptgod.client.hud.Overlays;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** The god's camera shake: turns the view a little, on top of vanilla's hurt tilt and bobbing. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private List<net.minecraft.resources.Identifier> requestedPostEffects;

    /** After the game lists this frame's post effects, the god's full-screen pass joins them if active. */
    @Inject(method = "update", at = @At("TAIL"))
    private void gptgod$post(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        net.bigyous.gptgod.client.fx.DivinePost.frame(requestedPostEffects);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void gptgod$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        net.bigyous.gptgod.client.fx.CameraFx.Frame cam = Overlays.camera();
        if (!cam.idle()) {
            // Zoom scales the picture; roll and gaze turn the view. The player's own aim is untouched.
            if (cam.zoom() != 1) poseStack.scale(cam.zoom(), cam.zoom(), 1);
            poseStack.rotateDegrees(Axis.ZP, cam.roll());
            poseStack.rotateDegrees(Axis.XP, cam.gazePitch());
            poseStack.rotateDegrees(Axis.YP, cam.gazeYaw());
        }
        ScreenFx.Frame f = Overlays.current();
        if (f.yaw() == 0 && f.pitch() == 0 && f.roll() == 0) return;
        poseStack.rotateDegrees(Axis.ZP, f.roll());
        poseStack.rotateDegrees(Axis.XP, f.pitch());
        poseStack.rotateDegrees(Axis.YP, f.yaw());
    }
}
