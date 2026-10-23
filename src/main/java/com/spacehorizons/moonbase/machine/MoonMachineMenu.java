package com.spacehorizons.moonbase.machine;

import com.spacehorizons.content.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu das máquinas da base lunar (um só para os quatro tipos com tela). Só os slots que o tipo usa ({@link MachineKind#slotUsed})
 * existem no menu; o resto fica escondido na tela. O estado (energia, progresso, situação, reservatórios, pressão) chega ao cliente
 * por {@link ContainerData}; a carga dos tanques e galões o cliente lê do próprio item do slot.
 */
public class MoonMachineMenu extends AbstractContainerMenu {
    /** Posições dos três slots do contêiner na tela da fornalha (entrada, auxiliar, saída). */
    public static final int[][] SLOT_POS = {{56, 17}, {56, 53}, {116, 35}};

    private final MachineKind kind;
    private final Container container;
    private final ContainerData data;
    private final int machineSlots;

    /** Lado do cliente: contêiner e dados vazios, preenchidos pelos pacotes do servidor. */
    public MoonMachineMenu(int containerId, Inventory inventory, MachineKind kind) {
        this(containerId, inventory, kind, new SimpleContainer(MachineKind.SLOT_COUNT), new SimpleContainerData(MoonBlockEntity.DATA_COUNT));
    }

    public MoonMachineMenu(int containerId, Inventory inventory, MachineKind kind, Container container, ContainerData data) {
        super(ModMenus.MOON_MACHINE.get(), containerId);
        checkContainerSize(container, MachineKind.SLOT_COUNT);
        checkContainerDataCount(data, MoonBlockEntity.DATA_COUNT);
        this.kind = kind;
        this.container = container;
        this.data = data;
        int used = 0;
        for (int slot = 0; slot < MachineKind.SLOT_COUNT; slot++) {
            if (kind.slotUsed(slot)) {
                final int index = slot;
                addSlot(new Slot(container, slot, SLOT_POS[slot][0], SLOT_POS[slot][1]) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(index, stack);
                    }
                });
                used++;
            }
        }
        this.machineSlots = used;
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public MachineKind kind() {
        return kind;
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
            if (slotIndex < machineSlots) {
                if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = false;
                for (int i = 0; i < machineSlots && !moved; i++) {
                    if (slots.get(i).mayPlace(stack)) {
                        moved = moveItemStackTo(stack, i, i + 1, false);
                    }
                }
                if (!moved) {
                    int inventoryEnd = machineSlots + 27;
                    boolean fromMain = slotIndex < inventoryEnd;
                    if (!(fromMain ? moveItemStackTo(stack, inventoryEnd, slots.size(), false)
                            : moveItemStackTo(stack, machineSlots, inventoryEnd, false))) {
                        return ItemStack.EMPTY;
                    }
                }
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

    /** Item do slot do contêiner (no cliente, o que o servidor sincronizou); vazio se o tipo não usa o slot. */
    public ItemStack getStack(int containerSlot) {
        int menuIndex = 0;
        for (int slot = 0; slot < MachineKind.SLOT_COUNT; slot++) {
            if (kind.slotUsed(slot)) {
                if (slot == containerSlot) {
                    return slots.get(menuIndex).getItem();
                }
                menuIndex++;
            }
        }
        return ItemStack.EMPTY;
    }

    public int getEnergy() {
        return data.get(MoonBlockEntity.DATA_ENERGY);
    }

    public int getProgress() {
        return data.get(MoonBlockEntity.DATA_PROGRESS);
    }

    public int getTotal() {
        return data.get(MoonBlockEntity.DATA_TOTAL);
    }

    public MachineStatus getStatus() {
        return MachineStatus.byId(data.get(MoonBlockEntity.DATA_STATUS));
    }

    public int getOxygen() {
        return data.get(MoonBlockEntity.DATA_OXYGEN);
    }

    public int getFluid() {
        return data.get(MoonBlockEntity.DATA_FLUID);
    }

    public int getPressure() {
        return data.get(MoonBlockEntity.DATA_PRESSURE);
    }
}
