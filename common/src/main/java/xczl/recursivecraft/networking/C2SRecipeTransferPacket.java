package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class C2SRecipeTransferPacket {
    private final List<TransferOperation> transferOperations;
    private final List<Integer> craftingSlots;
    private final List<Integer> inventorySlots;
    private final boolean maxTransfer;
    private final boolean requireCompleteSets;

    public C2SRecipeTransferPacket(List<TransferOperation> transferOperations,
                                   List<Integer> craftingSlots,
                                   List<Integer> inventorySlots,
                                   boolean maxTransfer,
                                   boolean requireCompleteSets) {
        this.transferOperations = List.copyOf(transferOperations);
        this.craftingSlots = List.copyOf(craftingSlots);
        this.inventorySlots = List.copyOf(inventorySlots);
        this.maxTransfer = maxTransfer;
        this.requireCompleteSets = requireCompleteSets;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(transferOperations.size());
        for (TransferOperation operation : transferOperations) {
            buf.writeVarInt(operation.inventorySlotId());
            buf.writeVarInt(operation.craftingSlotId());
        }

        buf.writeVarInt(craftingSlots.size());
        for (Integer slotId : craftingSlots) {
            buf.writeVarInt(slotId);
        }

        buf.writeVarInt(inventorySlots.size());
        for (Integer slotId : inventorySlots) {
            buf.writeVarInt(slotId);
        }

        buf.writeBoolean(maxTransfer);
        buf.writeBoolean(requireCompleteSets);
    }

    public static C2SRecipeTransferPacket decode(FriendlyByteBuf buf) {
        int transferCount = buf.readVarInt();
        List<TransferOperation> transferOperations = new ArrayList<>(transferCount);
        for (int i = 0; i < transferCount; i++) {
            transferOperations.add(new TransferOperation(buf.readVarInt(), buf.readVarInt()));
        }

        int craftingCount = buf.readVarInt();
        List<Integer> craftingSlots = new ArrayList<>(craftingCount);
        for (int i = 0; i < craftingCount; i++) {
            craftingSlots.add(buf.readVarInt());
        }

        int inventoryCount = buf.readVarInt();
        List<Integer> inventorySlots = new ArrayList<>(inventoryCount);
        for (int i = 0; i < inventoryCount; i++) {
            inventorySlots.add(buf.readVarInt());
        }

        boolean maxTransfer = buf.readBoolean();
        boolean requireCompleteSets = buf.readBoolean();
        return new C2SRecipeTransferPacket(transferOperations, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets);
    }

    public static void handle(C2SRecipeTransferPacket pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();
        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }

            AbstractContainerMenu container = player.containerMenu;
            List<Slot> craftingSlots = pkt.craftingSlots.stream().map(container::getSlot).toList();
            List<Slot> inventorySlots = pkt.inventorySlots.stream().map(container::getSlot).toList();
            StandardRecipeTransferHandlerServer.setItems(
                    player,
                    pkt.transferOperations,
                    craftingSlots,
                    inventorySlots,
                    pkt.maxTransfer,
                    pkt.requireCompleteSets
            );
        });
    }

    public record TransferOperation(int inventorySlotId, int craftingSlotId) {
        public Slot inventorySlot(AbstractContainerMenu container) {
            return container.getSlot(inventorySlotId);
        }

        public Slot craftingSlot(AbstractContainerMenu container) {
            return container.getSlot(craftingSlotId);
        }
    }

    static final class StandardRecipeTransferHandlerServer {
        private StandardRecipeTransferHandlerServer() {
        }

        static void setItems(Player player,
                             List<TransferOperation> transferOperations,
                             List<Slot> craftingSlots,
                             List<Slot> inventorySlots,
                             boolean maxTransfer,
                             boolean requireCompleteSets) {
            if (!validateSlots(player, transferOperations, craftingSlots, inventorySlots)) {
                return;
            }

            Map<Slot, ItemStackWithSlotHint> recipeSlotToRequiredItemStack = calculateRequiredStacks(transferOperations, player);
            if (recipeSlotToRequiredItemStack == null) {
                return;
            }

            boolean transferAsCompleteSets = requireCompleteSets || !maxTransfer;
            Map<Slot, ItemStack> recipeSlotToTakenStacks = takeItemsFromInventory(
                    player,
                    recipeSlotToRequiredItemStack,
                    craftingSlots,
                    inventorySlots,
                    transferAsCompleteSets,
                    maxTransfer
            );
            if (recipeSlotToTakenStacks.isEmpty()) {
                return;
            }

            List<ItemStack> clearedCraftingItems = clearCraftingGrid(craftingSlots, player);
            List<ItemStack> remainderItems = putItemsIntoCraftingGrid(recipeSlotToTakenStacks, requireCompleteSets);
            stowItems(player, inventorySlots, clearedCraftingItems);
            stowItems(player, inventorySlots, remainderItems);
            player.containerMenu.broadcastChanges();
        }

        private static boolean validateSlots(Player player,
                                             Collection<TransferOperation> transferOperations,
                                             Collection<Slot> craftingSlots,
                                             Collection<Slot> inventorySlots) {
            Set<Integer> inventorySlotIndexes = inventorySlots.stream().map(s -> s.index).collect(Collectors.toSet());
            Set<Integer> craftingSlotIndexes = craftingSlots.stream().map(s -> s.index).collect(Collectors.toSet());

            boolean invalidCraftSlot = transferOperations.stream()
                    .map(op -> op.craftingSlot(player.containerMenu).index)
                    .anyMatch(slotId -> !craftingSlotIndexes.contains(slotId));
            if (invalidCraftSlot) {
                return false;
            }

            boolean invalidSourceSlot = transferOperations.stream()
                    .map(op -> op.inventorySlot(player.containerMenu).index)
                    .anyMatch(slotId -> !inventorySlotIndexes.contains(slotId) && !craftingSlotIndexes.contains(slotId));
            if (invalidSourceSlot) {
                return false;
            }

            Set<Integer> overlapping = inventorySlotIndexes.stream()
                    .filter(craftingSlotIndexes::contains)
                    .collect(Collectors.toSet());
            if (!overlapping.isEmpty()) {
                return false;
            }

            return Stream.concat(craftingSlots.stream(), inventorySlots.stream()).noneMatch(Slot::isFake);
        }

        private static Map<Slot, ItemStackWithSlotHint> calculateRequiredStacks(List<TransferOperation> transferOperations, Player player) {
            Map<Slot, ItemStackWithSlotHint> recipeSlotToRequired = new HashMap<>(transferOperations.size());
            for (TransferOperation transferOperation : transferOperations) {
                Slot recipeSlot = transferOperation.craftingSlot(player.containerMenu);
                Slot inventorySlot = transferOperation.inventorySlot(player.containerMenu);
                if (!inventorySlot.allowModification(player)) {
                    return null;
                }
                ItemStack slotStack = inventorySlot.getItem();
                if (slotStack.isEmpty()) {
                    return null;
                }
                ItemStack stack = slotStack.copy();
                stack.setCount(1);
                recipeSlotToRequired.put(recipeSlot, new ItemStackWithSlotHint(inventorySlot, stack));
            }
            return recipeSlotToRequired;
        }

        private static Map<Slot, ItemStack> takeItemsFromInventory(Player player,
                                                                   Map<Slot, ItemStackWithSlotHint> recipeSlotToRequiredItemStack,
                                                                   List<Slot> craftingSlots,
                                                                   List<Slot> inventorySlots,
                                                                   boolean transferAsCompleteSets,
                                                                   boolean maxTransfer) {
            if (!maxTransfer) {
                return removeOneSetOfItemsFromInventory(
                        player,
                        recipeSlotToRequiredItemStack,
                        craftingSlots,
                        inventorySlots,
                        transferAsCompleteSets
                );
            }

            Map<Slot, ItemStack> recipeSlotToResult = new HashMap<>(recipeSlotToRequiredItemStack.size());
            while (true) {
                Map<Slot, ItemStack> foundItemsInSet = removeOneSetOfItemsFromInventory(
                        player,
                        recipeSlotToRequiredItemStack,
                        craftingSlots,
                        inventorySlots,
                        transferAsCompleteSets
                );
                if (foundItemsInSet.isEmpty()) {
                    break;
                }

                Set<Slot> fullSlots = merge(recipeSlotToResult, foundItemsInSet);
                for (Slot fullSlot : fullSlots) {
                    recipeSlotToRequiredItemStack.remove(fullSlot);
                }
            }

            return recipeSlotToResult;
        }

        private static Map<Slot, ItemStack> removeOneSetOfItemsFromInventory(Player player,
                                                                             Map<Slot, ItemStackWithSlotHint> recipeSlotToRequiredItemStack,
                                                                             List<Slot> craftingSlots,
                                                                             List<Slot> inventorySlots,
                                                                             boolean transferAsCompleteSets) {
            Map<Slot, ItemStack> originalSlotContents = transferAsCompleteSets ? new HashMap<>() : null;
            Map<Slot, ItemStack> foundItemsInSet = new HashMap<>(recipeSlotToRequiredItemStack.size());

            for (Map.Entry<Slot, ItemStackWithSlotHint> entry : recipeSlotToRequiredItemStack.entrySet()) {
                Slot recipeSlot = entry.getKey();
                ItemStack requiredStack = entry.getValue().stack;
                Slot hint = entry.getValue().hint;

                Slot sourceSlot = getSlotWithStack(player, requiredStack, craftingSlots, inventorySlots, hint).orElse(null);
                if (sourceSlot != null) {
                    if (originalSlotContents != null && !originalSlotContents.containsKey(sourceSlot)) {
                        originalSlotContents.put(sourceSlot, sourceSlot.getItem().copy());
                    }
                    ItemStack removedItemStack = sourceSlot.safeTake(1, Integer.MAX_VALUE, player);
                    foundItemsInSet.put(recipeSlot, removedItemStack);
                } else if (transferAsCompleteSets) {
                    for (Map.Entry<Slot, ItemStack> slotEntry : originalSlotContents.entrySet()) {
                        slotEntry.getKey().set(slotEntry.getValue());
                    }
                    return Map.of();
                }
            }

            return foundItemsInSet;
        }

        private static Set<Slot> merge(Map<Slot, ItemStack> result, Map<Slot, ItemStack> addition) {
            Set<Slot> fullSlots = new HashSet<>();
            addition.forEach((slot, itemStack) -> {
                ItemStack resultItemStack = result.get(slot);
                if (resultItemStack == null) {
                    result.put(slot, itemStack);
                    resultItemStack = itemStack;
                } else {
                    resultItemStack.grow(itemStack.getCount());
                }
                if (resultItemStack.getCount() == slot.getMaxStackSize(resultItemStack)) {
                    fullSlots.add(slot);
                }
            });
            return fullSlots;
        }

        private static List<ItemStack> clearCraftingGrid(List<Slot> craftingSlots, Player player) {
            List<ItemStack> clearedCraftingItems = new ArrayList<>();
            for (Slot craftingSlot : craftingSlots) {
                if (!craftingSlot.mayPickup(player)) {
                    continue;
                }
                ItemStack item = craftingSlot.getItem();
                if (!item.isEmpty() && craftingSlot.mayPlace(item)) {
                    ItemStack craftingItem = craftingSlot.safeTake(Integer.MAX_VALUE, Integer.MAX_VALUE, player);
                    clearedCraftingItems.add(craftingItem);
                }
            }
            return clearedCraftingItems;
        }

        private static List<ItemStack> putItemsIntoCraftingGrid(Map<Slot, ItemStack> recipeSlotToTakenStacks,
                                                                boolean requireCompleteSets) {
            int slotStackLimit = getSlotStackLimit(recipeSlotToTakenStacks, requireCompleteSets);
            List<ItemStack> remainderItems = new ArrayList<>();
            recipeSlotToTakenStacks.forEach((slot, stack) -> {
                ItemStack remainder = slot.safeInsert(stack, slotStackLimit);
                if (!remainder.isEmpty()) {
                    remainderItems.add(remainder);
                }
            });
            return remainderItems;
        }

        private static int getSlotStackLimit(Map<Slot, ItemStack> recipeSlotToTakenStacks, boolean requireCompleteSets) {
            if (!requireCompleteSets) {
                return Integer.MAX_VALUE;
            }
            return recipeSlotToTakenStacks.entrySet().stream()
                    .mapToInt(entry -> entry.getKey().mayPlace(entry.getValue()) ? entry.getKey().getMaxStackSize(entry.getValue()) : Integer.MAX_VALUE)
                    .min()
                    .orElse(Integer.MAX_VALUE);
        }

        private static Optional<Slot> getSlotWithStack(Player player, ItemStack stack, List<Slot> craftingSlots, List<Slot> inventorySlots, Slot hint) {
            return getSlotWithStack(player, craftingSlots, stack)
                    .or(() -> getValidatedHintSlot(player, stack, hint))
                    .or(() -> getSlotWithStack(player, inventorySlots, stack));
        }

        private static Optional<Slot> getValidatedHintSlot(Player player, ItemStack stack, Slot hint) {
            return isValidAndMatches(player, hint, stack) ? Optional.of(hint) : Optional.empty();
        }

        private static Optional<Slot> getSlotWithStack(Player player, Collection<Slot> slots, ItemStack stack) {
            return slots.stream().filter(slot -> isValidAndMatches(player, slot, stack)).findFirst();
        }

        private static boolean isValidAndMatches(Player player, Slot slot, ItemStack stack) {
            ItemStack containedStack = slot.getItem();
            return ItemStack.isSameItemSameComponents(stack, containedStack) && slot.allowModification(player);
        }

        private static void stowItems(Player player, List<Slot> inventorySlots, List<ItemStack> itemStacks) {
            for (ItemStack itemStack : itemStacks) {
                ItemStack remainder = stowItem(player, inventorySlots, itemStack);
                if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }

        private static ItemStack stowItem(Player player, Collection<Slot> slots, ItemStack stack) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack remainder = stack.copy();
            for (Slot slot : slots) {
                if (!slot.mayPickup(player)) {
                    continue;
                }
                ItemStack inventoryStack = slot.getItem();
                if (!inventoryStack.isEmpty() && inventoryStack.isStackable()) {
                    remainder = slot.safeInsert(remainder);
                    if (remainder.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            for (Slot slot : slots) {
                if (slot.getItem().isEmpty()) {
                    remainder = slot.safeInsert(remainder);
                    if (remainder.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            return remainder;
        }

        private record ItemStackWithSlotHint(Slot hint, ItemStack stack) {
        }
    }
}
