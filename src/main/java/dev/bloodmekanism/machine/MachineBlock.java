package dev.bloodmekanism.machine;

import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.machine.blockentity.WillGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class MachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    private final Kind kind;
    private final FactoryTier tier;
    private final FactoryMode fixedMode;

    public MachineBlock(Properties properties, Kind kind, @Nullable FactoryTier tier) {
        this(properties, kind, tier, kind == Kind.UNIVERSAL_FACTORY ? FactoryMode.ALTAR : null);
    }

    public MachineBlock(Properties properties, Kind kind, @Nullable FactoryTier tier, @Nullable FactoryMode fixedMode) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
        this.fixedMode = fixedMode;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(ACTIVE, false));
    }

    public Kind kind() { return kind; }
    public FactoryTier tier() { return tier; }
    public FactoryMode fixedMode() { return fixedMode; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case HEMOGENIC -> new HemogenicBlockEntity(pos, state);
            case WILL_GENERATOR -> new WillGeneratorBlockEntity(pos, state);
            case UNIVERSAL_FACTORY -> new UniversalFactoryBlockEntity(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof BaseMachineBlockEntity machine) machine.serverTick();
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BaseMachineBlockEntity machine) {
            NetworkHooks.openScreen(serverPlayer, machine, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof BaseMachineBlockEntity machine) machine.setCustomName(stack.getHoverName());
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BaseMachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine.asContainer());
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BaseMachineBlockEntity machine ? machine.comparatorLevel() : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) { return true; }

    public enum Kind { HEMOGENIC, WILL_GENERATOR, UNIVERSAL_FACTORY }
}
