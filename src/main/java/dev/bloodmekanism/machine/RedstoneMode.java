package dev.bloodmekanism.machine;

import net.minecraft.network.chat.Component;

public enum RedstoneMode {
    IGNORED("ignored"), HIGH("high"), LOW("low"), NEVER("never");

    private final String key;

    RedstoneMode(String key) { this.key = key; }

    public RedstoneMode next() {
        RedstoneMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean allows(boolean powered) {
        return this == IGNORED || this == HIGH && powered || this == LOW && !powered;
    }

    public Component title() {
        return Component.translatable("redstone.bloodmekanism." + key);
    }

    public Component shortTitle() {
        return Component.translatable("redstone.bloodmekanism." + key + "_short");
    }
}
