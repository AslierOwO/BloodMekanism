package dev.bloodmekanism.menu;

import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.ConnectionMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.machine.ProcessingStatus;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.Upgrade;
import mekanism.api.chemical.gas.GasStack;
import dev.bloodmekanism.will.WillGas;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import mekanism.common.item.interfaces.IUpgradeItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.SlotItemHandler;

public final class UniversalFactoryMenu extends AbstractContainerMenu {
    private final UniversalFactoryBlockEntity machine;
    private final ContainerData data;

    public static UniversalFactoryMenu clientConstructor(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new UniversalFactoryMenu(id, inventory,
              (UniversalFactoryBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()), true);
    }

    public UniversalFactoryMenu(int id, Inventory playerInventory, UniversalFactoryBlockEntity machine) {
        this(id, playerInventory, machine, false);
    }

    private UniversalFactoryMenu(int id, Inventory playerInventory, UniversalFactoryBlockEntity machine, boolean clientSide) {
        super(ModContent.UNIVERSAL_FACTORY_MENU.get(), id);
        this.machine = machine;
        this.data = clientSide || machine == null ? new SimpleContainerData(56) : machine.data;
        checkContainerDataCount(data, 56);
        addDataSlots(data);
        if (machine != null) {
            for (int channel = 0; channel < UniversalFactoryBlockEntity.INPUT_COUNT; channel++) {
                addSlot(new SlotItemHandler(machine.inventory(), channel, 20 + channel % 3 * 19, 22 + channel / 3 * 19));
            }
            addSlot(new SlotItemHandler(machine.inventory(), UniversalFactoryBlockEntity.CATALYST_SLOT, 90, 41));
            for (int channel = 0; channel < UniversalFactoryBlockEntity.OUTPUT_COUNT; channel++) {
                addSlot(new SlotItemHandler(machine.inventory(), UniversalFactoryBlockEntity.OUTPUT_START + channel, 138 + channel % 3 * 19, 22 + channel / 3 * 19));
            }
            addSlot(new UpgradeSlot(machine.inventory(), UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT, false));
            addSlot(new UpgradeSlot(machine.inventory(), UniversalFactoryBlockEntity.UPGRADE_OUTPUT_SLOT, true));
        }
        addPlayerInventory(playerInventory, 38, 112);
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, left + column * 18, top + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, left + column * 18, top + 58));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine != null && !player.level().isClientSide) {
            if (id >= BaseMachineBlockEntity.UPGRADE_BUTTON_BASE && id < BaseMachineBlockEntity.UPGRADE_BUTTON_END) machine.handleUpgradeButton(player, id);
            else machine.handleButton(id);
        }
        return id >= 1 && id <= UniversalFactoryBlockEntity.ALTAR_TARGET_BUTTON || id >= 100 && id < 304;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < UniversalFactoryBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(original, UniversalFactoryBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.getItem() instanceof IUpgradeItem upgradeItem
              && (upgradeItem.getUpgradeType(original) == Upgrade.SPEED || upgradeItem.getUpgradeType(original) == Upgrade.ENERGY)) {
            if (!moveItemStackTo(original, UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT, UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (UniversalFactoryBlockEntity.isBloodOrb(original)) {
            if (!moveItemStackTo(original, UniversalFactoryBlockEntity.CATALYST_SLOT, UniversalFactoryBlockEntity.CATALYST_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 0, UniversalFactoryBlockEntity.INPUT_COUNT, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return machine != null && machine.isMenuValid(player);
    }

    public int energy() { return data.get(0); }
    public int maxEnergy() { return data.get(1); }
    public int inputFluid() { return data.get(2); }
    public int inputFluidMax() { return data.get(3); }
    public int outputFluid() { return data.get(4); }
    public int outputFluidMax() { return data.get(5); }
    public int progress() { return data.get(6); }
    public int maxProgress() { return data.get(7); }
    public boolean autoOutput() { return data.get(9) != 0; }
    public int redstoneMode() { return data.get(10); }
    public FactoryMode mode() { return FactoryMode.values()[Math.min(data.get(11), FactoryMode.values().length - 1)]; }
    public FactoryTier tier() { return FactoryTier.values()[Math.min(data.get(12), FactoryTier.values().length - 1)]; }
    public boolean modeLocked() { return data.get(13) != 0; }
    public FluidStack inputFluidStack() { return fluidStack(data.get(14), inputFluid()); }
    public FluidStack outputFluidStack() { return fluidStack(data.get(15), outputFluid()); }
    public int activeInputMask() { return data.get(16); }
    public int speedUpgrades() { return data.get(17); }
    public int energyUpgrades() { return data.get(18); }
    public int lastEnergyUsed() { return data.get(43); }
    public int mechanicalLp() { return data.get(45); }
    public int mechanicalLpCapacity() { return data.get(46); }
    public int mechanicalLpRate() { return data.get(47); }
    public GasStack willGasStack() { return gasStack(data.get(48), data.get(49)); }
    public int willGasCapacity() { return data.get(50); }
    public int altarTarget() { return data.get(51); }
    public ItemStack altarTargetStack() { return UniversalFactoryBlockEntity.altarTargetStack(altarTarget()); }
    public int recipeWater() { return data.get(52); }
    public int recipeWaterMax() { return data.get(53); }
    public FluidStack recipeWaterStack() { return fluidStack(data.get(54), recipeWater()); }
    public int upgradeTicks() { return data.get(55); }
    public ProcessingStatus processingStatus() {
        int value = data.get(44);
        return ProcessingStatus.values()[Math.min(Math.max(value, 0), ProcessingStatus.values().length - 1)];
    }

    public ConnectionMode sideMode(MachineResource resource, RelativeMachineSide side) {
        int index = 19 + resource.ordinal() * RelativeMachineSide.values().length + side.ordinal();
        int value = data.get(index);
        return ConnectionMode.values()[Math.min(Math.max(value, 0), ConnectionMode.values().length - 1)];
    }

    private static FluidStack fluidStack(int encodedId, int amount) {
        if (encodedId <= 0 || amount <= 0) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(encodedId - 1);
        return fluid == null || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    private static GasStack gasStack(int encodedId, int amount) {
        if (encodedId <= 0 || amount <= 0) return GasStack.EMPTY;
        EnumDemonWillType[] types = EnumDemonWillType.values();
        return encodedId > types.length ? GasStack.EMPTY : WillGas.stack(types[encodedId - 1], amount);
    }
}
