package com.berlord.bertieweapons;

import com.berlord.bertieweapons.recipe.BertieRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(BertieWeapons.MODID)
public class BertieWeapons {
    public static final String MODID = "bertie_weapons";

    public BertieWeapons(IEventBus modBus, ModContainer container) {
        BertieRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
