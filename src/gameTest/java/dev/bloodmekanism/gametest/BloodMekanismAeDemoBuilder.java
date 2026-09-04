package dev.bloodmekanism.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.items.storage.CreativeCellItem;
import appeng.me.helpers.BaseActionSource;
import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.machine.blockentity.WillGeneratorBlockEntity;
import dev.bloodmekanism.will.WillGas;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import wayoftime.bloodmagic.common.item.potion.ItemAlchemyFlask;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
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
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@PrefixGameTestTemplate(false)
@GameTestHolder(BloodMekanism.MOD_ID)
public final class BloodMekanismAeDemoBuilder {
    public static final int EXPECTED_PATTERN_COUNT = 476;
    public static final int EXPECTED_PROVIDER_COUNT = 16;

    private static final String BLOOD_MAGIC = "bloodmagic";
    private static final ResourceLocation EXTENDED_PROVIDER_ID =
          new ResourceLocation("expatternprovider", "ex_pattern_provider");
    private static final BlockPos LIBRARY_CENTER = new BlockPos(40, -58, 67);
    private static final BlockPos DRIVE_POS = new BlockPos(39, -59, 64);
    private static final int PATTERNS_PER_PROVIDER = 36;
    private static final int STOCK_CRAFTS_PER_PATTERN = 100_000;

    private BloodMekanismAeDemoBuilder() {
    }

    @GameTest(templateNamespace = "bloodmagic", template = "four_way_corridor", timeoutTicks = 100)
    public static void everyBuiltInRecipeEncodesAsAe2ProcessingPattern(GameTestHelper helper) {
        List<PatternEntry> patterns = createPatternCatalog(helper.getLevel());
        require(patterns.size() == EXPECTED_PATTERN_COUNT,
              "Expected " + EXPECTED_PATTERN_COUNT + " AE2 patterns, found " + patterns.size());
        for (PatternEntry pattern : patterns) {
            require(PatternDetailsHelper.decodePattern(pattern.pattern(), helper.getLevel()) != null,
                  "AE2 could not decode " + pattern.recipeId());
        }
        helper.succeed();
    }

    /** Called by the local attach tool while the demonstration save is open. */
    public static String build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<PatternEntry> patterns = createPatternCatalog(level);
        Map<Family, Integer> providerCounts = requiredProviderCounts(patterns);
        int totalProviders = providerCounts.values().stream().mapToInt(Integer::intValue).sum();
        require(totalProviders == EXPECTED_PROVIDER_COUNT,
              "Expected " + EXPECTED_PROVIDER_COUNT + " providers, found " + totalProviders);

        buildHall(level);
        StorageSummary storage = buildNetwork(level, patterns);
        installPatterns(level, patterns, providerCounts);

        BlockPos arrival = new BlockPos(LIBRARY_CENTER.getX(), -59, LIBRARY_CENTER.getZ() - 5);
        server.getPlayerList().getPlayers().forEach(player -> player.teleportTo(
              level, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 0, 0));

