package dev.bloodmekanism.will;

import dev.bloodmekanism.registry.ModContent;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;

public final class WillGas {
    public static final int UNITS_PER_WILL = 100;

    private WillGas() { }

    public static RegistryObject<Gas> provider(EnumDemonWillType type) {
        return switch (type) {
            case DEFAULT -> ModContent.RAW_WILL;
            case CORROSIVE -> ModContent.CORROSIVE_WILL;
            case DESTRUCTIVE -> ModContent.DESTRUCTIVE_WILL;
            case VENGEFUL -> ModContent.VENGEFUL_WILL;
            case STEADFAST -> ModContent.STEADFAST_WILL;
        };
    }

    public static GasStack stack(EnumDemonWillType type, long amount) {
        return amount <= 0 ? GasStack.EMPTY : new GasStack(provider(type).get(), amount);
    }

    public static boolean isWill(Gas gas) {
        return typeOf(gas) != null;
    }

    @Nullable
    public static EnumDemonWillType typeOf(Gas gas) {
        for (EnumDemonWillType type : EnumDemonWillType.values()) {
            if (provider(type).get() == gas) return type;
        }
        return null;
    }

    public static long toGas(double will) {
        if (will <= 0) return 0;
        return Math.max(1, Math.round(will * UNITS_PER_WILL));
    }

    public static double toWill(long gas) {
        return gas / (double) UNITS_PER_WILL;
    }
}
