package com.spacehorizons.survival.fuel;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModMenus;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipePropertySet;

/**
 * Menu da refinaria. Slots: 0 entrada, 1 combustível de fornalha, 2 galão (entra vazio, sai cheio), depois o
 * inventário do jogador. Progresso, queima e tanque chegam ao cliente por {@link ContainerData} (cinco inteiros).
 *
 * A entrada aceita só itens que têm receita de refino: a lista vem de uma {@link RecipePropertySet} própria
 * ({@link #INPUT_SET}, registrada em {@link RefinerySetup}), sincronizada com o cliente.
 */
public class RefineryMenu extends AbstractContainerMenu {
    /** Conjunto dos ingredientes de todas as receitas de refino (para validar o slot de entrada nos dois lados). */
    public static final ResourceKey<RecipePropertySet> INPUT_SET = ResourceKey.create(RecipePropertySet.TYPE_KEY,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "refinery_input"));

    private static final int INV_START = RefineryBlockEntity.SLOT_COUNT;
    private static final int INV_END = INV_START + 27;
    private static final int HOTBAR_END = INV_END + 9;

    private final Container container;
    private final ContainerData data;
    private final RecipePropertySet acceptedInputs;

    /** Lado do cliente: contêiner e dados vazios, preenchidos pelos pacotes do servidor. */
    public RefineryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(RefineryBlockEntity.SLOT_COUNT),
                new SimpleContainerData(RefineryBlockEntity.DATA_COUNT));
    }

    public RefineryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.REFINERY.get(), containerId);
        checkContainerSize(container, RefineryBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, RefineryBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.acceptedInputs = inventory.player.level().recipeAccess().propertySet(INPUT_SET);

        addSlot(new Slot(container, RefineryBlockEntity.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptedInputs.test(stack);
            }
        });
        addSlot(new Slot(container, RefineryBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RefineryBlockEntity.isFurnaceFuel(stack) || stack.is(Items.BUCKET);
            }
        });
        addSlot(new Slot(container, RefineryBlockEntity.SLOT_CANISTER, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FuelCanisterItem;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            if (slotIndex < INV_START) {
                // da refinaria para o inventário
                if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof FuelCanisterItem) {
                if (!moveItemStackTo(stack, RefineryBlockEntity.SLOT_CANISTER, RefineryBlockEntity.SLOT_CANISTER + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (acceptedInputs.test(stack) && moveItemStackTo(stack, RefineryBlockEntity.SLOT_INPUT,
                    RefineryBlockEntity.SLOT_INPUT + 1, false)) {
                // entrada primeiro (carvão é entrada e combustível: a refinaria o quer como entrada)
            } else if (RefineryBlockEntity.isFurnaceFuel(stack)) {
                if (!moveItemStackTo(stack, RefineryBlockEntity.SLOT_FUEL, RefineryBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex < INV_END) {
                if (!moveItemStackTo(stack, INV_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return original;
    }

    // ---- leitura para a tela ----

    /** Progresso do processamento, de 0 a 1. */
    public float getProgress() {
        int total = data.get(RefineryBlockEntity.DATA_PROCESS_TOTAL);
        int current = data.get(RefineryBlockEntity.DATA_PROGRESS);
        return total > 0 && current > 0 ? Mth.clamp((float) current / total, 0.0F, 1.0F) : 0.0F;
    }

    /** Quanto resta da queima atual, de 0 a 1. */
    public float getLitProgress() {
        int total = data.get(RefineryBlockEntity.DATA_LIT_TOTAL);
        if (total == 0) {
            total = 200;
        }
        return Mth.clamp((float) data.get(RefineryBlockEntity.DATA_LIT_TIME) / total, 0.0F, 1.0F);
    }

    public boolean isLit() {
        return data.get(RefineryBlockEntity.DATA_LIT_TIME) > 0;
    }

    /** Combustível no tanque, em mB. */
    public int getTankMb() {
        return data.get(RefineryBlockEntity.DATA_TANK);
    }
}
