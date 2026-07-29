package com.lwi.luckyxp;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.VendingMachineBlock;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import com.lwi.luckyxp.machine.VendingMachineMenu;
import com.lwi.luckyxp.worldgen.VendingStandFeature;
import com.lwi.luckyxp.entity.LuckyXpOrb;
import com.lwi.luckyxp.item.LuckyGlassesItem;
import com.lwi.luckyxp.net.LuckyXpNetwork;
import com.lwi.luckyxp.xp.BreakXp;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Central registration + wiring for Lucky XP. */
public final class Registration {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LuckyXpMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LuckyXpMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LuckyXpMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, LuckyXpMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LuckyXpMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LuckyXpMod.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, LuckyXpMod.MODID);

    public static final RegistryObject<VendingStandFeature> VENDING_STAND = FEATURES.register("vending_stand", VendingStandFeature::new);

    /** One vending-machine block per type (the block IS the type; rarity lives on the entity/stand). */
    public static final Map<MachineType, RegistryObject<Block>> MACHINES = new EnumMap<>(MachineType.class);
    public static final Map<MachineType, RegistryObject<Item>> MACHINE_ITEMS = new EnumMap<>(MachineType.class);

    static {
        for (MachineType t : MachineType.values()) {
            String name = "vending_machine_" + t.id;
            // Unbreakable and blast-proof, bedrock-style (user 2026-07-09: a machine stays where the
            // world put it, whatever happens — its stock is a one-time, per-machine treasure, and the
            // upcoming merchant sells the reroll). destroySpeed -1 also stops pistons; survival cannot
            // mine it, creative still breaks it instantly. Wither/dragon immunity via the block tags.
            RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new VendingMachineBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion(), t));
            MACHINES.put(t, block);
            MACHINE_ITEMS.put(t, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<BlockEntityType<VendingMachineBlockEntity>> VENDING_MACHINE_BE =
            BLOCK_ENTITIES.register("vending_machine", () -> BlockEntityType.Builder.of(
                    VendingMachineBlockEntity::new,
                    MACHINES.get(MachineType.POTIONS).get(), MACHINES.get(MachineType.INFUSED_LB).get(),
                    MACHINES.get(MachineType.ORES).get(), MACHINES.get(MachineType.TOOLS).get()
            ).build(null));

    public static final RegistryObject<MenuType<VendingMachineMenu>> VENDING_MACHINE_MENU =
            MENUS.register("vending_machine", () -> IForgeMenuType.create(VendingMachineMenu::new));

    public static final RegistryObject<MenuType<com.lwi.luckyxp.machine.MerchantMenu>> MERCHANT_MENU =
            MENUS.register("merchant", () -> IForgeMenuType.create(com.lwi.luckyxp.machine.MerchantMenu::new));

    /** The stand's service NPC (see {@link com.lwi.luckyxp.entity.LuckyMerchant}). */
    public static final RegistryObject<EntityType<com.lwi.luckyxp.entity.LuckyMerchant>> LUCKY_MERCHANT =
            ENTITIES.register("lucky_merchant",
                    () -> EntityType.Builder.of(com.lwi.luckyxp.entity.LuckyMerchant::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F).clientTrackingRange(10).build("lucky_merchant"));

    /** Curios head item: reveals the Luck holograms over nearby lucky blocks while worn. */
    public static final RegistryObject<Item> LUCKY_GLASSES = ITEMS.register("lucky_glasses",
            () -> new LuckyGlassesItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<EntityType<LuckyXpOrb>> LUCKY_XP_ORB = ENTITIES.register("lucky_xp_orb",
            () -> EntityType.Builder.<LuckyXpOrb>of(LuckyXpOrb::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).build("lucky_xp_orb"));

    /** A blue xp bottle that bursts into Lucky XP orbs (see {@link com.lwi.luckyxp.item.LuckyExperienceBottleItem}). */
    public static final RegistryObject<Item> LUCKY_EXPERIENCE_BOTTLE = ITEMS.register("lucky_experience_bottle",
            () -> new com.lwi.luckyxp.item.LuckyExperienceBottleItem(new Item.Properties()));

    public static final RegistryObject<EntityType<com.lwi.luckyxp.entity.ThrownLuckyBottle>> THROWN_LUCKY_BOTTLE =
            ENTITIES.register("thrown_lucky_bottle",
                    () -> EntityType.Builder.<com.lwi.luckyxp.entity.ThrownLuckyBottle>of(
                                    com.lwi.luckyxp.entity.ThrownLuckyBottle::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thrown_lucky_bottle"));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("luckyxp", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.luckyxp"))
            .icon(() -> new ItemStack(MACHINE_ITEMS.get(MachineType.INFUSED_LB).get()))
            .displayItems((params, output) -> {
                for (MachineType t : MachineType.values()) {
                    output.accept(MACHINE_ITEMS.get(t).get());
                }
                output.accept(LUCKY_GLASSES.get());
                output.accept(LUCKY_EXPERIENCE_BOTTLE.get());
            })
            .build());

    private Registration() {}

    /** Mod-bus: base attributes for the merchant (a plain immobile mob). */
    public static void onEntityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(LUCKY_MERCHANT.get(),
                net.minecraft.world.entity.PathfinderMob.createMobAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 20.0D)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.0D)
                        .build());
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(Registration::onEntityAttributes);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(Registration::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LuckyXpNetwork.register();
            // A flat Lucky XP award on every lucky-block break...
            LuckyTweaksApi.registerBreakListener(BreakXp::onBroken);
            // ...topped up on the same tick when the drop it rolled turns out to be legendary.
            LuckyTweaksApi.registerLegendaryDropListener(BreakXp::onLegendaryDrop);
            // ...and taken away when the player falls (all of it on a death, a share when downed).
            com.lwi.luckyxp.xp.DeathXpLoss.register();
        });
    }
}
