package dev.bloodmekanism.item;

import net.minecraft.ChatFormatting;

public enum MechanicalOrbTier {
    WEAK(1, 5_000, 2, ChatFormatting.GRAY),
    APPRENTICE(2, 25_000, 5, ChatFormatting.GREEN),
    MAGICIAN(3, 150_000, 15, ChatFormatting.AQUA),
    MASTER(4, 1_000_000, 25, ChatFormatting.LIGHT_PURPLE),
    ARCHMAGE(5, 10_000_000, 50, ChatFormatting.GOLD);

    private final int bloodTier;
    private final int capacity;
    private final int fillRate;
    private final ChatFormatting color;

    MechanicalOrbTier(int bloodTier, int capacity, int fillRate, ChatFormatting color) {
        this.bloodTier = bloodTier;
        this.capacity = capacity;
        this.fillRate = fillRate;
        this.color = color;
    }

    public int bloodTier() { return bloodTier; }
    public int capacity() { return capacity; }
    public int fillRate() { return fillRate; }
    public ChatFormatting color() { return color; }
}
