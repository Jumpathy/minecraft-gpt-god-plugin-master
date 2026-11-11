package net.bigyous.gptgod.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.bigyous.gptgod.client.fx.DivinePost;
import net.minecraft.client.renderer.PostPass;

/** Gives the divine post pass a uniform block the mod can rewrite every frame (the game's is fixed). */
@Mixin(PostPass.class)
abstract class PostPassMixin {
    @Shadow
    @Final
    private Map<String, GpuBuffer> customUniforms;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gptgod$liveUniforms(CallbackInfo ci) {
        GpuBuffer fixed = customUniforms.get(DivinePost.UNIFORMS);
        if (fixed == null) return;
        fixed.close();
        customUniforms.put(DivinePost.UNIFORMS, DivinePost.createBuffer());
    }
}
