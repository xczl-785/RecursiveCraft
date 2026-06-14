package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMaterialIdentityNormalizerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void normalize_shouldKeepDamageAndNbtFields() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setDamageValue(7);
        CompoundTag customData = new CompoundTag();
        customData.putString("foo", "bar");
        customData.putInt("num", 42);
        stack.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(customData));

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult result = n.normalize(stack);

        assertEquals(NormalizationKind.NORMALIZED, result.kind());
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("damage") && f.value().equals("7")));
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("nbt:foo") && f.value().contains("bar")));
        assertTrue(result.key().payload().fields().stream().anyMatch(f -> f.key().equals("nbt:num") && f.value().contains("42")));
    }

    @Test
    void normalize_shouldKeepNonZeroDamageDistinctFromDefaultDamage() {
        ItemStack undamaged = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(5);

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult undamagedResult = n.normalize(undamaged);
        NormalizationResult damagedResult = n.normalize(damaged);

        assertNotEquals(undamagedResult.key(), damagedResult.key());
        assertTrue(damagedResult.key().payload().fields().stream().anyMatch(f -> f.key().equals("damage") && f.value().equals("5")));
        assertTrue(damagedResult.key().payload().fields().stream().noneMatch(f -> f.key().equals("nbt:Damage")));
    }

    @Test
    void normalize_whenNullOrEmpty_shouldThrow() {
        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        assertThrows(IllegalArgumentException.class, () -> n.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> n.normalize(ItemStack.EMPTY));
    }

    @Test
    void normalize_sameSemanticCustomDataDifferentInsertionOrder_shouldEqual() {
        ItemStack a = new ItemStack(Items.STICK);
        ItemStack b = new ItemStack(Items.STICK);

        CompoundTag nestedA = new CompoundTag();
        nestedA.putInt("b", 2);
        nestedA.putInt("a", 1);
        ListTag listA = new ListTag();
        CompoundTag elemA = new CompoundTag();
        elemA.putString("y", "2");
        elemA.putString("x", "1");
        listA.add(elemA);
        nestedA.put("list", listA);
        CompoundTag customDataA = new CompoundTag();
        customDataA.putString("z", "tail");
        customDataA.put("nested", nestedA);
        a.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(customDataA));

        CompoundTag nestedB = new CompoundTag();
        ListTag listB = new ListTag();
        CompoundTag elemB = new CompoundTag();
        elemB.putString("x", "1");
        elemB.putString("y", "2");
        listB.add(elemB);
        nestedB.put("list", listB);
        nestedB.putInt("a", 1);
        nestedB.putInt("b", 2);
        CompoundTag customDataB = new CompoundTag();
        customDataB.put("nested", nestedB);
        customDataB.putString("z", "tail");
        b.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(customDataB));

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();
        NormalizationResult ra = n.normalize(a);
        NormalizationResult rb = n.normalize(b);

        assertEquals(NormalizationKind.NORMALIZED, ra.kind());
        assertEquals(ra.key(), rb.key());
    }

    @Test
    void normalize_shouldTreatDifferentCustomDataAsDifferentMaterialKeys() {
        ItemStack red = stackWithVariant("red");
        ItemStack blue = stackWithVariant("blue");

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();

        assertNotEquals(n.normalize(red).key(), n.normalize(blue).key());
    }

    @Test
    void normalize_shouldTreatMissingCustomDataAsDifferentFromPresentCustomData() {
        ItemStack plain = new ItemStack(Items.STICK);
        ItemStack tagged = stackWithVariant("red");

        DefaultMaterialIdentityNormalizer n = new DefaultMaterialIdentityNormalizer();

        assertNotEquals(n.normalize(plain).key(), n.normalize(tagged).key());
    }

    private static ItemStack stackWithVariant(String variant) {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag customData = new CompoundTag();
        customData.putString("variant", variant);
        stack.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(customData));
        return stack;
    }
}
