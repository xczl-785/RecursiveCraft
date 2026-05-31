package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMaterialIdentityNormalizerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void normalize_shouldKeepDamageAndNbtFields() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setDamageValue(7);
        stack.getOrCreateTag().putString("foo", "bar");
        stack.getOrCreateTag().putInt("num", 42);

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult result = n.normalize(stack);

        assertEquals(NormalizationKind.NORMALIZED, result.kind());
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("damage") && f.value().equals("7")));
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("nbt:foo")));
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("nbt:num")));
    }

    @Test
    void normalize_whenUnsupportedTag_shouldReturnUnsupported() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.getOrCreateTag().put("bad", EndTag.INSTANCE);

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult result = n.normalize(stack);

        assertEquals(NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS, result.kind());
    }

    @Test
    void normalize_whenNullOrEmpty_shouldThrow() {
        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        assertThrows(IllegalArgumentException.class, () -> n.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> n.normalize(ItemStack.EMPTY));
    }

    @Test
    void normalize_sameSemanticTagDifferentInsertionOrder_shouldEqual() {
        ItemStack a = new ItemStack(Items.STICK);
        ItemStack b = new ItemStack(Items.STICK);

        a.getOrCreateTag().putString("z", "tail");
        CompoundTag nestedA = new CompoundTag();
        nestedA.putInt("b", 2);
        nestedA.putInt("a", 1);
        ListTag listA = new ListTag();
        CompoundTag elemA = new CompoundTag();
        elemA.putString("y", "2");
        elemA.putString("x", "1");
        listA.add(elemA);
        nestedA.put("list", listA);
        a.getOrCreateTag().put("nested", nestedA);

        CompoundTag nestedB = new CompoundTag();
        ListTag listB = new ListTag();
        CompoundTag elemB = new CompoundTag();
        elemB.putString("x", "1");
        elemB.putString("y", "2");
        listB.add(elemB);
        nestedB.put("list", listB);
        nestedB.putInt("a", 1);
        nestedB.putInt("b", 2);
        b.getOrCreateTag().put("nested", nestedB);
        b.getOrCreateTag().putString("z", "tail");

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult ra = n.normalize(a);
        NormalizationResult rb = n.normalize(b);

        assertEquals(NormalizationKind.NORMALIZED, ra.kind());
        assertEquals(ra.key(), rb.key());
    }
}
