package com.lwi.luckyxp.event;

import com.lwi.luckyxp.net.LuckyXpNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/**
 * Authoritative state of the single global Lucky XP event (design v4). An event is purely a REVEAL: the
 * case-opening roulette plays for {@code revealTicks}, then — unless it's a preview or a NOTHING miss —
 * the blocks APPEAR ({@link LuckyBlockShower}) and the event clears. There is NO temporal/active phase.
 * Stored on the overworld; the {@code seed} makes the roulette deterministic across clients.
 */
public final class LuckyEventManager extends SavedData {
    private static final String NAME = "luckyxp_events";
    private static final int RESYNC_INTERVAL = 20;

    @Nullable private LuckyEvent active;
    private int revealRemaining;
    private int revealTotal;
    private long seed;
    private boolean preview;

    public static LuckyEventManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(LuckyEventManager::load, LuckyEventManager::new, NAME);
    }

    @Nullable
    public LuckyEvent active() {
        return active;
    }

    public boolean hasActive() {
        return active != null;
    }

    public int revealRemaining() {
        return revealRemaining;
    }

    public int revealTotal() {
        return revealTotal;
    }

    public long seed() {
        return seed;
    }

    /** Start the roulette reveal for {@code revealTicks}; the result is decided up front in {@code event}. */
    public void start(MinecraftServer server, LuckyEvent event, int revealTicks, boolean preview, long seed) {
        this.active = event;
        this.revealTotal = Math.max(1, revealTicks);
        this.revealRemaining = this.revealTotal;
        this.preview = preview;
        this.seed = seed;
        setDirty();
        LuckyXpNetwork.broadcastEvent(server, this);
    }

    public void stop(MinecraftServer server) {
        if (active == null) {
            return;
        }
        active = null;
        revealRemaining = 0;
        revealTotal = 0;
        preview = false;
        setDirty();
        LuckyXpNetwork.broadcastEvent(server, this);
    }

    /** Count the reveal down; at zero, fire the shower (unless preview / NOTHING) and clear. */
    public void serverTick(MinecraftServer server) {
        if (active == null) {
            return;
        }
        revealRemaining--;
        if (revealRemaining <= 0) {
            LuckyEvent ev = active;
            if (!preview && !ev.isNothing()) {
                fire(server, ev);
            }
            stop(server);
            return;
        }
        setDirty();
        if (revealRemaining % RESYNC_INTERVAL == 0) {
            LuckyXpNetwork.broadcastEvent(server, this);
        }
    }

    private static void fire(MinecraftServer server, LuckyEvent ev) {
        boolean xp = ev.type() == LuckyEventType.DOUBLE_XP;
        ResourceLocation block = ev.isJackpot() ? null : ev.blockId();
        int luckRaw = xp ? 0 : ev.luckPercent();
        float mult = xp ? ev.xpMult() : 0.0F;
        LuckyBlockShower.shower(server, block, xp, luckRaw, mult, Math.max(1, ev.count()), ev.isMega());
    }

    /**
     * A reason an event must NOT start right now, or {@code null} if it may. Rule: no event while a player
     * is in the End and the Ender Dragon has not yet been beaten (the fight needs full focus). Mirrors
     * Optional Suffering's {@code noEndInvasionBeforeDragon}.
     */
    @Nullable
    public static String startBlockReason(MinecraftServer server) {
        boolean anyInEnd = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.END) {
                anyInEnd = true;
                break;
            }
        }
        if (anyInEnd && !dragonBeaten(server)) {
            return "no event in the End until the Ender Dragon is beaten";
        }
        return null;
    }

    private static boolean dragonBeaten(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        EndDragonFight fight = end != null ? end.getDragonFight() : null;
        return fight != null && fight.hasPreviouslyKilledDragon();
    }

    private static LuckyEventManager load(CompoundTag tag) {
        LuckyEventManager data = new LuckyEventManager();
        if (tag.contains("Active")) {
            data.active = LuckyEvent.load(tag.getCompound("Active"));
            if (data.active != null) {
                data.revealRemaining = tag.getInt("Remaining");
                data.revealTotal = tag.getInt("Total");
                data.seed = tag.getLong("Seed");
                data.preview = tag.getBoolean("Preview");
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (active != null) {
            tag.put("Active", active.save());
            tag.putInt("Remaining", revealRemaining);
            tag.putInt("Total", revealTotal);
            tag.putLong("Seed", seed);
            tag.putBoolean("Preview", preview);
        }
        return tag;
    }
}
