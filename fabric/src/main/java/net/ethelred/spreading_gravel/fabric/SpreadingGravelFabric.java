package net.ethelred.spreading_gravel.fabric;

import net.ethelred.spreading_gravel.ModContent;
import net.ethelred.spreading_gravel.SpreadingGravelBlock;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

public class SpreadingGravelFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SpreadingGravelBlock block = new SpreadingGravelBlock(
            net.minecraft.world.level.block.Block.Properties.ofFullCopy(Blocks.GRAVEL)
        );

        Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("spreading_gravel", "spreading_gravel"),
            block
        );
        ModContent.SPREADING_GRAVEL = () -> block;

        BlockItem item = new BlockItem(block, new Item.Properties());
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("spreading_gravel", "spreading_gravel"),
            item
        );
        ModContent.SPREADING_GRAVEL_ITEM = () -> item;
    }
}
