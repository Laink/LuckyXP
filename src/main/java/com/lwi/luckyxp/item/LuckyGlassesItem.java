package com.lwi.luckyxp.item;

import com.lwi.luckyxp.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Lucky Glasses - a Curios head item. While worn, the wearer sees the floating "Luck +X" / "xN XP"
 * holograms above every nearby lucky block (the same rendering as the op-only {@code /luckychance debug}
 * toggle: {@link com.lwi.luckyxp.event.EventDebug} polls {@link #isWorn} and syncs the labels).
 * No effect while sitting in the inventory. Distribution TBD - creative-only for now.
 */
public class LuckyGlassesItem extends Item implements ICurioItem {

    public LuckyGlassesItem(Properties props) {
        super(props);
    }

    /** Whether this entity wears the glasses in any Curios slot (server-side, polled by EventDebug). */
    public static boolean isWorn(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(handler -> handler.findFirstCurio(Registration.LUCKY_GLASSES.get()))
                .isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.luckyxp.glasses.desc")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Allow right-clicking the glasses in the inventory/hotbar to auto-equip them (matches ring/belt). */
    @Override
    public boolean canRightClickEquip(ItemStack stack) {
        return true;
    }
}
