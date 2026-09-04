package dev.bloodmekanism.machine;

import net.minecraft.network.chat.Component;

public enum ProcessingStatus {
    IDLE("idle"),
    RUNNING("running"),
    REDSTONE_DISABLED("redstone_disabled"),
    NO_RECIPE("no_recipe"),
    TIER_TOO_LOW("tier_too_low"),
    LIFE_ESSENCE_LOW("life_essence_low"),
    OUTPUT_FULL("output_full"),
    ENERGY_LOW("energy_low"),
    ORB_UNBOUND("orb_unbound");

    private final String key;

    ProcessingStatus(String key) {
        this.key = key;
    }

    public Component title() {
        return Component.translatable("status.bloodmekanism." + key);
    }
}
