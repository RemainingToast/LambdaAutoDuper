package com.lambda.mixins;

import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.inventory.IInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiScreenHorseInventory.class)
public interface AccessorGuiScreenHorseInventory {
    @Accessor
    IInventory getHorseInventory();
}