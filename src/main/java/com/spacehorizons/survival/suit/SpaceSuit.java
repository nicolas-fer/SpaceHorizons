package com.spacehorizons.survival.suit;

import java.util.EnumMap;
import java.util.Map;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Material de armadura do traje espacial (no 26.3 o material é só dados: durabilidade, defesa por peça, som, item de
 * reparo e o id do "equipment asset", o JSON {@code assets/spacehorizons/equipment/space_suit.json} que aponta as
 * texturas das camadas vestidas).
 *
 * Proteção modesta de propósito: o traje existe para manter o jogador vivo no vácuo (ar e frio), não para o combate.
 * Defesa total de 9 pontos (capacete 1, peitoral 4, calças 3, botas 1): acima do couro (7), um pouco abaixo do cobre
 * (10) e bem abaixo do ferro (15), mesmo custando mais caro (aço, circuito e vidro): o preço paga a função, não a
 * blindagem. Sem resistência a nocaute nem dureza. Durabilidade 20 (ferro: 15): a vida de quem está no vácuo depende
 * dele, então ele aguenta mais; repara-se com placa de aço (bigorna, tag {@code spacehorizons:repairs_space_suit}).
 * Encantabilidade 9, a do ferro: nada de especial.
 */
public final class SpaceSuit {
    /** Id do equipment asset: assets/spacehorizons/equipment/space_suit.json. */
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space_suit"));

    /** Itens que consertam o traje na bigorna: placa de aço (data/spacehorizons/tags/item/repairs_space_suit.json). */
    public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "repairs_space_suit"));

    /** Multiplicador de durabilidade (capacete 220, peitoral 320, calças 300, botas 260). */
    private static final int DURABILITY_FACTOR = 20;
    private static final int ENCHANTMENT_VALUE = 9;

    /**
     * O material (criado uma vez, na primeira chamada: usa os sons do jogo, que só existem depois do bootstrap, por
     * isso não é um campo estático desta classe, que os testes carregam sem o jogo).
     */
    public static ArmorMaterial material() {
        return Lazy.MATERIAL;
    }

    private static final class Lazy {
        static final ArmorMaterial MATERIAL = create();

        private static ArmorMaterial create() {
            Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
            defense.put(ArmorType.HELMET, 1);
            defense.put(ArmorType.CHESTPLATE, 4);
            defense.put(ArmorType.LEGGINGS, 3);
            defense.put(ArmorType.BOOTS, 1);
            return new ArmorMaterial(DURABILITY_FACTOR, defense, ENCHANTMENT_VALUE, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, REPAIRS,
                    ASSET);
        }
    }

    private SpaceSuit() {
    }
}
