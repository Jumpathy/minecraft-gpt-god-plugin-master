package net.bigyous.gptgod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.bigyous.gptgod.client.relic.Relics;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A relic that changes form in the hand (the server swaps its item_model for the awakened one) shows the
 * new form at once: the hand does not dip and come back as it does for a different item.
 */
@Mixin(FirstPersonHandsAndItems.class)
abstract class FirstPersonHandsAndItemsMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void gptgod$relicForm(ItemStack currentlyVisibleItem, ItemStack expectedItem, LocalPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (Relics.changedForm(currentlyVisibleItem, expectedItem)) cir.setReturnValue(true);
    }
}
