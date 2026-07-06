package com.lwi.luckyxp.integration;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.client.VendingMachineScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Optional JEI integration: exposes the vending machine's article under the mouse as a JEI
 * ingredient, so the R (recipes) / U (usages) hotkeys work over the trade list exactly like over
 * an inventory slot. Only loaded by JEI itself (via {@link JeiPlugin} scanning), so JEI stays a
 * soft dependency.
 */
@JeiPlugin
public class LuckyXpJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(LuckyXpMod.MODID, "jei");
    @Nullable private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(VendingMachineScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(VendingMachineScreen screen,
                                                                                      double mouseX, double mouseY) {
                IJeiRuntime rt = runtime;
                VendingMachineScreen.JeiHover hover = screen.jeiHover(mouseX, mouseY);
                if (rt == null || hover == null || hover.stack().isEmpty()) {
                    return Optional.empty();
                }
                return rt.getIngredientManager()
                        .createTypedIngredient(VanillaTypes.ITEM_STACK, hover.stack())
                        .map(typed -> (IClickableIngredient<?>) new Clickable(typed, hover.area()));
            }
        });
    }

    private record Clickable(ITypedIngredient<ItemStack> typed, Rect2i area) implements IClickableIngredient<ItemStack> {
        @Override
        public ITypedIngredient<ItemStack> getTypedIngredient() {
            return typed;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }
    }
}
