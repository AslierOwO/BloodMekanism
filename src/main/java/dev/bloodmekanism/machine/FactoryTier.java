package dev.bloodmekanism.machine;

import net.minecraft.ChatFormatting;

public enum FactoryTier {
    BASIC(1, 1, 10_000, 100_000, ChatFormatting.GRAY),
    ADVANCED(2, 2, 25_000, 400_000, ChatFormatting.GREEN),
    ELITE(3, 4, 100_000, 1_600_000, ChatFormatting.AQUA),
    ULTIMATE(4, 8, 500_000, 6_400_000, ChatFormatting.LIGHT_PURPLE),
    ABSOLUTE(5, 16, 2_000_000, 25_600_000, ChatFormatting.GOLD);

    private final int bloodTier;
    private final int batchSize;
    private final int tankCapacity;
    private final int energyCapacity;
    private final ChatFormatting color;

    FactoryTier(int bloodTier, int batchSize, int tankCapacity, int energyCapacity, ChatFormatting color) {
        this.bloodTier = bloodTier;
        this.batchSize = batchSize;
        this.tankCapacity = tankCapacity;
        this.energyCapacity = energyCapacity;
        this.color = color;
    }

    public int bloodTier() { return bloodTier; }
    public int batchSize() { return batchSize; }
    public int tankCapacity() { return tankCapacity; }
    public int energyCapacity() { return energyCapacity; }
    public ChatFormatting color() { return color; }
}
