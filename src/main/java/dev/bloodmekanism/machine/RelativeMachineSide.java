package dev.bloodmekanism.machine;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public enum RelativeMachineSide {
    TOP("top"),
    BOTTOM("bottom"),
    FRONT("front"),
    BACK("back"),
    LEFT("left"),
    RIGHT("right");

    private final String key;

    RelativeMachineSide(String key) {
        this.key = key;
    }

    public static RelativeMachineSide fromWorld(Direction side, Direction facing) {
        if (side == Direction.UP) return TOP;
        if (side == Direction.DOWN) return BOTTOM;
        if (side == facing) return FRONT;
        if (side == facing.getOpposite()) return BACK;
        if (side == facing.getCounterClockWise()) return LEFT;
        return RIGHT;
    }

    public Component title() {
        return Component.translatable("side.bloodmekanism." + key);
    }

    public Component shortTitle() {
        return Component.translatable("side.bloodmekanism." + key + "_short");
    }
}
