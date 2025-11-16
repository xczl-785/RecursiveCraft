package xczl.recursivecraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import xczl.recursivecraft.block.RecursiveCrafterBlock;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

import java.util.function.Supplier;

public class Registration {

    // 1. 创建 DeferredRegisters
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RecursiveCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RecursiveCraft.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RecursiveCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RecursiveCraft.MODID);


    // --- *** 2. 注册方块和其对应的 BlockItem (通过辅助方法) *** ---
    // 这个 RegistryObject 现在将持有我们的 RecursiveCrafterBlock 实例
    public static final RegistryObject<Block> RECURSIVE_CRAFTER_BLOCK = registerBlock("recursive_crafter",
            () -> new RecursiveCrafterBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE).sound(SoundType.WOOD))
    );

    // 我们可以直接从 RECURSIVE_CRAFTER_BLOCK 获取其对应的 BlockItem 的 RegistryObject
    // 注意：这个字段现在是 private 的，或者如果你需要外部访问，可以保持 public
    // 更推荐的做法是让 registerBlock 方法返回 RegistryObject<Block>，
    // 然后通过 RECURSIVE_CRAFTER_BLOCK.get().asItem() 来获取 Item 实例给 CreativeModeTab。
    // 为了 CreativeModeTab 的 icon，我们需要一个 Item 的 RegistryObject。
    // 所以我们让 registerBlock 方法返回一个 Wrapper，或者直接获取 Item 的 RegistryObject。
    // 为了简单起见，我们直接定义 RECURSIVE_CRAFTER_ITEM
    public static final RegistryObject<Item> RECURSIVE_CRAFTER_ITEM = ITEMS.register("recursive_crafter",
            () -> new BlockItem(RECURSIVE_CRAFTER_BLOCK.get(), new Item.Properties()));


    // 3. 注册菜单类型
    public static final RegistryObject<MenuType<RecursiveCrafterMenu>> RECURSIVE_CRAFTER_MENU = MENU_TYPES.register("recursive_crafter_menu",
            () -> IForgeMenuType.create(RecursiveCrafterMenu::new)
    );

    // 4. 注册自定义 CreativeModeTab
    public static final RegistryObject<CreativeModeTab> RECURSIVE_CRAFT_TAB = CREATIVE_MODE_TABS.register("recursive_craft_tab", () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(RECURSIVE_CRAFTER_ITEM.get())) // 标签页的图标，使用我们的方块物品
            .title(Component.translatable("itemGroup." + RecursiveCraft.MODID + ".recursive_craft_tab")) // 标签页的标题
            .displayItems((pParameters, pOutput) -> { // 定义此标签页显示哪些物品
                pOutput.accept(RECURSIVE_CRAFTER_ITEM.get()); // 将我们的方块物品添加到标签页
                // 如果将来有更多物品，可以在这里继续添加
            })
            .build());


    // --- *** 辅助注册方法 (修正版，与你的树苗 Mod 类似) *** ---
    // 这个方法现在只负责注册方块本身，BlockItem 在 RECURSIVE_CRAFTER_ITEM 字段中直接注册
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    // 5. 在 Mod 主类的构造函数中调用此方法
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus); // 确保 ITEMS 也注册
        MENU_TYPES.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus); // 注册 CreativeModeTab
        RecursiveCraft.LOGGER.info("Registered Recursive Crafting Block, Item, and Menu Type.");
    }
}