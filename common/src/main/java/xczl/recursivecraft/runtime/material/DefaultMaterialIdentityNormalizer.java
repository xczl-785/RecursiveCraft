package xczl.recursivecraft.runtime.material;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultMaterialIdentityNormalizer implements MaterialIdentityNormalizer {
    @Override
    public NormalizationResult normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("ItemStack must be non-null and non-empty");
        }
        List<CanonicalField> fields = new ArrayList<>();
        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                if (entry.getKey() == DataComponents.DAMAGE) {
                    continue;
                }
                if (entry.getKey() == DataComponents.CUSTOM_DATA) {
                    if (entry.getValue().isEmpty()) {
                        continue;
                    }
                    CustomData customData = (CustomData) entry.getValue().get();
                    if (!addCustomDataFields(fields, customData.copyTag())) {
                        return NormalizationResult.unsupported();
                    }
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
                String componentId = id != null ? id.toString() : entry.getKey().toString();
                Object value = entry.getValue().isPresent() ? entry.getValue().get() : "<removed>";
                fields.add(new CanonicalField("component:" + componentId, value.toString()));
            }
        }
        fields.add(new CanonicalField("damage", Integer.toString(stack.getDamageValue())));
        NormalizedMaterialPayload payload = new NormalizedMaterialPayload("v1", fields);
        return NormalizationResult.normalized(new MaterialKey(stack.getItem(), payload));
    }

    private boolean addCustomDataFields(List<CanonicalField> fields, CompoundTag tag) {
        for (String key : tag.keySet()) {
            String canonical = canonicalizeTag(tag.get(key));
            if (canonical == null) {
                return false;
            }
            fields.add(new CanonicalField("nbt:" + key, canonical));
        }
        return true;
    }

    private String canonicalizeTag(Tag tag) {
        if (tag == null || tag instanceof EndTag) return null;
        byte id = tag.getId();
        if (tag instanceof NumericTag n) {
            if (tag instanceof FloatTag || tag instanceof DoubleTag) {
                return id + ":" + Double.toString(n.doubleValue());
            }
            return id + ":" + Long.toString(n.longValue());
        }
        if (tag instanceof StringTag s) return id + ":" + s.value();
        if (tag instanceof ByteArrayTag b) return id + ":" + java.util.Arrays.toString(b.getAsByteArray());
        if (tag instanceof IntArrayTag i) return id + ":" + java.util.Arrays.toString(i.getAsIntArray());
        if (tag instanceof LongArrayTag l) return id + ":" + java.util.Arrays.toString(l.getAsLongArray());
        if (tag instanceof ListTag list) {
            List<String> vals = new ArrayList<>(list.size());
            for (Tag element : list) {
                String child = canonicalizeTag(element);
                if (child == null) return null;
                vals.add(child);
            }
            return id + vals.toString();
        }
        if (tag instanceof CompoundTag c) {
            List<String> entries = new ArrayList<>();
            for (String key : c.keySet()) {
                String child = canonicalizeTag(c.get(key));
                if (child == null) return null;
                entries.add(key + "=" + child);
            }
            entries.sort(String::compareTo);
            return id + entries.toString();
        }
        return null;
    }
}
