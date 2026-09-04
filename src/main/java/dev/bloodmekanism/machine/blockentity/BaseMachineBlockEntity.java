package dev.bloodmekanism.machine.blockentity;

import dev.bloodmekanism.machine.ConnectionMode;
import dev.bloodmekanism.machine.MachineBlock;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RedstoneMode;
import dev.bloodmekanism.machine.RelativeMachineSide;
import mekanism.api.Upgrade;
import mekanism.common.item.interfaces.IUpgradeItem;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public abstract class BaseMachineBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    public static final int SIDE_BUTTON_BASE = 100;
    public static final int REVERSE_SIDE_BUTTON_BASE = 200;
    public static final int CLEAR_SIDES_BUTTON_BASE = 300;
    public static final int UPGRADE_BUTTON_BASE = 4;
    public static final int UPGRADE_BUTTON_END = 8;
    public static final int UPGRADE_TICKS_REQUIRED = 20;

    protected final ManagedEnergyStorage energy;
    protected final LazyOptional<IEnergyStorage> energyCapability;
    protected final ItemStackHandler inventory;
    protected final int speedUpgradeSlot;
    protected final int energyUpgradeSlot;
    protected LazyOptional<IItemHandler> itemCapability;
    protected boolean autoOutput = true;
    protected RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    protected int transferCooldown;
    protected int lastEnergyUsed;

    private final int baseEnergyCapacity;
    private int installedSpeedUpgrades;
    private int installedEnergyUpgrades;
    private int upgradeTicks;
    private final Map<Direction, LazyOptional<IItemHandler>> sidedItemCapabilities = new EnumMap<>(Direction.class);
    private final Map<Direction, LazyOptional<IEnergyStorage>> sidedEnergyCapabilities = new EnumMap<>(Direction.class);
    private final Map<MachineResource, EnumMap<RelativeMachineSide, ConnectionMode>> sideConfiguration = new EnumMap<>(MachineResource.class);
    private Component customName;

    protected BaseMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, int energyCapacity,
          int speedUpgradeSlot, int energyUpgradeSlot) {
        super(type, pos, state);
        this.baseEnergyCapacity = energyCapacity;
        this.speedUpgradeSlot = speedUpgradeSlot;
        this.energyUpgradeSlot = energyUpgradeSlot;
        this.energy = new ManagedEnergyStorage(energyCapacity, Math.max(1_000, energyCapacity / 20), this::setChanged);
        this.energyCapability = LazyOptional.of(() -> createEnergyHandler(null));
        initializeSideConfiguration();
        this.inventory = new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return BaseMachineBlockEntity.this.isSlotItemValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return BaseMachineBlockEntity.this.isUpgradeSlot(slot) ? Upgrade.SPEED.getMax() : super.getSlotLimit(slot);
            }

            @Override
            protected void onContentsChanged(int slot) {
                BaseMachineBlockEntity.this.setChanged();
            }
        };
        this.itemCapability = LazyOptional.of(() -> createAutomationItemHandler(null));
        for (Direction direction : Direction.values()) {
            sidedItemCapabilities.put(direction, LazyOptional.of(() -> createAutomationItemHandler(direction)));
            sidedEnergyCapabilities.put(direction, LazyOptional.of(() -> createEnergyHandler(direction)));
        }
    }

    private void initializeSideConfiguration() {
        for (MachineResource resource : MachineResource.values()) {
            EnumMap<RelativeMachineSide, ConnectionMode> sides = new EnumMap<>(RelativeMachineSide.class);
            ConnectionMode defaultMode = resource == MachineResource.ENERGY ? ConnectionMode.INPUT : ConnectionMode.BOTH;
            for (RelativeMachineSide side : RelativeMachineSide.values()) sides.put(side, defaultMode);
            sideConfiguration.put(resource, sides);
        }
    }

    public abstract void serverTick();
    protected abstract boolean isItemValid(int slot, ItemStack stack);
    protected abstract boolean canAutomationExtract(int slot);
    protected abstract boolean canAutomationInsert(int slot);

    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return canAutomationInsert(slot);
    }

    private boolean isSlotItemValid(int slot, ItemStack stack) {
        if (slot == speedUpgradeSlot) return upgradeType(stack) != null;
        if (slot == energyUpgradeSlot) return false;
        return isItemValid(slot, stack);
    }

    private static boolean isUpgrade(ItemStack stack, Upgrade expected) {
        return upgradeType(stack) == expected;
    }

    @Nullable
    private static Upgrade upgradeType(ItemStack stack) {
        if (!(stack.getItem() instanceof IUpgradeItem upgradeItem)) return null;
        Upgrade upgrade = upgradeItem.getUpgradeType(stack);
        return upgrade == Upgrade.SPEED || upgrade == Upgrade.ENERGY ? upgrade : null;
    }

    protected boolean isUpgradeSlot(int slot) {
        return slot == speedUpgradeSlot || slot == energyUpgradeSlot;
    }

    /** Matches Mekanism's TileComponentUpgrade installation lifecycle. */
    protected final void tickUpgrades() {
        if (speedUpgradeSlot < 0 || energyUpgradeSlot < 0) return;
        ItemStack stack = inventory.getStackInSlot(speedUpgradeSlot);
        Upgrade upgrade = upgradeType(stack);
        if (upgrade != null && upgradeCount(upgrade) < upgrade.getMax()) {
            if (upgradeTicks < UPGRADE_TICKS_REQUIRED) {
                upgradeTicks++;
                setChanged();
                return;
            }
            int added = addUpgrades(upgrade, stack.getCount());
            if (added > 0) inventory.extractItem(speedUpgradeSlot, added, false);
        }
        if (upgradeTicks != 0) {
            upgradeTicks = 0;
            setChanged();
        }
    }

    protected IItemHandler createAutomationItemHandler(@Nullable Direction side) {
        return new IItemHandler() {
            @Override public int getSlots() { return inventory.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return allows(side, MachineResource.ITEM, true) && canAutomationInsert(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return allows(side, MachineResource.ITEM, false) && canAutomationExtract(slot) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return allows(side, MachineResource.ITEM, true) && canAutomationInsert(slot, stack) && inventory.isItemValid(slot, stack);
            }
        };
    }

    private IEnergyStorage createEnergyHandler(@Nullable Direction side) {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                return allows(side, MachineResource.ENERGY, true) ? energy.receiveEnergy(maxReceive, simulate) : 0;
            }
            @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return energy.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return allows(side, MachineResource.ENERGY, true); }
        };
    }

    protected boolean allows(@Nullable Direction worldSide, MachineResource resource, boolean input) {
        if (worldSide == null) return true;
        ConnectionMode mode = sideMode(resource, RelativeMachineSide.fromWorld(worldSide, facing()));
        return input ? mode.allowsInput() : mode.allowsOutput();
    }

    protected Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    protected boolean redstoneAllowsWork() {
        return level != null && redstoneMode.allows(level.hasNeighborSignal(worldPosition));
    }

    protected void updateActive(boolean active) {
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, active), 3);
        }
    }

    protected void transferItems() {
        if (level == null || ++transferCooldown < 10) return;
        transferCooldown = 0;
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null) continue;
            neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).ifPresent(handler -> {
                if (autoOutput && allows(direction, MachineResource.ITEM, false)) pushItems(handler);
            });
        }
    }

    private void pushItems(IItemHandler target) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!canAutomationExtract(slot)) continue;
            ItemStack simulated = inventory.extractItem(slot, 64, true);
            if (simulated.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, simulated, true);
            int accepted = simulated.getCount() - remainder.getCount();
            if (accepted > 0) {
                ItemStack extracted = inventory.extractItem(slot, accepted, false);
                ItemHandlerHelper.insertItemStacked(target, extracted, false);
            }
        }
    }

    public void handleButton(int id) {
        if (id >= CLEAR_SIDES_BUTTON_BASE && id < CLEAR_SIDES_BUTTON_BASE + MachineResource.values().length) {
            MachineResource resource = MachineResource.values()[id - CLEAR_SIDES_BUTTON_BASE];
            for (RelativeMachineSide side : RelativeMachineSide.values()) {
                sideConfiguration.get(resource).put(side, ConnectionMode.NONE);
            }
        } else if (id >= REVERSE_SIDE_BUTTON_BASE) {
            int encoded = id - REVERSE_SIDE_BUTTON_BASE;
            int resourceIndex = encoded / 10;
            int sideIndex = encoded % 10;
            if (resourceIndex >= MachineResource.values().length || sideIndex >= RelativeMachineSide.values().length) return;
            MachineResource resource = MachineResource.values()[resourceIndex];
            RelativeMachineSide side = RelativeMachineSide.values()[sideIndex];
            ConnectionMode current = sideMode(resource, side);
            sideConfiguration.get(resource).put(side, resource == MachineResource.ENERGY
                  ? current == ConnectionMode.INPUT ? ConnectionMode.NONE : ConnectionMode.INPUT
                  : current.previous());
        } else if (id >= SIDE_BUTTON_BASE) {
            int encoded = id - SIDE_BUTTON_BASE;
            int resourceIndex = encoded / 10;
            int sideIndex = encoded % 10;
            if (resourceIndex >= MachineResource.values().length || sideIndex >= RelativeMachineSide.values().length) return;
            MachineResource resource = MachineResource.values()[resourceIndex];
            RelativeMachineSide side = RelativeMachineSide.values()[sideIndex];
            ConnectionMode current = sideMode(resource, side);
            sideConfiguration.get(resource).put(side, resource == MachineResource.ENERGY
                  ? current == ConnectionMode.INPUT ? ConnectionMode.NONE : ConnectionMode.INPUT
                  : current.next());
        } else {
            switch (id) {
                case 1 -> autoOutput = !autoOutput;
                case 2 -> redstoneMode = redstoneMode.next();
                default -> { return; }
            }
        }
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void handleUpgradeButton(Player player, int id) {
        if (id < UPGRADE_BUTTON_BASE || id >= UPGRADE_BUTTON_END) return;
        Upgrade upgrade = id < UPGRADE_BUTTON_BASE + 2 ? Upgrade.SPEED : Upgrade.ENERGY;
        boolean removeAll = (id - UPGRADE_BUTTON_BASE) % 2 == 1;
        removeUpgrades(upgrade, removeAll);
    }

    public static int removeUpgradeButtonId(Upgrade upgrade, boolean removeAll) {
        int typeOffset = upgrade == Upgrade.ENERGY ? 2 : 0;
        return UPGRADE_BUTTON_BASE + typeOffset + (removeAll ? 1 : 0);
    }

    public static int sideButtonId(MachineResource resource, RelativeMachineSide side) {
        return SIDE_BUTTON_BASE + resource.ordinal() * 10 + side.ordinal();
    }

    public static int reverseSideButtonId(MachineResource resource, RelativeMachineSide side) {
        return REVERSE_SIDE_BUTTON_BASE + resource.ordinal() * 10 + side.ordinal();
    }

    public static int clearSidesButtonId(MachineResource resource) {
        return CLEAR_SIDES_BUTTON_BASE + resource.ordinal();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        // Retain the legacy key so older builds also keep active pulling disabled.
        tag.putBoolean("AutoInput", false);
        tag.putBoolean("AutoOutput", autoOutput);
        tag.putInt("RedstoneMode", redstoneMode.ordinal());
        tag.putInt("InstalledSpeedUpgrades", installedSpeedUpgrades);
        tag.putInt("InstalledEnergyUpgrades", installedEnergyUpgrades);
        tag.putInt("UpgradeTicks", upgradeTicks);
        CompoundTag configurationTag = new CompoundTag();
        for (MachineResource resource : MachineResource.values()) {
            CompoundTag resourceTag = new CompoundTag();
            for (RelativeMachineSide side : RelativeMachineSide.values()) {
                resourceTag.putInt(side.name(), sideMode(resource, side).ordinal());
            }
            configurationTag.put(resource.name(), resourceTag);
        }
        tag.put("SideConfiguration", configurationTag);
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag inventoryTag = tag.getCompound("Inventory");
        int expectedSlots = inventory.getSlots();
        int savedSlots = inventoryTag.contains("Size") ? inventoryTag.getInt("Size") : expectedSlots;
        if (savedSlots == expectedSlots) {
            inventory.deserializeNBT(inventoryTag);
        } else {
            ItemStackHandler savedInventory = new ItemStackHandler();
            savedInventory.deserializeNBT(inventoryTag);
            inventory.setSize(expectedSlots);
            for (int slot = 0; slot < Math.min(expectedSlots, savedInventory.getSlots()); slot++) {
                inventory.setStackInSlot(slot, savedInventory.getStackInSlot(slot));
            }
        }
        boolean hasInstalledUpgradeData = tag.contains("InstalledSpeedUpgrades") || tag.contains("InstalledEnergyUpgrades");
        installedSpeedUpgrades = clampUpgradeCount(tag.getInt("InstalledSpeedUpgrades"), Upgrade.SPEED);
        installedEnergyUpgrades = clampUpgradeCount(tag.getInt("InstalledEnergyUpgrades"), Upgrade.ENERGY);
        upgradeTicks = Math.min(Math.max(tag.getInt("UpgradeTicks"), 0), UPGRADE_TICKS_REQUIRED);
        if (!hasInstalledUpgradeData) migrateLegacyUpgradeSlots();
        refreshEnergyCapacity();
        energy.setStored(tag.getInt("Energy"));
        autoOutput = tag.getBoolean("AutoOutput");
        int mode = tag.getInt("RedstoneMode");
        redstoneMode = RedstoneMode.values()[Math.min(Math.max(mode, 0), RedstoneMode.values().length - 1)];
        if (tag.contains("SideConfiguration")) {
            CompoundTag configurationTag = tag.getCompound("SideConfiguration");
            for (MachineResource resource : MachineResource.values()) {
                if (!configurationTag.contains(resource.name())) continue;
                CompoundTag resourceTag = configurationTag.getCompound(resource.name());
                for (RelativeMachineSide side : RelativeMachineSide.values()) {
                    if (!resourceTag.contains(side.name())) continue;
                    int value = resourceTag.getInt(side.name());
                    sideConfiguration.get(resource).put(side, ConnectionMode.values()[Math.min(Math.max(value, 0), ConnectionMode.values().length - 1)]);
                }
            }
        }
        if (tag.contains("CustomName")) customName = Component.Serializer.fromJson(tag.getString("CustomName"));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return (side == null ? energyCapability : sidedEnergyCapabilities.get(side)).cast();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? itemCapability : sidedItemCapabilities.get(side)).cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
        itemCapability.invalidate();
        sidedItemCapabilities.values().forEach(LazyOptional::invalidate);
        sidedEnergyCapabilities.values().forEach(LazyOptional::invalidate);
    }

    private void refreshEnergyCapacity() {
        if (inventory == null) return;
        int capacity = (int) Math.min(Integer.MAX_VALUE, Math.round(baseEnergyCapacity * Math.pow(10, energyUpgradeCount() / 8.0)));
        energy.setCapacity(capacity, Math.max(1_000, capacity / 20));
    }

    public int speedUpgradeCount() { return installedSpeedUpgrades; }
    public int energyUpgradeCount() { return installedEnergyUpgrades; }
    public int upgradeTicks() { return upgradeTicks; }
    public boolean supportsUpgrades() { return speedUpgradeSlot >= 0 && energyUpgradeSlot >= 0; }
    public int lastEnergyUsed() { return lastEnergyUsed; }

    public int addUpgrades(Upgrade upgrade, int maxAvailable) {
        if ((upgrade != Upgrade.SPEED && upgrade != Upgrade.ENERGY) || maxAvailable <= 0 || speedUpgradeSlot < 0) return 0;
        int installed = upgradeCount(upgrade);
        int added = Math.min(upgrade.getMax() - installed, maxAvailable);
        if (added <= 0) return 0;
        setUpgradeCount(upgrade, installed + added);
        refreshEnergyCapacity();
        setChanged();
        return added;
    }

    private void removeUpgrades(Upgrade upgrade, boolean removeAll) {
        if (energyUpgradeSlot < 0) return;
        int installed = upgradeCount(upgrade);
        if (installed <= 0) return;
        ItemStack output = inventory.getStackInSlot(energyUpgradeSlot);
        ItemStack upgradeStack = UpgradeUtils.getStack(upgrade);
        int space = output.isEmpty() ? upgrade.getMax()
              : ItemStack.isSameItemSameTags(output, upgradeStack) ? upgrade.getMax() - output.getCount() : 0;
        int removed = Math.min(removeAll ? installed : 1, space);
        if (removed <= 0) return;
        setUpgradeCount(upgrade, installed - removed);
        if (output.isEmpty()) {
            inventory.setStackInSlot(energyUpgradeSlot, UpgradeUtils.getStack(upgrade, removed));
        } else {
            ItemStack grown = output.copy();
            grown.grow(removed);
            inventory.setStackInSlot(energyUpgradeSlot, grown);
        }
        refreshEnergyCapacity();
        setChanged();
    }

    private int upgradeCount(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? installedSpeedUpgrades : installedEnergyUpgrades;
    }

    private void setUpgradeCount(Upgrade upgrade, int count) {
        int clamped = clampUpgradeCount(count, upgrade);
        if (upgrade == Upgrade.SPEED) installedSpeedUpgrades = clamped;
        else if (upgrade == Upgrade.ENERGY) installedEnergyUpgrades = clamped;
    }

    private static int clampUpgradeCount(int count, Upgrade upgrade) {
        return Math.min(Math.max(count, 0), upgrade.getMax());
    }

    private void migrateLegacyUpgradeSlots() {
        if (speedUpgradeSlot < 0 || energyUpgradeSlot < 0) return;
        for (int slot : new int[]{speedUpgradeSlot, energyUpgradeSlot}) {
            ItemStack stack = inventory.getStackInSlot(slot);
            Upgrade upgrade = upgradeType(stack);
            if (upgrade != null) setUpgradeCount(upgrade, upgradeCount(upgrade) + stack.getCount());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        upgradeTicks = 0;
    }

    protected int upgradedDuration(int baseDuration) {
        return Math.max(1, (int) Math.ceil(baseDuration / Math.pow(10, speedUpgradeCount() / 8.0)));
    }

    protected int upgradedEnergyPerTick(int baseEnergyPerTick) {
        double exponent = (2.0 * speedUpgradeCount() - energyUpgradeCount()) / 8.0;
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.ceil(baseEnergyPerTick * Math.pow(10, exponent))));
    }

    protected int upgradedOperationEnergy(int baseEnergy) {
        double exponent = (speedUpgradeCount() - energyUpgradeCount()) / 8.0;
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.ceil(baseEnergy * Math.pow(10, exponent))));
    }

    protected double speedMultiplier() {
        return Math.pow(10, speedUpgradeCount() / 8.0);
    }

    public ConnectionMode sideMode(MachineResource resource, RelativeMachineSide side) {
        return sideConfiguration.get(resource).get(side);
    }

    public boolean isMenuValid(Player player) {
        return !isRemoved() && level != null && player.level() == level
              && level.getBlockEntity(worldPosition) == this
              && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
              worldPosition.getZ() + 0.5) <= 64;
    }

    public ItemStackHandler inventory() { return inventory; }
    public ManagedEnergyStorage energy() { return energy; }
    public boolean autoOutput() { return autoOutput; }
    public RedstoneMode redstoneMode() { return redstoneMode; }

    public Container removeContentsForDrop() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) drops.add(stack);
        }
        if (supportsUpgrades()) {
            if (installedSpeedUpgrades > 0) drops.add(UpgradeUtils.getStack(Upgrade.SPEED, installedSpeedUpgrades));
            if (installedEnergyUpgrades > 0) drops.add(UpgradeUtils.getStack(Upgrade.ENERGY, installedEnergyUpgrades));
            installedSpeedUpgrades = 0;
            installedEnergyUpgrades = 0;
        }
        collectAdditionalDrops(drops);
        return new SimpleContainer(drops.toArray(ItemStack[]::new));
    }

    protected void collectAdditionalDrops(List<ItemStack> drops) { }

    public int comparatorLevel() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(inventory.getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getSlots(); i++) stacks.set(i, inventory.getStackInSlot(i));
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(new SimpleContainer(stacks.toArray(ItemStack[]::new)));
    }

    public void setCustomName(Component name) { customName = name; }

    @Override
    public Component getDisplayName() { return customName != null ? customName : defaultName(); }

    protected abstract Component defaultName();
}
