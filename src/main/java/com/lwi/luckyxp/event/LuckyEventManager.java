package com.lwi.luckyxp.event;

import com.lwi.luckyxp.net.LuckyXpNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/**
 * Authoritative, persisted state of the single global Lucky XP event (one server-wide). Stored on the
 * overworld's data storage; ticks the active event down and broadcasts every change to all clients.
 *
 * <p>Always fetch via {@link #get(MinecraftServer)} so callers in any dimension share the one overworld
 * instance.
 */
public final class LuckyEventManager extends SavedData {
    private static final String NAME = "luckyxp_events";
    /** Resync the countdown to clients about once per second to correct any client-side drift. */
    private static final int RESYNC_INTERVAL = 20;

    @Nullable private LuckyEvent active;
    private int ticksRemaining;
    private int totalTicks;

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

    public int ticksRemaining() {
        return ticksRemaining;
    }

    public int totalTicks() {
        return totalTicks;
    }

    /** Start (or replace) the global event for {@code durationTicks}; broadcasts to all clients. */
    public void start(MinecraftServer server, LuckyEvent event, int durationTicks) {
        this.active = event;
        this.totalTicks = Math.max(1, durationTicks);
        this.ticksRemaining = this.totalTicks;
        setDirty();
        LuckyXpNetwork.broadcastEvent(server, this);
    }

    /** End the active event now (no-op if none); broadcasts the cleared state. */
    public void stop(MinecraftServer server) {
        if (active == null) {
            return;
        }
        active = null;
        ticksRemaining = 0;
        totalTicks = 0;
        setDirty();
        LuckyXpNetwork.broadcastEvent(server, this);
    }

    /** Once per overworld tick: count the active event down and end it at zero. */
    public void serverTick(MinecraftServer server) {
        if (active == null) {
            return;
        }
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            stop(server);
            return;
        }
        setDirty();
        if (ticksRemaining % RESYNC_INTERVAL == 0) {
            LuckyXpNetwork.broadcastEvent(server, this);
        }
    }

    private static LuckyEventManager load(CompoundTag tag) {
        LuckyEventManager data = new LuckyEventManager();
        if (tag.contains("Active")) {
            data.active = LuckyEvent.load(tag.getCompound("Active"));
            data.ticksRemaining = tag.getInt("Remaining");
            data.totalTicks = tag.getInt("Total");
            if (data.active == null) {
                data.ticksRemaining = 0;
                data.totalTicks = 0;
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (active != null) {
            tag.put("Active", active.save());
            tag.putInt("Remaining", ticksRemaining);
            tag.putInt("Total", totalTicks);
        }
        return tag;
    }
}
