package dev.bloodmekanism.gametest;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.item.MechanicalBloodOrbItem;
import dev.bloodmekanism.item.MechanicalOrbTier;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.machine.blockentity.WillGeneratorBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import dev.bloodmekanism.will.WillGas;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.item.BloodMagicItems;
import wayoftime.bloodmagic.common.item.potion.ItemAlchemyFlask;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismCoverageGameTests {
    private BloodMekanismCoverageGameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 400)
    public static void everyAltarFactoryTierProcessesItsMaximumSlate(GameTestHelper helper) {
        FactoryTier[] tiers = FactoryTier.values();
        Item[] inputs = {
              Items.STONE,
              BloodMagicItems.SLATE.get(),
              BloodMagicItems.REINFORCED_SLATE.get(),
              BloodMagicItems.IMBUED_SLATE.get(),
              BloodMagicItems.DEMONIC_SLATE.get()
        };
        Item[] outputs = {
              BloodMagicItems.SLATE.get(),
              BloodMagicItems.REINFORCED_SLATE.get(),
              BloodMagicItems.IMBUED_SLATE.get(),
              BloodMagicItems.DEMONIC_SLATE.get(),
              BloodMagicItems.ETHEREAL_SLATE.get()
        };
        int[] lifePerItem = {1_000, 2_000, 5_000, 15_000, 30_000};
        UniversalFactoryBlockEntity[] machines = new UniversalFactoryBlockEntity[tiers.length];

        for (int i = 0; i < tiers.length; i++) {
            BlockPos pos = new BlockPos(2 + i * 2, 2, 5);
            machines[i] = placeFactory(helper, pos, ModContent.UNIVERSAL_FACTORIES.get(tiers[i]).get());
            int batch = tiers[i].batchSize();
            machines[i].inventory().setStackInSlot(0, new ItemStack(inputs[i], batch));
            fillLife(machines[i], lifePerItem[i] * batch);
            machines[i].energy().setStored(machines[i].energy().getMaxEnergyStored());
        }

        helper.startSequence()
              .thenWaitUntil(() -> {
                  for (int i = 0; i < machines.length; i++) {
                      int expected = tiers[i].batchSize();
                      int actual = countOutputs(machines[i], outputs[i]);
                      if (actual != expected) {
                          helper.fail(tiers[i] + " altar factory output " + actual + "/" + expected);
                      }
                  }
              })
              .thenExecute(() -> {
                  for (int i = 0; i < machines.length; i++) {
                      require(machines[i].inputTank().isEmpty(), helper,
                            tiers[i] + " altar factory did not consume its exact Life Essence input");
                      require(machines[i].energy().getEnergyStored() < machines[i].energy().getMaxEnergyStored(), helper,
                            tiers[i] + " altar factory did not consume FE");
                  }
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 250)
    public static void everyDedicatedFactoryProcessesARealRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity alchemy = placeProcessFactory(helper, new BlockPos(2, 2, 4), FactoryMode.ALCHEMY_TABLE);
        BlockPos alchemyOutputPos = new BlockPos(2, 2, 3);
        helper.setBlock(alchemyOutputPos, Blocks.CHEST);
        Container alchemyOutput = requireBlockEntity(helper, alchemyOutputPos, Container.class);
        for (int i = 0; i < 4; i++) {
            Item ingredient = switch (i) {
                case 0 -> Items.REDSTONE;
                case 1 -> Items.WHITE_DYE;
                case 2 -> Items.GUNPOWDER;
                default -> Items.COAL;
            };
            alchemy.inventory().setStackInSlot(i, new ItemStack(ingredient, 16));
        }
        fillLife(alchemy, 8_000);
        powerAndUpgrade(alchemy);

        UniversalFactoryBlockEntity array = placeProcessFactory(helper, new BlockPos(4, 2, 4), FactoryMode.ALCHEMY_ARRAY);
        Container arrayOutput = placeOutputChest(helper, new BlockPos(4, 2, 3));
        array.inventory().setStackInSlot(0, new ItemStack(Items.REDSTONE, 16));
        array.inventory().setStackInSlot(1, new ItemStack(BloodMagicItems.SLATE.get(), 16));
        powerAndUpgrade(array);

        UniversalFactoryBlockEntity soulForge = placeProcessFactory(helper, new BlockPos(6, 2, 4), FactoryMode.SOUL_FORGE);
        Container soulForgeOutput = placeOutputChest(helper, new BlockPos(6, 2, 3));
        soulForge.inventory().setStackInSlot(0, new ItemStack(Items.REDSTONE, 16));
        soulForge.inventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 16));
        soulForge.inventory().setStackInSlot(2, new ItemStack(Items.GLASS, 16));
        soulForge.inventory().setStackInSlot(3, new ItemStack(Items.LAPIS_LAZULI, 16));
        soulForge.willTank().insert(WillGas.stack(EnumDemonWillType.DEFAULT, WillGas.toGas(16)),
              Action.EXECUTE, AutomationType.INTERNAL);
        powerAndUpgrade(soulForge);

        UniversalFactoryBlockEntity arc = placeProcessFactory(helper, new BlockPos(8, 2, 4), FactoryMode.ARC);
        Container arcOutput = placeOutputChest(helper, new BlockPos(8, 2, 3));
        arc.inventory().setStackInSlot(0, new ItemStack(Items.TERRACOTTA));
        arc.inventory().setStackInSlot(UniversalFactoryBlockEntity.CATALYST_SLOT,
              new ItemStack(BloodMagicItems.PRIMITIVE_HYDRATION_CELL.get()));
        arc.inputTank().fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
        powerAndUpgrade(arc);

        helper.startSequence()
              .thenWaitUntil(() -> {
                  assertOutput(helper, alchemy, alchemyOutput, BloodMagicItems.ARCANE_ASHES.get(), 16,
                        "Industrial Alchemy Factory");
                  assertOutput(helper, array, arrayOutput, BloodMagicItems.DIVINATION_SIGIL.get(), 16,
                        "Industrial Array Factory");
                  assertOutput(helper, soulForge, soulForgeOutput, BloodMagicItems.PETTY_GEM.get(), 16,
                        "Industrial Soul Forge");
                  assertOutput(helper, arc, arcOutput, Items.CLAY, 1, "Industrial ARC Factory");
              })
              .thenExecute(() -> {
                  require(alchemy.inputTank().isEmpty(), helper, "Alchemy factory Life Essence accounting mismatch");
                  require(soulForge.willTank().isEmpty(), helper, "Soul Forge Will accounting mismatch");
                  require(arc.inputTank().isEmpty(), helper, "ARC water accounting mismatch");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 180)
    public static void alchemyFactoryProcessesPotionFlaskRecipes(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeProcessFactory(helper, new BlockPos(5, 2, 5), FactoryMode.ALCHEMY_TABLE);
        ItemStack flask = new ItemStack(BloodMagicItems.ALCHEMY_FLASK.get());
        flask.getOrCreateTag();
        machine.inventory().setStackInSlot(0, flask);
        machine.inventory().setStackInSlot(1, new ItemStack(BloodMagicItems.SIMPLE_CATALYST.get()));
        machine.inventory().setStackInSlot(2, new ItemStack(Items.SLIME_BALL));
        fillLife(machine, 500);
        powerAndUpgrade(machine);

        helper.startSequence()
              .thenWaitUntil(() -> assertOutput(helper, machine, BloodMagicItems.ALCHEMY_FLASK.get(), 1,
                    "Potion Flask branch"))
              .thenExecute(() -> {
                  ItemStack output = findOutput(machine, BloodMagicItems.ALCHEMY_FLASK.get());
                  ItemAlchemyFlask item = (ItemAlchemyFlask) output.getItem();
                  require(item.getEffectHoldersOfFlask(output).size() == 1, helper,
                        "Potion Flask output did not receive exactly one effect");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 180)
    public static void alchemyFactoryUsesWaterFluidForBucketIngredients(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeProcessFactory(helper, new BlockPos(5, 2, 5), FactoryMode.ALCHEMY_TABLE);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.SUGAR, 16));
        IFluidHandler fluids = machine.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
        require(fluids.fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 4_800),
                    IFluidHandler.FluidAction.EXECUTE) == 4_800,
              helper, "Fluid capability did not accept 4,800 mB Life Essence");
        require(fluids.fill(new FluidStack(Fluids.WATER, 32_000), IFluidHandler.FluidAction.EXECUTE) == 32_000,
              helper, "Fluid capability did not route 32,000 mB water to the recipe tank");
        powerAndUpgrade(machine);

        helper.startSequence()
              .thenWaitUntil(() -> assertOutput(helper, machine, BloodMagicItems.REAGENT_WATER.get(), 16,
                    "Fluid-backed Reagent Water batch"))
              .thenExecute(() -> {
                  require(machine.inputTank().isEmpty(), helper, "Reagent Water batch did not consume exactly 4,800 LP");
                  require(machine.recipeWaterTank().isEmpty(), helper,
                        "Reagent Water batch did not consume exactly 32,000 mB water");
                  require(countOutputs(machine, Items.BUCKET) == 0, helper,
                        "Fluid-backed water bucket ingredients unexpectedly produced empty buckets");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 120)
    public static void willGeneratorAndMechanicalOrbProcessResources(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 5), ModContent.WILL_GENERATOR.get());
        WillGeneratorBlockEntity generator = requireBlockEntity(helper, new BlockPos(3, 2, 5),
              WillGeneratorBlockEntity.class);
        generator.inventory().setStackInSlot(WillGeneratorBlockEntity.INPUT_SLOT, new ItemStack(Items.SOUL_SAND));
        generator.addUpgrades(Upgrade.SPEED, 8);
        generator.addUpgrades(Upgrade.ENERGY, 8);
        generator.lifeTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 1_000),
              IFluidHandler.FluidAction.EXECUTE);
        generator.energy().setStored(generator.energy().getMaxEnergyStored());

        UniversalFactoryBlockEntity factory = placeFactory(helper, new BlockPos(7, 2, 5),
              ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.BASIC).get());
        ItemStack orb = new ItemStack(ModContent.MECHANICAL_BLOOD_ORBS.get(MechanicalOrbTier.WEAK).get());
        factory.inventory().setStackInSlot(UniversalFactoryBlockEntity.CATALYST_SLOT, orb);
        fillLife(factory, 100);
        factory.energy().setStored(factory.energy().getMaxEnergyStored());

        helper.startSequence()
              .thenWaitUntil(() -> {
                  if (generator.willTank().getStored() != WillGeneratorBlockEntity.SOUL_SAND_GAS) {
                      helper.fail("Will Generator output " + generator.willTank().getStored() + "/"
                            + WillGeneratorBlockEntity.SOUL_SAND_GAS);
                  }
                  MechanicalBloodOrbItem orbItem = (MechanicalBloodOrbItem) orb.getItem();
                  if (orbItem.getStoredLp(orb) != 100) helper.fail("Mechanical Blood Orb did not store 100 LP");
              })
              .thenExecute(() -> {
                  require(generator.lifeTank().isEmpty(), helper, "Will Generator Life Essence accounting mismatch");
                  require(generator.inventory().getStackInSlot(WillGeneratorBlockEntity.INPUT_SLOT).isEmpty(), helper,
                        "Will Generator did not consume Soul Sand");
              })
              .thenSucceed();
    }

    private static UniversalFactoryBlockEntity placeProcessFactory(GameTestHelper helper, BlockPos pos, FactoryMode mode) {
        return placeFactory(helper, pos, ModContent.PROCESS_FACTORIES.get(mode).get());
    }

    private static Container placeOutputChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return requireBlockEntity(helper, pos, Container.class);
    }

    private static UniversalFactoryBlockEntity placeFactory(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        return requireBlockEntity(helper, pos, UniversalFactoryBlockEntity.class);
    }

    private static void fillLife(UniversalFactoryBlockEntity machine, int amount) {
        machine.inputTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), amount),
              IFluidHandler.FluidAction.EXECUTE);
    }

    private static void powerAndUpgrade(UniversalFactoryBlockEntity machine) {
        machine.addUpgrades(Upgrade.SPEED, 8);
        machine.addUpgrades(Upgrade.ENERGY, 8);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
    }

    private static void assertOutput(GameTestHelper helper, UniversalFactoryBlockEntity machine, Item item,
          int expected, String label) {
        int actual = countOutputs(machine, item);
        if (actual != expected) helper.fail(label + " output " + actual + "/" + expected
              + ", status=" + machine.data.get(44) + ", inputs=" + describeInputs(machine));
    }

    private static void assertOutput(GameTestHelper helper, UniversalFactoryBlockEntity machine, Container external,
          Item item, int expected, String label) {
        int actual = countOutputs(machine, item) + countItems(external, item);
        if (actual != expected) helper.fail(label + " output " + actual + "/" + expected
              + ", status=" + machine.data.get(44) + ", inputs=" + describeInputs(machine));
    }

    private static int countItems(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    static String describeInputs(UniversalFactoryBlockEntity machine) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < UniversalFactoryBlockEntity.INPUT_COUNT; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!result.isEmpty()) result.append(',');
            result.append(slot).append('=').append(stack.getItem()).append('x').append(stack.getCount());
        }
        return result.isEmpty() ? "empty" : result.toString();
    }

    private static int countOutputs(UniversalFactoryBlockEntity machine, Item item) {
        int count = 0;
        for (int slot = UniversalFactoryBlockEntity.OUTPUT_START;
             slot < UniversalFactoryBlockEntity.OUTPUT_START + UniversalFactoryBlockEntity.OUTPUT_COUNT; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static ItemStack findOutput(UniversalFactoryBlockEntity machine, Item item) {
        for (int slot = UniversalFactoryBlockEntity.OUTPUT_START;
             slot < UniversalFactoryBlockEntity.OUTPUT_START + UniversalFactoryBlockEntity.OUTPUT_COUNT; slot++) {
            ItemStack stack = machine.inventory().getStackInSlot(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
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
