package dev.bloodmekanism.machine.blockentity;

import dev.bloodmekanism.item.MechanicalBloodOrbItem;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.ProcessingStatus;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.menu.LpChargerMenu;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.common.item.BloodOrb;
import wayoftime.bloodmagic.common.item.IBindable;
import wayoftime.bloodmagic.common.item.IBloodOrb;
import wayoftime.bloodmagic.common.tags.BloodMagicTags;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.core.data.SoulNetwork;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

import java.util.EnumMap;
import java.util.Map;

public final class LpChargerBlockEntity extends BaseMachineBlockEntity {
    public static final int LP_CAPACITY = 10_000_000;
    public static final int ENERGY_CAPACITY = 10_000_000;
    public static final int ENERGY_PER_LP = 1;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private final FluidTank lifeTank = new FluidTank(LP_CAPACITY, LpChargerBlockEntity::isLifeEssence) {
        @Override protected void onContentsChanged() { LpChargerBlockEntity.this.setChanged(); }
    };
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new ChargerFluidHandler(null));
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedFluidCapabilities = new EnumMap<>(Direction.class);
    private ProcessingStatus processingStatus = ProcessingStatus.IDLE;
    private int lastTransfer;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored();
                case 1 -> energy.getMaxEnergyStored();
                case 2 -> lifeTank.getFluidAmount();
                case 3 -> lifeTank.getCapacity();
                case 4 -> inputStoredLp();
                case 5 -> inputCapacity();
                case 6 -> 0;
                case 7 -> autoOutput ? 1 : 0;
                case 8 -> redstoneMode.ordinal();
                case 9, 10 -> 0;
                case 35 -> lastEnergyUsed();
                case 36 -> processingStatus.ordinal();
                case 37 -> lastTransfer;
                default -> {
                    int sideIndex = index - 11;
                    if (sideIndex >= 0 && sideIndex < MachineResource.values().length * RelativeMachineSide.values().length) {
                        MachineResource resource = MachineResource.values()[sideIndex / RelativeMachineSide.values().length];
                        RelativeMachineSide side = RelativeMachineSide.values()[sideIndex % RelativeMachineSide.values().length];
                        yield sideMode(resource, side).ordinal();
                    }
                    yield 0;
                }
            };
        }

        @Override public void set(int index, int value) { }
        @Override public int getCount() { return 38; }
    };

    public LpChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.LP_CHARGER_BLOCK_ENTITY.get(), pos, state, SLOT_COUNT, ENERGY_CAPACITY, -1, -1);
        for (Direction direction : Direction.values()) {
            sidedFluidCapabilities.put(direction, LazyOptional.of(() -> new ChargerFluidHandler(direction)));
        }
    }

    @Override
    public void serverTick() {
        lastEnergyUsed = 0;
        lastTransfer = 0;
        transferItems();
        if (!redstoneAllowsWork() || level == null) {
            processingStatus = ProcessingStatus.REDSTONE_DISABLED;
            updateActive(false);
            return;
        }

        ItemStack stack = inventory.getStackInSlot(INPUT_SLOT);
        if (stack.isEmpty()) {
            processingStatus = ProcessingStatus.IDLE;
            updateActive(false);
            return;
        }
        if (!isBloodOrb(stack)) {
            processingStatus = ProcessingStatus.NO_RECIPE;
            updateActive(false);
            return;
        }
        if (isFull(stack)) {
            processingStatus = moveToOutput() ? ProcessingStatus.RUNNING : ProcessingStatus.OUTPUT_FULL;
            updateActive(processingStatus == ProcessingStatus.RUNNING);
            return;
        }
        if (!isLifeEssence(lifeTank.getFluid()) || lifeTank.isEmpty()) {
            processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
            updateActive(false);
            return;
        }
        if (energy.getEnergyStored() < ENERGY_PER_LP) {
            processingStatus = ProcessingStatus.ENERGY_LOW;
            updateActive(false);
            return;
        }

        int transferred = charge(stack);
        if (transferred <= 0) {
            updateActive(false);
            return;
        }
        lifeTank.drain(transferred, IFluidHandler.FluidAction.EXECUTE);
        int energyUsed = transferred * ENERGY_PER_LP;
        energy.extractEnergy(energyUsed, false);
        lastEnergyUsed = energyUsed;
        lastTransfer = transferred;
        processingStatus = ProcessingStatus.RUNNING;
        inventory.setStackInSlot(INPUT_SLOT, stack);
        if (isFull(stack)) moveToOutput();
        updateActive(true);
        setChanged();
    }

    private int charge(ItemStack stack) {
        int available = Math.min(lifeTank.getFluidAmount(), energy.getEnergyStored() / ENERGY_PER_LP);
        if (stack.getItem() instanceof MechanicalBloodOrbItem mechanicalOrb) {
            int requested = Math.min(available, mechanicalOrb.getCapacity() - mechanicalOrb.getStoredLp(stack));
            return mechanicalOrb.insertLp(stack, requested, false);
        }
        if (!(stack.getItem() instanceof IBloodOrb orbItem) || !(stack.getItem() instanceof IBindable bindable)) {
            processingStatus = ProcessingStatus.NO_RECIPE;
            return 0;
        }
        BloodOrb orb = orbItem.getOrb(stack);
        Binding binding = bindable.getBinding(stack);
        if (orb == null || binding == null) {
            processingStatus = ProcessingStatus.ORB_UNBOUND;
            return 0;
        }
        SoulNetwork network = NetworkHelper.getSoulNetwork(binding);
        if (network == null) {
            processingStatus = ProcessingStatus.ORB_UNBOUND;
            return 0;
        }
        int requested = Math.min(available, Math.max(0, orb.getCapacity() - network.getCurrentEssence()));
        return network.add(requested, orb.getCapacity());
    }

    private boolean isFull(ItemStack stack) {
        if (stack.getItem() instanceof MechanicalBloodOrbItem mechanicalOrb) return mechanicalOrb.isFull(stack);
        if (!(stack.getItem() instanceof IBloodOrb orbItem) || !(stack.getItem() instanceof IBindable bindable)) return false;
        BloodOrb orb = orbItem.getOrb(stack);
        Binding binding = bindable.getBinding(stack);
        if (orb == null || binding == null) return false;
        SoulNetwork network = NetworkHelper.getSoulNetwork(binding);
        return network != null && network.getCurrentEssence() >= orb.getCapacity();
    }

    private boolean moveToOutput() {
        if (!inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) return false;
        ItemStack stack = inventory.extractItem(INPUT_SLOT, 1, false);
        if (stack.isEmpty()) return false;
        inventory.setStackInSlot(OUTPUT_SLOT, stack);
        return true;
    }

    private int inputStoredLp() {
        ItemStack stack = inventory.getStackInSlot(INPUT_SLOT);
        if (stack.getItem() instanceof MechanicalBloodOrbItem mechanicalOrb) return mechanicalOrb.getStoredLp(stack);
        if (!(stack.getItem() instanceof IBindable bindable)) return 0;
        Binding binding = bindable.getBinding(stack);
        SoulNetwork network = binding == null ? null : NetworkHelper.getSoulNetwork(binding);
        return network == null ? 0 : network.getCurrentEssence();
    }

    private int inputCapacity() {
        ItemStack stack = inventory.getStackInSlot(INPUT_SLOT);
        if (stack.getItem() instanceof MechanicalBloodOrbItem mechanicalOrb) return mechanicalOrb.getCapacity();
        return stack.getItem() instanceof IBloodOrb orbItem && orbItem.getOrb(stack) != null
              ? orbItem.getOrb(stack).getCapacity() : 0;
    }

    public static boolean isBloodOrb(ItemStack stack) {
        return stack.getItem() instanceof MechanicalBloodOrbItem || stack.getItem() instanceof IBloodOrb;
    }

    private static boolean isLifeEssence(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().is(BloodMagicTags.LIFE_ESSENCE);
    }

    @Override protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && isBloodOrb(stack);
    }

    @Override protected boolean canAutomationExtract(int slot) { return slot == OUTPUT_SLOT; }
    @Override protected boolean canAutomationInsert(int slot) { return slot == INPUT_SLOT; }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return (side == null ? fluidCapability : sidedFluidCapabilities.get(side)).cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
        sidedFluidCapabilities.values().forEach(LazyOptional::invalidate);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("LifeTank", lifeTank.writeToNBT(new CompoundTag()));
        tag.putInt("ProcessingStatus", processingStatus.ordinal());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        lifeTank.readFromNBT(tag.getCompound("LifeTank"));
        int status = tag.getInt("ProcessingStatus");
        processingStatus = ProcessingStatus.values()[Math.min(Math.max(status, 0), ProcessingStatus.values().length - 1)];
    }

    public FluidTank lifeTank() { return lifeTank; }
    public ProcessingStatus processingStatus() { return processingStatus; }

    @Override protected Component defaultName() { return Component.translatable("block.bloodmekanism.lp_charger"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LpChargerMenu(id, inventory, this);
    }

    private final class ChargerFluidHandler implements IFluidHandler {
        @Nullable private final Direction side;

        private ChargerFluidHandler(@Nullable Direction side) { this.side = side; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return lifeTank.getFluid(); }
        @Override public int getTankCapacity(int tank) { return lifeTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return allows(side, MachineResource.FLUID, true) && lifeTank.isFluidValid(stack);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return allows(side, MachineResource.FLUID, true) ? lifeTank.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }
}
