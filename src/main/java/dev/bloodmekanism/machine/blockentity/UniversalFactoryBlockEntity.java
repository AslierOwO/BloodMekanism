package dev.bloodmekanism.machine.blockentity;

import dev.bloodmekanism.item.MechanicalBloodOrbItem;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.MachineBlock;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.ProcessingStatus;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.menu.UniversalFactoryMenu;
import dev.bloodmekanism.registry.ModContent;
import dev.bloodmekanism.will.WillGas;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.api.compat.IDemonWill;
import wayoftime.bloodmagic.api.compat.IDemonWillGem;
import wayoftime.bloodmagic.api.event.BloodMagicCraftedEvent;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;
import wayoftime.bloodmagic.common.tags.BloodMagicTags;
import wayoftime.bloodmagic.common.item.BloodOrb;
import wayoftime.bloodmagic.common.item.IAlchemyItem;
import wayoftime.bloodmagic.common.item.IBindable;
import wayoftime.bloodmagic.common.item.IBloodOrb;
import wayoftime.bloodmagic.common.item.potion.ItemAlchemyFlask;
import wayoftime.bloodmagic.common.recipe.BloodMagicRecipeType;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.impl.BloodMagicAPI;
import wayoftime.bloodmagic.recipe.EffectHolder;
import wayoftime.bloodmagic.recipe.RecipeARC;
import wayoftime.bloodmagic.recipe.RecipeAlchemyArray;
import wayoftime.bloodmagic.recipe.RecipeAlchemyTable;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.recipe.RecipeTartaricForge;
import wayoftime.bloodmagic.recipe.flask.RecipePotionFlaskBase;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class UniversalFactoryBlockEntity extends BaseMachineBlockEntity {
    public static final int INPUT_COUNT = 9;
    public static final int CATALYST_SLOT = 9;
    public static final int OUTPUT_START = 10;
    public static final int OUTPUT_COUNT = 9;
    public static final int SPEED_UPGRADE_SLOT = 19;
    public static final int ENERGY_UPGRADE_SLOT = 20;
    public static final int SLOT_COUNT = 21;
    public static final int ALTAR_TARGET_BUTTON = 8;
    private static final int ALTAR_BUFFER_SLOTS = 5;
    private static final ResourceLocation[] ALTAR_TARGETS = {
          new ResourceLocation("bloodmagic", "blankslate"),
          new ResourceLocation("bloodmagic", "reinforcedslate"),
          new ResourceLocation("bloodmagic", "infusedslate"),
          new ResourceLocation("bloodmagic", "demonslate"),
          new ResourceLocation("bloodmagic", "etherealslate")
    };

    private final FactoryTier tier;
    private final FactoryMode fixedMode;
    private FactoryMode mode = FactoryMode.ALTAR;
    private final FluidTank inputTank;
    private final FluidTank outputTank;
    private final IGasTank willTank;
    private final net.minecraftforge.items.ItemStackHandler altarBuffer;
    private final LazyOptional<IFluidHandler> fluidCapability;
    private final LazyOptional<IGasHandler> gasCapability;
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedFluidCapabilities = new EnumMap<>(Direction.class);
    private final Map<Direction, LazyOptional<IGasHandler>> sidedGasCapabilities = new EnumMap<>(Direction.class);
    private int progress;
    private int maxProgress;
    private int activeInputMask;
    private String processKey = "";
    private int fluidTransferCooldown;
    private ProcessingStatus processingStatus = ProcessingStatus.IDLE;
    private int mechanicalLpRate;
    private int altarTarget;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored();
                case 1 -> energy.getMaxEnergyStored();
                case 2 -> inputTank.getFluidAmount();
                case 3 -> inputTank.getCapacity();
                case 4 -> outputTank.getFluidAmount();
                case 5 -> outputTank.getCapacity();
                case 6 -> progress;
                case 7 -> maxProgress;
                case 8 -> 0; // Reserved legacy auto-input index.
                case 9 -> autoOutput ? 1 : 0;
                case 10 -> redstoneMode.ordinal();
                case 11 -> mode.ordinal();
                case 12 -> tier.ordinal();
                case 13 -> fixedMode == null ? 0 : 1;
                case 14 -> fluidRegistryId(inputTank.getFluid());
                case 15 -> fluidRegistryId(outputTank.getFluid());
                case 16 -> activeInputMask;
                case 17 -> speedUpgradeCount();
                case 18 -> energyUpgradeCount();
                case 43 -> lastEnergyUsed();
                case 44 -> processingStatus.ordinal();
                case 45 -> mechanicalStoredLp();
                case 46 -> mechanicalLpCapacity();
                case 47 -> mechanicalLpRate;
                case 48 -> gasRegistryId(willTank.getStack());
                case 49 -> (int) Math.min(Integer.MAX_VALUE, willTank.getStored());
                case 50 -> (int) Math.min(Integer.MAX_VALUE, willTank.getCapacity());
                case 51 -> altarTarget;
                default -> {
                    int sideIndex = index - 19;
                    if (sideIndex >= 0 && sideIndex < MachineResource.values().length * RelativeMachineSide.values().length) {
                        MachineResource resource = MachineResource.values()[sideIndex / RelativeMachineSide.values().length];
                        RelativeMachineSide side = RelativeMachineSide.values()[sideIndex % RelativeMachineSide.values().length];
                        yield sideMode(resource, side).ordinal();
                    }
                    yield 0;
                }
            };
        }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return 52; }
    };

    private static int fluidRegistryId(FluidStack stack) {
        return stack.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(stack.getFluid()) + 1;
    }

    private static int gasRegistryId(GasStack stack) {
        EnumDemonWillType type = stack.isEmpty() ? null : WillGas.typeOf(stack.getType());
        return type == null ? 0 : type.ordinal() + 1;
    }

    public UniversalFactoryBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof MachineBlock block && block.tier() != null ? block.tier() : FactoryTier.BASIC);
    }

    private UniversalFactoryBlockEntity(BlockPos pos, BlockState state, FactoryTier tier) {
        super(ModContent.UNIVERSAL_FACTORY_BLOCK_ENTITY.get(), pos, state, SLOT_COUNT, tier.energyCapacity(), SPEED_UPGRADE_SLOT, ENERGY_UPGRADE_SLOT);
        this.tier = tier;
        this.fixedMode = state.getBlock() instanceof MachineBlock block ? block.fixedMode() : null;
        if (fixedMode != null) this.mode = fixedMode;
        this.inputTank = changedTank(tier.tankCapacity());
        this.outputTank = changedTank(tier.tankCapacity());
        this.willTank = ChemicalTankBuilder.GAS.input(tier.tankCapacity(), WillGas::isWill, this::setChanged);
        this.altarBuffer = new net.minecraftforge.items.ItemStackHandler(ALTAR_BUFFER_SLOTS) {
            @Override protected void onContentsChanged(int slot) { UniversalFactoryBlockEntity.this.setChanged(); }
        };
        this.fluidCapability = LazyOptional.of(() -> new FactoryFluidHandler(null));
        this.gasCapability = LazyOptional.of(() -> new FactoryGasHandler(null));
        for (Direction direction : Direction.values()) {
            sidedFluidCapabilities.put(direction, LazyOptional.of(() -> new FactoryFluidHandler(direction)));
            sidedGasCapabilities.put(direction, LazyOptional.of(() -> new FactoryGasHandler(direction)));
        }
    }

    private FluidTank changedTank(int capacity) {
        return new FluidTank(capacity) {
            @Override protected void onContentsChanged() { UniversalFactoryBlockEntity.this.setChanged(); }
        };
    }

    @Override
    public void serverTick() {
        lastEnergyUsed = 0;
        mechanicalLpRate = 0;
        processingStatus = ProcessingStatus.IDLE;
        transferItems();
        transferFluids();
        activeInputMask = 0;
        if (!redstoneAllowsWork() || level == null) {
            processingStatus = ProcessingStatus.REDSTONE_DISABLED;
            updateActive(false);
            return;
        }
        boolean active = switch (mode) {
            case ALTAR -> chargeOrb() || tickAltar();
            case ALCHEMY_TABLE -> tickAlchemy();
            case ALCHEMY_ARRAY -> tickArray();
            case SOUL_FORGE -> tickSoulForge();
            case ARC -> tickArc();
        };
        updateActive(active);
        if (!active && progress > 0 && mode == FactoryMode.ALTAR) progress = Math.max(0, progress - 1);
    }

    private boolean chargeOrb() {
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        if (stack.getItem() instanceof MechanicalBloodOrbItem mechanicalOrb) {
            int remaining = mechanicalOrb.getCapacity() - mechanicalOrb.getStoredLp(stack);
            if (remaining <= 0) return false;
            if (!isLifeEssence(inputTank.getFluid())) {
                processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
                return false;
            }
            int requested = Math.min(remaining, Math.min(inputTank.getFluidAmount(),
                  Math.max(1, (int) Math.floor(mechanicalOrb.getFillRate() * tier.batchSize() * speedMultiplier()))));
            int energyCost = upgradedOperationEnergy(requested * 8);
            if (requested <= 0 || energy.extractEnergy(energyCost, true) < energyCost) {
                processingStatus = ProcessingStatus.ENERGY_LOW;
                return false;
            }
            int accepted = mechanicalOrb.insertLp(stack, requested, false);
            if (accepted <= 0) return false;
            int actualEnergyCost = upgradedOperationEnergy(accepted * 8);
            inputTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            energy.extractEnergy(actualEnergyCost, false);
            inventory.setStackInSlot(CATALYST_SLOT, stack);
            lastEnergyUsed = actualEnergyCost;
            mechanicalLpRate = accepted;
            processingStatus = ProcessingStatus.RUNNING;
            return true;
        }
        if (!(stack.getItem() instanceof IBloodOrb orbItem) || !(stack.getItem() instanceof IBindable bindable)) return false;
        BloodOrb orb = orbItem.getOrb(stack);
        Binding binding = bindable.getBinding(stack);
        if (orb == null || binding == null || !isLifeEssence(inputTank.getFluid())) return false;
        var network = NetworkHelper.getSoulNetwork(binding);
        int remaining = Math.max(0, orb.getCapacity() - network.getCurrentEssence());
        int requested = Math.min(remaining, Math.min(inputTank.getFluidAmount(),
              Math.max(1, (int) Math.floor(orb.getFillRate() * tier.batchSize() * speedMultiplier()))));
        int energyCost = upgradedOperationEnergy(requested * 8);
        if (requested <= 0 || energy.extractEnergy(energyCost, true) < energyCost) return false;
        int accepted = network.add(requested, orb.getCapacity());
        if (accepted <= 0) return false;
        int actualEnergyCost = upgradedOperationEnergy(accepted * 8);
        inputTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        energy.extractEnergy(actualEnergyCost, false);
        lastEnergyUsed = actualEnergyCost;
        processingStatus = ProcessingStatus.RUNNING;
        return true;
    }

    private boolean tickAltar() {
        if (altarTarget > 0) return tickLockedAltar();
        Boolean flushed = flushFinishedAltarBuffer(0);
        if (flushed != null) return flushed;
        boolean foundInput = false;
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            ItemStack input = inventory.getStackInSlot(slot);
            if (input.isEmpty()) continue;
            foundInput = true;
            RecipeBloodAltar recipe = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getBloodAltar(level, input);
            if (recipe == null) {
                processingStatus = ProcessingStatus.NO_RECIPE;
                continue;
            }
            if (recipe.getMinimumTier() >= tier.bloodTier()) {
                processingStatus = ProcessingStatus.TIER_TOO_LOW;
                continue;
            }
            int batch = Math.min(tier.batchSize(), input.getCount());
            int lp = recipe.getSyphon() * batch;
            List<ItemStack> outputs = repeat(recipe.getOutput(), batch);
            if (!hasLife(lp)) {
                processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
                return false;
            }
            if (!canFit(outputs)) {
                processingStatus = ProcessingStatus.OUTPUT_FULL;
                return false;
            }
            int duration = Math.max(1, divideRoundUp(recipe.getSyphon(), Math.max(1, recipe.getConsumeRate() * tier.bloodTier())));
            int energyPerTick = Math.max(1, divideRoundUp(lp * 8, duration));
            int inputSlot = slot;
            activeInputMask = 1 << slot;
            return advance(recipe.getId() + ":" + batch, duration, energyPerTick, () -> {
                inputTank.drain(lp, IFluidHandler.FluidAction.EXECUTE);
                inventory.extractItem(inputSlot, batch, false);
                for (ItemStack output : outputs) {
                    BloodMagicCraftedEvent.Altar event = new BloodMagicCraftedEvent.Altar(output.copy(), input.copyWithCount(1));
                    MinecraftForge.EVENT_BUS.post(event);
                    insertOutput(event.getOutput());
                }
            });
        }
        if (!foundInput && !(inventory.getStackInSlot(CATALYST_SLOT).getItem() instanceof MechanicalBloodOrbItem)) {
            processingStatus = ProcessingStatus.IDLE;
        }
        return false;
    }

    private boolean tickLockedAltar() {
        Boolean flushed = flushFinishedAltarBuffer(altarTarget);
        if (flushed != null) return flushed;
        AltarWork work = findLockedAltarWork();
        if (work == null) {
            processingStatus = ProcessingStatus.NO_RECIPE;
            return false;
        }
        RecipeBloodAltar recipe = work.recipe();
        if (recipe.getMinimumTier() >= tier.bloodTier()) {
            processingStatus = ProcessingStatus.TIER_TOO_LOW;
            return false;
        }
        int batch = Math.min(tier.batchSize(), work.input().getCount());
        int outputRank = altarTargetRank(recipe.getOutput());
        int lp = recipe.getSyphon() * batch;
        List<ItemStack> outputs = repeat(recipe.getOutput(), batch);
        if (!hasLife(lp)) {
            processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
            return false;
        }
        if (outputRank == altarTarget ? !canFit(outputs) : !canFitAltarBuffer(outputs)) {
            processingStatus = ProcessingStatus.OUTPUT_FULL;
            return false;
        }
        int duration = Math.max(1, divideRoundUp(recipe.getSyphon(), Math.max(1, recipe.getConsumeRate() * tier.bloodTier())));
        int energyPerTick = Math.max(1, divideRoundUp(lp * 8, duration));
        activeInputMask = 1 << (work.inputSlot() < 0 ? CATALYST_SLOT : work.inputSlot());
        int finalBatch = batch;
        return advance(recipe.getId() + ":locked:" + batch, duration, energyPerTick, () -> {
            inputTank.drain(lp, IFluidHandler.FluidAction.EXECUTE);
            if (work.bufferSlot() >= 0) altarBuffer.extractItem(work.bufferSlot(), finalBatch, false);
            else inventory.extractItem(work.inputSlot(), finalBatch, false);
            for (ItemStack output : outputs) {
                BloodMagicCraftedEvent.Altar event = new BloodMagicCraftedEvent.Altar(output.copy(), work.input().copyWithCount(1));
                MinecraftForge.EVENT_BUS.post(event);
                if (outputRank == altarTarget) insertOutput(event.getOutput());
                else insertAltarBuffer(event.getOutput());
            }
        });
    }

    @Nullable
    private AltarWork findLockedAltarWork() {
        for (int wantedRank = altarTarget - 1; wantedRank >= 1; wantedRank--) {
            for (int slot = 0; slot < ALTAR_BUFFER_SLOTS; slot++) {
                ItemStack input = altarBuffer.getStackInSlot(slot);
                if (altarTargetRank(input) != wantedRank) continue;
                RecipeBloodAltar recipe = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getBloodAltar(level, input);
                if (recipe != null && altarTargetRank(recipe.getOutput()) > wantedRank && altarTargetRank(recipe.getOutput()) <= altarTarget) {
                    return new AltarWork(recipe, -1, slot, input.copy());
                }
            }
        }
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            ItemStack input = inventory.getStackInSlot(slot);
            if (input.isEmpty()) continue;
            RecipeBloodAltar recipe = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getBloodAltar(level, input);
            int outputRank = recipe == null ? 0 : altarTargetRank(recipe.getOutput());
            if (outputRank > 0 && outputRank <= altarTarget) return new AltarWork(recipe, slot, -1, input.copy());
        }
        return null;
    }

    @Nullable
    private Boolean flushFinishedAltarBuffer(int targetRank) {
        for (int slot = 0; slot < ALTAR_BUFFER_SLOTS; slot++) {
            ItemStack stack = altarBuffer.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int rank = altarTargetRank(stack);
            if (targetRank > 0 && rank < targetRank) continue;
            if (!canFit(List.of(stack))) {
                processingStatus = ProcessingStatus.OUTPUT_FULL;
                return Boolean.FALSE;
            }
            insertOutput(altarBuffer.extractItem(slot, stack.getCount(), false));
            processingStatus = ProcessingStatus.RUNNING;
            return Boolean.TRUE;
        }
        return null;
    }

    private boolean tickAlchemy() {
        if (nonEmptyInputs().isEmpty()) return false;
        AlchemyMatch match = findAlchemyMatch();
        if (match != null) {
            RecipeAlchemyTable recipe = match.recipe();
            IngredientPlan plan = match.plan();
            if (tier.bloodTier() < recipe.getMinimumTier()) {
                processingStatus = ProcessingStatus.TIER_TOO_LOW;
                return false;
            }
            int batch = plan.batch();
            int lp = recipe.getSyphon() * batch;
            ItemStack result = recipe.getOutput(plan.recipeInputs());
            List<ItemStack> outputs = repeat(result, batch);
            if (!hasLife(lp)) {
                processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
                return false;
            }
            if (!canFit(outputs)) {
                processingStatus = ProcessingStatus.OUTPUT_FULL;
                return false;
            }
            int duration = Math.max(1, divideRoundUp(recipe.getTicks(), tier.bloodTier()));
            activeInputMask = plan.inputMask();
            return advance(recipe.getId() + ":" + batch, duration, Math.max(40, lp * 4 / duration), () -> {
                inputTank.drain(lp, IFluidHandler.FluidAction.EXECUTE);
                for (int i = 0; i < batch; i++) {
                    BloodMagicCraftedEvent.AlchemyTable event = new BloodMagicCraftedEvent.AlchemyTable(result.copy(), plan.recipeInputs().toArray(ItemStack[]::new));
                    MinecraftForge.EVENT_BUS.post(event);
                    insertOutput(event.getOutput());
                    consumeAlchemyInputs(plan, -1);
                }
            });
        }
        return tickPotion();
    }

    private boolean tickPotion() {
        int flaskSlot = -1;
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            if (inventory.getStackInSlot(slot).getItem() instanceof ItemAlchemyFlask) {
                flaskSlot = slot;
                break;
            }
        }
        if (flaskSlot < 0) {
            processingStatus = ProcessingStatus.NO_RECIPE;
            return false;
        }
        ItemStack flask = inventory.getStackInSlot(flaskSlot);
        List<EffectHolder> effects = ((ItemAlchemyFlask) flask.getItem()).getEffectHoldersOfFlask(flask);
        PotionMatch match = findPotionMatch(flask, effects, flaskSlot);
        if (match == null) {
            processingStatus = ProcessingStatus.NO_RECIPE;
            return false;
        }
        RecipePotionFlaskBase recipe = match.recipe();
        IngredientPlan plan = match.plan();
        if (tier.bloodTier() < recipe.getMinimumTier()) {
            processingStatus = ProcessingStatus.TIER_TOO_LOW;
            return false;
        }
        if (!hasLife(recipe.getSyphon())) {
            processingStatus = ProcessingStatus.LIFE_ESSENCE_LOW;
            return false;
        }
        ItemStack output = recipe.getOutput(flask, effects);
        if (!canFit(List.of(output))) return false;
        int duration = Math.max(1, divideRoundUp(recipe.getTicks(), tier.bloodTier()));
        int finalFlaskSlot = flaskSlot;
        activeInputMask = plan.inputMask() | 1 << flaskSlot;
        return advance(recipe.getId().toString(), duration, Math.max(40, recipe.getSyphon() * 4 / duration), () -> {
            inputTank.drain(recipe.getSyphon(), IFluidHandler.FluidAction.EXECUTE);
            if (output.getItem() instanceof ItemAlchemyFlask outputFlask) outputFlask.resyncEffectInstances(output);
            insertOutput(output);
            inventory.extractItem(finalFlaskSlot, 1, false);
            consumeAlchemyInputs(plan, finalFlaskSlot);
        });
    }

    private boolean tickArray() {
        for (int first = 0; first < INPUT_COUNT; first++) for (int second = 0; second < INPUT_COUNT; second++) {
            if (first == second) continue;
            ItemStack base = inventory.getStackInSlot(first);
            ItemStack catalyst = inventory.getStackInSlot(second);
            if (base.isEmpty() || catalyst.isEmpty()) continue;
            Pair<Boolean, RecipeAlchemyArray> match = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyArray(level, base, catalyst);
            if (match == null || !match.getLeft() || match.getRight() == null) continue;
            RecipeAlchemyArray recipe = match.getRight();
            int batch = Math.min(tier.batchSize(), Math.min(base.getCount(), catalyst.getCount()));
            List<ItemStack> outputs = repeat(recipe.getOutput(), batch);
            while (batch > 0 && !canFit(outputs)) {
                batch--;
                outputs = repeat(recipe.getOutput(), batch);
            }
            if (batch <= 0) {
                processingStatus = ProcessingStatus.OUTPUT_FULL;
                return false;
            }
            int baseSlot = first;
            int catalystSlot = second;
            int finalBatch = batch;
            List<ItemStack> finalOutputs = outputs;
            activeInputMask = (1 << first) | (1 << second);
            return advance(recipe.getId() + ":" + finalBatch, Math.max(10, 100 / tier.bloodTier()), 200 * finalBatch, () -> {
                inventory.extractItem(baseSlot, finalBatch, false);
                inventory.extractItem(catalystSlot, finalBatch, false);
                finalOutputs.forEach(this::insertOutput);
            });
        }
        return false;
    }

    private boolean tickSoulForge() {
        List<ItemStack> inputs = nonEmptyInputs();
        if (inputs.isEmpty()) return false;
        RecipeTartaricForge recipe = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getTartaricForge(level, inputs);
        if (recipe == null) return false;
        EnumDemonWillType willType = findWillType();
        if (willType == null) return false;
        double willAmount = getWill(willType);
        if (willAmount < recipe.getMinimumSouls()) return false;
        int batch = Math.min(tier.batchSize(), batchForInputs());
        while (batch > 1 && willAmount - recipe.getSoulDrain() * (batch - 1) < recipe.getMinimumSouls()) batch--;
        List<ItemStack> outputs = repeat(recipe.getOutput(), batch);
        while (batch > 0 && !canFit(outputs)) {
            batch--;
            outputs = repeat(recipe.getOutput(), batch);
        }
        if (batch <= 0) {
            processingStatus = ProcessingStatus.OUTPUT_FULL;
            return false;
        }
        int finalBatch = batch;
        activeInputMask = nonEmptyInputMask();
        return advance(recipe.getId() + ":" + batch, Math.max(10, 100 / tier.bloodTier()), 300 * batch, () -> {
            drainWill(willType, recipe.getSoulDrain() * finalBatch);
            for (int i = 0; i < finalBatch; i++) {
                BloodMagicCraftedEvent.SoulForge event = new BloodMagicCraftedEvent.SoulForge(recipe.getOutput().copy(), inputs.toArray(ItemStack[]::new));
                MinecraftForge.EVENT_BUS.post(event);
                insertOutput(event.getOutput());
                shrinkAllInputs();
            }
        });
    }

    private boolean tickArc() {
        ItemStack tool = inventory.getStackInSlot(CATALYST_SLOT);
        if (tool.isEmpty()) return false;
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            ItemStack input = inventory.getStackInSlot(slot);
            if (input.isEmpty()) continue;
            RecipeARC recipe = BloodMagicAPI.INSTANCE.getRecipeRegistrar().getARC(level, input, tool, inputTank.getFluid());
            if (recipe == null || input.getCount() < recipe.getRequiredInputCount()) continue;
            List<ItemStack> worstCase = recipe.getAllListedOutputs(input, tool);
            FluidStack fluidOut = recipe.getFluidOutput();
            if (!canFit(worstCase) || (!fluidOut.isEmpty() && outputTank.fill(fluidOut, IFluidHandler.FluidAction.SIMULATE) < fluidOut.getAmount())) return false;
            int inputSlot = slot;
            activeInputMask = 1 << slot;
            return advance(recipe.getId().toString(), Math.max(10, 100 / tier.bloodTier()), 600, () -> {
                if (recipe.getFluidIngredient() != null) {
                    FluidStack required = recipe.getFluidIngredient().getMatchingInstance(inputTank.getFluid());
                    inputTank.drain(required, IFluidHandler.FluidAction.EXECUTE);
                }
                recipe.getAllOutputs(level.random, input, tool, 1).forEach(this::insertOutput);
                if (!fluidOut.isEmpty()) outputTank.fill(fluidOut.copy(), IFluidHandler.FluidAction.EXECUTE);
                inventory.extractItem(inputSlot, recipe.getRequiredInputCount(), false);
                consumeArcTool();
            });
        }
        return false;
    }

    private boolean advance(String key, int duration, int energyPerTick, Runnable finish) {
        duration = upgradedDuration(duration);
        energyPerTick = upgradedEnergyPerTick(energyPerTick);
        if (!key.equals(processKey)) {
            processKey = key;
            progress = 0;
        }
        maxProgress = duration;
        if (energy.extractEnergy(energyPerTick, true) < energyPerTick) {
            processingStatus = ProcessingStatus.ENERGY_LOW;
            return false;
        }
        energy.extractEnergy(energyPerTick, false);
        lastEnergyUsed = energyPerTick;
        processingStatus = ProcessingStatus.RUNNING;
        progress++;
        if (progress >= duration) {
            finish.run();
            progress = 0;
            setChanged();
        }
        return true;
    }

    private boolean hasLife(int amount) {
        return amount == 0 || isLifeEssence(inputTank.getFluid()) && inputTank.getFluidAmount() >= amount;
    }

    private static boolean isLifeEssence(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid() == BloodMagicFluids.LIFE_ESSENCE_FLUID.get() || stack.getFluid().is(BloodMagicTags.LIFE_ESSENCE));
    }

    private List<ItemStack> nonEmptyInputs() {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < INPUT_COUNT; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) inputs.add(inventory.getStackInSlot(slot));
        return inputs;
    }

    private int nonEmptyInputMask() {
        int mask = 0;
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) mask |= 1 << slot;
        }
        return mask;
    }

    @Nullable
    private AlchemyMatch findAlchemyMatch() {
        for (RecipeAlchemyTable recipe : level.getRecipeManager().getAllRecipesFor(BloodMagicRecipeType.ALCHEMYTABLE.get())) {
            IngredientPlan plan = matchIngredients(recipe.getInput(), -1, tier.batchSize());
            if (plan != null) return new AlchemyMatch(recipe, plan);
        }
        return null;
    }

    @Nullable
    private PotionMatch findPotionMatch(ItemStack flask, List<EffectHolder> effects, int flaskSlot) {
        PotionMatch best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (RecipePotionFlaskBase recipe : level.getRecipeManager().getAllRecipesFor(BloodMagicRecipeType.POTIONFLASK.get())) {
            IngredientPlan plan = matchIngredients(recipe.getInput(), flaskSlot, 1);
            if (plan == null || !recipe.canModifyFlask(flask, effects)) continue;
            int priority = recipe.getPriority(effects);
            if (priority > bestPriority) {
                best = new PotionMatch(recipe, plan);
                bestPriority = priority;
            }
        }
        return best;
    }

    @Nullable
    private IngredientPlan matchIngredients(List<Ingredient> ingredients, int excludedSlot, int maxBatch) {
        List<Integer> occupiedSlots = new ArrayList<>();
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            if (slot != excludedSlot && !inventory.getStackInSlot(slot).isEmpty()) occupiedSlots.add(slot);
        }
        if (ingredients.isEmpty() || occupiedSlots.isEmpty() || occupiedSlots.size() > ingredients.size()) return null;
        for (int batch = Math.max(1, maxBatch); batch >= 1; batch--) {
            int[] usage = new int[INPUT_COUNT];
            int[] assignment = new int[ingredients.size()];
            Arrays.fill(assignment, -1);
            if (assignIngredients(ingredients, occupiedSlots, 0, batch, usage, assignment)) {
                List<ItemStack> recipeInputs = new ArrayList<>(ingredients.size());
                for (int slot : assignment) recipeInputs.add(inventory.getStackInSlot(slot).copyWithCount(1));
                return new IngredientPlan(usage, recipeInputs, batch);
            }
        }
        return null;
    }

    private boolean assignIngredients(List<Ingredient> ingredients, List<Integer> occupiedSlots, int ingredientIndex,
          int batch, int[] usage, int[] assignment) {
        if (ingredientIndex >= ingredients.size()) {
            for (int slot : occupiedSlots) if (usage[slot] == 0) return false;
            return true;
        }
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slot : occupiedSlots) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!ingredient.test(stack)) continue;
            int requiredPerOperation = usage[slot] + 1;
            int requiredCount = stack.getItem() instanceof IAlchemyItem ? requiredPerOperation : requiredPerOperation * batch;
            if (requiredCount > stack.getCount()) continue;
            usage[slot]++;
            assignment[ingredientIndex] = slot;
            if (assignIngredients(ingredients, occupiedSlots, ingredientIndex + 1, batch, usage, assignment)) return true;
            assignment[ingredientIndex] = -1;
            usage[slot]--;
        }
        return false;
    }

    private int batchForInputs() {
        int batch = tier.batchSize();
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && !(stack.getItem() instanceof IAlchemyItem)) batch = Math.min(batch, stack.getCount());
        }
        return Math.max(1, batch);
    }

    private void consumeAlchemyInputs(IngredientPlan plan, int excludedSlot) {
        for (int slot = 0; slot < INPUT_COUNT; slot++) {
            if (slot == excludedSlot) continue;
            for (int use = 0; use < plan.perSlot()[slot]; use++) consumeAlchemyInput(slot);
        }
    }

    private void consumeAlchemyInput(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) return;
        if (stack.getItem() instanceof IAlchemyItem alchemyItem) {
            if (alchemyItem.isStackChangedOnUse(stack)) inventory.setStackInSlot(slot, alchemyItem.onConsumeInput(stack));
        } else if (stack.getItem().hasCraftingRemainingItem(stack)) {
            ItemStack remainder = stack.getItem().getCraftingRemainingItem(stack);
            inventory.extractItem(slot, 1, false);
            insertInputRemainder(remainder);
        } else if (stack.isDamageableItem()) {
            if (stack.hurt(1, level.random, null)) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        } else inventory.extractItem(slot, 1, false);
    }

    private void insertInputRemainder(ItemStack remainder) {
        ItemStack remaining = remainder.copy();
        for (int slot = 0; slot < INPUT_COUNT && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
        }
        if (!remaining.isEmpty()) insertOutput(remaining);
    }

    private void shrinkAllInputs() {
        for (int slot = 0; slot < INPUT_COUNT; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) inventory.extractItem(slot, 1, false);
    }

    private void consumeArcTool() {
        ItemStack tool = inventory.getStackInSlot(CATALYST_SLOT);
        if (tool.isDamageableItem()) {
            if (tool.hurt(1, level.random, null)) inventory.setStackInSlot(CATALYST_SLOT, ItemStack.EMPTY);
        } else if (tool.getItem().hasCraftingRemainingItem(tool)) inventory.setStackInSlot(CATALYST_SLOT, tool.getItem().getCraftingRemainingItem(tool));
        else inventory.extractItem(CATALYST_SLOT, 1, false);
    }

    @Nullable
    private EnumDemonWillType findWillType() {
        EnumDemonWillType tankType = WillGas.typeOf(willTank.getType());
        if (tankType != null && willTank.getStored() > 0) return tankType;
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        if (stack.getItem() instanceof IDemonWillGem) stack.getOrCreateTag();
        double best = 0;
        EnumDemonWillType bestType = EnumDemonWillType.DEFAULT;
        for (EnumDemonWillType type : EnumDemonWillType.values()) {
            double amount = stack.getItem() instanceof IDemonWillGem gem ? gem.getWill(type, stack)
                  : stack.getItem() instanceof IDemonWill will && will.getType(stack) == type ? will.getWill(type, stack) : 0;
            if (amount > best) { best = amount; bestType = type; }
        }
        return best > 0 ? bestType : null;
    }

    private double getWill(EnumDemonWillType type) {
        if (WillGas.typeOf(willTank.getType()) == type) return WillGas.toWill(willTank.getStored());
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        if (stack.getItem() instanceof IDemonWillGem gem) return gem.getWill(type, stack);
        if (stack.getItem() instanceof IDemonWill will && will.getType(stack) == type) return will.getWill(type, stack);
        return 0;
    }

    private void drainWill(EnumDemonWillType type, double amount) {
        if (WillGas.typeOf(willTank.getType()) == type) {
            willTank.extract(WillGas.toGas(amount), Action.EXECUTE, AutomationType.INTERNAL);
            return;
        }
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        if (stack.getItem() instanceof IDemonWillGem gem) gem.drainWill(type, stack, amount, true);
        else if (stack.getItem() instanceof IDemonWill will) {
            will.drainWill(type, stack, amount);
            if (will.getWill(type, stack) <= 0) inventory.setStackInSlot(CATALYST_SLOT, ItemStack.EMPTY);
        }
    }

    private List<ItemStack> repeat(ItemStack stack, int count) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(stack.copy());
        return result;
    }

    private boolean canFit(List<ItemStack> outputs) {
        net.minecraftforge.items.ItemStackHandler simulation = new net.minecraftforge.items.ItemStackHandler(OUTPUT_COUNT);
        for (int i = 0; i < OUTPUT_COUNT; i++) simulation.setStackInSlot(i, inventory.getStackInSlot(OUTPUT_START + i).copy());
        for (ItemStack output : outputs) if (!ItemHandlerHelper.insertItemStacked(simulation, output.copy(), false).isEmpty()) return false;
        return true;
    }

    private boolean canFitAltarBuffer(List<ItemStack> outputs) {
        net.minecraftforge.items.ItemStackHandler simulation = new net.minecraftforge.items.ItemStackHandler(ALTAR_BUFFER_SLOTS);
        for (int i = 0; i < ALTAR_BUFFER_SLOTS; i++) simulation.setStackInSlot(i, altarBuffer.getStackInSlot(i).copy());
        for (ItemStack output : outputs) if (!ItemHandlerHelper.insertItemStacked(simulation, output.copy(), false).isEmpty()) return false;
        return true;
    }

    private void insertAltarBuffer(ItemStack stack) {
        ItemHandlerHelper.insertItemStacked(altarBuffer, stack.copy(), false);
    }

    private void insertOutput(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_COUNT && !remaining.isEmpty(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing.isEmpty()) {
                int inserted = Math.min(remaining.getCount(), Math.min(inventory.getSlotLimit(slot), remaining.getMaxStackSize()));
                inventory.setStackInSlot(slot, remaining.copyWithCount(inserted));
                remaining.shrink(inserted);
            } else if (ItemHandlerHelper.canItemStacksStack(existing, remaining)) {
                int limit = Math.min(inventory.getSlotLimit(slot), existing.getMaxStackSize());
                int inserted = Math.min(remaining.getCount(), limit - existing.getCount());
                if (inserted > 0) {
                    ItemStack merged = existing.copy();
                    merged.grow(inserted);
                    inventory.setStackInSlot(slot, merged);
                    remaining.shrink(inserted);
                }
            }
        }
    }

    private static int divideRoundUp(int value, int divisor) { return (value + divisor - 1) / divisor; }

    private void transferFluids() {
        if (level == null || ++fluidTransferCooldown < 10) return;
        fluidTransferCooldown = 0;
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null) continue;
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).ifPresent(handler -> {
                if (autoOutput && allows(direction, MachineResource.FLUID, false) && !outputTank.isEmpty()) {
                    FluidStack offered = outputTank.drain(1_000, IFluidHandler.FluidAction.SIMULATE);
                    int accepted = handler.fill(offered, IFluidHandler.FluidAction.SIMULATE);
                    if (accepted > 0) handler.fill(outputTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
                }
            });
        }
    }

    @Override protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot == CATALYST_SLOT && mode == FactoryMode.ALTAR) return isBloodOrb(stack);
        if (slot < INPUT_COUNT && mode == FactoryMode.ALTAR && isBloodOrb(stack)) return false;
        return slot < OUTPUT_START;
    }
    @Override protected boolean canAutomationExtract(int slot) {
        if (slot >= OUTPUT_START && slot < SPEED_UPGRADE_SLOT) return true;
        ItemStack stack = inventory.getStackInSlot(slot);
        return slot == CATALYST_SLOT && stack.getItem() instanceof MechanicalBloodOrbItem orb && orb.isFull(stack);
    }
    @Override protected boolean canAutomationInsert(int slot) { return slot < OUTPUT_START; }

    public static boolean isBloodOrb(ItemStack stack) {
        return stack.getItem() instanceof MechanicalBloodOrbItem || stack.getItem() instanceof IBloodOrb;
    }

    public static ItemStack altarTargetStack(int target) {
        if (target <= 0 || target > ALTAR_TARGETS.length) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(ALTAR_TARGETS[target - 1]));
    }

    private static int altarTargetRank(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (int i = 0; i < ALTAR_TARGETS.length; i++) if (ALTAR_TARGETS[i].equals(id)) return i + 1;
        return 0;
    }

    private int mechanicalStoredLp() {
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        return stack.getItem() instanceof MechanicalBloodOrbItem orb ? orb.getStoredLp(stack) : 0;
    }

    private int mechanicalLpCapacity() {
        ItemStack stack = inventory.getStackInSlot(CATALYST_SLOT);
        return stack.getItem() instanceof MechanicalBloodOrbItem orb ? orb.getCapacity() : 0;
    }

    @Override public void handleButton(int id) {
        if (id == ALTAR_TARGET_BUTTON && mode == FactoryMode.ALTAR) {
            altarTarget = (altarTarget + 1) % (Math.min(ALTAR_TARGETS.length, tier.bloodTier()) + 1);
            progress = 0;
            processKey = "";
            setChanged();
        } else if (id == 3) {
            if (fixedMode != null) return;
            mode = mode.next();
            progress = 0;
            processKey = "";
            setChanged();
        } else super.handleButton(id);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return (side == null ? fluidCapability : sidedFluidCapabilities.get(side)).cast();
        }
        if (capability == Capabilities.GAS_HANDLER && mode == FactoryMode.SOUL_FORGE) {
            return (side == null ? gasCapability : sidedGasCapabilities.get(side)).cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
        sidedFluidCapabilities.values().forEach(LazyOptional::invalidate);
        gasCapability.invalidate();
        sidedGasCapabilities.values().forEach(LazyOptional::invalidate);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("InputTank", inputTank.writeToNBT(new CompoundTag()));
        tag.put("OutputTank", outputTank.writeToNBT(new CompoundTag()));
        tag.put("WillTank", willTank.serializeNBT());
        tag.put("AltarBuffer", altarBuffer.serializeNBT());
        tag.putInt("AltarTarget", altarTarget);
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putString("ProcessKey", processKey);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inputTank.readFromNBT(tag.getCompound("InputTank"));
        outputTank.readFromNBT(tag.getCompound("OutputTank"));
        willTank.deserializeNBT(tag.getCompound("WillTank"));
        if (tag.contains("AltarBuffer")) altarBuffer.deserializeNBT(tag.getCompound("AltarBuffer"));
        altarTarget = Math.min(Math.max(tag.getInt("AltarTarget"), 0), Math.min(ALTAR_TARGETS.length, tier.bloodTier()));
        mode = fixedMode == null ? FactoryMode.values()[Math.min(tag.getInt("Mode"), FactoryMode.values().length - 1)] : fixedMode;
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        processKey = tag.getString("ProcessKey");
    }

    public FactoryTier tier() { return tier; }
    public FactoryMode mode() { return mode; }
    public FluidTank inputTank() { return inputTank; }
    public FluidTank outputTank() { return outputTank; }
    public IGasTank willTank() { return willTank; }

    private record AltarWork(RecipeBloodAltar recipe, int inputSlot, int bufferSlot, ItemStack input) { }
    private record AlchemyMatch(RecipeAlchemyTable recipe, IngredientPlan plan) { }
    private record PotionMatch(RecipePotionFlaskBase recipe, IngredientPlan plan) { }
    private record IngredientPlan(int[] perSlot, List<ItemStack> recipeInputs, int batch) {
        private int inputMask() {
            int mask = 0;
            for (int slot = 0; slot < perSlot.length; slot++) if (perSlot[slot] > 0) mask |= 1 << slot;
            return mask;
        }
    }

    @Override protected Component defaultName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new UniversalFactoryMenu(id, inventory, this); }

    private final class FactoryFluidHandler implements IFluidHandler {
        @Nullable private final Direction side;

        private FactoryFluidHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? inputTank.getFluid() : outputTank.getFluid(); }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? inputTank.getCapacity() : outputTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && allows(side, MachineResource.FLUID, true);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return allows(side, MachineResource.FLUID, true) ? inputTank.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return allows(side, MachineResource.FLUID, false) ? outputTank.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return allows(side, MachineResource.FLUID, false) ? outputTank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    private final class FactoryGasHandler implements IGasHandler.IMekanismGasHandler {
        @Nullable private final Direction side;

        private FactoryGasHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public List<IGasTank> getChemicalTanks(@Nullable Direction ignored) {
            return allows(side, MachineResource.GAS, true) ? List.of(willTank) : List.of();
        }

        @Override public void onContentsChanged() { setChanged(); }
    }
}
