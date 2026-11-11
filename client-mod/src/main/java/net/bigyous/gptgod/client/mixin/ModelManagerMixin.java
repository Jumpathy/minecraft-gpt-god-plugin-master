package net.bigyous.gptgod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.bigyous.gptgod.client.relic.Relics;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;

/**
 * Where the game looks up the model for a stack's item_model id: a relic whose art the plugin has sent
 * is drawn from that art, whatever the resource packs hold. Every other id is left to the game.
 */
@Mixin(ModelManager.class)
abstract class ModelManagerMixin {
    @Inject(method = "getItemModel", at = @At("HEAD"), cancellable = true)
    private void gptgod$relic(Identifier id, CallbackInfoReturnable<ItemModel> cir) {
        ItemModel relic = Relics.model(id);
        if (relic != null) cir.setReturnValue(relic);
    }
}
