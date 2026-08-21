package dev.bloodmekanism.machine;

import net.minecraft.network.chat.Component;

public enum FactoryMode {
    ALTAR("altar"),
    ALCHEMY_TABLE("alchemy_table"),
    ALCHEMY_ARRAY("alchemy_array"),
    SOUL_FORGE("soul_forge"),
    ARC("arc");

    private final String key;

    FactoryMode(String key) {
        this.key = key;
    }

    public Component title() {
        return Component.translatable("mode.bloodmekanism." + key);
    }

    public FactoryMode next() {
        FactoryMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
