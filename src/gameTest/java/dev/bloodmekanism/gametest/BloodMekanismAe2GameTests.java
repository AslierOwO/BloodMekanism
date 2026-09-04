package dev.bloodmekanism.gametest;

import appeng.api.config.Actionable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
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
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismAe2GameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(5, 2, 5);
    private static final BlockPos EXPORT_POS = new BlockPos(4, 2, 5);
    private static final BlockPos IMPORT_POS = new BlockPos(6, 2, 5);
    private static final BlockPos DRIVE_POS = new BlockPos(5, 2, 4);

    private BloodMekanismAe2GameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 700)
    public static void ae2ExportsInputAndImportsAltarOutput(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.BASIC).get());
        UniversalFactoryBlockEntity machine = requireBlockEntity(helper, MACHINE_POS,
              UniversalFactoryBlockEntity.class);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        machine.inputTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 4_000),
              IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = requireBlockEntity(helper, DRIVE_POS, DriveBlockEntity.class);
        // setBlock bypasses placement orientation and leaves the drive facing down,
        // which prevents it from connecting to the energy cell directly below.
        BlockOrientation.get(Direction.NORTH).setOn(drive);
        drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        helper.setBlock(DRIVE_POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());

        placeCable(helper, new BlockPos(4, 2, 4));
        placeCable(helper, new BlockPos(6, 2, 4));
        placeCable(helper, EXPORT_POS);
        placeCable(helper, IMPORT_POS);
        ExportBusPart exportBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(EXPORT_POS), Direction.EAST,
              null, partItem(AEParts.EXPORT_BUS.stack().getItem()));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(IMPORT_POS), Direction.WEST,
              null, partItem(AEParts.IMPORT_BUS.stack().getItem()));
        if (exportBus == null) helper.fail("Failed to place AE2 Export Bus");
        exportBus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 1));

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  var grid = exportBus.getMainNode().getGrid();
                  if (grid == null) helper.fail("AE2 network did not form");
                  long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(Items.STONE), 1,
                        Actionable.MODULATE, new BaseActionSource());
                  BloodMekanismMachineGameTests.require(inserted == 1, helper,
                        "Could not insert stone into AE2 storage (drive online: " + drive.getMainNode().isOnline()
                              + ", export bus online: " + exportBus.getMainNode().isOnline() + ")");
              })
              .thenWaitUntil(() -> {
                  var grid = exportBus.getMainNode().getGrid();
                  if (grid == null) helper.fail("AE2 network disconnected");
                  long slateCount = grid.getStorageService().getInventory().getAvailableStacks()
                        .get(AEItemKey.of(BloodMagicItems.SLATE.get()));
                  if (slateCount != 1) {
                      helper.fail("Waiting for AE2 to import one Blank Slate; stored count is " + slateCount);
                  }
              })
              .thenExecute(() -> {
                  BloodMekanismMachineGameTests.require(machine.inputTank().getFluidAmount() == 3_000, helper,
                        "Altar factory did not consume exactly 1,000 mB Life Essence");
                  BloodMekanismMachineGameTests.require(machine.energy().getEnergyStored()
                              < machine.energy().getMaxEnergyStored(), helper,
                        "Altar factory did not consume FE");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 700)
    public static void ae2SuppliesWaterFluidForReagentWater(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(5, 2, 5);
        BlockPos itemExportPos = new BlockPos(4, 2, 5);
        BlockPos fluidExportPos = new BlockPos(6, 2, 5);
        BlockPos importPos = new BlockPos(5, 2, 4);
        BlockPos drivePos = new BlockPos(5, 2, 2);

        helper.setBlock(machinePos, ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_TABLE).get());
        UniversalFactoryBlockEntity machine = requireBlockEntity(helper, machinePos,
              UniversalFactoryBlockEntity.class);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        machine.inputTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 300),
              IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = requireBlockEntity(helper, drivePos, DriveBlockEntity.class);
        BlockOrientation.get(Direction.NORTH).setOn(drive);
        drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        drive.getInternalInventory().addItems(AEItems.FLUID_CELL_64K.stack());
        helper.setBlock(drivePos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());

        placeCable(helper, new BlockPos(5, 2, 3));
        placeCable(helper, new BlockPos(4, 2, 4));
        placeCable(helper, new BlockPos(6, 2, 4));
        placeCable(helper, itemExportPos);
        placeCable(helper, fluidExportPos);
        placeCable(helper, importPos);
        ExportBusPart itemExport = PartHelper.setPart(helper.getLevel(), helper.absolutePos(itemExportPos),
              Direction.EAST, null, partItem(AEParts.EXPORT_BUS.stack().getItem()));
        ExportBusPart fluidExport = PartHelper.setPart(helper.getLevel(), helper.absolutePos(fluidExportPos),
              Direction.WEST, null, partItem(AEParts.EXPORT_BUS.stack().getItem()));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(importPos), Direction.SOUTH,
              null, partItem(AEParts.IMPORT_BUS.stack().getItem()));
        if (itemExport == null || fluidExport == null) helper.fail("Failed to place AE2 Export Buses");
        itemExport.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.SUGAR), 1));
        fluidExport.getConfig().setStack(0, new GenericStack(AEFluidKey.of(Fluids.WATER), AEFluidKey.AMOUNT_BUCKET));

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  var grid = itemExport.getMainNode().getGrid();
                  if (grid == null) helper.fail("AE2 mixed item/fluid network did not form");
                  long sugarInserted = grid.getStorageService().getInventory().insert(AEItemKey.of(Items.SUGAR), 1,
                        Actionable.MODULATE, new BaseActionSource());
                  long waterInserted = grid.getStorageService().getInventory().insert(AEFluidKey.of(Fluids.WATER),
                        2L * AEFluidKey.AMOUNT_BUCKET, Actionable.MODULATE, new BaseActionSource());
                  BloodMekanismMachineGameTests.require(sugarInserted == 1, helper,
                        "Could not insert sugar into AE2 storage");
                  BloodMekanismMachineGameTests.require(waterInserted == 2L * AEFluidKey.AMOUNT_BUCKET, helper,
                        "Could not insert 2,000 mB water into AE2 storage");
              })
              .thenWaitUntil(() -> {
                  var grid = itemExport.getMainNode().getGrid();
                  if (grid == null) helper.fail("AE2 mixed item/fluid network disconnected");
                  long reagentCount = grid.getStorageService().getInventory().getAvailableStacks()
                        .get(AEItemKey.of(BloodMagicItems.REAGENT_WATER.get()));
                  if (reagentCount != 1) {
                      helper.fail("Waiting for AE2 to import one Water Reagent; stored count is " + reagentCount
                            + ", machine water=" + machine.recipeWaterTank().getFluidAmount());
                  }
              })
              .thenExecute(() -> {
                  BloodMekanismMachineGameTests.require(machine.inputTank().isEmpty(), helper,
                        "AE2 Water Reagent recipe did not consume exactly 300 LP");
                  BloodMekanismMachineGameTests.require(machine.recipeWaterTank().isEmpty(), helper,
                        "AE2 Water Reagent recipe did not consume exactly 2,000 mB water");
              })
              .thenSucceed();
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null,
              AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) helper.fail("Failed to place AE2 cable at " + pos);
    }

    @SuppressWarnings("unchecked")
    private static <T extends appeng.api.parts.IPart> IPartItem<T> partItem(net.minecraft.world.item.Item item) {
        return (IPartItem<T>) item;
    }

    private static <T> T requireBlockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Object blockEntity = helper.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) {
            helper.fail("Expected " + type.getSimpleName() + " at " + pos + ", got " + blockEntity);
        }
        return type.cast(blockEntity);
    }
}
