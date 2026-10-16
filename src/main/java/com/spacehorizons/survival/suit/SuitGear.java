package com.spacehorizons.survival.suit;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.content.ModItems;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Liga as regras puras ({@link SuitPieces}, {@link TankMath}) ao jogo de verdade: que peças do traje o jogador veste
 * e que recipientes de oxigênio ele carrega. Serve ao servidor (consumo, frio) e ao cliente (HUD): o equipamento e as
 * cargas ({@code spacehorizons:oxygen}) chegam ao cliente pela sincronização normal do inventário.
 */
public final class SuitGear {
    private SuitGear() {
    }

    /** Peças do traje vestidas (cada uma precisa ser a peça certa no espaço certo: o capacete só vale na cabeça). */
    public static SuitPieces piecesOf(LivingEntity entity) {
        return new SuitPieces(
                entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SPACE_HELMET.get()),
                entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SPACE_CHESTPLATE.get()),
                entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SPACE_LEGGINGS.get()),
                entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.SPACE_BOOTS.get()));
    }

    /**
     * Os recipientes de oxigênio que o jogador carrega, na ordem de varredura (barra de atalhos, mochila, mão
     * secundária). São as próprias pilhas do inventário (não cópias): quem mexe nelas muda o inventário de verdade.
     */
    public static List<ItemStack> containers(Player player) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (OxygenStorage.isContainer(stack)) {
                found.add(stack);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (OxygenStorage.isContainer(offhand)) {
            found.add(offhand);
        }
        return found;
    }

    /** Carga total de oxigênio dos recipientes do inventário, em ticks de respiração (para o HUD). */
    public static int totalOxygen(Player player) {
        return TankMath.total(containers(player).stream().mapToInt(OxygenStorage::get).toArray());
    }

    /**
     * Tira até {@code wanted} de oxigênio dos recipientes (o mais vazio primeiro, ver {@link TankMath#plan}) e devolve
     * quanto realmente saiu. Só no servidor. O que sai dos recipientes é exatamente o que se devolve: nada se perde
     * nem se duplica.
     */
    public static int drawOxygen(Player player, int wanted) {
        List<ItemStack> containers = containers(player);
        int[] plan = TankMath.plan(containers.stream().mapToInt(OxygenStorage::get).toArray(), wanted);
        int taken = 0;
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] > 0) {
                taken += OxygenStorage.extract(containers.get(i), plan[i]);
            }
        }
        return taken;
    }
}
