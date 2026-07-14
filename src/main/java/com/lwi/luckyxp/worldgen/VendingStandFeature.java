package com.lwi.luckyxp.worldgen;

import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlock;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * The market stand, placed from the designer's structure template ({@code data/luckyxp/structures/
 * vending_stand.nbt}, 8x12x9): the built stall, its decorative entities and the floating "00:00" timer
 * display. The template's bottom 3 layers are BURIED (the closing smoke system lives down there), so
 * the structure's lower corner sits 3 blocks below the surface.
 *
 * <p>Designer's reference points, measured from the structure's lower corner (0,0,0):
 * machine (1,3,3) — the template carries a MATERIALS placeholder whose block we swap to the rolled
 * type, keeping its facing/half states; merchant (4,3,3); timer display (5, 5.5, 3).
 *
 * <p>Rolls a rarity (config weights) and a machine type; the rarity lands on both the block entity
 * (stock quality) and the {@code RARITY} block-state property (the external screen model).
 */
public class VendingStandFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation TEMPLATE = new ResourceLocation(LuckyXpMod.MODID, "vending_stand");

    /** Template footprint (X, Y, Z) and the buried depth. */
    public static final int SIZE_X = 8, SIZE_Y = 12, SIZE_Z = 9, BURIED = 3;
    /** The machine's LOWER half, relative to the structure's lower corner. */
    public static final BlockPos MACHINE_OFFSET = new BlockPos(1, 3, 3);
    private static final BlockPos MERCHANT_OFFSET = new BlockPos(4, 3, 3);
    /** East face of the bubble-column's top block — where the dropped wall lever is re-placed. */
    private static final BlockPos LEVER_OFFSET = new BlockPos(2, 5, 5);

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
     * Places the full stand — template, machine (rarity applied) and bound merchant. Shared by
     * worldgen ({@link #place}) and the dev command {@code /luckyevent stand}, which skips the
     * density roll above. {@code surface} is the ground-level origin; the template is sunk
     * {@link #BURIED} blocks so its smoke layers end up underground.
     */
    public static boolean build(WorldGenLevel level, BlockPos surface, Rarity rarity, MachineType type) {
        StructureTemplate tpl = level.getLevel().getServer().getStructureManager()
                .get(TEMPLATE).orElse(null);
        if (tpl == null) {
            LOGGER.error("Vending stand template {} is missing", TEMPLATE);
            return false;
        }
        BlockPos origin = surface.below(BURIED);
        // Two guards are needed to place this hand-built stall VERBATIM — the designer's blocks break
        // strict vanilla support rules and would be culled otherwise:
        //  - setKnownShape(true) skips placeInWorld's FINAL re-validation pass (updateFromNeighbourShapes
        //    on every block, StructureTemplate:326-333) — that alone saved the roof rows / wall-signs.
        //  - flag 16 (UPDATE_KNOWN_SHAPE) on the placement itself stops each block, as it lands, from
        //    pushing a shape-update onto its already-placed neighbours. Without it a later block dropped
        //    the wall lever mounted against the decorative bubble-column. That column also sits on the
        //    designer's acacia (not soul-sand), so it only stays a bubble column while nothing sends it
        //    a shape update — fine here, the whole stall is unbreakable in play and the water is walled
        //    in on every side so it never flows. The NBT states already carry every fence/stair/wall
        //    connection, so nothing needs a shape-update to look right.
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
        tpl.placeInWorld(level, origin, origin, settings, level.getRandom(), 2 | 16);

        // Restore the wall lever on the column's east face. The designer's soul-fire → bubble-column
        // swap dropped it (a lever can't attach to water, so it fell in his world and his export shows a
        // blank sign there now). We re-place it at the same spot/state, flag 2|16 so it holds against the
        // column like the rest of the stall's decor — no export round-trip needed.
        level.setBlock(origin.offset(LEVER_OFFSET), Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 2 | 16);

        // Swap the template's placeholder machine to the rolled TYPE, keeping the designer's states
        // (facing/half) — the same withPropertiesOf recipe as the merchant's paid type conversion —
        // and stamp the rolled rarity on both the state (screen model) and the entity (stock).
        BlockPos mPos = origin.offset(MACHINE_OFFSET);
        BlockPos upPos = mPos.above();
        Block target = Registration.MACHINES.get(type).get();
        BlockState oldLower = level.getBlockState(mPos);
        BlockState oldUpper = level.getBlockState(upPos);
        int flags = 2 | 16;                             // clients + known-shape (no double-block self-destruct)
        if (oldLower.getBlock() instanceof VendingMachineBlock) {
            level.setBlock(mPos, target.withPropertiesOf(oldLower)
                    .setValue(VendingMachineBlock.RARITY, rarity), flags);
        } else {
            LOGGER.warn("Vending stand template placed no machine at {} — placing a default-facing one", mPos);
            level.setBlock(mPos, target.defaultBlockState()
                    .setValue(VendingMachineBlock.RARITY, rarity), flags);
        }
        if (oldUpper.getBlock() instanceof VendingMachineBlock) {
            level.setBlock(upPos, target.withPropertiesOf(oldUpper)
                    .setValue(VendingMachineBlock.RARITY, rarity), flags);
        } else {
            level.setBlock(upPos, level.getBlockState(mPos)
                    .setValue(VendingMachineBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), flags);
        }
        if (level.getBlockEntity(mPos) instanceof VendingMachineBlockEntity be) {
            be.setRarity(rarity);
        }

        // The merchant, by his counter, looking the same way the machine faces.
        com.lwi.luckyxp.entity.LuckyMerchant merchant =
                Registration.LUCKY_MERCHANT.get().create(level.getLevel());
        if (merchant != null) {
            BlockPos sPos = origin.offset(MERCHANT_OFFSET);
            Direction facing = level.getBlockState(mPos).getBlock() instanceof VendingMachineBlock
                    ? level.getBlockState(mPos).getValue(VendingMachineBlock.FACING) : Direction.NORTH;
            merchant.moveTo(sPos.getX() + 0.5, sPos.getY(), sPos.getZ() + 0.5, facing.toYRot(), 0.0F);
            merchant.setMachinePos(mPos);
            level.addFreshEntity(merchant);
        }
        LOGGER.info("Vending stand placed: {} {} at {} {} {}", rarity, type, surface.getX(), surface.getY(), surface.getZ());
        return true;
    }

    /**
     * Whether this surface spot can host the 8x9 stall: every footprint column must be dry and sit on
     * REAL ground (soft cover — grass, snow layers — is skipped; leaves are rejected, so no stall on a
     * flat tree canopy), and the real-ground heights may not spread more than {@link #MAX_UNEVENNESS}
     * (small dips are hidden by the template's buried layers). Caves are impossible by construction:
     * the placement heightmap always resolves the world surface. Rejecting returns false — the rarity
     * roll simply tries elsewhere another chunk.
     */
    public static boolean suitable(WorldGenLevel level, BlockPos o) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx < SIZE_X; dx++) {
            for (int dz = 0; dz < SIZE_Z; dz++) {
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
}
