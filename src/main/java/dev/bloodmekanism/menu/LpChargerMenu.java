package dev.bloodmekanism.menu;

import dev.bloodmekanism.machine.ConnectionMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.ProcessingStatus;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.machine.blockentity.LpChargerBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class LpChargerMenu extends AbstractContainerMenu {
    private final LpChargerBlockEntity machine;
    private final ContainerData data;

    public static LpChargerMenu clientConstructor(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new LpChargerMenu(id, inventory,
              (LpChargerBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()), true);
    }

    public LpChargerMenu(int id, Inventory playerInventory, LpChargerBlockEntity machine) {
        this(id, playerInventory, machine, false);
    }

    private LpChargerMenu(int id, Inventory playerInventory, LpChargerBlockEntity machine, boolean clientSide) {
        super(ModContent.LP_CHARGER_MENU.get(), id);
        this.machine = machine;
        this.data = clientSide || machine == null ? new SimpleContainerData(38) : machine.data;
        checkContainerDataCount(data, 38);
        addDataSlots(data);
        if (machine != null) {
            addSlot(new SlotItemHandler(machine.inventory(), LpChargerBlockEntity.INPUT_SLOT, 58, 35));
            addSlot(new SlotItemHandler(machine.inventory(), LpChargerBlockEntity.OUTPUT_SLOT, 94, 35));
        }
        addPlayerInventory(playerInventory, 38, 91);
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, left + column * 18, top + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, left + column * 18, top + 58));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine != null && !player.level().isClientSide) machine.handleButton(id);
        return id >= 1 && id <= 2 || id >= 100 && id < 304;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < LpChargerBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(original, LpChargerBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!LpChargerBlockEntity.isBloodOrb(original)
              || !moveItemStackTo(original, LpChargerBlockEntity.INPUT_SLOT, LpChargerBlockEntity.INPUT_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return machine != null && machine.isMenuValid(player);
    }

    public int energy() { return data.get(0); }
    public int maxEnergy() { return data.get(1); }
    public int fluid() { return data.get(2); }
    public int maxFluid() { return data.get(3); }
    public int orbLp() { return data.get(4); }
    public int orbCapacity() { return data.get(5); }
    public boolean autoOutput() { return data.get(7) != 0; }
    public int redstoneMode() { return data.get(8); }
    public int lastEnergyUsed() { return data.get(35); }
    public ProcessingStatus status() {
        return ProcessingStatus.values()[Math.min(Math.max(data.get(36), 0), ProcessingStatus.values().length - 1)];
    }
    public int lastTransfer() { return data.get(37); }

    public ConnectionMode sideMode(MachineResource resource, RelativeMachineSide side) {
        int index = 11 + resource.ordinal() * RelativeMachineSide.values().length + side.ordinal();
        int value = data.get(index);
        return ConnectionMode.values()[Math.min(Math.max(value, 0), ConnectionMode.values().length - 1)];
    }
}
