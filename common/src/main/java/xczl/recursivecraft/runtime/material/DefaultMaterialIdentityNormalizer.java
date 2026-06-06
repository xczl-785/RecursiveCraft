package xczl.recursivecraft.runtime.material;

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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DefaultMaterialIdentityNormalizer implements MaterialIdentityNormalizer {
    @Override
    public NormalizationResult normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("ItemStack must be non-null and non-empty");
        }
        CompoundTag tag = stack.getTag();
        List<CanonicalField> fields = new ArrayList<>();
        if (tag != null) {
            for (String k : tag.getAllKeys()) {
                if ("Damage".equals(k)) {
                    continue;
                }
                String canonical = canonicalizeTag(tag.get(k));
                if (canonical == null) return NormalizationResult.unsupported();
                fields.add(new CanonicalField("nbt:" + k, canonical));
            }
        }
        fields.add(new CanonicalField("damage", Integer.toString(stack.getDamageValue())));
        NormalizedMaterialPayload payload = new NormalizedMaterialPayload("v1", fields);
        return NormalizationResult.normalized(new MaterialKey(stack.getItem(), payload));
    }

    private String canonicalizeTag(Tag tag) {
        if (tag == null || tag instanceof EndTag) return null;
        byte id = tag.getId();
        if (tag instanceof NumericTag n) {
            if (tag instanceof FloatTag || tag instanceof DoubleTag) {
                return id + ":" + Double.toString(n.getAsDouble());
            }
            return id + ":" + Long.toString(n.getAsLong());
        }
        if (tag instanceof StringTag s) return id + ":" + s.getAsString();
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
            return id + "[" + list.getElementType() + "]" + vals;
        }
        if (tag instanceof CompoundTag c) {
            List<String> entries = new ArrayList<>();
            for (String key : c.getAllKeys()) {
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
