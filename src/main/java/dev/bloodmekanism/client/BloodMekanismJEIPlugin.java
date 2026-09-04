package dev.bloodmekanism.client;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.recipe.BloodGeneratorRecipe;
import dev.bloodmekanism.registry.ModContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import wayoftime.bloodmagic.compat.jei.alchemytable.AlchemyTableRecipeCategory;
import wayoftime.bloodmagic.compat.jei.alchemytable.PotionRecipeCategory;
import wayoftime.bloodmagic.compat.jei.altar.BloodAltarRecipeCategory;
import wayoftime.bloodmagic.compat.jei.arc.ARCRecipeCategory;
import wayoftime.bloodmagic.compat.jei.array.AlchemyArrayCraftingCategory;
import wayoftime.bloodmagic.compat.jei.forge.TartaricForgeRecipeCategory;

import java.util.Objects;

@JeiPlugin
public final class BloodMekanismJEIPlugin implements IModPlugin {
    public static final RecipeType<BloodGeneratorRecipe> BLOOD_GENERATOR_TYPE =
          RecipeType.create(BloodMekanism.MOD_ID, "blood_generator", BloodGeneratorRecipe.class);

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new BloodGeneratorRecipeCategory(helper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        registration.addRecipes(BLOOD_GENERATOR_TYPE,
              level.getRecipeManager().getAllRecipesFor(ModContent.BLOOD_GENERATOR_RECIPE_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var factory : ModContent.UNIVERSAL_FACTORIES.values()) {
            registration.addRecipeCatalyst(new ItemStack(factory.get()), BloodAltarRecipeCategory.RECIPE_TYPE);
        }
        registration.addRecipeCatalyst(new ItemStack(ModContent.PROCESS_FACTORIES.get(dev.bloodmekanism.machine.FactoryMode.ALCHEMY_TABLE).get()),
              AlchemyTableRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.PROCESS_FACTORIES.get(dev.bloodmekanism.machine.FactoryMode.ALCHEMY_TABLE).get()),
              PotionRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.PROCESS_FACTORIES.get(dev.bloodmekanism.machine.FactoryMode.ALCHEMY_ARRAY).get()),
              AlchemyArrayCraftingCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.PROCESS_FACTORIES.get(dev.bloodmekanism.machine.FactoryMode.SOUL_FORGE).get()),
              TartaricForgeRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.PROCESS_FACTORIES.get(dev.bloodmekanism.machine.FactoryMode.ARC).get()),
              ARCRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.HEMOGENIC_MACHINE.get()), BLOOD_GENERATOR_TYPE);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(BloodMekanism.MOD_ID, "jei_plugin");
    }
}
