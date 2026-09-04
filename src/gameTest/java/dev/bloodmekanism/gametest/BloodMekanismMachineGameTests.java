package dev.bloodmekanism.gametest;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.machine.blockentity.LpChargerBlockEntity;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.block.BloodMagicBlocks;
import wayoftime.bloodmagic.common.item.BloodMagicItems;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.util.helper.BindableHelper;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismMachineGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(5, 2, 5);

    private BloodMekanismMachineGameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void chargedEnergyTabletCraftsBloodFactory(GameTestHelper helper) {
        ItemStack tablet = MekanismItems.ENERGY_TABLET.getItemStack();
        IEnergyContainer tabletEnergy = StorageUtils.getEnergyContainer(tablet, 0);
        require(tabletEnergy != null, helper, "Energy Tablet did not expose an energy container");
        tabletEnergy.setEnergy(tabletEnergy.getMaxEnergy());
        require(tablet.hasTag(), helper, "Charged Energy Tablet did not persist its energy data");

        CraftingContainer grid = MekanismUtils.getDummyCraftingInv();
        grid.setItem(0, MekanismItems.INFUSED_ALLOY.getItemStack());
        grid.setItem(1, MekanismItems.BASIC_CONTROL_CIRCUIT.getItemStack());
        grid.setItem(2, MekanismItems.INFUSED_ALLOY.getItemStack());
        grid.setItem(3, MekanismBlocks.STEEL_CASING.getItemStack());
        grid.setItem(4, new ItemStack(BloodMagicBlocks.BLOOD_ALTAR.get()));
        grid.setItem(5, MekanismBlocks.STEEL_CASING.getItemStack());
        grid.setItem(6, MekanismItems.INFUSED_ALLOY.getItemStack());
        grid.setItem(7, tablet);
        grid.setItem(8, MekanismItems.INFUSED_ALLOY.getItemStack());

        Object recipe = helper.getLevel().getRecipeManager()
              .byKey(new ResourceLocation(BloodMekanism.MOD_ID, "basic_blood_factory")).orElse(null);
        require(recipe instanceof CraftingRecipe, helper, "Basic Blood Factory crafting recipe was not loaded");
        CraftingRecipe craftingRecipe = (CraftingRecipe) recipe;
        require(craftingRecipe.matches(grid, helper.getLevel()), helper,
              "Basic Blood Factory recipe rejected a fully charged Energy Tablet");
        require(craftingRecipe.assemble(grid, helper.getLevel().registryAccess())
                    .is(ModContent.UNIVERSAL_FACTORIES.get(dev.bloodmekanism.machine.FactoryTier.BASIC).get().asItem()),
              helper, "Charged Energy Tablet recipe did not produce a Basic Blood Factory");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void alchemyFactoryUsesTankLpAndWaterWithoutOrb(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_TABLE).get());
        UniversalFactoryBlockEntity machine = requireMachine(helper, MACHINE_POS, UniversalFactoryBlockEntity.class);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.SAND, 2));
        machine.inputTank().setFluid(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), 50));
        machine.recipeWaterTank().setFluid(new FluidStack(Fluids.WATER, 1_000));
        machine.energy().setStored(machine.energy().getMaxEnergyStored());

        for (int tick = 0; tick < 30; tick++) machine.serverTick();

        ItemStack output = machine.inventory().getStackInSlot(UniversalFactoryBlockEntity.OUTPUT_START);
        require(output.is(Items.CLAY_BALL) && output.getCount() == 2, helper,
              "Alchemy Factory did not craft clay from tank LP and water without a Blood Orb");
        require(machine.inventory().getStackInSlot(UniversalFactoryBlockEntity.CATALYST_SLOT).isEmpty(), helper,
              "Alchemy Factory unexpectedly required a catalyst");
        require(machine.inputTank().isEmpty() && machine.recipeWaterTank().isEmpty(), helper,
              "Alchemy Factory did not consume the exact LP and water amounts");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 300)
    public static void hemogenicMachineProcessesBeef(GameTestHelper helper) {
        HemogenicBlockEntity machine = placeHemogenicMachine(helper);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        machine.inventory().setStackInSlot(HemogenicBlockEntity.INPUT_SLOT, new ItemStack(Items.BEEF));

        helper.startSequence()
              .thenIdle(130)
              .thenExecute(() -> {
                  require(machine.inventory().getStackInSlot(HemogenicBlockEntity.INPUT_SLOT).isEmpty(), helper,
                        "Hemogenic Machine did not consume beef");
                  require(machine.tank().getFluidAmount() == 400, helper,
                        "Hemogenic Machine produced " + machine.tank().getFluidAmount() + " mB instead of 400 mB");
                  require(machine.energy().getEnergyStored() < machine.energy().getMaxEnergyStored(), helper,
                        "Hemogenic Machine did not consume FE");
              })
              .thenSucceed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void sideConfigurationBlocksAutomationAndSurvivesNbt(GameTestHelper helper) {
        HemogenicBlockEntity machine = placeHemogenicMachine(helper);
        var westHandler = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.WEST)
              .orElseThrow(() -> new AssertionError("Missing west item capability"));
        ItemStack remainder = westHandler.insertItem(HemogenicBlockEntity.INPUT_SLOT,
              new ItemStack(Items.ROTTEN_FLESH), false);
        require(remainder.isEmpty(), helper, "Default item side rejected a valid input");

        machine.inventory().setStackInSlot(HemogenicBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
        machine.handleButton(BaseMachineBlockEntity.clearSidesButtonId(MachineResource.ITEM));
        remainder = westHandler.insertItem(HemogenicBlockEntity.INPUT_SLOT,
              new ItemStack(Items.ROTTEN_FLESH), false);
        require(remainder.getCount() == 1, helper, "Disabled item side accepted an input");

        machine.energy().setStored(12_345);
        CompoundTag saved = machine.saveWithFullMetadata();
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        helper.setBlock(MACHINE_POS, ModContent.HEMOGENIC_MACHINE.get());
        HemogenicBlockEntity restored = requireMachine(helper, MACHINE_POS, HemogenicBlockEntity.class);
        restored.load(saved);

        require(restored.energy().getEnergyStored() == 12_345, helper, "Energy did not survive NBT round-trip");
        var restoredWest = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.WEST)
              .orElseThrow(() -> new AssertionError("Missing restored west item capability"));
        remainder = restoredWest.insertItem(HemogenicBlockEntity.INPUT_SLOT,
              new ItemStack(Items.ROTTEN_FLESH), false);
        require(remainder.getCount() == 1, helper, "Side configuration did not survive NBT round-trip");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void lpChargerInstantlyFillsMechanicalAndBoundOrbs(GameTestHelper helper) {
        LpChargerBlockEntity machine = placeLpCharger(helper);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        fillLife(machine, LpChargerBlockEntity.LP_CAPACITY, helper);
        ItemStack mechanical = ModContent.MECHANICAL_BLOOD_ORBS
              .get(dev.bloodmekanism.item.MechanicalOrbTier.ARCHMAGE).get().getDefaultInstance();
        machine.inventory().setStackInSlot(LpChargerBlockEntity.INPUT_SLOT, mechanical);

        machine.serverTick();

        ItemStack mechanicalOutput = machine.inventory().getStackInSlot(LpChargerBlockEntity.OUTPUT_SLOT);
        require(mechanicalOutput.getItem() instanceof dev.bloodmekanism.item.MechanicalBloodOrbItem orb
                    && orb.getStoredLp(mechanicalOutput) == orb.getCapacity(), helper,
              "LP Charger did not fill the 10M mechanical orb in one tick");
        require(machine.lifeTank().isEmpty(), helper, "Mechanical orb charging did not consume exact Life Essence");

        machine.inventory().setStackInSlot(LpChargerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        fillLife(machine, LpChargerBlockEntity.LP_CAPACITY, helper);
        UUID owner = UUID.fromString("06c529a9-2672-4b08-9094-77ab18c629ad");
        Binding binding = new Binding(owner, "LpChargerGameTest");
        NetworkHelper.getSoulNetwork(binding).setCurrentEssence(0);
        ItemStack personal = BloodMagicItems.ARCHMAGE_BLOOD_ORB.get().getDefaultInstance();
        BindableHelper.applyBinding(personal, binding);
        machine.inventory().setStackInSlot(LpChargerBlockEntity.INPUT_SLOT, personal);

        machine.serverTick();

        require(NetworkHelper.getSoulNetwork(binding).getCurrentEssence() == LpChargerBlockEntity.LP_CAPACITY,
              helper, "LP Charger did not fill the bound player's 10M Soul Network in one tick");
        require(machine.inventory().getStackInSlot(LpChargerBlockEntity.OUTPUT_SLOT)
                    .is(BloodMagicItems.ARCHMAGE_BLOOD_ORB.get()), helper,
              "LP Charger did not move the full bound Blood Orb to output");
        require(machine.lifeTank().isEmpty(), helper, "Bound orb charging did not consume exact Life Essence");
        helper.succeed();
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void lpChargerTransfersAllAvailableEssenceWhenPartial(GameTestHelper helper) {
        LpChargerBlockEntity machine = placeLpCharger(helper);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
        int available = 123_456;
        fillLife(machine, available, helper);
        ItemStack mechanical = ModContent.MECHANICAL_BLOOD_ORBS
              .get(dev.bloodmekanism.item.MechanicalOrbTier.MASTER).get().getDefaultInstance();
        machine.inventory().setStackInSlot(LpChargerBlockEntity.INPUT_SLOT, mechanical);

        machine.serverTick();

        ItemStack partial = machine.inventory().getStackInSlot(LpChargerBlockEntity.INPUT_SLOT);
        require(partial.getItem() instanceof dev.bloodmekanism.item.MechanicalBloodOrbItem orb
                    && orb.getStoredLp(partial) == available, helper,
              "LP Charger did not transfer all available Life Essence in one tick");
        require(machine.lifeTank().isEmpty(), helper, "Partial charging left Life Essence behind");
        require(machine.inventory().getStackInSlot(LpChargerBlockEntity.OUTPUT_SLOT).isEmpty(), helper,
              "Partially charged orb was incorrectly moved to output");
        helper.succeed();
    }

    private static HemogenicBlockEntity placeHemogenicMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ModContent.HEMOGENIC_MACHINE.get());
        return requireMachine(helper, MACHINE_POS, HemogenicBlockEntity.class);
    }

    private static LpChargerBlockEntity placeLpCharger(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ModContent.LP_CHARGER.get());
        return requireMachine(helper, MACHINE_POS, LpChargerBlockEntity.class);
    }

    private static void fillLife(LpChargerBlockEntity machine, int amount, GameTestHelper helper) {
        int filled = machine.lifeTank().fill(
              new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), amount),
              IFluidHandler.FluidAction.EXECUTE);
        require(filled == amount, helper, "LP Charger accepted " + filled + "/" + amount + " Life Essence");
    }

    private static <T> T requireMachine(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Object blockEntity = helper.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) {
            helper.fail("Expected " + type.getSimpleName() + " at " + pos + ", got " + blockEntity);
        }
        return type.cast(blockEntity);
    }

    static void require(boolean condition, GameTestHelper helper, String message) {
        if (!condition) helper.fail(message);
    }
}
