package com.lwi.luckyxp.event;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Makes lucky blocks APPEAR around each player as the dopamine payoff of an event (design v4): they pop
 * in one by one with a rising chime, infused for a Luck event (Luck NBT) or flagged for ×mult Lucky XP on
 * break (an XP event). A mega jackpot adds fireworks.
 *
 * <p>Placed blocks do NOT expire — they stay until a player breaks them (user choice). They are also
 * "untouchable": every placed block is tracked ({@link EventBlockData}) and a periodic sweep RE-PLACES any
 * that got overwritten by another drop's structure/explosion, so an event reward is never lost to a
 * side-effect (only the player breaking it removes it). Blocks are spaced apart to reduce collisions.
 */
public final class LuckyBlockShower {
    private static final int STAGGER = 6;            // ticks between each block popping in
    private static final int RADIUS = 7;             // horizontal spread around the player
    private static final int MIN_SPACING = 4;        // min blocks between two event blocks of the same shower
    private static final int PROTECT_INTERVAL = 5;   // ticks between protection sweeps

    private record Pending(ResourceLocation dim, BlockPos pos, ResourceLocation block,
                           int luckRaw, float xpMult, boolean mega, int index, long atGameTime) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private LuckyBlockShower() {}

    /**
     * Schedule a shower around every online player. {@code block == null} = a mega-jackpot assortment
     * (random types). {@code xpMult > 0} marks the blocks as ×mult XP; otherwise they're infused to
     * {@code luckRaw} Luck. {@code count} blocks per player.
     */
    public static void shower(MinecraftServer server, @Nullable ResourceLocation block, boolean isXp,
                              int luckRaw, float xpMult, int count, boolean mega) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            RandomSource rng = level.getRandom();
            ResourceLocation dim = level.dimension().location();
            long now = level.getGameTime();
            List<ResourceLocation> pool = null;
            if (block == null) {
                pool = LuckyTweaksApi.getLuckyBlockIds(dim);
                if (pool.isEmpty()) {
                    pool = LuckyTweaksApi.getLuckyBlockIds();
                }
            }
            List<BlockPos> chosen = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BlockPos pos = findSpot(level, player.blockPosition(), rng, chosen);
                if (pos == null) {
                    continue;
                }
                ResourceLocation pick = block != null ? block
                        : (pool.isEmpty() ? null : pool.get(rng.nextInt(pool.size())));
                if (pick == null) {
                    continue;
                }
                chosen.add(pos);
                PENDING.add(new Pending(dim, pos, pick, isXp ? 0 : luckRaw, isXp ? xpMult : 0.0F,
                        mega, chosen.size() - 1, now + (long) chosen.size() * STAGGER));
            }
        }
    }

    /** Per-level tick: pop in due blocks (crescendo), then re-place any overwritten event block. */
    public static void tickLevel(ServerLevel level) {
        if (!PENDING.isEmpty()) {
            ResourceLocation dim = level.dimension().location();
            long now = level.getGameTime();
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending pd = it.next();
                if (!pd.dim().equals(dim) || now < pd.atGameTime()) {
                    continue;
                }
                it.remove();
                spawnOne(level, pd);
            }
        }
        if (level.getGameTime() % PROTECT_INTERVAL == 0) {
            protect(level);
        }
    }

    /** Drop the pending-spawn queue (on server stop, so the static list never bleeds across worlds). */
    public static void clear() {
        PENDING.clear();
    }

    /** Re-place any tracked event block that's been overwritten (structure/explosion) — makes them untouchable. */
    private static void protect(ServerLevel level) {
        EventBlockData data = EventBlockData.get(level);
        if (data.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, EventBlockData.Entry> me : data.view().entrySet()) {
            EventBlockData.Entry e = me.getValue();
            Block b = ForgeRegistries.BLOCKS.getValue(e.block);
            if (b == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(me.getKey());
            if (!level.getBlockState(pos).is(b)) {
                level.setBlock(pos, b.defaultBlockState(), 3);
                if (e.luckRaw != 0) {
                    setLuck(level, pos, e.luckRaw);
                }
            }
        }
    }

    private static void spawnOne(ServerLevel level, Pending pd) {
        Block b = ForgeRegistries.BLOCKS.getValue(pd.block());
        if (b == null) {
            return;
        }
        BlockPos pos = pd.pos();
        if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).canBeReplaced()) {
            return; // the spot got occupied since we picked it
        }
        level.setBlock(pos, b.defaultBlockState(), 3);
        if (pd.luckRaw() != 0) {
            setLuck(level, pos, pd.luckRaw());
        }
        EventBlockData.get(level).mark(pos, pd.block(), pd.luckRaw(), pd.xpMult());

        float pitch = Math.min(2.0F, 0.7F + 0.09F * pd.index());   // crescendo as the count climbs
        playAt(level, pos, "block.note_block.bell", 0.8F, pitch);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                8, 0.3, 0.4, 0.3, 0.0);
        if (pd.mega()) {
            fireworks(level, pos);
        }
    }

    /** Write the lucky block's stored Luck so its drops roll boosted (NBT "Luck", per the Lucky Block mod). */
    private static void setLuck(ServerLevel level, BlockPos pos, int luck) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt("Luck", luck);
        be.load(tag);
        be.setChanged();
        BlockState st = level.getBlockState(pos);
        level.sendBlockUpdated(pos, st, st, 3);
    }

    private static void fireworks(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                40, 0.5, 0.5, 0.5, 0.08);
        playAt(level, pos, "entity.firework_rocket.large_blast", 1.2F, 1.0F);
        playAt(level, pos, "entity.firework_rocket.twinkle", 1.0F, 1.0F);
    }

    private static void playAt(ServerLevel level, BlockPos pos, String soundId, float vol, float pitch) {
        SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
        if (se != null) {
            level.playSound(null, pos, se, SoundSource.PLAYERS, vol, pitch);
        }
    }

    @Nullable
    private static BlockPos findSpot(ServerLevel level, BlockPos around, RandomSource rng, List<BlockPos> avoid) {
        for (int attempt = 0; attempt < 28; attempt++) {
            int r = RADIUS + attempt / 4;          // widen the search as attempts fail (caves, towers, open air)
            int dx = rng.nextInt(r * 2 + 1) - r;
            int dz = rng.nextInt(r * 2 + 1) - r;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue; // never on top of the player
            }
            int bx = around.getX() + dx;
            int bz = around.getZ() + dz;
            if (tooClose(bx, bz, avoid)) {
                continue; // keep event blocks spaced so one drop's structure can't bury its neighbours
            }
            for (int y = around.getY() + 4; y >= around.getY() - 6; y--) {
                BlockPos pos = new BlockPos(bx, y, bz);
                if (!level.isInWorldBounds(pos)) {
                    continue;
                }
                // a free cell with solid support below is enough (the lucky block is 1 high)
                if (level.isEmptyBlock(pos)
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean tooClose(int x, int z, List<BlockPos> avoid) {
        for (BlockPos a : avoid) {
            if (Math.abs(a.getX() - x) < MIN_SPACING && Math.abs(a.getZ() - z) < MIN_SPACING) {
                return true;
            }
        }
        return false;
    }
}
