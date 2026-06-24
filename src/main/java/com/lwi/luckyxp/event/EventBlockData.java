package com.lwi.luckyxp.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-dimension memory of the lucky blocks an event spawned: their type, their infused Luck (for a LUCK
 * event) and their XP ×mult (for an XP event). Two uses:
 * <ul>
 *   <li>on break: {@link #consume} gives the XP ×mult and forgets the position;</li>
 *   <li>protection: {@code LuckyBlockShower} re-places any tracked block that got overwritten by another
 *       drop's structure/explosion, so the player never loses an event reward to a side-effect.</li>
 * </ul>
 * Persisted (the blocks don't expire), so they keep their bonus + protection across save/reload.
 */
public final class EventBlockData extends SavedData {
    private static final String NAME = "luckyxp_event_blocks";

    /** A tracked event block: its type, infused Luck (0 if none) and XP multiplier (0 if not an XP block). */
    public static final class Entry {
        public final ResourceLocation block;
        public final int luckRaw;
        public final float xpMult;

        Entry(ResourceLocation block, int luckRaw, float xpMult) {
            this.block = block;
            this.luckRaw = luckRaw;
            this.xpMult = xpMult;
        }
    }

    private final Map<Long, Entry> blocks = new HashMap<>();

    public static EventBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(EventBlockData::load, EventBlockData::new, NAME);
    }

    public void mark(BlockPos pos, ResourceLocation block, int luckRaw, float xpMult) {
        blocks.put(pos.asLong(), new Entry(block, luckRaw, xpMult));
        setDirty();
    }

    /** XP multiplier for this position AND forgets it (player broke it). 0 if not a tracked event block. */
    public float consume(BlockPos pos) {
        Entry e = blocks.remove(pos.asLong());
        if (e != null) {
            setDirty();
            return e.xpMult;
        }
        return 0.0F;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** The XP multiplier tracked at this position WITHOUT removing it (0 if not an XP event block). */
    public float xpMultAt(BlockPos pos) {
        Entry e = blocks.get(pos.asLong());
        return e != null ? e.xpMult : 0.0F;
    }

    /** Live view for the protection sweep (do not structurally modify during iteration). */
    public Map<Long, Entry> view() {
        return blocks;
    }

    private static EventBlockData load(CompoundTag tag) {
        EventBlockData data = new EventBlockData();
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation b = ResourceLocation.tryParse(c.getString("Block"));
            if (b != null) {
                data.blocks.put(c.getLong("Pos"), new Entry(b, c.getInt("Luck"), c.getFloat("Mult")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> me : blocks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", me.getKey());
            c.putString("Block", me.getValue().block.toString());
            c.putInt("Luck", me.getValue().luckRaw);
            c.putFloat("Mult", me.getValue().xpMult);
            list.add(c);
        }
        tag.put("Blocks", list);
        return tag;
    }
}
