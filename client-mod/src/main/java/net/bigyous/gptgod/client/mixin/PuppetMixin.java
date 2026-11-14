package net.bigyous.gptgod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.bigyous.gptgod.client.puppet.Puppet;
import net.minecraft.client.Minecraft;

/**
 * Two hooks for the test-only puppet (net.bigyous.gptgod.client.puppet). Both only read a switch that is
 * false unless the game was started with -Dgptgod.puppet.port, so a normal player's game is unchanged.
 */
@Mixin(Minecraft.class)
abstract class PuppetMixin {
    /**
     * Every tick the game stops any digging when the attack button is not held by a grabbed mouse. A puppet
     * digs without the mouse, so while it does, that is skipped.
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gptgod$puppetDig(boolean down, CallbackInfo ci) {
        if (Puppet.digging) ci.cancel();
    }

    /** After a frame is drawn: the moment a puppet screenshot of "the next frame" is taken. */
    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void gptgod$puppetFrame(boolean advanceGameTime, CallbackInfo ci) {
        if (Puppet.shotPending) Puppet.frame();
    }
}
