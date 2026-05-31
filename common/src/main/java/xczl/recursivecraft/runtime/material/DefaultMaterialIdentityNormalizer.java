package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DefaultMaterialIdentityNormalizer implements MaterialIdentityNormalizer {
    @Override
    public NormalizationResult normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NormalizationResult.unsupported();
        CompoundTag tag = stack.getTag();
        List<CanonicalField> fields = new ArrayList<>();
        if (tag != null) {
            for (String k : tag.getAllKeys()) {
                fields.add(new CanonicalField("nbt:" + k, String.valueOf(tag.get(k))));
            }
        }
        fields.add(new CanonicalField("damage", Integer.toString(stack.getDamageValue())));
        NormalizedMaterialPayload payload = new NormalizedMaterialPayload("v1", fields);
        return NormalizationResult.normalized(new MaterialKey(stack.getItem(), payload));
    }
}
