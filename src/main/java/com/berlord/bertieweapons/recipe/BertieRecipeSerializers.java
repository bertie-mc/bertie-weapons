package com.berlord.bertieweapons.recipe;

import com.berlord.bertieweapons.BertieWeapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BertieRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BertieWeapons.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<StatUpgradeRecipe>>
            STAT_UPGRADE = RECIPE_SERIALIZERS.register(
                    "stat_upgrade", () -> new SimpleCraftingRecipeSerializer<>(StatUpgradeRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SpellUpgradeRecipe>>
            SPELL_UPGRADE = RECIPE_SERIALIZERS.register(
                    "spell_upgrade", () -> new SimpleCraftingRecipeSerializer<>(SpellUpgradeRecipe::new));

    private BertieRecipeSerializers() {}
}
