package dev.bloodmekanism.menu;

import dev.bloodmekanism.machine.ConnectionMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.Upgrade;
import mekanism.common.item.interfaces.IUpgradeItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class HemogenicMenu extends AbstractContainerMenu {
    private final HemogenicBlockEntity machine;
    private final ContainerData data;

    public static HemogenicMenu clientConstructor(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new HemogenicMenu(id, inventory,
              (HemogenicBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()), true);
    }

    public HemogenicMenu(int id, Inventory playerInventory, HemogenicBlockEntity machine) {
        this(id, playerInventory, machine, false);
    }

    private HemogenicMenu(int id, Inventory playerInventory, HemogenicBlockEntity machine, boolean clientSide) {
        super(ModContent.HEMOGENIC_MENU.get(), id);
        this.machine = machine;
        this.data = clientSide || machine == null ? new SimpleContainerData(37) : machine.data;
        checkContainerDataCount(data, 37);
        addDataSlots(data);
        if (machine != null) {
            addSlot(new SlotItemHandler(machine.inventory(), HemogenicBlockEntity.INPUT_SLOT, 57, 35));
            addSlot(new UpgradeSlot(machine.inventory(), HemogenicBlockEntity.UPGRADE_INPUT_SLOT, false));
            addSlot(new UpgradeSlot(machine.inventory(), HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT, true));
        }
        addPlayerInventory(playerInventory, 38, 91);
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
        return id >= 1 && id <= 2 || id >= 4 && id <= 7 || id >= 100 && id < 304;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < HemogenicBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(original, HemogenicBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.getItem() instanceof IUpgradeItem upgradeItem
              && (upgradeItem.getUpgradeType(original) == Upgrade.SPEED || upgradeItem.getUpgradeType(original) == Upgrade.ENERGY)) {
            if (!moveItemStackTo(original, HemogenicBlockEntity.UPGRADE_INPUT_SLOT, HemogenicBlockEntity.UPGRADE_INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, HemogenicBlockEntity.INPUT_SLOT, HemogenicBlockEntity.INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
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
    public int progress() { return data.get(4); }
    public int maxProgress() { return data.get(5); }
    public boolean autoOutput() { return data.get(7) != 0; }
    public int redstoneMode() { return data.get(8); }
    public int speedUpgrades() { return data.get(9); }
    public int energyUpgrades() { return data.get(10); }
    public int lastEnergyUsed() { return data.get(35); }
    public int upgradeTicks() { return data.get(36); }

    public ConnectionMode sideMode(MachineResource resource, RelativeMachineSide side) {
        int index = 11 + resource.ordinal() * RelativeMachineSide.values().length + side.ordinal();
        int value = data.get(index);
        return ConnectionMode.values()[Math.min(Math.max(value, 0), ConnectionMode.values().length - 1)];
    }
}
