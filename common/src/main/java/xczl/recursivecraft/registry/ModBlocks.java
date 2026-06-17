package xczl.recursivecraft.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.block.RecursiveCrafterBlock;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> RECURSIVE_CRAFTER_BLOCK = BLOCKS.register("recursive_crafter",
            () -> new RecursiveCrafterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
                    .setId(blockKey("recursive_crafter")))
    );

    public static void register() {
        BLOCKS.register();
    }

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, RecursiveCraft.id(name));
    }
}
