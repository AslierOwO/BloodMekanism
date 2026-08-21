package dev.bloodmekanism.machine.blockentity;

import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.menu.WillGeneratorMenu;
import dev.bloodmekanism.registry.ModContent;
import dev.bloodmekanism.will.WillGas;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.api.compat.IDemonWill;
import wayoftime.bloodmagic.api.compat.IDemonWillGem;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.tags.BloodMagicTags;

public final class WillGeneratorBlockEntity extends BaseMachineBlockEntity {
    public static final int INPUT_SLOT = 0;
    public static final int SPEED_UPGRADE_SLOT = 1;
    public static final int ENERGY_UPGRADE_SLOT = 2;
    public static final int SLOT_COUNT = 3;
    public static final int FLUID_CAPACITY = 32_000;
    public static final int GAS_CAPACITY = 64_000;
    public static final int SOUL_SAND_LIFE = 1_000;
    public static final int SOUL_SAND_GAS = 16 * WillGas.UNITS_PER_WILL;
    public static final int SOUL_SAND_TICKS = 200;
    public static final int SOUL_SAND_ENERGY = 20_000;

    private final FluidTank lifeTank = new FluidTank(FLUID_CAPACITY, WillGeneratorBlockEntity::isLifeEssence) {
        @Override protected void onContentsChanged() { WillGeneratorBlockEntity.this.setChanged(); }
    };
    private final IGasTank willTank = ChemicalTankBuilder.GAS.output(GAS_CAPACITY, this::setChanged);
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new GeneratorFluidHandler(null));
    private final LazyOptional<IGasHandler> gasCapability = LazyOptional.of(() -> new GeneratorGasHandler(null));
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedFluidCapabilities = new EnumMap<>(Direction.class);
    private final Map<Direction, LazyOptional<IGasHandler>> sidedGasCapabilities = new EnumMap<>(Direction.class);
    private int progress;
    private int maxProgress;
    private String processKey = "";
    private int gasTransferCooldown;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored();
                case 1 -> energy.getMaxEnergyStored();
                case 2 -> lifeTank.getFluidAmount();
                case 3 -> lifeTank.getCapacity();
                case 4 -> progress;
                case 5 -> maxProgress;
                case 6 -> 0;
                case 7 -> autoOutput ? 1 : 0;
                case 8 -> redstoneMode.ordinal();
                case 9 -> speedUpgradeCount();
                case 10 -> energyUpgradeCount();
                case 35 -> lastEnergyUsed;
                case 36 -> gasRegistryId();
                case 37 -> (int) willTank.getStored();
                case 38 -> (int) willTank.getCapacity();
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
        @Override public int getCount() { return 39; }
    };

    public WillGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WILL_GENERATOR_BLOCK_ENTITY.get(), pos, state, SLOT_COUNT, 400_000, SPEED_UPGRADE_SLOT, ENERGY_UPGRADE_SLOT);
        for (Direction direction : Direction.values()) {
            sidedFluidCapabilities.put(direction, LazyOptional.of(() -> new GeneratorFluidHandler(direction)));
            sidedGasCapabilities.put(direction, LazyOptional.of(() -> new GeneratorGasHandler(direction)));
        }
    }

    @Override
    public void serverTick() {
        lastEnergyUsed = 0;
        transferItems();
        transferGas();
        if (!redstoneAllowsWork() || level == null) {
            updateActive(false);
            return;
        }
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        boolean active = input.is(Items.SOUL_SAND) ? tickSoulSand() : tickWillItem(input);
        if (!active && input.isEmpty()) resetProgress();
        updateActive(active);
    }

    private boolean tickSoulSand() {
        GasStack output = WillGas.stack(EnumDemonWillType.DEFAULT, SOUL_SAND_GAS);
        if (!isLifeEssence(lifeTank.getFluid()) || lifeTank.getFluidAmount() < SOUL_SAND_LIFE || !canFit(output)) return false;
        return advance("soul_sand", SOUL_SAND_TICKS, Math.max(1, SOUL_SAND_ENERGY / SOUL_SAND_TICKS), () -> {
            inventory.extractItem(INPUT_SLOT, 1, false);
            lifeTank.drain(SOUL_SAND_LIFE, IFluidHandler.FluidAction.EXECUTE);
            willTank.insert(output, Action.EXECUTE, AutomationType.INTERNAL);
        });
    }

    private boolean tickWillItem(ItemStack stack) {
        EnumDemonWillType type = findItemWillType(stack);
        if (type == null) return false;
        long available = WillGas.toGas(getItemWill(stack, type));
        long amount = Math.min(available, willTank.getNeeded());
        GasStack output = WillGas.stack(type, amount);
        if (amount <= 0 || !canFit(output)) return false;
        return advance("item:" + type + ":" + amount, 20, 50, () -> {
            ItemStack current = inventory.getStackInSlot(INPUT_SLOT);
            double drained = drainItemWill(current, type, WillGas.toWill(amount));
            long actual = Math.min(amount, WillGas.toGas(drained));
            if (actual > 0) willTank.insert(WillGas.stack(type, actual), Action.EXECUTE, AutomationType.INTERNAL);
            if (current.getItem() instanceof IDemonWill will && will.getWill(type, current) <= 0) {
                inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
            } else {
                inventory.setStackInSlot(INPUT_SLOT, current);
            }
        });
    }

    private boolean advance(String key, int duration, int baseEnergyPerTick, Runnable finish) {
        duration = upgradedDuration(duration);
        int energyPerTick = upgradedEnergyPerTick(baseEnergyPerTick);
        if (!key.equals(processKey)) {
            processKey = key;
            progress = 0;
        }
        maxProgress = duration;
        if (energy.extractEnergy(energyPerTick, true) < energyPerTick) return false;
        energy.extractEnergy(energyPerTick, false);
        lastEnergyUsed = energyPerTick;
        progress++;
        if (progress >= maxProgress) {
            finish.run();
            progress = 0;
            setChanged();
        }
        return true;
    }

    private void resetProgress() {
        progress = 0;
        maxProgress = 0;
        processKey = "";
    }

    private boolean canFit(GasStack stack) {
        return !stack.isEmpty() && willTank.insert(stack, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    @Nullable
    private static EnumDemonWillType findItemWillType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof IDemonWill will) {
            EnumDemonWillType type = will.getType(stack);
            return will.getWill(type, stack) > 0 ? type : null;
        }
        if (stack.getItem() instanceof IDemonWillGem gem) {
            stack.getOrCreateTag();
            for (EnumDemonWillType type : EnumDemonWillType.values()) {
                if (gem.getWill(type, stack) > 0) return type;
            }
        }
        return null;
    }

    private static double getItemWill(ItemStack stack, EnumDemonWillType type) {
        if (stack.getItem() instanceof IDemonWill will) return will.getWill(type, stack);
        if (stack.getItem() instanceof IDemonWillGem gem) return gem.getWill(type, stack);
        return 0;
    }

    private static double drainItemWill(ItemStack stack, EnumDemonWillType type, double amount) {
        if (stack.getItem() instanceof IDemonWill will) return will.drainWill(type, stack, amount);
        if (stack.getItem() instanceof IDemonWillGem gem) return gem.drainWill(type, stack, amount, true);
        return 0;
    }

    private void transferGas() {
        if (!autoOutput || level == null || willTank.isEmpty() || ++gasTransferCooldown < 10) return;
        gasTransferCooldown = 0;
        for (Direction direction : Direction.values()) {
            if (!allows(direction, MachineResource.GAS, false)) continue;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null) continue;
            neighbor.getCapability(Capabilities.GAS_HANDLER, direction.getOpposite()).ifPresent(handler -> {
                GasStack offered = willTank.extract(10_000, Action.SIMULATE, AutomationType.INTERNAL);
                GasStack remainder = handler.insertChemical(offered, Action.SIMULATE);
                long accepted = offered.getAmount() - remainder.getAmount();
                if (accepted > 0) {
                    GasStack extracted = willTank.extract(accepted, Action.EXECUTE, AutomationType.INTERNAL);
                    handler.insertChemical(extracted, Action.EXECUTE);
                }
            });
        }
    }

    private int gasRegistryId() {
        EnumDemonWillType type = willTank.isEmpty() ? null : WillGas.typeOf(willTank.getType());
        return type == null ? 0 : type.ordinal() + 1;
    }

    private static boolean isLifeEssence(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid() == BloodMagicFluids.LIFE_ESSENCE_FLUID.get() || stack.getFluid().is(BloodMagicTags.LIFE_ESSENCE));
    }

    @Override protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && (stack.is(Items.SOUL_SAND) || stack.getItem() instanceof IDemonWill || stack.getItem() instanceof IDemonWillGem);
    }

    @Override protected boolean canAutomationExtract(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        return slot == INPUT_SLOT && stack.getItem() instanceof IDemonWillGem && findItemWillType(stack) == null;
    }

    @Override protected boolean canAutomationInsert(int slot) { return slot == INPUT_SLOT; }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return (side == null ? fluidCapability : sidedFluidCapabilities.get(side)).cast();
        }
        if (capability == Capabilities.GAS_HANDLER) {
            return (side == null ? gasCapability : sidedGasCapabilities.get(side)).cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
        gasCapability.invalidate();
        sidedFluidCapabilities.values().forEach(LazyOptional::invalidate);
        sidedGasCapabilities.values().forEach(LazyOptional::invalidate);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("LifeTank", lifeTank.writeToNBT(new CompoundTag()));
        tag.put("WillTank", willTank.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putString("ProcessKey", processKey);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        lifeTank.readFromNBT(tag.getCompound("LifeTank"));
        willTank.deserializeNBT(tag.getCompound("WillTank"));
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        processKey = tag.getString("ProcessKey");
    }

    public FluidTank lifeTank() { return lifeTank; }
    public IGasTank willTank() { return willTank; }

    @Override protected Component defaultName() { return Component.translatable("block.bloodmekanism.will_generator"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new WillGeneratorMenu(id, inventory, this); }

    private final class GeneratorFluidHandler implements IFluidHandler {
        @Nullable private final Direction side;

        private GeneratorFluidHandler(@Nullable Direction side) { this.side = side; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return lifeTank.getFluid(); }
        @Override public int getTankCapacity(int tank) { return lifeTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return allows(side, MachineResource.FLUID, true) && lifeTank.isFluidValid(stack); }
        @Override public int fill(FluidStack resource, FluidAction action) { return allows(side, MachineResource.FLUID, true) ? lifeTank.fill(resource, action) : 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    private final class GeneratorGasHandler implements IGasHandler.IMekanismGasHandler {
        @Nullable private final Direction side;

        private GeneratorGasHandler(@Nullable Direction side) { this.side = side; }
        @Override public List<IGasTank> getChemicalTanks(@Nullable Direction ignored) {
            return allows(side, MachineResource.GAS, false) ? List.of(willTank) : List.of();
        }
        @Override public void onContentsChanged() { setChanged(); }
    }
}
