package xczl.recursivecraft.runtime.material;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemStackComponentSupport {
    private static final String REMOVED_COMPONENT_MARKER = "<removed>";

    private ItemStackComponentSupport() {}

    public static String componentKey(ItemStack stack) {
        return componentKey(stack.getComponentsPatch());
    }

    public static String componentKey(DataComponentPatch patch) {
        if (patch.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>(patch.size());
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            String componentId = id != null ? id.toString() : entry.getKey().toString();
            Object value = entry.getValue().isPresent() ? entry.getValue().get() : REMOVED_COMPONENT_MARKER;
            entries.add(componentId + "=" + value);
        }
        entries.sort(String::compareTo);
        return String.join(",", entries);
    }

    public static @Nullable CompoundTag copyCustomData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? null : customData.copyTag();
    }

    public static @Nullable CompoundTag copyCustomData(DataComponentPatch patch) {
        Optional<? extends CustomData> customData = patch.get(DataComponents.CUSTOM_DATA);
        return customData.isPresent() ? customData.get().copyTag() : null;
    }

    public static DataComponentPatch patchFromCustomData(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DataComponentPatch.EMPTY;
        }
        return DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()))
                .build();
    }
}
