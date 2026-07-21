package com.lwi.luckyxp.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * A two-block-tall classic vending machine. The block IS the {@link MachineType} (one block per type);
 * the lower half holds the {@link VendingMachineBlockEntity} (rolled stock + rarity). Right-clicking
 * either half opens the trade screen; rarity (stock quality) lives on the entity, set by the stand at
 * worldgen — the machine body itself does not change with rarity. Faces the player when placed.
 */
public class VendingMachineBlock extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Drives the upper half's screen model; kept in sync with the block entity's rarity (see
     *  {@link VendingMachineBlockEntity#setRarity}). */
    public static final EnumProperty<Rarity> RARITY = EnumProperty.create("rarity", Rarity.class);
    /** Once the stand's timer closes, the upper half swaps to the universal "404" screen (see
     *  {@link VendingMachineBlockEntity#markClosed}). Overrides the rarity screen in the blockstate. */
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    /** Briefly true after a sale: the lower half shows the open-tray body while the item drops out
     *  (see {@link VendingMachineBlockEntity#openTray}). */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private final MachineType type;

    public VendingMachineBlock(Properties props, MachineType type) {
        super(props);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.NORTH)
                .setValue(RARITY, Rarity.COMMON).setValue(CLOSED, false).setValue(OPEN, false));
    }

    public MachineType getMachineType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, RARITY, CLOSED, OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Only the lower half carries the block entity. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new VendingMachineBlockEntity(pos, state) : null;
    }

    /** Needs a free block above for the upper half; faces the placer. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return this.defaultBlockState()
                    .setValue(HALF, DoubleBlockHalf.LOWER)
                    .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        BlockPos belowPos = pos.below();
        return level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP);
    }

    /** Keep the two halves bonded: remove one and the other follows. */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOther = (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP);
        if (dir.getAxis() == Direction.Axis.Y && towardOther) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.UPPER && dir == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Self-contained drop handling (the loot tables are intentionally empty): break either half and
     * the machine drops exactly once (from the lower position), respecting tool requirement and never
     * duplicating in creative. Also removes the partner half.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            if (other.is(this) && other.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(other));
            }
            if (!player.isCreative() && player.hasCorrectToolForDrops(state)) {
                BlockPos lowerPos = half == DoubleBlockHalf.LOWER ? pos : otherPos;
                popResource(level, lowerPos, new ItemStack(this));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (level.getBlockEntity(lowerPos) instanceof VendingMachineBlockEntity machine) {
                if (machine.isClosed()) {
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component
                            .literal("This stand has closed for good.")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                machine.startTimerIfNeeded(level);      // the FIRST look arms the stand's closing timer
                machine.ensureStock(level);
                NetworkHooks.openScreen(serverPlayer, machine,
                        buf -> VendingMachineMenu.writeOpenData(buf, machine.stock(), machine.getMachineType(), machine.getRarity(), machine.closeAt()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Server-side once-a-second stand timer (display countdown, decor burn-down, terminal close). */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != com.lwi.luckyxp.Registration.VENDING_MACHINE_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof VendingMachineBlockEntity machine && lvl instanceof net.minecraft.server.level.ServerLevel server) {
                machine.tickTypeCheck(server);              // once per load: repair a 1.3.0 stale-type stock
                StandTimer.tick(server, pos, machine);
                machine.tickTray(server);                   // close the sale-tray after its brief window
            }
        };
    }
}
