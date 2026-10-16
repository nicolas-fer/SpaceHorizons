package com.spacehorizons.survival.oxygen;

import com.spacehorizons.content.ModDataComponents;

import net.minecraft.world.item.ItemStack;

/**
 * Contrato único de "item que guarda oxigênio" (etapa 2): um item é recipiente se tem o componente
 * {@code spacehorizons:oxygen_capacity}; a carga fica em {@code spacehorizons:oxygen}, em ticks de respiração.
 * O traje gasta daqui (via {@link OxygenSource}) e o compressor enche por aqui. Só mexe em componentes: chame no
 * servidor e deixe a sincronização do inventário levar ao cliente.
 */
public final class OxygenStorage {
    private OxygenStorage() {
    }

    /** true se o item guarda oxigênio (tem capacidade). */
    public static boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ModDataComponents.OXYGEN_CAPACITY.get());
    }

    /** Capacidade, ou 0 se não for recipiente. */
    public static int capacity(ItemStack stack) {
        return isContainer(stack) ? stack.getOrDefault(ModDataComponents.OXYGEN_CAPACITY.get(), 0) : 0;
    }

    /** Carga atual (0 se não for recipiente ou estiver vazio). */
    public static int get(ItemStack stack) {
        return isContainer(stack) ? OxygenAmounts.clamp(stack.getOrDefault(ModDataComponents.OXYGEN.get(), 0), capacity(stack)) : 0;
    }

    /** Define a carga (limitada à capacidade); vazio remove o componente. Não faz nada se não for recipiente. */
    public static void set(ItemStack stack, int amount) {
        if (!isContainer(stack)) {
            return;
        }
        int value = OxygenAmounts.clamp(amount, capacity(stack));
        if (value == 0) {
            stack.remove(ModDataComponents.OXYGEN.get());
        } else {
            stack.set(ModDataComponents.OXYGEN.get(), value);
        }
    }

    /** Coloca até {@code amount}; devolve quanto entrou. */
    public static int insert(ItemStack stack, int amount) {
        int accepted = OxygenAmounts.acceptable(get(stack), capacity(stack), amount);
        if (accepted > 0) {
            set(stack, get(stack) + accepted);
        }
        return accepted;
    }

    /** Tira até {@code amount}; devolve quanto saiu. */
    public static int extract(ItemStack stack, int amount) {
        int taken = OxygenAmounts.extractable(get(stack), capacity(stack), amount);
        if (taken > 0) {
            set(stack, get(stack) - taken);
        }
        return taken;
    }
}
