package com.lwi.luckyxp.item;

import com.lwi.luckyxp.entity.ThrownLuckyBottle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Lucky Experience Bottle: thrown exactly like the vanilla xp bottle, but it releases blue Lucky
 * XP orbs (see {@link ThrownLuckyBottle}). The item is a plain throwable; all the payout logic is on
 * the projectile.
 */
public class LuckyExperienceBottleItem extends Item {
    public LuckyExperienceBottleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_BOTTLE_THROW, SoundSource.NEUTRAL, 0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownLuckyBottle bottle = new ThrownLuckyBottle(level, player);
            bottle.setItem(held);
            bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.7F, 1.0F);
            level.addFreshEntity(bottle);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }
}
