package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.worldgen.VendingStandFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The stand's one-time open window (design frozen 2026-07-10: armed on the FIRST interaction with the
 * machine or the merchant, runs {@code standTimerSeconds}, then the stand closes FOR GOOD). This is the
 * anti-farm: a stand is a treasure window, not a shop to return to after grinding levels.
 *
 * <p>All driven from the machine block entity's tick, once a second, world-time based (walking away
 * does not pause it):
 * <ul>
 *   <li><b>Display</b> — the designer's floating {@code text_display} ("00:00", placed by the structure
 *       template) is updated live with the remaining mm:ss.</li>
 *   <li><b>Burn-down</b> — the stall's red wool turns into campfires and its yellow wool into hay,
 *       progressively (each remaining block flips with probability 1/seconds-left, so the decor decays
 *       roughly linearly and needs no persisted state).</li>
 *   <li><b>Close</b> — remaining wool flips, every campfire in the volume lights up (the designer's
 *       buried smoke system starts smoking through), a few non-destructive explosions pop, the display
 *       reads CLOSED, and machine + merchant refuse forever.</li>
 * </ul>
 */
public final class StandTimer {

    private StandTimer() {}

    /** Below this many seconds left, the countdown goes into its "final" state: the display blinks red
     *  (here and on the machine HUD) and a beep plays each second. */
    public static final int URGENT_SECONDS = 10;
    /** One alarm blip (electronic "bit" note) at the stand, heard by everyone nearby. */
    private static void beep(ServerLevel level, BlockPos machinePos, float pitch) {
        level.playSound(null, machinePos.getX() + 0.5, machinePos.getY() + 1.0, machinePos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.9F, pitch);
    }

    /** Boot every player still in THIS stand's machine or merchant screen. */
    private static void closeOpenScreens(ServerLevel level, BlockPos machinePos) {
        for (ServerPlayer sp : level.players()) {
            boolean mine = (sp.containerMenu instanceof VendingMachineMenu vm && machinePos.equals(vm.machinePos()))
                    || (sp.containerMenu instanceof MerchantMenu mm && machinePos.equals(mm.machinePos()));
            if (mine) {
                sp.closeContainer();
            }
        }
    }

    /** Block-entity ticker body (server side, lower half only — that's where the BE lives). Runs every
     *  tick; the once-a-second work (display refresh, burn-down, beep) is gated on the game clock, while
     *  the final-countdown blink refreshes at 2 Hz. */
    public static void tick(ServerLevel level, BlockPos machinePos, VendingMachineBlockEntity be) {
        if (be.isClosed() || be.closeAt() < 0) {
            return;
        }
        long now = level.getGameTime();
        long remainingTicks = be.closeAt() - now;
        if (remainingTicks <= 0) {
            close(level, machinePos, be);
            return;
        }
        int secondsLeft = (int) (remainingTicks / 20);
        String text = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);

        if (secondsLeft <= URGENT_SECONDS) {
            // Blink red at 2 Hz (refresh twice a second, red on alternate half-seconds).
            if (now % 10 == 0) {
                setDisplay(level, machinePos, text, (now / 10) % 2 == 0 ? ChatFormatting.RED : null);
            }
            // The alarm: a two-tone klaxon (high on even seconds, low on odd) from 10 s down to 4 s,
            // then in the FINAL 3 seconds a same-tone "bip" hammering twice a second straight into the
            // blast — bip, boop, bip, boop, … , bip-bip-bip-bip-bip-bip.
            if (secondsLeft > 3) {
                if (now % 20 == 0) {
                    beep(level, machinePos, (secondsLeft % 2 == 0) ? 1.5F : 0.9F);
                }
            } else if (secondsLeft >= 1 && now % 5 == 0) {     // last 3 s: fast same-tone hammer (4×/s)
                beep(level, machinePos, 1.5F);
            }
        } else if (now % 20 == 0) {
            setDisplay(level, machinePos, text, null);
        }

