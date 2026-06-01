package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetOutputSpecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void constructor_withItemAndTag_shouldExportTemplateStack() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:water");

        TargetOutputSpec spec = new TargetOutputSpec(Items.POTION, tag);

        ItemStack template = spec.toTemplateStack();
        assertEquals(Items.POTION, template.getItem());
        assertEquals(1, template.getCount());
        assertEquals(tag, template.getTag());
        assertNotSame(tag, template.getTag());
    }

    @Test
    void constructor_withNullTag_shouldExportTemplateStackWithoutTag() {
        TargetOutputSpec spec = new TargetOutputSpec(Items.STICK, null);

        ItemStack template = spec.toTemplateStack();
        assertEquals(Items.STICK, template.getItem());
        assertNull(template.getTag());
    }

    @Test
    void constructor_whenItemIsNullOrAir_shouldReject() {
        CompoundTag tag = new CompoundTag();

        assertThrows(NullPointerException.class, () -> new TargetOutputSpec(null, tag));
        assertThrows(IllegalArgumentException.class, () -> new TargetOutputSpec(Items.AIR, tag));
    }
}
