package xczl.recursivecraft.runtime.material;

import net.minecraft.world.item.ItemStack;

public interface MaterialIdentityNormalizer {
    NormalizationResult normalize(ItemStack stack);
}
