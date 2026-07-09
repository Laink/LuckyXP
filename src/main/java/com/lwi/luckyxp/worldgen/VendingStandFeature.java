package com.lwi.luckyxp.worldgen;

import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlock;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A finer-grained open market stall (5x4 footprint) housing a vending machine. Built from slim
 * elements (fences for posts, a fence + pressure-plate counter, a trapdoor awning valance, a thin
 * carpet rug) rather than full blocks, to avoid a blocky look. Origin = front-left ground corner;
 * extends +X (right), +Z (back), +Y (up); the machine faces the front (-Z). Rolls a rarity (awning +
 * rug colour, machine stock/LED) and a machine type. Left bay left open for a future merchant NPC.
 */
public class VendingStandFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = 2;
    /** Max ground-height spread across the footprint before the spot is rejected. */
    private static final int MAX_UNEVENNESS = 2;

    public VendingStandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos o = ctx.origin();
        RandomSource rand = ctx.random();

        // "1 stand per N chunks", drawn here rather than by a rarity_filter in the placed_feature, so
        // the density stays tunable from the config instead of being frozen into the jar. The placed
        // feature is therefore tried once per chunk and this is the first thing it does.
        int chance = LuckyXpCommonConfig.COMMON.standChance.get();
        if (chance > 1 && rand.nextInt(chance) != 0) {
            return false;
        }

        if (!suitable(level, o)) {
            return false;
        }

        Rarity rarity = Rarity.roll(rand, new int[]{
                LuckyXpCommonConfig.COMMON.weightCommon.get(),
                LuckyXpCommonConfig.COMMON.weightRare.get(),
                LuckyXpCommonConfig.COMMON.weightEpic.get(),
                LuckyXpCommonConfig.COMMON.weightLegendary.get()});
        MachineType type = MachineType.values()[rand.nextInt(MachineType.values().length)];
        return build(level, o, rarity, type);
    }

    /**
     * Builds the full stand — structure, machine (rarity applied) and bound merchant. Shared by
     * worldgen ({@link #place}) and the dev command {@code /luckyevent stand}, which skips the
     * density roll above.
     */
    public static boolean build(WorldGenLevel level, BlockPos o, Rarity rarity, MachineType type) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState plate = Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
        // Single stall colour on purpose (user 2026-07-04): the rarity is read on the machine's own
        // screen LED / trade GUI, never on the stand, so every stand looks the same from afar.
        BlockState stripe = Blocks.RED_WOOL.defaultBlockState();
        BlockState white = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState rug = Blocks.RED_CARPET.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState valance = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP)
                .setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState lectern = Blocks.LECTERN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);

        // 1. clear the volume
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                for (int dy = 1; dy <= 6; dy++) {
                    level.setBlock(o.offset(dx, dy, dz), air, FLAGS);
                }
            }
        }

        // 2. floor + thin centre rug (with a short foundation under dips, so no corner floats)
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                level.setBlock(o.offset(dx, 0, dz), floor, FLAGS);
                for (int dy = -1; dy >= -3; dy--) {
                    BlockPos below = o.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(below);
                    if (!st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced()) {
                        break; // reached real ground
                    }
                    level.setBlock(below, floor, FLAGS);
                }
            }
        }
        level.setBlock(o.offset(2, 1, 1), rug, FLAGS);
        level.setBlock(o.offset(2, 1, 2), rug, FLAGS);

        // 3. slim fence posts (back taller for the awning slope)
        for (int dx : new int[]{0, 4}) {
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(o.offset(dx, dy, 0), fence, FLAGS);  // front
            }
            for (int dy = 1; dy <= 4; dy++) {
                level.setBlock(o.offset(dx, dy, 3), fence, FLAGS);  // back
            }
        }

        // 4. airy back railing
        for (int dx = 1; dx <= 3; dx++) {
            level.setBlock(o.offset(dx, 1, 3), fence, FLAGS);
            level.setBlock(o.offset(dx, 2, 3), fence, FLAGS);
        }

        // 5. counter = fence + pressure plate (classic slim table), merchant bay
        for (int dx = 2; dx <= 3; dx++) {
            level.setBlock(o.offset(dx, 1, 0), fence, FLAGS);
            level.setBlock(o.offset(dx, 2, 0), plate, FLAGS);
        }

        // 6. striped awning sloping to the front (Y5 back -> Y4 mid -> Y3 overhang)
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                level.setBlock(o.offset(dx, awningY(dz), dz), (dx % 2 == 0) ? stripe : white, FLAGS);
            }
        }

        // 7. trapdoor valance hanging under the front overhang
        for (int dx = 0; dx <= 4; dx++) {
            level.setBlock(o.offset(dx, 2, -1), valance, FLAGS);
        }

        // 8. a hanging lantern in each bay (under the Y4 awning at dz=1)
        level.setBlock(o.offset(1, 3, 1), lantern, FLAGS);
        level.setBlock(o.offset(3, 3, 1), lantern, FLAGS);

        // 9. merchant spot (left bay): a lectern, open floor for a future NPC
        level.setBlock(o.offset(3, 1, 2), lectern, FLAGS);

        // 10. the vending machine (right bay), facing the front (-Z / north)
        Block machineBlock = Registration.MACHINES.get(type).get();
        BlockPos mPos = o.offset(1, 1, 2);
        BlockState lower = machineBlock.defaultBlockState()
                .setValue(VendingMachineBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(VendingMachineBlock.FACING, Direction.NORTH);
        level.setBlock(mPos, lower, FLAGS);
        level.setBlock(mPos.above(), lower.setValue(VendingMachineBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
        if (level.getBlockEntity(mPos) instanceof VendingMachineBlockEntity be) {
            be.setRarity(rarity);
        }

        // 11. the merchant, in the left bay by his lectern, facing the front like his machine. He
        // sells the six services (reroll, convert, luck, heal, repair) — see MerchantMenu.
        com.lwi.luckyxp.entity.LuckyMerchant merchant =
                Registration.LUCKY_MERCHANT.get().create(level.getLevel());
        if (merchant != null) {
            BlockPos sPos = o.offset(3, 1, 1);
            merchant.moveTo(sPos.getX() + 0.5, sPos.getY(), sPos.getZ() + 0.5, 180.0F, 0.0F);
            merchant.setMachinePos(mPos);
            level.addFreshEntity(merchant);
        }
        LOGGER.info("Vending stand placed: {} {} at {} {} {}", rarity, type, o.getX(), o.getY(), o.getZ());
        return true;
    }

    /**
     * Whether this surface spot can host the 5x4 stall: every footprint column must be dry and sit on
     * REAL ground (soft cover — grass, snow layers — is skipped; leaves are rejected, so no stall on a
     * flat tree canopy), and the real-ground heights may not spread more than {@link #MAX_UNEVENNESS}
     * (dips up to that are bridged by the foundation). Caves are impossible by construction: the
     * placement heightmap always resolves the world surface. Rejecting returns false — the rarity roll
     * simply tries elsewhere another chunk.
     */
    public static boolean suitable(WorldGenLevel level, BlockPos o) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, o.getX() + dx, o.getZ() + dz);
                // Walk down through soft cover (plants, snow layers) counted by the heightmap,
                // to measure the REAL ground and its height.
                int gy = h - 1;
                BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(o.getX() + dx, gy, o.getZ() + dz);
                BlockState gs = level.getBlockState(ground);
                int guard = 6;
                while (guard-- > 0 && (gs.isAir() || (gs.canBeReplaced() && gs.getFluidState().isEmpty()))) {
                    ground.move(0, -1, 0);
                    gs = level.getBlockState(ground);
                }
                if (!gs.getFluidState().isEmpty() || !level.getBlockState(ground.above()).getFluidState().isEmpty()) {
                    return false; // water/lava column (ocean, river, pond)
                }
                if (gs.is(net.minecraft.tags.BlockTags.LEAVES) || gs.isAir()) {
                    return false; // tree canopy / hollow surface
                }
                min = Math.min(min, ground.getY());
                max = Math.max(max, ground.getY());
            }
        }
        return max - min <= MAX_UNEVENNESS;
    }

    private static int awningY(int dz) {
        return dz >= 2 ? 5 : (dz >= 0 ? 4 : 3);
    }
}
