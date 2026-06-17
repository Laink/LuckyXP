package com.lwi.luckyxp;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.machine.MachineType;
import com.lwi.luckyxp.machine.VendingMachineBlock;
import com.lwi.luckyxp.machine.VendingMachineBlockEntity;
import com.lwi.luckyxp.machine.VendingMachineMenu;
import com.lwi.luckyxp.entity.LuckyXpOrb;
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

    /** One vending-machine block per type (the block IS the type; rarity lives on the entity/stand). */
    public static final Map<MachineType, RegistryObject<Block>> MACHINES = new EnumMap<>(MachineType.class);
    public static final Map<MachineType, RegistryObject<Item>> MACHINE_ITEMS = new EnumMap<>(MachineType.class);

    static {
        for (MachineType t : MachineType.values()) {
            String name = "vending_machine_" + t.id;
            RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new VendingMachineBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().noOcclusion(), t));
            MACHINES.put(t, block);
            MACHINE_ITEMS.put(t, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<BlockEntityType<VendingMachineBlockEntity>> VENDING_MACHINE_BE =
            BLOCK_ENTITIES.register("vending_machine", () -> BlockEntityType.Builder.of(
                    VendingMachineBlockEntity::new,
                    MACHINES.get(MachineType.POTIONS).get(), MACHINES.get(MachineType.INFUSED_LB).get(),
                    MACHINES.get(MachineType.ORES).get()
            ).build(null));

    public static final RegistryObject<MenuType<VendingMachineMenu>> VENDING_MACHINE_MENU =
            MENUS.register("vending_machine", () -> IForgeMenuType.create(VendingMachineMenu::new));

    public static final RegistryObject<EntityType<LuckyXpOrb>> LUCKY_XP_ORB = ENTITIES.register("lucky_xp_orb",
            () -> EntityType.Builder.<LuckyXpOrb>of(LuckyXpOrb::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).build("lucky_xp_orb"));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("luckyxp", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.luckyxp"))
            .icon(() -> new ItemStack(MACHINE_ITEMS.get(MachineType.INFUSED_LB).get()))
            .displayItems((params, output) -> {
                for (MachineType t : MachineType.values()) {
                    output.accept(MACHINE_ITEMS.get(t).get());
                }
            })
            .build());

    private Registration() {}

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(Registration::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LuckyXpNetwork.register();
            // Award Lucky XP whenever a player breaks a lucky block (scaled by the block's rarity proxy).
            LuckyTweaksApi.registerBreakListener(BreakXp::onBroken);
        });
    }
}
