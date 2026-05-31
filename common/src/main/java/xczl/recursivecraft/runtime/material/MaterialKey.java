package xczl.recursivecraft.runtime.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Objects;

public final class MaterialKey {
    private final Item item;
    private final NormalizedMaterialPayload payload;

    public MaterialKey(Item item, NormalizedMaterialPayload payload) {
        this.item = Objects.requireNonNull(item);
        this.payload = Objects.requireNonNull(payload);
    }

    public Item item() { return item; }
    public NormalizedMaterialPayload payload() { return payload; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaterialKey that)) return false;
        return item.equals(that.item) && payload.equals(that.payload);
    }

    @Override
    public int hashCode() { return Objects.hash(item, payload); }

    @Override
    public String toString() {
        return "MaterialKey{item=" + BuiltInRegistries.ITEM.getKey(item) + ", payload=" + payload + "}";
    }
}
