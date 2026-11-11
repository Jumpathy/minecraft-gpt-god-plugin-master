package net.bigyous.gptgod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.bigyous.gptgod.client.fx.SkyLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;

/** Adds the god's mood to the sky, fog and cloud colours of every client world. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
    private void gptgod$moodLayers(EnvironmentAttributeSystem.Builder builder,
            CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        SkyLayers.add(cir.getReturnValue());
    }
}
