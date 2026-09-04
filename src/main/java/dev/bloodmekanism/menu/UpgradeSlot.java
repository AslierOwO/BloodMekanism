package dev.bloodmekanism.menu;

import mekanism.api.Upgrade;
import mekanism.common.item.interfaces.IUpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Mekanism-style transient upgrade input or uninstall output slot. */
final class UpgradeSlot extends SlotItemHandler {
    private final boolean output;

    UpgradeSlot(IItemHandler handler, int index, boolean output) {
        super(handler, index, -1_000, -1_000);
        this.output = output;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (output || !(stack.getItem() instanceof IUpgradeItem upgrade)) return false;
        Upgrade type = upgrade.getUpgradeType(stack);
        return type == Upgrade.SPEED || type == Upgrade.ENERGY;
    }

    @Override
    public int getMaxStackSize() {
        return Upgrade.SPEED.getMax();
    }
}
