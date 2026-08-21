package dev.bloodmekanism.machine.blockentity;

import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.menu.HemogenicMenu;
import dev.bloodmekanism.recipe.BloodGeneratorRecipe;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
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
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;

import java.util.EnumMap;
import java.util.Map;

public final class HemogenicBlockEntity extends BaseMachineBlockEntity {
    public static final int TANK_CAPACITY = 32_000;
    public static final int INPUT_SLOT = 0;
    public static final int SPEED_UPGRADE_SLOT = 1;
    public static final int ENERGY_UPGRADE_SLOT = 2;
    public static final int SLOT_COUNT = 3;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, stack -> stack.getFluid() == BloodMagicFluids.LIFE_ESSENCE_FLUID.get()) {
        @Override protected void onContentsChanged() { HemogenicBlockEntity.this.setChanged(); }
    };
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new HemogenicFluidHandler(null));
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedFluidCapabilities = new EnumMap<>(Direction.class);
    private int progress;
    private int maxProgress;
    private ResourceLocation currentRecipe;
    private int fluidTransferCooldown;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored();
                case 1 -> energy.getMaxEnergyStored();
                case 2 -> tank.getFluidAmount();
                case 3 -> tank.getCapacity();
                case 4 -> progress;
                case 5 -> maxProgress;
                case 6 -> 0; // Reserved legacy auto-input index.
                case 7 -> autoOutput ? 1 : 0;
                case 8 -> redstoneMode.ordinal();
                case 9 -> speedUpgradeCount();
                case 10 -> energyUpgradeCount();
                case 35 -> lastEnergyUsed();
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
        @Override public int getCount() { return 36; }
    };

    public HemogenicBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.HEMOGENIC_BLOCK_ENTITY.get(), pos, state, SLOT_COUNT, 400_000, SPEED_UPGRADE_SLOT, ENERGY_UPGRADE_SLOT);
        for (Direction direction : Direction.values()) {
            sidedFluidCapabilities.put(direction, LazyOptional.of(() -> new HemogenicFluidHandler(direction)));
        }
    }

    @Override
    public void serverTick() {
        lastEnergyUsed = 0;
        transferItems();
        transferFluid();
        if (!redstoneAllowsWork() || level == null) {
            updateActive(false);
            return;
        }

        ItemStack input = inventory.getStackInSlot(0);
        BloodGeneratorRecipe recipe = input.isEmpty() ? null : level.getRecipeManager()
              .getRecipeFor(ModContent.BLOOD_GENERATOR_RECIPE_TYPE.get(), new SimpleContainer(input), level).orElse(null);
        if (recipe == null || tank.getFluidAmount() + recipe.blood() > tank.getCapacity()) {
            progress = 0;
            maxProgress = 0;
            currentRecipe = null;
            updateActive(false);
            return;
        }

        if (!recipe.getId().equals(currentRecipe)) {
            currentRecipe = recipe.getId();
            progress = 0;
        }
        maxProgress = upgradedDuration(recipe.ticks());
        int baseEnergyPerTick = Math.max(1, (recipe.energy() + recipe.ticks() - 1) / recipe.ticks());
        int energyPerTick = upgradedEnergyPerTick(baseEnergyPerTick);
        if (energy.extractEnergy(energyPerTick, true) < energyPerTick) {
            updateActive(false);
            return;
        }

        energy.extractEnergy(energyPerTick, false);
        lastEnergyUsed = energyPerTick;
        progress++;
        updateActive(true);
        if (progress >= maxProgress) {
            inventory.extractItem(0, 1, false);
            tank.fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), recipe.blood()), IFluidHandler.FluidAction.EXECUTE);
            progress = 0;
            setChanged();
        }
    }

    private void transferFluid() {
        if (!autoOutput || level == null || tank.isEmpty() || ++fluidTransferCooldown < 10) return;
        fluidTransferCooldown = 0;
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null) continue;
            if (!allows(direction, MachineResource.FLUID, false)) continue;
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).ifPresent(handler -> {
                FluidStack offered = tank.drain(1_000, IFluidHandler.FluidAction.SIMULATE);
                if (offered.isEmpty()) return;
                int accepted = handler.fill(offered, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                }
            });
        }
    }

    @Override protected boolean isItemValid(int slot, ItemStack stack) {
        if (level == null) return true;
        return level.getRecipeManager().getRecipeFor(ModContent.BLOOD_GENERATOR_RECIPE_TYPE.get(), new SimpleContainer(stack), level).isPresent();
    }
    @Override protected boolean canAutomationExtract(int slot) { return false; }
    @Override protected boolean canAutomationInsert(int slot) { return slot == 0; }

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
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        if (currentRecipe != null) tag.putString("Recipe", currentRecipe.toString());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("Tank"));
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        currentRecipe = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
    }

    public FluidTank tank() { return tank; }
    @Override protected Component defaultName() { return Component.translatable("block.bloodmekanism.hemogenic_machine"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new HemogenicMenu(id, inventory, this); }

    private final class HemogenicFluidHandler implements IFluidHandler {
        @Nullable private final Direction side;

        private HemogenicFluidHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tankIndex) { return tank.getFluid(); }
        @Override public int getTankCapacity(int tankIndex) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return allows(side, MachineResource.FLUID, true) && tank.isFluidValid(stack);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return allows(side, MachineResource.FLUID, true) ? tank.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return allows(side, MachineResource.FLUID, false) ? tank.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return allows(side, MachineResource.FLUID, false) ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
