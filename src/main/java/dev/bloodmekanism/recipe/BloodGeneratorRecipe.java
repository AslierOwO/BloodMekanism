package dev.bloodmekanism.recipe;

import com.google.gson.JsonObject;
import dev.bloodmekanism.registry.ModContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record BloodGeneratorRecipe(ResourceLocation id, Ingredient input, int blood, int energy, int ticks) implements Recipe<SimpleContainer> {
    @Override
    public boolean matches(SimpleContainer container, Level level) { return input.test(container.getItem(0)); }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess access) { return ItemStack.EMPTY; }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }

    @Override
    public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, input); }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() { return ModContent.BLOOD_GENERATOR_RECIPE_SERIALIZER.get(); }

    @Override
    public RecipeType<?> getType() { return ModContent.BLOOD_GENERATOR_RECIPE_TYPE.get(); }

    @Override
    public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<BloodGeneratorRecipe> {
        @Override
        public BloodGeneratorRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "input"));
            int blood = GsonHelper.getAsInt(json, "blood");
            int energy = GsonHelper.getAsInt(json, "energy", blood * 20);
            int ticks = GsonHelper.getAsInt(json, "ticks", 100);
            return new BloodGeneratorRecipe(id, input, blood, energy, ticks);
        }

        @Override
        public BloodGeneratorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new BloodGeneratorRecipe(id, Ingredient.fromNetwork(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, BloodGeneratorRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeVarInt(recipe.blood);
            buffer.writeVarInt(recipe.energy);
            buffer.writeVarInt(recipe.ticks);
        }
    }
}