        String counts = Arrays.stream(Family.values())
              .map(family -> family.label + "=" + countFamily(patterns, family))
              .reduce((left, right) -> left + ", " + right)
              .orElse("");
        BloodMekanism.LOGGER.info("Built AE2 processing-pattern demonstration: {} patterns in {} providers ({})",
              patterns.size(), totalProviders, counts);
        return "patterns=" + patterns.size() + ", providers=" + totalProviders + ", materials="
              + storage.materialTypes + ", cells=" + storage.cells + ", " + counts;
    }

    public static String verify(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int providers = 0;
        int patterns = 0;
        int onlineProviders = 0;
        int machines = 0;
        List<BlockPos> positions = providerPositions();
        int positionIndex = 0;
        for (Family family : Family.values()) {
            for (int i = 0; i < providersFor(family.expectedRecipes); i++) {
                BlockPos pos = positions.get(positionIndex++);
                if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity provider) {
                    providers++;
                    patterns += provider.getLogic().getAvailablePatterns().size();
                    if (provider.getMainNode().isOnline()) onlineProviders++;
                }
                if (level.getBlockState(pos.north()).is(machineBlock(family))) machines++;
                require(level.getBlockState(pos).getValue(PatternProviderBlock.PUSH_DIRECTION)
                            == PushDirection.NORTH,
                      "Provider at " + pos + " does not push toward its machine");
            }
        }
        require(providers == EXPECTED_PROVIDER_COUNT,
              "Saved hall contains " + providers + "/" + EXPECTED_PROVIDER_COUNT + " providers");
        require(patterns == EXPECTED_PATTERN_COUNT,
              "Network exposes " + patterns + "/" + EXPECTED_PATTERN_COUNT + " patterns");
        require(onlineProviders == EXPECTED_PROVIDER_COUNT,
              "Network has " + onlineProviders + "/" + EXPECTED_PROVIDER_COUNT + " online providers");
        require(machines == EXPECTED_PROVIDER_COUNT,
              "Hall has " + machines + "/" + EXPECTED_PROVIDER_COUNT + " matching machines");
        StorageSummary storage = verifyInputStorage(level, createPatternCatalog(level));
        return "patterns=" + patterns + ", providers=" + providers + ", machines=" + machines
              + ", online=" + onlineProviders + ", materials=" + storage.materialTypes
              + ", cells=" + storage.cells;
    }

    private static List<PatternEntry> createPatternCatalog(ServerLevel level) {
        List<PatternEntry> patterns = new ArrayList<>(EXPECTED_PATTERN_COUNT);

        for (RecipeBloodAltar recipe : recipes(level, BloodMagicRecipeType.ALTAR.get(), Family.ALTAR)) {
            ItemStack input = findSingleInput(recipe.getInput(), stack -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getBloodAltar(level, stack), recipe));
            require(!input.isEmpty(), "No resolvable altar input for " + recipe.getId());
            patterns.add(pattern(Family.ALTAR, recipe.getId(), List.of(input), List.of(recipe.getOutput()),
                  List.of(), List.of()));
        }

        for (RecipeAlchemyTable recipe : recipes(level, BloodMagicRecipeType.ALCHEMYTABLE.get(),
              Family.ALCHEMY_TABLE)) {
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyTable(level, stacks), recipe));
            require(!inputs.isEmpty(), "No resolvable alchemy table inputs for " + recipe.getId());
            patterns.add(alchemyPattern(Family.ALCHEMY_TABLE, recipe.getId(), inputs,
                  recipe.getOutput(copyStacks(inputs))));
        }

        for (RecipePotionFlaskBase recipe : recipes(level, BloodMagicRecipeType.POTIONFLASK.get(), Family.FLASK)) {
            ItemStack flask = recipe.getExamplePotionFlask();
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getPotionFlaskRecipe(
                        level, flask, flaskEffects(flask.copy()), stacks), recipe));
            require(!inputs.isEmpty(), "No resolvable potion flask inputs for " + recipe.getId());
            ItemStack output = recipe.getOutput(flask.copy(), flaskEffects(flask.copy()));
            if (output.getItem() instanceof ItemAlchemyFlask outputFlask) outputFlask.resyncEffectInstances(output);
            List<ItemStack> allInputs = new ArrayList<>(inputs.size() + 1);
            allInputs.add(flask);
            allInputs.addAll(inputs);
            patterns.add(alchemyPattern(Family.FLASK, recipe.getId(), allInputs, output));
        }

        for (RecipeAlchemyArray recipe : recipes(level, BloodMagicRecipeType.ARRAY.get(), Family.ARRAY)) {
            List<Ingredient> ingredients = List.of(recipe.getBaseInput(), recipe.getAddedInput());
            List<ItemStack> inputs = findInputs(ingredients, stacks -> {
                var match = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyArray(
                      level, stacks.get(0), stacks.get(1));
                return match != null && match.getLeft() && sameRecipe(match.getRight(), recipe);
            });
            require(!inputs.isEmpty(), "No resolvable alchemy array inputs for " + recipe.getId());
            patterns.add(pattern(Family.ARRAY, recipe.getId(), inputs, List.of(recipe.getOutput()),
                  List.of(), List.of()));
        }

        for (RecipeTartaricForge recipe : recipes(level, BloodMagicRecipeType.TARTARICFORGE.get(),
              Family.SOUL_FORGE)) {
            List<ItemStack> inputs = findInputs(recipe.getInput(), stacks -> sameRecipe(
                  BloodMagicAPI.INSTANCE.getRecipeRegistrar().getTartaricForge(level, stacks), recipe));
            require(!inputs.isEmpty(), "No resolvable soul forge inputs for " + recipe.getId());
            patterns.add(pattern(Family.SOUL_FORGE, recipe.getId(), inputs, List.of(recipe.getOutput()),
                  List.of(), List.of()));
        }

        for (RecipeARC recipe : recipes(level, BloodMagicRecipeType.ARC.get(), Family.ARC)) {
            ArcInputs inputs = findArcInputs(level, recipe);
            require(inputs != null, "No resolvable ARC inputs for " + recipe.getId());
            ItemStack countedInput = inputs.input.copyWithCount(recipe.getRequiredInputCount());
            List<FluidStack> fluidInputs = inputs.fluid.isEmpty() ? List.of() : List.of(inputs.fluid);
            FluidStack fluidOutput = recipe.getFluidOutput();
            List<FluidStack> fluidOutputs = fluidOutput.isEmpty() ? List.of() : List.of(fluidOutput);
            ItemStack primaryOutput = recipe.getAllListedOutputs(inputs.input, inputs.tool).get(0);
            patterns.add(pattern(Family.ARC, recipe.getId(), List.of(countedInput, inputs.tool),
                  List.of(primaryOutput), fluidInputs, fluidOutputs));
        }

        patterns.sort(Comparator.comparing((PatternEntry entry) -> entry.family.ordinal())
              .thenComparing(entry -> entry.recipeId.toString()));
        for (Family family : Family.values()) {
            require(countFamily(patterns, family) == family.expectedRecipes,
                  "Expected " + family.expectedRecipes + " " + family.label + " recipes, found "
                        + countFamily(patterns, family));
        }
        require(patterns.size() == EXPECTED_PATTERN_COUNT,
              "Expected " + EXPECTED_PATTERN_COUNT + " recipes, found " + patterns.size());
        return patterns;
    }

    private static PatternEntry alchemyPattern(Family family, ResourceLocation recipeId, List<ItemStack> inputs,
          ItemStack output) {
        List<ItemStack> itemInputs = new ArrayList<>();
        int waterBuckets = 0;
        for (ItemStack input : inputs) {
            if (input.is(Items.WATER_BUCKET)) waterBuckets += input.getCount();
            else itemInputs.add(input);
        }
        List<FluidStack> fluids = waterBuckets == 0 ? List.of()
              : List.of(new FluidStack(Fluids.WATER, waterBuckets * AEFluidKey.AMOUNT_BUCKET));
        return pattern(family, recipeId, itemInputs, List.of(output), fluids, List.of());
    }

    private static PatternEntry pattern(Family family, ResourceLocation recipeId, List<ItemStack> itemInputs,
          List<ItemStack> itemOutputs, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {
        GenericStack[] inputs = genericStacks(itemInputs, fluidInputs);
        GenericStack[] outputs = genericStacks(itemOutputs, fluidOutputs);
        return new PatternEntry(family, recipeId, PatternDetailsHelper.encodeProcessingPattern(inputs, outputs),
              List.copyOf(Arrays.asList(inputs)));
    }

    private static GenericStack[] genericStacks(List<ItemStack> items, List<FluidStack> fluids) {
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            AEItemKey key = AEItemKey.of(stack);
            require(key != null, "Could not encode item " + stack);
            totals.merge(key, (long) stack.getCount(), Long::sum);
        }
        for (FluidStack stack : fluids) {
            AEFluidKey key = AEFluidKey.of(stack);
            require(key != null, "Could not encode fluid " + stack);
            totals.merge(key, (long) stack.getAmount(), Long::sum);
        }
        return totals.entrySet().stream()
              .map(entry -> new GenericStack(entry.getKey(), entry.getValue()))
              .toArray(GenericStack[]::new);
    }

    private static void buildHall(ServerLevel level) {
        BlockPos min = new BlockPos(10, -60, 61);
        BlockPos max = new BlockPos(71, -52, 73);
        fill(level, min, max, Blocks.AIR);
        fill(level, new BlockPos(29, -60, 61), new BlockPos(51, -60, 73), Blocks.SMOOTH_STONE);
        fill(level, new BlockPos(29, -52, 61), new BlockPos(51, -52, 73), Blocks.WHITE_STAINED_GLASS);
        fill(level, new BlockPos(29, -59, 73), new BlockPos(51, -53, 73), Blocks.QUARTZ_BLOCK);
        fill(level, new BlockPos(29, -59, 61), new BlockPos(29, -53, 73), Blocks.QUARTZ_BLOCK);
        fill(level, new BlockPos(51, -59, 61), new BlockPos(51, -53, 73), Blocks.QUARTZ_BLOCK);

        List<BlockPos> positions = providerPositions();
        int positionIndex = 0;
        for (Family family : Family.values()) {
            int providers = providersFor(family.expectedRecipes);
            for (int i = 0; i < providers; i++) {
                BlockPos providerPos = positions.get(positionIndex++);
                level.setBlock(providerPos.below(), family.floorBlock.defaultBlockState(), 3);
            }
        }
        for (int x = 31; x <= 49; x += 3) {
            level.setBlock(new BlockPos(x, -59, 62), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        }
    }

    private static StorageSummary buildNetwork(ServerLevel level, List<PatternEntry> patterns) {
        level.setBlock(LIBRARY_CENTER, AEBlocks.CONTROLLER.block().defaultBlockState(), 3);
        level.setBlock(LIBRARY_CENTER.below(), AEBlocks.CONTROLLER.block().defaultBlockState(), 3);

        for (int x = 32; x <= 39; x++) placeDenseCable(level, new BlockPos(x, -58, 70));
        for (int x = 41; x <= 48; x++) placeDenseCable(level, new BlockPos(x, -58, 70));
        for (int x = 32; x <= 39; x++) placeDenseCable(level, new BlockPos(x, -58, 67));
        for (int x = 41; x <= 48; x++) placeDenseCable(level, new BlockPos(x, -58, 67));
        for (int z = 68; z <= 70; z++) {
            placeDenseCable(level, new BlockPos(32, -58, z));
            placeDenseCable(level, new BlockPos(48, -58, z));
        }

        level.setBlock(LIBRARY_CENTER.below().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        placeGlassCable(level, new BlockPos(40, -59, 65));
        placeGlassCable(level, new BlockPos(40, -59, 64));
        BlockPos accessTerminal = new BlockPos(40, -59, 63);
        placeGlassCable(level, accessTerminal);
        setPart(level, accessTerminal, Direction.SOUTH, AEParts.PATTERN_ACCESS_TERMINAL.stack().getItem());

        BlockPos encodingTerminal = new BlockPos(41, -59, 64);
        placeGlassCable(level, encodingTerminal);
        setPart(level, encodingTerminal, Direction.SOUTH, AEParts.PATTERN_ENCODING_TERMINAL.stack().getItem());

        level.setBlock(DRIVE_POS, AEBlocks.DRIVE.block().defaultBlockState(), 3);
        DriveBlockEntity drive = requireBlockEntity(level, DRIVE_POS, DriveBlockEntity.class);
        BlockOrientation.get(Direction.NORTH).setOn(drive);
        return populateInputStorage(drive, patterns);
    }

    private static StorageSummary populateInputStorage(DriveBlockEntity drive, List<PatternEntry> patterns) {
        Map<AEKey, Long> pending = requiredInputStock(patterns);
        int cells = addStorageCells(drive, pending, AEItemKey.class, AEItems.ITEM_CELL_CREATIVE.stack());
        cells += addStorageCells(drive, pending, AEFluidKey.class, AEItems.FLUID_CELL_CREATIVE.stack());
        require(pending.isEmpty(), "Could not store all demonstration inputs: " + pending.size() + " remain");
        drive.saveChanges();
        return new StorageSummary(requiredInputStock(patterns).size(), cells);
    }

    private static int addStorageCells(DriveBlockEntity drive, Map<AEKey, Long> pending,
          Class<? extends AEKey> keyType, ItemStack cellTemplate) {
        int cells = 0;
        while (pending.keySet().stream().anyMatch(keyType::isInstance)) {
            ItemStack cellStack = cellTemplate.copy();
            var config = ((CreativeCellItem) cellStack.getItem()).getConfigInventory(cellStack);
            boolean insertedAny = false;
            Iterator<Map.Entry<AEKey, Long>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<AEKey, Long> entry = iterator.next();
                if (!keyType.isInstance(entry.getKey())) continue;
                config.insert(entry.getKey(), 1, Actionable.MODULATE, new BaseActionSource());
                if (config.keySet().contains(entry.getKey())) {
                    insertedAny = true;
                    iterator.remove();
                }
            }
            require(insertedAny, "A fresh storage cell rejected " + keyType.getSimpleName() + " inputs");
            require(drive.getInternalInventory().addItems(cellStack).isEmpty(),
                  "AE drive ran out of slots while storing demonstration inputs");
            cells++;
        }
        return cells;
    }

    private static Map<AEKey, Long> requiredInputStock(List<PatternEntry> patterns) {
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (PatternEntry pattern : patterns) {
            for (GenericStack input : pattern.inputs) {
                totals.merge(input.what(), input.amount() * STOCK_CRAFTS_PER_PATTERN, Long::sum);
            }
        }
        return totals;
    }

    private static StorageSummary verifyInputStorage(ServerLevel level, List<PatternEntry> patterns) {
        DriveBlockEntity drive = requireBlockEntity(level, DRIVE_POS, DriveBlockEntity.class);
        List<appeng.api.storage.MEStorage> storageCells = new ArrayList<>();
        int cells = 0;
        for (int slot = 0; slot < drive.getCellCount(); slot++) {
            if (!drive.getInternalInventory().getStackInSlot(slot).isEmpty()) cells++;
            var cell = drive.getCellInventory(slot);
            if (cell != null) storageCells.add(cell);
        }
        Map<AEKey, Long> required = requiredInputStock(patterns);
        for (Map.Entry<AEKey, Long> entry : required.entrySet()) {
            long available = 0;
            for (var cell : storageCells) {
                available += cell.extract(entry.getKey(), entry.getValue() - available,
                      Actionable.SIMULATE, new BaseActionSource());
                if (available >= entry.getValue()) break;
            }
            require(available >= entry.getValue(),
                  "Storage is missing input " + entry.getKey() + ": " + available + "/" + entry.getValue());
        }
        return new StorageSummary(required.size(), cells);
    }

    private static void installPatterns(ServerLevel level, List<PatternEntry> patterns,
          Map<Family, Integer> providerCounts) {
        List<BlockPos> positions = providerPositions();
        int positionIndex = 0;
        int patternIndex = 0;
        for (Family family : Family.values()) {
            int familyProviders = providerCounts.get(family);
            for (int providerIndex = 0; providerIndex < familyProviders; providerIndex++) {
                BlockPos pos = positions.get(positionIndex++);
                BlockPos machinePos = pos.north();
                level.setBlock(machinePos, machineBlock(family).defaultBlockState(), 3);
                UniversalFactoryBlockEntity machine = requireBlockEntity(level, machinePos,
                      UniversalFactoryBlockEntity.class);
                machine.energy().setStored(machine.energy().getMaxEnergyStored());
                if (family == Family.SOUL_FORGE) {
                    // AE2 stores items and fluids, while Will is a Mekanism gas. Keep a visible,
                    // dedicated gas source beside each Soul Forge for the demonstration.
                    machine.willTank().insert(WillGas.stack(EnumDemonWillType.DEFAULT,
                          machine.willTank().getCapacity()), Action.EXECUTE, AutomationType.INTERNAL);
                    BlockPos willSourcePos = machinePos.north();
                    level.setBlock(willSourcePos, ModContent.WILL_GENERATOR.get().defaultBlockState(), 3);
                    WillGeneratorBlockEntity willSource = requireBlockEntity(level, willSourcePos,
                          WillGeneratorBlockEntity.class);
                    willSource.energy().setStored(willSource.energy().getMaxEnergyStored());
                    willSource.inventory().setStackInSlot(WillGeneratorBlockEntity.INPUT_SLOT,
                          new ItemStack(Items.SOUL_SAND, 64));
                    willSource.lifeTank().fill(new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(),
                          willSource.lifeTank().getCapacity()), IFluidHandler.FluidAction.EXECUTE);
                    willSource.setChanged();
                }

                level.setBlock(pos, extendedProviderBlock().defaultBlockState()
                      .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.NORTH), 3);
                PatternProviderBlockEntity provider = requireBlockEntity(level, pos,
                      PatternProviderBlockEntity.class);
                provider.setName(String.format("%02d %s %02d/%02d", family.ordinal() + 1, family.label,
                      providerIndex + 1, familyProviders));
                int installed = 0;
                while (patternIndex < patterns.size() && patterns.get(patternIndex).family == family
                      && installed < PATTERNS_PER_PROVIDER) {
                    ItemStack remainder = provider.getLogic().getPatternInv()
                          .addItems(patterns.get(patternIndex).pattern.copy());
                    require(remainder.isEmpty(), "Provider rejected pattern " + patterns.get(patternIndex).recipeId);
                    installed++;
                    patternIndex++;
                }
                provider.getLogic().updatePatterns();
                provider.saveChanges();
            }
        }
        require(patternIndex == patterns.size(),
              "Installed " + patternIndex + "/" + patterns.size() + " patterns");
    }

    private static Block machineBlock(Family family) {
        return switch (family) {
            case ALTAR -> ModContent.UNIVERSAL_FACTORIES.get(FactoryTier.ABSOLUTE).get();
            case ALCHEMY_TABLE, FLASK -> ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_TABLE).get();
            case ARRAY -> ModContent.PROCESS_FACTORIES.get(FactoryMode.ALCHEMY_ARRAY).get();
            case SOUL_FORGE -> ModContent.PROCESS_FACTORIES.get(FactoryMode.SOUL_FORGE).get();
            case ARC -> ModContent.PROCESS_FACTORIES.get(FactoryMode.ARC).get();
        };
    }

    private static Block extendedProviderBlock() {
        Block block = BuiltInRegistries.BLOCK.get(EXTENDED_PROVIDER_ID);
        require(block != Blocks.AIR, "ExtendedAE block is not registered: " + EXTENDED_PROVIDER_ID);
        return block;
    }

    private static List<BlockPos> providerPositions() {
        List<BlockPos> positions = new ArrayList<>(EXPECTED_PROVIDER_COUNT);
        for (int x = 32; x <= 39; x++) positions.add(new BlockPos(x, -59, 70));
        for (int x = 41; x <= 48; x++) positions.add(new BlockPos(x, -59, 70));
        return positions;
    }

    private static Map<Family, Integer> requiredProviderCounts(List<PatternEntry> patterns) {
        Map<Family, Integer> result = new EnumMap<>(Family.class);
        for (Family family : Family.values()) result.put(family, providersFor(countFamily(patterns, family)));
        return result;
    }

    private static int providersFor(int patternCount) {
        return (patternCount + PATTERNS_PER_PROVIDER - 1) / PATTERNS_PER_PROVIDER;
    }

    private static int countFamily(List<PatternEntry> patterns, Family family) {
        return (int) patterns.stream().filter(pattern -> pattern.family == family).count();
    }

    private static void fill(ServerLevel level, BlockPos min, BlockPos max, Block block) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeDenseCable(ServerLevel level, BlockPos pos) {
        setPart(level, pos, null, AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT));
    }

    private static void placeGlassCable(ServerLevel level, BlockPos pos) {
        setPart(level, pos, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
    }

    private static void setPart(ServerLevel level, BlockPos pos, Direction side,
          net.minecraft.world.item.Item item) {
        var part = PartHelper.setPart(level, pos, side, null, partItem(item));
        require(part != null, "Could not place AE2 part at " + pos + " on " + side);
    }

    @SuppressWarnings("unchecked")
    private static <T extends appeng.api.parts.IPart> IPartItem<T> partItem(net.minecraft.world.item.Item item) {
        return (IPartItem<T>) item;
    }

    private static ItemStack findSingleInput(Ingredient ingredient, Predicate<ItemStack> resolvesTarget) {
        for (ItemStack candidate : ingredient.getItems()) {
            ItemStack stack = candidate.copy();
            if (!stack.isEmpty() && resolvesTarget.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> findInputs(List<Ingredient> ingredients,
          Predicate<List<ItemStack>> resolvesTarget) {
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

    private static ArcInputs findArcInputs(ServerLevel level, RecipeARC recipe) {
        List<FluidStack> fluids = recipe.getFluidIngredient() == null
              ? List.of(FluidStack.EMPTY)
              : recipe.getFluidIngredient().getRepresentations();
        ArcInputs shadowed = null;
        for (ItemStack input : recipe.getInput().getItems()) {
            if (input.isEmpty()) continue;
            for (ItemStack tool : recipe.getTool().getItems()) {
                if (tool.isEmpty()) continue;
                for (FluidStack fluid : fluids) {
                    RecipeARC resolved = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getARC(level, input, tool, fluid);
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

    private static <T extends BloodMagicRecipe> List<T> recipes(ServerLevel level, RecipeType<T> type,
          Family family) {
        List<T> recipes = level.getRecipeManager().getAllRecipesFor(type).stream()
              .filter(recipe -> recipe.getId().getNamespace().equals(BLOOD_MAGIC))
              .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
              .toList();
        require(recipes.size() == family.expectedRecipes,
              "Expected " + family.expectedRecipes + " " + family.label + " recipes, found " + recipes.size());
        return recipes;
    }

    private static <T> T requireBlockEntity(ServerLevel level, BlockPos pos, Class<T> type) {
        Object blockEntity = level.getBlockEntity(pos);
        require(type.isInstance(blockEntity), "Expected " + type.getSimpleName() + " at " + pos
              + ", got " + blockEntity);
        return type.cast(blockEntity);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Family {
        ALTAR("Blood Altar", 23, Blocks.RED_CONCRETE),
        ALCHEMY_TABLE("Alchemy Table", 131, Blocks.YELLOW_CONCRETE),
        FLASK("Potion Flask", 110, Blocks.MAGENTA_CONCRETE),
        ARRAY("Alchemy Array", 26, Blocks.LIME_CONCRETE),
        SOUL_FORGE("Soul Forge", 89, Blocks.CYAN_CONCRETE),
        ARC("ARC", 97, Blocks.ORANGE_CONCRETE);

        private final String label;
        private final int expectedRecipes;
        private final Block floorBlock;

        Family(String label, int expectedRecipes, Block floorBlock) {
            this.label = label;
            this.expectedRecipes = expectedRecipes;
            this.floorBlock = floorBlock;
        }
    }

    private record PatternEntry(Family family, ResourceLocation recipeId, ItemStack pattern,
          List<GenericStack> inputs) {
    }

    private record StorageSummary(int materialTypes, int cells) {
    }

    private record ArcInputs(ItemStack input, ItemStack tool, FluidStack fluid) {
    }
}
