package dev.bloodmekanism.machine;

import net.minecraft.network.chat.Component;

public enum ConnectionMode {
    NONE("none", false, false),
    INPUT("input", true, false),
    OUTPUT("output", false, true),
    BOTH("both", true, true);

    private final String key;
    private final boolean input;
    private final boolean output;

    ConnectionMode(String key, boolean input, boolean output) {
        this.key = key;
        this.input = input;
        this.output = output;
    }

    public boolean allowsInput() { return input; }
    public boolean allowsOutput() { return output; }

    public ConnectionMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ConnectionMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public Component title() {
        return Component.translatable("side_mode.bloodmekanism." + key);
    }

    public Component shortTitle() {
        return Component.translatable("side_mode.bloodmekanism." + key + "_short");
    }
}
