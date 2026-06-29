package net.ethelred.spreading_gravel.neoforge;

import net.ethelred.spreading_gravel.ModContent;
import net.ethelred.spreading_gravel.SpreadingGravelBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("spreading_gravel")
public class SpreadingGravelNeoForge {

    public SpreadingGravelNeoForge(IEventBus modEventBus) {
        // Registries.BLOCK / Registries.ITEM are ResourceKey<Registry<T>> — required by DeferredRegister.create
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, "spreading_gravel");
        DeferredRegister<Item>  items  = DeferredRegister.create(Registries.ITEM,  "spreading_gravel");

        ModContent.SPREADING_GRAVEL = blocks.register("spreading_gravel",
            () -> new SpreadingGravelBlock(
                Block.Properties.ofFullCopy(Blocks.GRAVEL)
            )
        );

        ModContent.SPREADING_GRAVEL_ITEM = items.register("spreading_gravel",
            () -> new BlockItem(ModContent.SPREADING_GRAVEL.get(), new Item.Properties())
        );

        blocks.register(modEventBus);
        items.register(modEventBus);
    }
}