        if (now % 20 == 0) {
            burnStep(level, machinePos, secondsLeft);
        }
    }

    /** The structure's bounding box, recovered from the machine position (a fixed template offset). */
    private static AABB standBox(BlockPos machinePos) {
        BlockPos origin = machinePos.subtract(VendingStandFeature.MACHINE_OFFSET);
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + VendingStandFeature.SIZE_X,
                origin.getY() + VendingStandFeature.SIZE_Y,
                origin.getZ() + VendingStandFeature.SIZE_Z);
    }

    private static void setDisplay(ServerLevel level, BlockPos machinePos, String text, ChatFormatting color) {
        Component c = color == null ? Component.literal(text) : Component.literal(text).withStyle(color);
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, standBox(machinePos))) {
            display.setText(c);                          // opened by our access transformer
        }
    }

    /**
     * One second of decay: every remaining red/yellow wool block flips with probability
     * 1/seconds-left — red to a lit campfire, yellow to hay — so the count drains roughly linearly
     * and everything is guaranteed gone by the end without persisting any list.
     */
    private static void burnStep(ServerLevel level, BlockPos machinePos, int secondsLeft) {
        RandomSource rng = level.random;
        for (BlockPos pos : woolPositions(level, machinePos)) {
            if (rng.nextInt(Math.max(1, secondsLeft)) != 0) {
                continue;
            }
            flipWool(level, pos);
        }
    }

    private static List<BlockPos> woolPositions(ServerLevel level, BlockPos machinePos) {
        BlockPos origin = machinePos.subtract(VendingStandFeature.MACHINE_OFFSET);
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin,
                origin.offset(VendingStandFeature.SIZE_X - 1, VendingStandFeature.SIZE_Y - 1, VendingStandFeature.SIZE_Z - 1))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.RED_WOOL) || s.is(Blocks.YELLOW_WOOL)) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** Clients + UPDATE_KNOWN_SHAPE, NO neighbour updates: a flip must not knock off the wall signs,
     *  trapdoors, rods and levers the designer attached to the wool (found the hard way — the burn-down
     *  was popping half the stall's decor). */
    private static final int FLIP_FLAGS = 2 | 16;

    private static void flipWool(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.RED_WOOL)) {
            level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), FLIP_FLAGS);
        } else if (s.is(Blocks.YELLOW_WOOL)) {
            level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), FLIP_FLAGS);
        }
    }

    /**
     * Terminal ruin: swap the whole stall for the designer's broken template, blow up the merchant,
     * pop a few harmless blasts, and boot any open screen — all at once, on the blast.
     */
    private static void close(ServerLevel level, BlockPos machinePos, VendingMachineBlockEntity be) {
        be.markClosed();                                     // gate the current BE first (in case placement fails)
        BlockPos origin = machinePos.subtract(VendingStandFeature.MACHINE_OFFSET);

        // Replace the stall with the ruined version (verbatim, like the base placement). It drops a
        // FRESH machine BE on the machine spot, so re-close it: keeps the 404 screen + inert + re-arms
        // the indestructible region (StandProtection registers on the new BE's onLoad).
        StructureTemplate broken = level.getServer().getStructureManager().get(VendingStandFeature.BROKEN_TEMPLATE).orElse(null);
        if (broken != null) {
            // Drain any water in the box FIRST (the base's bubble column reverts to a plain water source
            // the instant it's disturbed). The ruined template is 60% air, so an undrained source would
            // spill through its open walls. Flag 2|16 = no flow triggered.
            for (BlockPos p : BlockPos.betweenClosed(origin,
                    origin.offset(VendingStandFeature.SIZE_X - 1, VendingStandFeature.SIZE_Y - 1, VendingStandFeature.SIZE_Z - 1))) {
                if (!level.getFluidState(p).isEmpty()) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            broken.placeInWorld(level, origin, origin, new StructurePlaceSettings().setKnownShape(true), level.getRandom(), 2 | 16);
            if (level.getBlockEntity(machinePos) instanceof VendingMachineBlockEntity fresh) {
                fresh.markClosed();
            }
        }

        // The floating timer display and the standing merchant become the ruin: drop the display, and
        // switch the merchant to his blown-up skin (he already refuses everything once closed).
        for (Display.TextDisplay d : level.getEntitiesOfClass(Display.TextDisplay.class, standBox(machinePos))) {
            d.discard();
        }
        for (com.lwi.luckyxp.entity.LuckyMerchant m : level.getEntitiesOfClass(com.lwi.luckyxp.entity.LuckyMerchant.class, standBox(machinePos))) {
            m.setExploded(true);
        }

        // A few cosmetic bangs (non-destructive) spread over the stall.
        AABB box = standBox(machinePos);
        RandomSource rng = level.random;
        for (int i = 0; i < 3; i++) {
            double x = box.minX + rng.nextDouble() * (box.maxX - box.minX);
            double z = box.minZ + rng.nextDouble() * (box.maxZ - box.minZ);
            double y = machinePos.getY() + rng.nextDouble() * 2.0;
            level.explode(null, x, y, z, 1.5F, Level.ExplosionInteraction.NONE);
        }
        closeOpenScreens(level, machinePos);                 // kick any open screen right on the blast
    }
}
