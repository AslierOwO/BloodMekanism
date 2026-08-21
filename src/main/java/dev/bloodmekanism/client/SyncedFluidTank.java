package dev.bloodmekanism.client;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Read-only client view used by Mekanism's native fluid gauge. */
final class SyncedFluidTank implements IExtendedFluidTank {
    private final Supplier<FluidStack> stackSupplier;
    private final IntSupplier capacitySupplier;

    SyncedFluidTank(Supplier<FluidStack> stackSupplier, IntSupplier capacitySupplier) {
        this.stackSupplier = stackSupplier;
        this.capacitySupplier = capacitySupplier;
    }

    @Override public FluidStack getFluid() { return stackSupplier.get(); }
    @Override public int getFluidAmount() { return getFluid().getAmount(); }
    @Override public int getCapacity() { return Math.max(0, capacitySupplier.getAsInt()); }
    @Override public boolean isFluidValid(FluidStack stack) { return false; }
    @Override public void setStack(FluidStack stack) { }
    @Override public void setStackUnchecked(FluidStack stack) { }
    @Override public void onContentsChanged() { }
    @Override public void deserializeNBT(CompoundTag nbt) { }
}
