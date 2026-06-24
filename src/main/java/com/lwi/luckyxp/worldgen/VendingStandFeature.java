package com.lwi.luckyxp.worldgen;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.Rarity;
import com.lwi.luckyxp.machine.VendingMachineBlock;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
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
    private static final int FLAGS = 2;

    public VendingStandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos o = ctx.origin();
        RandomSource rand = ctx.random();

        Rarity rarity = Rarity.roll(rand);
        MachineType type = MachineType.values()[rand.nextInt(MachineType.values().length)];

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState plate = Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
        BlockState stripe = awningWool(rarity).defaultBlockState();
        BlockState white = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState rug = awningCarpet(rarity).defaultBlockState();
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

        // 2. floor + thin centre rug
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                level.setBlock(o.offset(dx, 0, dz), floor, FLAGS);
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
        return true;
    }

    private static int awningY(int dz) {
        return dz >= 2 ? 5 : (dz >= 0 ? 4 : 3);
    }

    private static Block awningWool(Rarity r) {
        return switch (r) {
            case COMMON -> Blocks.LIME_WOOL;
            case RARE -> Blocks.LIGHT_BLUE_WOOL;
            case EPIC -> Blocks.PURPLE_WOOL;
            case LEGENDARY -> Blocks.YELLOW_WOOL;
        };
    }

    private static Block awningCarpet(Rarity r) {
        return switch (r) {
            case COMMON -> Blocks.LIME_CARPET;
            case RARE -> Blocks.LIGHT_BLUE_CARPET;
            case EPIC -> Blocks.PURPLE_CARPET;
            case LEGENDARY -> Blocks.YELLOW_CARPET;
        };
    }
}
