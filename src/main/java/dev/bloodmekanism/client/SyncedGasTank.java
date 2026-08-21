package dev.bloodmekanism.client;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasTank;
import net.minecraft.nbt.CompoundTag;

/** Read-only client view used by Mekanism's native gas widgets. */
final class SyncedGasTank implements IGasTank {
    private final Supplier<GasStack> stackSupplier;
    private final IntSupplier capacitySupplier;

    SyncedGasTank(Supplier<GasStack> stackSupplier, IntSupplier capacitySupplier) {
        this.stackSupplier = stackSupplier;
        this.capacitySupplier = capacitySupplier;
    }

    @Override public GasStack getStack() { return stackSupplier.get(); }
    @Override public long getCapacity() { return Math.max(0, capacitySupplier.getAsInt()); }
    @Override public boolean isValid(GasStack stack) { return false; }
    @Override public void setStack(GasStack stack) { }
    @Override public void setStackUnchecked(GasStack stack) { }
    @Override public void onContentsChanged() { }
    @Override public void deserializeNBT(CompoundTag nbt) { }
}
