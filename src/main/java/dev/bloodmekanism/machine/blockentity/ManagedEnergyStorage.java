package dev.bloodmekanism.machine.blockentity;

import net.minecraftforge.energy.EnergyStorage;

public final class ManagedEnergyStorage extends EnergyStorage {
    private final Runnable changed;

    public ManagedEnergyStorage(int capacity, int maxReceive, Runnable changed) {
        super(capacity, maxReceive, capacity);
        this.changed = changed;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0) changed.run();
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (!simulate && extracted > 0) changed.run();
        return extracted;
    }

    public void setStored(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }

    public void setCapacity(int capacity, int maxReceive) {
        this.capacity = Math.max(1, capacity);
        this.maxReceive = Math.max(1, maxReceive);
        this.maxExtract = this.capacity;
        if (energy > this.capacity) energy = this.capacity;
    }
}
