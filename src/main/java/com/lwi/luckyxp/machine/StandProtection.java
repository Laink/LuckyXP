package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.worldgen.VendingStandFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a whole placed stand indestructible. The machine block is already unbreakable on its own
 * (−1 hardness + bedrock-level blast resistance), but the designer's decorative VANILLA blocks — signs,
 * wool, copper, the bubble column, lightning rods, beds… — are not, and their hardness can't be changed
 * globally without affecting every such block in the world. A stand is a rare treasure; a creeper or a
 * pickaxe must not dent it.
 *
 * <p>Each stand's protection box is derived from its machine {@link VendingMachineBlockEntity} and held
 * only while that BE is loaded ({@link VendingMachineBlockEntity#onLoad} registers,
 * {@link VendingMachineBlockEntity#setRemoved} drops it). No save data, self-cleaning — and protection
 * is only ever needed in loaded chunks anyway (you can't mine or blow up an unloaded block). BE
 * load/unload and the events below are all main-thread; the maps are concurrent purely as a guard.
 */
public final class StandProtection {
    /** Per-dimension: machine lower-half position → the stand's full 8×12×9 protection box. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, BoundingBox>> REGIONS = new ConcurrentHashMap<>();

    private StandProtection() {}

    public static void register(Level level, BlockPos machineLowerPos) {
        if (level.isClientSide) {
            return;
        }
        BlockPos o = machineLowerPos.subtract(VendingStandFeature.MACHINE_OFFSET);   // structure lower corner
        BoundingBox box = new BoundingBox(o.getX(), o.getY(), o.getZ(),
                o.getX() + VendingStandFeature.SIZE_X - 1,
                o.getY() + VendingStandFeature.SIZE_Y - 1,
                o.getZ() + VendingStandFeature.SIZE_Z - 1);
        REGIONS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(machineLowerPos.immutable(), box);
    }

    public static void unregister(Level level, BlockPos machineLowerPos) {
        if (level.isClientSide) {
            return;
        }
        Map<BlockPos, BoundingBox> m = REGIONS.get(level.dimension());
        if (m != null) {
            m.remove(machineLowerPos);
        }
    }

    public static boolean isProtected(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof Level lvl) || lvl.isClientSide) {
            return false;
        }
        Map<BlockPos, BoundingBox> m = REGIONS.get(lvl.dimension());
        if (m == null) {
            return false;
        }
        for (BoundingBox box : m.values()) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    @Mod.EventBusSubscriber(modid = LuckyXpMod.MODID)
    public static final class Events {
        private Events() {}

        /** Survival mining of any stand block — blocked. Creative still edits it (the machine's
         *  −1 hardness is bypassed in creative too), so a builder can still remove a stand on purpose. */
        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            Player p = event.getPlayer();
            if ((p == null || !p.isCreative()) && isProtected(event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        }

        /** Creeper / TNT / any blast: strip every stand block out of the affected list, so the
         *  explosion still hurts entities and destroys the surroundings but leaves the stall intact. */
        @SubscribeEvent
        public static void onExplosion(ExplosionEvent.Detonate event) {
            Level level = event.getLevel();
            event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
        }

        /** Mobs that break/pick up blocks directly (endermen, etc.). */
        @SubscribeEvent
        public static void onMobDestroy(LivingDestroyBlockEvent event) {
            if (isProtected(event.getEntity().level(), event.getPos())) {
                event.setCanceled(true);
            }
        }
    }
}
