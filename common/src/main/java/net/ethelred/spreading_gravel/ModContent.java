package net.ethelred.spreading_gravel;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class ModContent {
    /** Assigned by each platform module during registration. */
    public static Supplier<Block> SPREADING_GRAVEL = null;
    public static Supplier<Item> SPREADING_GRAVEL_ITEM = null;
}
