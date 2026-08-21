package dev.bloodmekanism.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class MechanicalBloodOrbItem extends Item {
    private static final String LP_TAG = "MechanicalLp";

    private final MechanicalOrbTier tier;

    public MechanicalBloodOrbItem(MechanicalOrbTier tier) {
        super(new Item.Properties().stacksTo(1));
        this.tier = tier;
    }

    public MechanicalOrbTier tier() { return tier; }
    public int getCapacity() { return tier.capacity(); }
    public int getFillRate() { return tier.fillRate(); }

    public int getStoredLp(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(stack.getTag().getInt(LP_TAG), 0, getCapacity()) : 0;
    }

    public int insertLp(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int stored = getStoredLp(stack);
        int accepted = Math.min(amount, getCapacity() - stored);
        if (!simulate && accepted > 0) stack.getOrCreateTag().putInt(LP_TAG, stored + accepted);
        return accepted;
    }

    public int extractLp(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int stored = getStoredLp(stack);
        int extracted = Math.min(amount, stored);
        if (!simulate && extracted > 0) stack.getOrCreateTag().putInt(LP_TAG, stored - extracted);
        return extracted;
    }

    public boolean isFull(ItemStack stack) {
        return getStoredLp(stack) >= getCapacity();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStoredLp(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getStoredLp(stack) / getCapacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB6202E;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bloodmekanism.mechanical_orb.lp", getStoredLp(stack), getCapacity())
              .withStyle(tier.color()));
        tooltip.add(Component.translatable("tooltip.bloodmekanism.mechanical_orb.rate", getFillRate()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bloodmekanism.mechanical_orb.local").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.bloodmekanism.mechanical_orb.automation").withStyle(ChatFormatting.DARK_GRAY));
    }
}
