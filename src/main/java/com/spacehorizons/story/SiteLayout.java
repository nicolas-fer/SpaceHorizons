package com.spacehorizons.story;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * O desenho de um lugar da trama como DADO puro (sem registros do Minecraft, então dá para testar): uma lista de operações em coordenadas locais
 * {@code (x, y, z)} com {@code x} em {@code [0, width)}, {@code z} em {@code [0, depth)} e {@code y = 0} a laje do piso (o chão do terreno fica nela;
 * o interior começa em {@code y = 1}). Blocos são strings de estado ({@code namespace:bloco[prop=valor,...]}) que {@link StoryStamper} resolve na hora de
 * colocar. As operações valem na ordem: as de depois sobrescrevem as de antes.
 *
 * <p>O "desgaste" das ruínas (paredes rachadas, vidro quebrado, buracos no teto) é um sorteio por posição que depende só do {@code salt} da operação e da
 * posição local ({@link #hash}), então o mesmo lugar sai idêntico em todo chunk e em toda geração: a estrutura pode ser desenhada um pedaço por chunk.
 *
 * <p>Fundação: abaixo da laje, cada coluna da pegada é preenchida com {@link #foundation()} enquanto houver ar, líquido ou planta (até
 * {@link #FOUNDATION_DEPTH} blocos), para a laje não ficar flutuando em terreno irregular; acima da laje, até {@link #clearHeight()}, o que não é ar é
 * removido antes do desenho (árvores, rochas).
 */
public final class SiteLayout {
    /** Até onde a fundação desce abaixo da laje. */
    public static final int FOUNDATION_DEPTH = 24;

    /** Uma operação do desenho. */
    public sealed interface Op permits Box, Mix, Chest, Terminal {
    }

    /** Preenche a caixa (inclusive nos dois cantos) com um estado. */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1, String state) implements Op {
    }

    /** Preenche a caixa com {@code a}, trocando por {@code b} em {@code percentB}% das posições (sorteio fixo pelo {@code salt} e pela posição). */
    public record Mix(int x0, int y0, int z0, int x1, int y1, int z1, String a, String b, int percentB, int salt) implements Op {
    }

    /** Um baú com a tabela de saque {@code spacehorizons:<loot>} (os diários); {@code facing} é {@code north|south|east|west}. */
    public record Chest(int x, int y, int z, String facing, String loot) implements Op {
    }

    /** O terminal de dados ({@code record} é {@code aurora}, {@code selene} ou {@code vigia}); {@code facing} para onde a tela olha. */
    public record Terminal(int x, int y, int z, String facing, String record) implements Op {
    }

    private final String id;
    private final int width;
    private final int depth;
    private final int height;
    private final String foundation;
    private final List<Op> ops = new ArrayList<>();

    public SiteLayout(String id, int width, int depth, int height, String foundation) {
        this.id = id;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.foundation = foundation;
    }

    public SiteLayout box(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        return this.add(new Box(x0, y0, z0, x1, y1, z1, state));
    }

    public SiteLayout air(int x0, int y0, int z0, int x1, int y1, int z1) {
        return this.add(new Box(x0, y0, z0, x1, y1, z1, "minecraft:air"));
    }

    public SiteLayout set(int x, int y, int z, String state) {
        return this.add(new Box(x, y, z, x, y, z, state));
    }

    public SiteLayout mix(int x0, int y0, int z0, int x1, int y1, int z1, String a, String b, int percentB, int salt) {
        return this.add(new Mix(x0, y0, z0, x1, y1, z1, a, b, percentB, salt));
    }

    public SiteLayout chest(int x, int y, int z, String facing, String loot) {
        return this.add(new Chest(x, y, z, facing, loot));
    }

    public SiteLayout terminal(int x, int y, int z, String facing, String record) {
        return this.add(new Terminal(x, y, z, facing, record));
    }

    private SiteLayout add(Op op) {
        this.ops.add(op);
        return this;
    }

    public String id() {
        return this.id;
    }

    public int width() {
        return this.width;
    }

    public int depth() {
        return this.depth;
    }

    /** A altura da caixa do lugar (da laje, em y = 0, ao ponto mais alto). */
    public int height() {
        return this.height;
    }

    /** Até onde acima da laje o que não é ar é removido antes do desenho. */
    public int clearHeight() {
        return this.height;
    }

    public String foundation() {
        return this.foundation;
    }

    public List<Op> ops() {
        return List.copyOf(this.ops);
    }

    /** Todas as strings de estado de bloco usadas (a fundação inclusive), para conferir que existem no jogo. */
    public Set<String> states() {
        Set<String> out = new LinkedHashSet<>();
        out.add(this.foundation);
        for (Op op : this.ops) {
            switch (op) {
                case Box b -> out.add(b.state());
                case Mix m -> {
                    out.add(m.a());
                    out.add(m.b());
                }
                case Chest c -> out.add("minecraft:chest[facing=" + c.facing() + "]");
                case Terminal t -> out.add("spacehorizons:data_terminal[facing=" + t.facing() + ",record=" + t.record() + "]");
            }
        }
        return out;
    }

    /** Os terminais do lugar, na ordem do desenho. */
    public List<Terminal> terminals() {
        return this.ops.stream().filter(Terminal.class::isInstance).map(Terminal.class::cast).toList();
    }

    public List<Chest> chests() {
        return this.ops.stream().filter(Chest.class::isInstance).map(Chest.class::cast).toList();
    }

    /** Sorteio fixo de 0 a 99 pela posição local e pelo sal (o mesmo valor em todo chunk e em toda geração). */
    public static int hash(int salt, int x, int y, int z) {
        long h = salt * 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h ^= y * 0x165667B19E3779F9L;
        h ^= z * 0x85EBCA77C2B2AE63L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) Math.floorMod(h, 100L);
    }

    /** O estado que a operação põe na posição local {@code (x, y, z)}: o da caixa, ou o do sorteio do {@link Mix}; null se a posição está fora da caixa. */
    public static String stateAt(Op op, int x, int y, int z) {
        return switch (op) {
            case Box b -> inside(x, y, z, b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()) ? b.state() : null;
            case Mix m -> inside(x, y, z, m.x0(), m.y0(), m.z0(), m.x1(), m.y1(), m.z1()) ? (hash(m.salt(), x, y, z) < m.percentB() ? m.b() : m.a()) : null;
            case Chest c -> x == c.x() && y == c.y() && z == c.z() ? "minecraft:chest[facing=" + c.facing() + "]" : null;
            case Terminal t -> x == t.x() && y == t.y() && z == t.z() ? "spacehorizons:data_terminal[facing=" + t.facing() + ",record=" + t.record() + "]" : null;
        };
    }

    private static boolean inside(int x, int y, int z, int x0, int y0, int z0, int x1, int y1, int z1) {
        return x >= Math.min(x0, x1) && x <= Math.max(x0, x1) && y >= Math.min(y0, y1) && y <= Math.max(y0, y1) && z >= Math.min(z0, z1)
                && z <= Math.max(z0, z1);
    }
}
