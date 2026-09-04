package dev.bloodmekanism.gametest;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.item.MechanicalBloodOrbItem;
import dev.bloodmekanism.item.MechanicalOrbTier;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.ProcessingStatus;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.machine.blockentity.WillGeneratorBlockEntity;
import dev.bloodmekanism.menu.UniversalFactoryMenu;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.Upgrade;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismStateGameTests {
    private BloodMekanismStateGameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyRedstoneModeGatesRealProcessing(GameTestHelper helper) {
        HemogenicBlockEntity ignoredPowered = redstoneMachine(helper, new BlockPos(2, 2, 3), 0, true);
        HemogenicBlockEntity highPowered = redstoneMachine(helper, new BlockPos(5, 2, 3), 1, true);
        HemogenicBlockEntity lowUnpowered = redstoneMachine(helper, new BlockPos(8, 2, 3), 2, false);
        HemogenicBlockEntity highUnpowered = redstoneMachine(helper, new BlockPos(2, 2, 7), 1, false);
        HemogenicBlockEntity lowPowered = redstoneMachine(helper, new BlockPos(5, 2, 7), 2, true);
        HemogenicBlockEntity never = redstoneMachine(helper, new BlockPos(8, 2, 7), 3, false);

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  require(ignoredPowered.tank().getFluidAmount() == 400, helper, "IGNORED mode did not run while powered");
                  require(highPowered.tank().getFluidAmount() == 400, helper, "HIGH mode did not run while powered");
                  require(lowUnpowered.tank().getFluidAmount() == 400, helper, "LOW mode did not run while unpowered");
                  assertRedstoneBlocked(helper, highUnpowered, "HIGH mode ran without power");
                  assertRedstoneBlocked(helper, lowPowered, "LOW mode ran while powered");
                  assertRedstoneBlocked(helper, never, "NEVER mode processed an item");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 180)
    public static void fullOutputRecoversThroughAutoOutput(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(5, 2, 5);
        UniversalFactoryBlockEntity machine = placeFactory(helper, machinePos,
              ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_ARRAY).get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.REDSTONE, 16));
        machine.inventory().setStackInSlot(1, new ItemStack(BloodMagicItems.SLATE.get(), 16));
        installUpgrades(machine, 8, 8);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        Container[] output = new Container[1];

        helper.startSequence()
              .thenIdle(30)
              .thenExecute(() -> {
                  require(countFactoryOutputs(machine, BloodMagicItems.DIVINATION_SIGIL.get()) == 9, helper,
                        "Unstackable output did not fill all nine output slots");
                  require(machine.data.get(44) == ProcessingStatus.OUTPUT_FULL.ordinal(), helper,
                        "Factory did not report OUTPUT_FULL while blocked");
                  BlockPos outputPos = machinePos.north();
                  helper.setBlock(outputPos, Blocks.CHEST);
                  output[0] = requireBlockEntity(helper, outputPos, Container.class);
              })
              .thenWaitUntil(() -> {
                  int total = countFactoryOutputs(machine, BloodMagicItems.DIVINATION_SIGIL.get())
                        + countContainer(output[0], BloodMagicItems.DIVINATION_SIGIL.get());
                  if (total != 16) helper.fail("Factory did not recover after output extraction: " + total + "/16");
              })
              .thenExecute(() -> require(countFactoryInputs(machine) == 0, helper,
                    "Factory left ingredients after output blockage recovery"))
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void removedMachineInvalidatesOpenMenu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 2, 5);
        UniversalFactoryBlockEntity machine = placeFactory(helper, pos,
              ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.BASIC).get());
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absolutePos(pos).getCenter());
        UniversalFactoryMenu menu = new UniversalFactoryMenu(1, player.getInventory(), machine);

        require(menu.stillValid(player), helper, "Placed machine menu was not valid");
        helper.setBlock(pos, Blocks.AIR);
        require(!menu.stillValid(player), helper, "Removed machine left its menu valid");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void machineDropDrainsVisibleAndBufferedContents(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeFactory(helper, new BlockPos(5, 2, 5),
              ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.ABSOLUTE).get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.STONE, 3));
        machine.addUpgrades(Upgrade.SPEED, 4);
        machine.addUpgrades(Upgrade.ENERGY, 6);

        ItemStackHandler buffered = new ItemStackHandler(5);
        buffered.setStackInSlot(0, new ItemStack(BloodMagicItems.SLATE.get(), 2));
        CompoundTag state = machine.saveWithFullMetadata();
        state.put("AltarBuffer", buffered.serializeNBT());
        machine.load(state);

        Container drops = machine.removeContentsForDrop();
        require(countContainer(drops, Items.STONE) == 3, helper, "Visible input was not included in machine drops");
        require(countContainer(drops, BloodMagicItems.SLATE.get()) == 2, helper,
              "Hidden altar buffer was not included in machine drops");
        require(countContainer(drops, MekanismItems.SPEED_UPGRADE.get()) == 4, helper,
              "Installed speed upgrades were not included in machine drops");
        require(countContainer(drops, MekanismItems.ENERGY_UPGRADE.get()) == 6, helper,
              "Installed energy upgrades were not included in machine drops");
        require(countFactoryInputs(machine) == 0 && machine.speedUpgradeCount() == 0
                    && machine.energyUpgradeCount() == 0, helper,
              "Machine retained visible contents after preparing drops");

        ItemStackHandler remainingBuffer = new ItemStackHandler(5);
        remainingBuffer.deserializeNBT(machine.saveWithFullMetadata().getCompound("AltarBuffer"));
        require(remainingBuffer.getStackInSlot(0).isEmpty(), helper,
              "Machine retained hidden altar contents after preparing drops");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 180)
    public static void zeroFourEightUpgradesScaleTimeAndEnergy(GameTestHelper helper) {
        int[] counts = {0, 4, 8};
        int[] expectedDurations = {120, 38, 12};
        int[] expectedEnergy = {8_040, 8_056, 8_040};
        HemogenicBlockEntity[] machines = new HemogenicBlockEntity[counts.length];
        int[] startingEnergy = new int[counts.length];

        for (int i = 0; i < counts.length; i++) {
            machines[i] = placeHemogenic(helper, new BlockPos(2 + i * 3, 2, 5));
            installUpgrades(machines[i], counts[i], counts[i]);
            machines[i].inventory().setStackInSlot(HemogenicBlockEntity.INPUT_SLOT, new ItemStack(Items.BEEF));
            machines[i].energy().setStored(machines[i].energy().getMaxEnergyStored());
            startingEnergy[i] = machines[i].energy().getEnergyStored();
        }

        helper.startSequence()
              .thenIdle(1)
              .thenExecute(() -> {
                  for (int i = 0; i < machines.length; i++) {
                      require(machines[i].data.get(5) == expectedDurations[i], helper,
                            counts[i] + " upgrades produced duration " + machines[i].data.get(5)
                                  + " instead of " + expectedDurations[i]);
                  }
              })
              .thenWaitUntil(() -> {
                  for (HemogenicBlockEntity machine : machines) {
                      if (machine.tank().getFluidAmount() != 400) helper.fail("Upgrade timing run is not complete");
                  }
              })
              .thenExecute(() -> {
                  for (int i = 0; i < machines.length; i++) {
                      int consumed = startingEnergy[i] - machines[i].energy().getEnergyStored();
                      require(consumed == expectedEnergy[i], helper,
                            counts[i] + " upgrades consumed " + consumed + " FE instead of " + expectedEnergy[i]);
                  }
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void upgradesInstallInternallyAndMigrateLegacySlots(GameTestHelper helper) {
        HemogenicBlockEntity installing = placeHemogenic(helper, new BlockPos(3, 2, 5));
        installing.inventory().setStackInSlot(HemogenicBlockEntity.UPGRADE_INPUT_SLOT,
              MekanismItems.SPEED_UPGRADE.getItemStack(8));

        HemogenicBlockEntity migrated = placeHemogenic(helper, new BlockPos(7, 2, 5));
        migrated.inventory().setStackInSlot(HemogenicBlockEntity.UPGRADE_INPUT_SLOT,
              MekanismItems.SPEED_UPGRADE.getItemStack(4));
        migrated.inventory().setStackInSlot(HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT,
              MekanismItems.ENERGY_UPGRADE.getItemStack(6));
        CompoundTag legacy = migrated.saveWithFullMetadata();
        legacy.remove("InstalledSpeedUpgrades");
        legacy.remove("InstalledEnergyUpgrades");
        legacy.remove("UpgradeTicks");
        migrated.load(legacy);
        require(migrated.speedUpgradeCount() == 4 && migrated.energyUpgradeCount() == 6, helper,
              "Legacy upgrade stacks were not migrated into installed counts");
        require(migrated.inventory().getStackInSlot(HemogenicBlockEntity.UPGRADE_INPUT_SLOT).isEmpty()
                    && migrated.inventory().getStackInSlot(HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT).isEmpty(), helper,
              "Legacy upgrade stacks remained visible after migration");

        helper.startSequence()
              .thenWaitUntil(() -> {
                  if (installing.speedUpgradeCount() != 8) helper.fail("Upgrade installation has not completed");
              })
              .thenExecute(() -> {
                  require(installing.inventory().getStackInSlot(HemogenicBlockEntity.UPGRADE_INPUT_SLOT).isEmpty(), helper,
                        "Installed upgrades remained in the transient input slot");
                  installing.handleUpgradeButton(null, BaseMachineBlockEntity.removeUpgradeButtonId(Upgrade.SPEED, true));
                  require(installing.speedUpgradeCount() == 0, helper, "Shift-uninstall did not clear installed upgrades");
                  ItemStack output = installing.inventory().getStackInSlot(HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT);
                  require(output.is(MekanismItems.SPEED_UPGRADE.get()) && output.getCount() == 8, helper,
                        "Uninstalled upgrades did not enter the output slot");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 220)
    public static void midProcessNbtReloadResumesWork(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 2, 5);
        HemogenicBlockEntity[] machine = {placeHemogenic(helper, pos)};
        machine[0].inventory().setStackInSlot(HemogenicBlockEntity.INPUT_SLOT, new ItemStack(Items.BEEF));
        machine[0].energy().setStored(machine[0].energy().getMaxEnergyStored());

        helper.startSequence()
              .thenIdle(40)
              .thenExecute(() -> {
                  int savedProgress = machine[0].data.get(4);
                  require(savedProgress > 0 && savedProgress < machine[0].data.get(5), helper,
                        "Machine was not mid-process before reload");
                  CompoundTag saved = machine[0].saveWithFullMetadata();
                  helper.setBlock(pos, Blocks.AIR);
                  helper.setBlock(pos, ModContent.HEMOGENIC_MACHINE.get());
                  machine[0] = requireBlockEntity(helper, pos, HemogenicBlockEntity.class);
                  machine[0].load(saved);
                  require(machine[0].data.get(4) == savedProgress, helper, "Progress did not survive NBT reload");
              })
              .thenWaitUntil(() -> {
                  if (machine[0].tank().getFluidAmount() != 400) helper.fail("Reloaded machine did not finish its recipe");
              })
              .thenExecute(() -> require(machine[0].inventory().getStackInSlot(HemogenicBlockEntity.INPUT_SLOT).isEmpty(),
                    helper, "Reloaded machine did not consume its input"))
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 300)
    public static void lockedAltarBuildsTheFullSlateChain(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeFactory(helper, new BlockPos(5, 2, 5),
              ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.ABSOLUTE).get());
        for (int i = 0; i < 5; i++) machine.handleButton(UniversalFactoryBlockEntity.ALTAR_TARGET_BUTTON);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.STONE));
        fillLife(machine, 53_000);
        installUpgrades(machine, 8, 8);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());

        helper.startSequence()
              .thenWaitUntil(() -> {
                  if (countFactoryOutputs(machine, BloodMagicItems.ETHEREAL_SLATE.get()) != 1) {
                      helper.fail("Locked altar did not complete Stone -> Ethereal Slate");
                  }
              })
              .thenExecute(() -> {
                  require(machine.data.get(51) == 5, helper, "Locked altar target changed during processing");
                  require(machine.inputTank().isEmpty(), helper, "Locked altar did not consume exactly 53,000 mB");
                  require(countFactoryInputs(machine) == 0, helper, "Locked altar left an external input");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyMechanicalOrbTierChargesAtItsRealRate(GameTestHelper helper) {
        MechanicalOrbTier[] orbTiers = MechanicalOrbTier.values();
        FactoryTier[] factoryTiers = FactoryTier.values();
        UniversalFactoryBlockEntity[] machines = new UniversalFactoryBlockEntity[orbTiers.length];
        ItemStack[] orbs = new ItemStack[orbTiers.length];
        int[] expected = new int[orbTiers.length];
        int[] startingEnergy = new int[orbTiers.length];

        for (int i = 0; i < orbTiers.length; i++) {
            machines[i] = placeFactory(helper, new BlockPos(2 + i * 2, 2, 5),
                  ModContent.UNIVERSAL_FACTORIES.get(factoryTiers[i]).get());
            orbs[i] = new ItemStack(ModContent.MECHANICAL_BLOOD_ORBS.get(orbTiers[i]).get());
            expected[i] = orbTiers[i].fillRate() * factoryTiers[i].batchSize();
            machines[i].inventory().setStackInSlot(UniversalFactoryBlockEntity.CATALYST_SLOT, orbs[i]);
            fillLife(machines[i], expected[i]);
            machines[i].energy().setStored(machines[i].energy().getMaxEnergyStored());
            startingEnergy[i] = machines[i].energy().getEnergyStored();
        }

        helper.startSequence()
              .thenIdle(3)
              .thenExecute(() -> {
                  for (int i = 0; i < orbTiers.length; i++) {
                      MechanicalBloodOrbItem item = (MechanicalBloodOrbItem) orbs[i].getItem();
                      require(item.getStoredLp(orbs[i]) == expected[i], helper,
                            orbTiers[i] + " orb stored " + item.getStoredLp(orbs[i]) + "/" + expected[i] + " LP");
                      require(machines[i].inputTank().isEmpty(), helper, orbTiers[i] + " orb left Life Essence behind");
                      int consumed = startingEnergy[i] - machines[i].energy().getEnergyStored();
                      require(consumed == expected[i] * 8, helper,
                            orbTiers[i] + " orb consumed " + consumed + " FE instead of " + expected[i] * 8);
                  }
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void willGasHonorsSidesAndTransfersToSoulForge(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(4, 2, 5);
        helper.setBlock(generatorPos, ModContent.WILL_GENERATOR.get());
        WillGeneratorBlockEntity generator = requireBlockEntity(helper, generatorPos, WillGeneratorBlockEntity.class);
        UniversalFactoryBlockEntity soulForge = placeFactory(helper, generatorPos.east(),
              ModContent.PROCESS_FACTORIES.get(FactoryMode.SOUL_FORGE).get());

        generator.handleButton(BaseMachineBlockEntity.clearSidesButtonId(MachineResource.GAS));
        var eastGas = generator.getCapability(Capabilities.GAS_HANDLER, Direction.EAST)
              .orElseThrow(() -> new AssertionError("Missing east gas capability"));
        require(eastGas.getTanks() == 0, helper, "Disabled gas side exposed its tank");
        generator.handleButton(BaseMachineBlockEntity.sideButtonId(MachineResource.GAS, RelativeMachineSide.RIGHT));
        generator.handleButton(BaseMachineBlockEntity.sideButtonId(MachineResource.GAS, RelativeMachineSide.RIGHT));
        require(eastGas.getTanks() == 1, helper, "OUTPUT gas side did not expose its tank");

        generator.inventory().setStackInSlot(WillGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.SOUL_SAND));
        generator.lifeTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 1_000),
              IFluidHandler.FluidAction.EXECUTE);
        installUpgrades(generator, 8, 8);
        generator.energy().setStored(generator.energy().getMaxEnergyStored());

        helper.startSequence()
              .thenWaitUntil(() -> {
                  if (soulForge.willTank().getStored() != WillGeneratorBlockEntity.SOUL_SAND_GAS) {
                      helper.fail("Will gas transfer " + soulForge.willTank().getStored() + "/"
                            + WillGeneratorBlockEntity.SOUL_SAND_GAS);
                  }
              })
              .thenExecute(() -> {
                  require(generator.willTank().isEmpty(), helper, "Generator retained Will after transfer");
                  require(generator.lifeTank().isEmpty(), helper, "Generator did not consume its Life Essence");
                  require(generator.inventory().getStackInSlot(WillGeneratorBlockEntity.INPUT_SLOT).isEmpty(), helper,
                        "Generator did not consume Soul Sand");
              })
              .thenSucceed();
    }

    private static HemogenicBlockEntity redstoneMachine(GameTestHelper helper, BlockPos pos, int modeClicks,
          boolean powered) {
        HemogenicBlockEntity machine = placeHemogenic(helper, pos);
        for (int click = 0; click < modeClicks; click++) machine.handleButton(2);
        if (powered) helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
        installUpgrades(machine, 8, 8);
        machine.inventory().setStackInSlot(HemogenicBlockEntity.INPUT_SLOT, new ItemStack(Items.BEEF));
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        return machine;
    }

    private static void assertRedstoneBlocked(GameTestHelper helper, HemogenicBlockEntity machine, String message) {
        require(machine.tank().isEmpty(), helper, message);
        require(machine.inventory().getStackInSlot(HemogenicBlockEntity.INPUT_SLOT).is(Items.BEEF), helper,
              message + " and consumed its input");
    }

    private static HemogenicBlockEntity placeHemogenic(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModContent.HEMOGENIC_MACHINE.get());
        return requireBlockEntity(helper, pos, HemogenicBlockEntity.class);
    }

    private static UniversalFactoryBlockEntity placeFactory(GameTestHelper helper, BlockPos pos,
          net.minecraft.world.level.block.Block block) {
        helper.setBlock(pos, block);
        return requireBlockEntity(helper, pos, UniversalFactoryBlockEntity.class);
    }

    private static void fillLife(UniversalFactoryBlockEntity machine, int amount) {
        machine.inputTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), amount),
              IFluidHandler.FluidAction.EXECUTE);
    }

    private static void installUpgrades(BaseMachineBlockEntity machine, int speed, int energy) {
        machine.addUpgrades(Upgrade.SPEED, speed);
        machine.addUpgrades(Upgrade.ENERGY, energy);
    }

    private static int countFactoryInputs(UniversalFactoryBlockEntity machine) {
        int count = 0;
        for (int slot = 0; slot < UniversalFactoryBlockEntity.INPUT_COUNT; slot++) {
            count += machine.inventory().getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static int countFactoryOutputs(UniversalFactoryBlockEntity machine, Item item) {
        int count = 0;
        for (int slot = UniversalFactoryBlockEntity.OUTPUT_START;
             slot < UniversalFactoryBlockEntity.OUTPUT_START + UniversalFactoryBlockEntity.OUTPUT_COUNT; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countContainer(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static <T> T requireBlockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Object blockEntity = helper.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) helper.fail("Expected " + type.getSimpleName() + " at " + pos);
        return type.cast(blockEntity);
    }

    private static void require(boolean condition, GameTestHelper helper, String message) {
        if (!condition) helper.fail(message);
    }
}
