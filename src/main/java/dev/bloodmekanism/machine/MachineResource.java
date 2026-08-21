package dev.bloodmekanism.machine;

import net.minecraft.network.chat.Component;

public enum MachineResource {
    ITEM("item"),
    FLUID("fluid"),
    ENERGY("energy"),
    GAS("gas");

    private final String key;

    MachineResource(String key) {
        this.key = key;
    }

    public Component title() {
        return Component.translatable("resource.bloodmekanism." + key);
    }

    public Component shortTitle() {
        return Component.translatable("resource.bloodmekanism." + key + "_short");
    }
}
