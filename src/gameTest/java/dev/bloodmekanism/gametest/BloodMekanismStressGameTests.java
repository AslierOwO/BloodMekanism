package dev.bloodmekanism.gametest;

import appeng.api.config.Actionable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.BaseActionSource;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismStressGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(5, 2, 5);
    private static final BlockPos EXPORT_POS = new BlockPos(4, 2, 5);
    private static final BlockPos IMPORT_POS = new BlockPos(6, 2, 5);
    private static final BlockPos DRIVE_POS = new BlockPos(5, 2, 4);

    private BloodMekanismStressGameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 7_000)
    public static void ae2ProcessesTwentyThousandBlankSlates(GameTestHelper helper) {
        final int target = 20_000;
        UniversalFactoryBlockEntity machine = placeUpgradedFactory(helper,
              ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.ABSOLUTE).get());
        NetworkRig network = createNetwork(helper, Items.STONE);
        StressState state = new StressState("20k altar", target);

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  requireOnline(helper, network);
                  insertExact(helper, network, Items.STONE, target);
                  sustain(helper, machine, network, BloodMagicItems.SLATE.get(), state, true);
              })
              .thenWaitUntil(() -> awaitTarget(helper, network, BloodMagicItems.SLATE.get(), state))
              .thenExecute(() -> {
                  long consumedLife = state.lifeSupplied - machine.inputTank().getFluidAmount();
                  long consumedEnergy = state.energySupplied - machine.energy().getEnergyStored();
                  require(consumedLife == (long) target * 1_000, helper,
                        "20k altar consumed " + consumedLife + " mB instead of " + (long) target * 1_000);
                  require(consumedEnergy == (long) target * 8_000, helper,
                        "20k altar consumed " + consumedEnergy + " FE instead of " + (long) target * 8_000);
                  require(stored(network, Items.STONE) == 0, helper, "20k altar left stone in ME storage");
                  require(countMachineInputs(machine) == 0, helper, "20k altar left stone inside the factory");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 7_000)
    public static void ae2ProcessesTenThousandAlchemyArrays(GameTestHelper helper) {
        final int target = 10_000;
        UniversalFactoryBlockEntity machine = placeUpgradedFactory(helper,
              ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_ARRAY).get());
        NetworkRig network = createNetwork(helper, Items.REDSTONE, BloodMagicItems.SLATE.get());
        StressState state = new StressState("10k array", target);

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  requireOnline(helper, network);
                  insertExact(helper, network, Items.REDSTONE, target);
                  insertExact(helper, network, BloodMagicItems.SLATE.get(), target);
                  sustain(helper, machine, network, BloodMagicItems.DIVINATION_SIGIL.get(), state, false);
              })
              .thenWaitUntil(() -> awaitTarget(helper, network, BloodMagicItems.DIVINATION_SIGIL.get(), state))
              .thenExecute(() -> {
                  long consumedEnergy = state.energySupplied - machine.energy().getEnergyStored();
                  require(consumedEnergy == (long) target * 4_000, helper,
                        "10k array consumed " + consumedEnergy + " FE instead of " + (long) target * 4_000);
                  require(stored(network, Items.REDSTONE) == 0, helper, "10k array left Redstone in ME storage");
                  require(stored(network, BloodMagicItems.SLATE.get()) == 0, helper,
                        "10k array left Blank Slates in ME storage");
                  require(countMachineInputs(machine) == 0, helper, "10k array left ingredients inside the factory");
              })
              .thenSucceed();
    }

    private static UniversalFactoryBlockEntity placeUpgradedFactory(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        helper.setBlock(MACHINE_POS, block);
        UniversalFactoryBlockEntity machine = requireBlockEntity(helper, MACHINE_POS, UniversalFactoryBlockEntity.class);
        machine.addUpgrades(Upgrade.SPEED, 8);
        machine.addUpgrades(Upgrade.ENERGY, 8);
        return machine;
    }

    private static NetworkRig createNetwork(GameTestHelper helper, Item... exportItems) {
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = requireBlockEntity(helper, DRIVE_POS, DriveBlockEntity.class);
        BlockOrientation.get(Direction.NORTH).setOn(drive);
        drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        helper.setBlock(DRIVE_POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());

        placeCable(helper, new BlockPos(4, 2, 4));
        placeCable(helper, new BlockPos(6, 2, 4));
        placeCable(helper, EXPORT_POS);
        placeCable(helper, IMPORT_POS);

        ExportBusPart exportBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(EXPORT_POS), Direction.EAST,
              null, partItem(AEParts.EXPORT_BUS.stack().getItem()));
        ImportBusPart importBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(IMPORT_POS), Direction.WEST,
              null, partItem(AEParts.IMPORT_BUS.stack().getItem()));
        if (exportBus == null || importBus == null) helper.fail("Failed to place accelerated AE2 buses");
        exportBus.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4));
        importBus.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4));
        for (int i = 0; i < exportItems.length; i++) {
            exportBus.getConfig().setStack(i, new GenericStack(AEItemKey.of(exportItems[i]), 1));
        }
        return new NetworkRig(drive, exportBus, importBus);
    }

    private static void sustain(GameTestHelper helper, UniversalFactoryBlockEntity machine, NetworkRig network,
          Item output, StressState state, boolean supplyLife) {
        if (state.complete) return;
        if (supplyLife) {
            int filled = machine.inputTank().fill(
                  new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), machine.inputTank().getCapacity()),
                  IFluidHandler.FluidAction.EXECUTE);
            state.lifeSupplied += filled;
        }
        int energyNeeded = machine.energy().getMaxEnergyStored() - machine.energy().getEnergyStored();
        if (energyNeeded > 0) {
            machine.energy().setStored(machine.energy().getMaxEnergyStored());
            state.energySupplied += energyNeeded;
        }

        long produced = stored(network, output);
        if (produced > state.lastProduced) {
            state.lastProduced = produced;
            state.stagnantTicks = 0;
            while (produced >= state.nextMilestone) {
                BloodMekanism.LOGGER.info("GameTest {} progress: {}/{}", state.label, state.nextMilestone, state.target);
                state.nextMilestone += 1_000;
            }
        } else if (++state.stagnantTicks > 400) {
            helper.fail(state.label + " made no output progress for 400 ticks at " + produced + "/" + state.target
                  + ", status=" + machine.data.get(44)
                  + ", inputs=" + BloodMekanismCoverageGameTests.describeInputs(machine)
                  + ", ME redstone=" + stored(network, Items.REDSTONE)
                  + ", ME slate=" + stored(network, BloodMagicItems.SLATE.get()));
            return;
        }

        if (produced >= state.target) {
            state.complete = true;
            return;
        }
        helper.runAfterDelay(1, () -> sustain(helper, machine, network, output, state, supplyLife));
    }

    private static void awaitTarget(GameTestHelper helper, NetworkRig network, Item output, StressState state) {
        long produced = stored(network, output);
        if (produced != state.target) helper.fail(state.label + " output " + produced + "/" + state.target);
    }

    private static void requireOnline(GameTestHelper helper, NetworkRig network) {
        require(network.drive.getMainNode().isOnline(), helper, "AE2 drive is offline");
        require(network.exportBus.getMainNode().isOnline(), helper, "AE2 export bus is offline");
        require(network.importBus.getMainNode().isOnline(), helper, "AE2 import bus is offline");
    }

    private static void insertExact(GameTestHelper helper, NetworkRig network, Item item, long amount) {
        var grid = network.exportBus.getMainNode().getGrid();
        if (grid == null) helper.fail("AE2 grid disconnected while inserting " + item);
        long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(item), amount,
              Actionable.MODULATE, new BaseActionSource());
        require(inserted == amount, helper, "Inserted " + inserted + "/" + amount + " of " + item + " into AE2");
    }

    private static long stored(NetworkRig network, Item item) {
        var grid = network.exportBus.getMainNode().getGrid();
        if (grid == null) return -1;
        return grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(item));
    }

    private static int countMachineInputs(UniversalFactoryBlockEntity machine) {
        int count = 0;
        for (int slot = 0; slot < UniversalFactoryBlockEntity.INPUT_COUNT; slot++) {
            count += machine.inventory().getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null,
              AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) helper.fail("Failed to place AE2 cable at " + pos);
    }

    @SuppressWarnings("unchecked")
    private static <T extends appeng.api.parts.IPart> IPartItem<T> partItem(Item item) {
        return (IPartItem<T>) item;
    }

    private static <T> T requireBlockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Object blockEntity = helper.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) helper.fail("Expected " + type.getSimpleName() + " at " + pos);
        return type.cast(blockEntity);
    }

    private static void require(boolean condition, GameTestHelper helper, String message) {
        if (!condition) helper.fail(message);
    }

    private record NetworkRig(DriveBlockEntity drive, ExportBusPart exportBus, ImportBusPart importBus) {
    }

    private static final class StressState {
        private final String label;
        private final int target;
        private long lifeSupplied;
        private long energySupplied;
        private long lastProduced = -1;
        private long nextMilestone = 1_000;
        private int stagnantTicks;
        private boolean complete;

        private StressState(String label, int target) {
            this.label = label;
            this.target = target;
        }
    }
}
