package com.lwi.luckyxp.machine;

/**
 * The kind of goods a vending machine sells. Drives the external screen icon (a big animated visual,
 * no text) and the reward category rolled into its stock. Independent of {@link Rarity} (which lives
 * on the stand/awning and gates stock quality). New types = a new screen texture + a reward branch.
 */
public enum MachineType {
    POTIONS("potions"),
    INFUSED_LB("infused_lb"),
    ORES("ores");

    /** Stable id used in block/registry names, model paths and NBT. */
    public final String id;

    MachineType(String id) {
        this.id = id;
    }

    public static MachineType byId(String s) {
        for (MachineType t : values()) {
            if (t.id.equals(s)) {
                return t;
            }
        }
        return POTIONS;
    }
}
