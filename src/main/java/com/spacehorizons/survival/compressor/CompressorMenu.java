package com.spacehorizons.survival.compressor;

import com.spacehorizons.content.ModMenus;
import com.spacehorizons.survival.oxygen.OxygenStorage;

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

/**
 * Menu do compressor de oxigênio. Slots: 0 tanque (entra vazio, sai cheio), 1 combustível de fornalha, depois o
 * inventário do jogador. A queima e a situação ({@link CompressorLogic.Status}) chegam ao cliente por
 * {@link ContainerData} (três inteiros); a carga do tanque o cliente lê do próprio item do slot (os data components
 * do item já são sincronizados com o slot).
 */
public class CompressorMenu extends AbstractContainerMenu {
    private static final int INV_START = OxygenCompressorBlockEntity.SLOT_COUNT;
    private static final int INV_END = INV_START + 27;
    private static final int HOTBAR_END = INV_END + 9;

    private final Container container;
    private final ContainerData data;

    /** Lado do cliente: contêiner e dados vazios, preenchidos pelos pacotes do servidor. */
    public CompressorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(OxygenCompressorBlockEntity.SLOT_COUNT),
                new SimpleContainerData(OxygenCompressorBlockEntity.DATA_COUNT));
    }

    public CompressorMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.OXYGEN_COMPRESSOR.get(), containerId);
        checkContainerSize(container, OxygenCompressorBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, OxygenCompressorBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;

        // o tanque ocupa o lugar do "resultado" da fornalha (o fundo da tela é o da fornalha); o do combustível é igual
        addSlot(new Slot(container, OxygenCompressorBlockEntity.SLOT_TANK, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return OxygenStorage.isContainer(stack);
            }
        });
        addSlot(new Slot(container, OxygenCompressorBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return OxygenCompressorBlockEntity.isFurnaceFuel(stack) || stack.is(Items.BUCKET);
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
                // do compressor para o inventário
                if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (OxygenStorage.isContainer(stack)) {
                if (!moveItemStackTo(stack, OxygenCompressorBlockEntity.SLOT_TANK, OxygenCompressorBlockEntity.SLOT_TANK + 1,
                        false)) {
                    return ItemStack.EMPTY;
                }
            } else if (OxygenCompressorBlockEntity.isFurnaceFuel(stack)) {
                if (!moveItemStackTo(stack, OxygenCompressorBlockEntity.SLOT_FUEL, OxygenCompressorBlockEntity.SLOT_FUEL + 1,
                        false)) {
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

    /** Item do slot do tanque (no cliente, o que o servidor sincronizou). */
    public ItemStack getTank() {
        return slots.get(OxygenCompressorBlockEntity.SLOT_TANK).getItem();
    }

    /** Quanto o tanque está cheio, de 0 a 1 (0 sem tanque). */
    public float getTankFraction() {
        ItemStack tank = getTank();
        int capacity = OxygenStorage.capacity(tank);
        return capacity > 0 ? Mth.clamp((float) OxygenStorage.get(tank) / capacity, 0.0F, 1.0F) : 0.0F;
    }

    /** Quanto resta da queima atual, de 0 a 1. */
    public float getLitProgress() {
        int total = data.get(OxygenCompressorBlockEntity.DATA_LIT_TOTAL);
        if (total == 0) {
            total = 200;
        }
        return Mth.clamp((float) data.get(OxygenCompressorBlockEntity.DATA_LIT_TIME) / total, 0.0F, 1.0F);
    }

    /** Há combustível queimando (a chama da tela). */
    public boolean hasFlame() {
        return data.get(OxygenCompressorBlockEntity.DATA_LIT_TIME) > 0;
    }

    public CompressorLogic.Status getStatus() {
        return CompressorLogic.Status.byId(data.get(OxygenCompressorBlockEntity.DATA_STATUS));
    }
}
