package xczl.recursivecraft.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.RecursiveCraft;

public class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> RECURSIVE_CRAFT_TAB = TABS.register("recursive_craft_tab", () ->
            CreativeTabRegistry.create(builder -> {
                builder.title(Component.translatable("itemGroup." + RecursiveCraft.MOD_ID + ".recursive_craft_tab"));
                builder.icon(() -> new ItemStack(ModItems.RECURSIVE_CRAFTER_ITEM.get()));

                // 修复：必须在这里把物品加进去
                builder.displayItems((itemDisplayParameters, output) -> {
                    output.accept(ModItems.RECURSIVE_CRAFTER_ITEM.get());
                    output.accept(ModItems.HANDHELD_CRAFTER_ITEM.get());
                });
            })
    );

    public static void register() {
        TABS.register();
    }
}