package dev.bloodmekanism.gametest;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import dev.bloodmekanism.will.WillGas;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Upgrade;
import mekanism.api.chemical.gas.GasStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.item.IAlchemyItem;
import wayoftime.bloodmagic.common.item.potion.ItemAlchemyFlask;
import wayoftime.bloodmagic.common.recipe.BloodMagicRecipeType;
import wayoftime.bloodmagic.impl.BloodMagicAPI;
import wayoftime.bloodmagic.recipe.BloodMagicRecipe;
import wayoftime.bloodmagic.recipe.EffectHolder;
import wayoftime.bloodmagic.recipe.RecipeARC;
import wayoftime.bloodmagic.recipe.RecipeAlchemyArray;
import wayoftime.bloodmagic.recipe.RecipeAlchemyTable;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.recipe.RecipeTartaricForge;
import wayoftime.bloodmagic.recipe.flask.RecipePotionFlaskBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismRecipeMatrixGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(5, 2, 5);
    private static final String BLOOD_MAGIC = "bloodmagic";
    private static final int MAX_BATCH = FactoryTier.ABSOLUTE.batchSize();

    private BloodMekanismRecipeMatrixGameTests() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInAltarRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.ALTAR);
        List<RecipeBloodAltar> recipes = builtInRecipes(helper, BloodMagicRecipeType.ALTAR.get(), 23);
        int operations = 0;
        for (RecipeBloodAltar recipe : recipes) {
            reset(machine);
            ItemStack input = findSingleInput(recipe.getInput(), stack -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getBloodAltar(helper.getLevel(), stack), recipe));
            requireInput(input, helper, recipe.getId());
            int batch = batchForRecipe(List.of(input), recipe.getOutput());
            machine.inventory().setStackInSlot(0, input.copyWithCount(batch));
            fillLife(machine, recipe.getSyphon() * batch);
            runUntilOutput(machine, recipe.getOutput(), batch, 2_000, helper, recipe.getId());
            require(machine.inputTank().isEmpty(), helper, recipe.getId() + " did not consume exact LP");
            operations += batch;
        }
        passFamily(helper, "altar", recipes.size(), operations);
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInAlchemyTableRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.ALCHEMY_TABLE);
        List<RecipeAlchemyTable> recipes = builtInRecipes(helper, BloodMagicRecipeType.ALCHEMYTABLE.get(), 131);
        int operations = 0;
        for (RecipeAlchemyTable recipe : recipes) {
            reset(machine);
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyTable(helper.getLevel(), stacks), recipe));
            requireInputs(inputs, helper, recipe.getId());
            ItemStack expected = recipe.getOutput(copyStacks(inputs));
            int batch = Math.min(batchForAlchemyInputs(inputs), batchForOutput(expected));
            installAlchemyInputs(machine, inputs, 0, batch);
            fillLife(machine, recipe.getSyphon() * batch);
            runUntilOutput(machine, expected, batch, 500, helper, recipe.getId());
            require(machine.inputTank().isEmpty(), helper, recipe.getId() + " did not consume exact LP");
            require(machine.recipeWaterTank().isEmpty(), helper, recipe.getId() + " did not consume exact water");
            operations += batch;
        }
        passFamily(helper, "alchemy table", recipes.size(), operations);
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInAlchemyArrayRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.ALCHEMY_ARRAY);
        List<RecipeAlchemyArray> recipes = builtInRecipes(helper, BloodMagicRecipeType.ARRAY.get(), 26);
        int operations = 0;
        for (RecipeAlchemyArray recipe : recipes) {
            reset(machine);
            List<Ingredient> ingredients = List.of(recipe.getBaseInput(), recipe.getAddedInput());
            List<ItemStack> inputs = findInputs(ingredients, stacks -> {
                var match = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyArray(
                      helper.getLevel(), stacks.get(0), stacks.get(1));
                return match != null && match.getLeft() && sameRecipe(match.getRight(), recipe);
            });
            requireInputs(inputs, helper, recipe.getId());
            int batch = batchForRecipe(inputs, recipe.getOutput());
            machine.inventory().setStackInSlot(0, inputs.get(0).copyWithCount(batch));
            machine.inventory().setStackInSlot(1, inputs.get(1).copyWithCount(batch));
            runUntilOutput(machine, recipe.getOutput(), batch, 100, helper, recipe.getId());
            operations += batch;
        }
        passFamily(helper, "alchemy array", recipes.size(), operations);
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInSoulForgeRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.SOUL_FORGE);
        List<RecipeTartaricForge> recipes = builtInRecipes(helper, BloodMagicRecipeType.TARTARICFORGE.get(), 89);
        int operations = 0;
        for (RecipeTartaricForge recipe : recipes) {
            reset(machine);
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getTartaricForge(helper.getLevel(), stacks), recipe));
            requireInputs(inputs, helper, recipe.getId());
            int batch = batchForRecipe(inputs, recipe.getOutput());
            installStackedInputs(machine, inputs, batch);
            double suppliedWill = recipe.getMinimumSouls() + recipe.getSoulDrain() * batch + 1;
            GasStack remainder = machine.willTank().insert(
                  WillGas.stack(EnumDemonWillType.DEFAULT, WillGas.toGas(suppliedWill)),
                  Action.EXECUTE, AutomationType.INTERNAL);
            require(remainder.isEmpty(), helper, recipe.getId() + " Will did not fit in the machine");
            long willBefore = machine.willTank().getStored();
            runUntilOutput(machine, recipe.getOutput(), batch, 100, helper, recipe.getId());
            long expectedDrain = WillGas.toGas(recipe.getSoulDrain() * batch);
            require(willBefore - machine.willTank().getStored() == expectedDrain, helper,
                  recipe.getId() + " Will consumption mismatch");
            operations += batch;
        }
        passFamily(helper, "soul forge", recipes.size(), operations);
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInArcRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.ARC);
        List<RecipeARC> recipes = builtInRecipes(helper, BloodMagicRecipeType.ARC.get(), 97);
        int operations = 0;
        for (RecipeARC recipe : recipes) {
            reset(machine);
            ArcInputs inputs = findArcInputs(helper, recipe);
            require(inputs != null, helper, "No resolvable input combination for " + recipe.getId());
            int batch = arcBatch(machine, inputs, recipe);
            machine.inventory().setStackInSlot(0,
                  inputs.input().copyWithCount(recipe.getRequiredInputCount() * batch));
            machine.inventory().setStackInSlot(UniversalFactoryBlockEntity.CATALYST_SLOT,
                  inputs.tool().copyWithCount(inputs.tool().isDamageableItem() ? 1 : batch));
            if (!inputs.fluid().isEmpty()) {
                FluidStack fluid = inputs.fluid().copy();
                fluid.setAmount(fluid.getAmount() * batch);
                int filled = machine.inputTank().fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                require(filled == fluid.getAmount(), helper, recipe.getId() + " input fluid did not fit");
            }
            ItemStack expected = recipe.getAllListedOutputs(inputs.input(), inputs.tool()).get(0);
            runArcBatch(machine, expected, batch, 100 * batch, helper, recipe.getId());
            if (recipe.getFluidIngredient() != null) {
                require(machine.inputTank().isEmpty(), helper, recipe.getId() + " did not consume exact input fluid");
            }
            if (!recipe.getFluidOutput().isEmpty()) {
                FluidStack expectedFluid = recipe.getFluidOutput().copy();
                expectedFluid.setAmount(expectedFluid.getAmount() * batch);
                require(machine.outputTank().getFluid().isFluidStackIdentical(expectedFluid), helper,
                      recipe.getId() + " fluid output mismatch");
            }
            operations += batch;
        }
        passFamily(helper, "ARC", recipes.size(), operations);
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInPotionFlaskRecipe(GameTestHelper helper) {
        UniversalFactoryBlockEntity machine = placeMachine(helper, FactoryMode.ALCHEMY_TABLE);
        List<RecipePotionFlaskBase> recipes = builtInRecipes(helper, BloodMagicRecipeType.POTIONFLASK.get(), 110);
        for (RecipePotionFlaskBase recipe : recipes) {
            reset(machine);
            ItemStack flask = recipe.getExamplePotionFlask();
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> {
                List<EffectHolder> effects = flaskEffects(flask.copy());
                return sameRecipe(BloodMagicAPI.INSTANCE.getRecipeRegistrar().getPotionFlaskRecipe(
                      helper.getLevel(), flask, effects, stacks), recipe);
            });
            requireInputs(inputs, helper, recipe.getId());
            machine.inventory().setStackInSlot(0, flask.copy());
            installAlchemyInputs(machine, inputs, 1, 1);
            fillLife(machine, recipe.getSyphon());
            List<EffectHolder> expectedEffects = flaskEffects(flask.copy());
            ItemStack expected = recipe.getOutput(flask.copy(), expectedEffects);
            if (expected.getItem() instanceof ItemAlchemyFlask outputFlask) {
                outputFlask.resyncEffectInstances(expected);
            }
            runUntilOutput(machine, expected, 1, 500, helper, recipe.getId());
            require(machine.inputTank().isEmpty(), helper, recipe.getId() + " did not consume exact LP");
            require(machine.recipeWaterTank().isEmpty(), helper, recipe.getId() + " did not consume exact water");
        }
        passFamily(helper, "potion flask", recipes.size(), recipes.size());
    }

    private static UniversalFactoryBlockEntity placeMachine(GameTestHelper helper, FactoryMode mode) {
        helper.setBlock(MACHINE_POS, mode == FactoryMode.ALTAR
              ? ModContent.UNIVERSAL_FACTORIES.get(dev.bloodmekanism.machine.FactoryTier.ABSOLUTE).get()
              : ModContent.PROCESS_FACTORIES.get(mode).get());
        Object blockEntity = helper.getBlockEntity(MACHINE_POS);
        require(blockEntity instanceof UniversalFactoryBlockEntity, helper, "Factory block entity was not created");
        return (UniversalFactoryBlockEntity) blockEntity;
    }

    private static void reset(UniversalFactoryBlockEntity machine) {
        for (int slot = 0; slot < UniversalFactoryBlockEntity.SLOT_COUNT; slot++) {
            machine.inventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
        machine.inputTank().setFluid(FluidStack.EMPTY);
        machine.recipeWaterTank().setFluid(FluidStack.EMPTY);
        machine.outputTank().setFluid(FluidStack.EMPTY);
        machine.willTank().setEmpty();
        machine.addUpgrades(Upgrade.SPEED, 8);
        machine.addUpgrades(Upgrade.ENERGY, 8);
        machine.energy().setStored(machine.energy().getMaxEnergyStored());
    }

    private static void fillLife(UniversalFactoryBlockEntity machine, int amount) {
        if (amount <= 0) return;
        int filled = machine.inputTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), amount),
              IFluidHandler.FluidAction.EXECUTE);
        if (filled != amount) throw new AssertionError("Life Essence did not fit: " + filled + "/" + amount);
    }

    private static void installAlchemyInputs(UniversalFactoryBlockEntity machine, List<ItemStack> inputs,
          int startSlot, int batch) {
        int slot = startSlot;
        int waterBuckets = 0;
        for (ItemStack input : inputs) {
            if (input.is(Items.WATER_BUCKET)) {
                waterBuckets++;
            } else {
                int count = input.getItem() instanceof IAlchemyItem ? 1 : batch;
                machine.inventory().setStackInSlot(slot++, input.copyWithCount(count));
            }
        }
        if (waterBuckets > 0) {
            int water = waterBuckets * batch * 1_000;
            int filled = machine.recipeWaterTank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, water),
                  IFluidHandler.FluidAction.EXECUTE);
            if (filled != water) throw new AssertionError("Recipe water did not fit: " + filled + "/" + water);
        }
    }

    private static void installStackedInputs(UniversalFactoryBlockEntity machine, List<ItemStack> inputs, int batch) {
        int nextSlot = 0;
        for (ItemStack input : inputs) {
            int matchingSlot = -1;
            for (int slot = 0; slot < nextSlot; slot++) {
                if (ItemHandlerHelper.canItemStacksStack(machine.inventory().getStackInSlot(slot), input)) {
                    matchingSlot = slot;
                    break;
                }
            }
            if (matchingSlot >= 0) {
                machine.inventory().getStackInSlot(matchingSlot).grow(batch);
            } else {
                machine.inventory().setStackInSlot(nextSlot++, input.copyWithCount(batch));
            }
        }
    }

    private static void runUntilOutput(UniversalFactoryBlockEntity machine, ItemStack expected, int operations,
          int maxTicks,
          GameTestHelper helper, ResourceLocation recipeId) {
        int expectedCount = expected.getCount() * operations;
        for (int tick = 0; tick < maxTicks; tick++) {
            machine.serverTick();
            int actualCount = outputCount(machine, expected);
            if (actualCount >= expectedCount) {
                require(actualCount == expectedCount, helper, recipeId + " produced " + actualCount
                      + " items instead of " + expectedCount);
                return;
            }
        }
        helper.fail(recipeId + " produced " + outputCount(machine, expected) + "/" + expectedCount
              + " expected items after " + maxTicks
              + " ticks; status=" + machine.data.get(44) + ", inputs="
              + BloodMekanismCoverageGameTests.describeInputs(machine));
    }

    private static int outputCount(UniversalFactoryBlockEntity machine, ItemStack expected) {
        int count = 0;
        for (int slot = UniversalFactoryBlockEntity.OUTPUT_START;
             slot < UniversalFactoryBlockEntity.OUTPUT_START + UniversalFactoryBlockEntity.OUTPUT_COUNT; slot++) {
            ItemStack actual = machine.inventory().getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(actual, expected)) count += actual.getCount();
        }
        return count;
    }

    private static int batchForAlchemyInputs(List<ItemStack> inputs) {
        int batch = MAX_BATCH;
        for (ItemStack input : inputs) {
            if (!input.is(Items.WATER_BUCKET) && !(input.getItem() instanceof IAlchemyItem)) {
                batch = Math.min(batch, input.getMaxStackSize());
            }
        }
        return Math.max(1, batch);
    }

    private static int batchForStacks(List<ItemStack> inputs) {
        int batch = MAX_BATCH;
        for (ItemStack input : inputs) batch = Math.min(batch, input.getMaxStackSize());
        return Math.max(1, batch);
    }

    private static int batchForRecipe(List<ItemStack> inputs, ItemStack output) {
        return Math.min(batchForStacks(inputs), batchForOutput(output));
    }

    private static int batchForOutput(ItemStack output) {
        int capacity = UniversalFactoryBlockEntity.OUTPUT_COUNT * output.getMaxStackSize();
        return Math.max(1, Math.min(MAX_BATCH, capacity / Math.max(1, output.getCount())));
    }

    private static int arcBatch(UniversalFactoryBlockEntity machine, ArcInputs inputs, RecipeARC recipe) {
        int inputOperations = inputs.input().getMaxStackSize() / recipe.getRequiredInputCount();
        int toolOperations = inputs.tool().isDamageableItem()
              ? inputs.tool().getMaxDamage() - inputs.tool().getDamageValue()
              : inputs.tool().getMaxStackSize();
        int inputFluidOperations = inputs.fluid().isEmpty() ? MAX_BATCH
              : machine.inputTank().getCapacity() / inputs.fluid().getAmount();
        FluidStack fluidOutput = recipe.getFluidOutput();
        int outputFluidOperations = fluidOutput.isEmpty() ? MAX_BATCH
              : machine.outputTank().getCapacity() / fluidOutput.getAmount();
        int outputOperations = arcOutputCapacity(recipe, inputs);
        return Math.max(1, Math.min(MAX_BATCH, Math.min(Math.min(inputOperations, toolOperations),
              Math.min(Math.min(inputFluidOperations, outputFluidOperations), outputOperations))));
    }

    private static int arcOutputCapacity(RecipeARC recipe, ArcInputs inputs) {
        ItemStackHandler simulation = new ItemStackHandler(UniversalFactoryBlockEntity.OUTPUT_COUNT);
        int operations = 0;
        while (operations < MAX_BATCH) {
            ItemStackHandler attempt = copyHandler(simulation);
            boolean fits = true;
            for (ItemStack output : recipe.getAllListedOutputs(inputs.input(), inputs.tool())) {
                if (!ItemHandlerHelper.insertItemStacked(attempt, output.copy(), false).isEmpty()) {
                    fits = false;
                    break;
                }
            }
            if (!fits) break;
            simulation = attempt;
            operations++;
        }
        return Math.max(1, operations);
    }

    private static ItemStackHandler copyHandler(ItemStackHandler source) {
        ItemStackHandler copy = new ItemStackHandler(source.getSlots());
        for (int slot = 0; slot < source.getSlots(); slot++) {
            copy.setStackInSlot(slot, source.getStackInSlot(slot).copy());
        }
        return copy;
    }

    private static void runArcBatch(UniversalFactoryBlockEntity machine, ItemStack expected, int operations,
          int maxTicks, GameTestHelper helper, ResourceLocation recipeId) {
        for (int tick = 0; tick < maxTicks && !machine.inventory().getStackInSlot(0).isEmpty(); tick++) {
            machine.serverTick();
        }
        require(machine.inventory().getStackInSlot(0).isEmpty(), helper,
              recipeId + " did not consume all " + operations + " ARC inputs; status=" + machine.data.get(44));
        int minimumOutput = expected.getCount() * operations;
        require(outputCount(machine, expected) >= minimumOutput, helper,
              recipeId + " produced fewer than " + minimumOutput + " guaranteed items");
    }

    private static ItemStack findSingleInput(Ingredient ingredient, Predicate<ItemStack> resolvesTarget) {
        for (ItemStack candidate : ingredient.getItems()) {
            ItemStack stack = candidate.copy();
            if (!stack.isEmpty() && resolvesTarget.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> findInputs(List<Ingredient> ingredients, Predicate<List<ItemStack>> resolvesTarget) {
        List<List<ItemStack>> candidates = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            List<ItemStack> examples = Arrays.stream(ingredient.getItems())
                  .filter(stack -> !stack.isEmpty())
                  .map(ItemStack::copy)
                  .toList();
            if (examples.isEmpty()) return List.of();
            candidates.add(examples);
        }
        List<ItemStack> selected = new ArrayList<>();
        int[] attempts = {0};
        return chooseInputs(candidates, 0, selected, resolvesTarget, attempts) ? copyStacks(selected) : List.of();
    }

    private static boolean chooseInputs(List<List<ItemStack>> candidates, int index, List<ItemStack> selected,
          Predicate<List<ItemStack>> resolvesTarget, int[] attempts) {
        if (attempts[0] > 100_000) return false;
        if (index == candidates.size()) {
            attempts[0]++;
            return resolvesTarget.test(selected);
        }
        for (ItemStack candidate : candidates.get(index)) {
            selected.add(candidate);
            if (chooseInputs(candidates, index + 1, selected, resolvesTarget, attempts)) return true;
            selected.remove(selected.size() - 1);
        }
        return false;
    }

    private static ArcInputs findArcInputs(GameTestHelper helper, RecipeARC recipe) {
        List<FluidStack> fluids = recipe.getFluidIngredient() == null
              ? List.of(FluidStack.EMPTY)
              : recipe.getFluidIngredient().getRepresentations();
        ArcInputs shadowed = null;
        for (ItemStack input : recipe.getInput().getItems()) {
            if (input.isEmpty()) continue;
            for (ItemStack tool : recipe.getTool().getItems()) {
                if (tool.isEmpty()) continue;
                for (FluidStack fluid : fluids) {
                    RecipeARC resolved = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getARC(
                          helper.getLevel(), input, tool, fluid);
                    if (sameRecipe(resolved, recipe)) return new ArcInputs(input.copy(), tool.copy(), fluid.copy());
                    if (resolved != null && shadowed == null) {
                        shadowed = new ArcInputs(input.copy(), tool.copy(), fluid.copy());
                    }
                }
            }
        }
        return shadowed;
    }

    private static List<EffectHolder> flaskEffects(ItemStack flask) {
        return new ArrayList<>(((ItemAlchemyFlask) flask.getItem()).getEffectHoldersOfFlask(flask));
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static boolean sameRecipe(BloodMagicRecipe actual, BloodMagicRecipe expected) {
        return actual != null && actual.getId().equals(expected.getId());
    }

    private static void requireInput(ItemStack input, GameTestHelper helper, ResourceLocation recipeId) {
        require(!input.isEmpty(), helper, "No resolvable input for " + recipeId);
    }

    private static void requireInputs(List<ItemStack> inputs, GameTestHelper helper, ResourceLocation recipeId) {
        require(!inputs.isEmpty(), helper, "No resolvable input combination for " + recipeId);
    }

    private static <T extends BloodMagicRecipe> List<T> builtInRecipes(GameTestHelper helper, RecipeType<T> type,
          int expectedCount) {
        List<T> recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(type).stream()
              .filter(recipe -> recipe.getId().getNamespace().equals(BLOOD_MAGIC))
              .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
              .toList();
        require(recipes.size() == expectedCount, helper,
              "Expected " + expectedCount + " built-in recipes for " + type + ", found " + recipes.size());
        return recipes;
    }

    private static void passFamily(GameTestHelper helper, String family, int count, int operations) {
        BloodMekanism.LOGGER.info(
              "GameTest recipe matrix: batch-verified all {} built-in {} recipes across {} operations",
              count, family, operations);
        helper.succeed();
    }

    private static void require(boolean condition, GameTestHelper helper, String message) {
        if (!condition) helper.fail(message);
    }

    private record ArcInputs(ItemStack input, ItemStack tool, FluidStack fluid) {
    }
}
