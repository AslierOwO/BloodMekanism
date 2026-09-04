package dev.bloodmekanism.client;

import dev.bloodmekanism.recipe.BloodGeneratorRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;

import java.util.List;

public final class BloodGeneratorRecipeCategory implements IRecipeCategory<BloodGeneratorRecipe> {
    private final IDrawable background;
    private final IDrawable icon;

    public BloodGeneratorRecipeCategory(IGuiHelper helper) {
        background = helper.createBlankDrawable(150, 55);
        icon = helper.createDrawableItemStack(new ItemStack(dev.bloodmekanism.registry.ModContent.HEMOGENIC_MACHINE.get()));
    }

    @Override
    public RecipeType<BloodGeneratorRecipe> getRecipeType() {
        return BloodMekanismJEIPlugin.BLOOD_GENERATOR_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bloodmekanism.blood_generator");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BloodGeneratorRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 18, 18).addIngredients(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 112, 18)
              .addFluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), recipe.blood());
    }

    @Override
    public void draw(BloodGeneratorRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
          double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.bloodmekanism.energy", recipe.energy()), 2, 2, 0x707070, false);
        graphics.drawString(font, Component.translatable("jei.bloodmekanism.time", recipe.ticks()), 2, 40, 0x707070, false);
    }
}
