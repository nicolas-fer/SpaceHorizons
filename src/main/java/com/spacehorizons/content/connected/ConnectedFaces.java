package com.spacehorizons.content.connected;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Mecanismo reutilizável de TEXTURAS CONECTADAS (docs/TEXTURAS_CONECTADAS.md): seis propriedades booleanas, uma por lado do bloco, que
 * dizem se o vizinho daquele lado "se conecta" a este bloco. O blockstate do bloco (gerado por {@code tools/gen_connected_block.py}) tira a
 * moldura das bordas e dos cantos de cada face que encostam num vizinho conectado, e o vidro (ou o tanque) fica contínuo.
 *
 * <p>Para ligar o mecanismo a um bloco:
 * <pre>{@code
 * public MeuBloco(Properties p) { super(p); registerDefaultState(ConnectedFaces.disconnected(stateDefinition.any())); }
 * protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { ConnectedFaces.addProperties(b); }
 * public BlockState getStateForPlacement(BlockPlaceContext c) { return ConnectedFaces.connect(defaultBlockState(), c.getLevel(), c.getClickedPos(), RULE); }
 * protected BlockState updateShape(BlockState s, LevelReader l, ScheduledTickAccess t, BlockPos p, Direction d, BlockPos np, BlockState n, RandomSource r) {
 *     return ConnectedFaces.update(s, d, n, RULE);
 * }
 * }</pre>
 * onde {@code RULE} é {@link #sameBlock()} (padrão) ou uma regra própria, por exemplo "qualquer bloco do grupo dos tanques".
 *
 * <p>O estado guarda a conexão (como a cerca): o retrato do foguete e as conversões de e para o estaleiro levam o estado inteiro, então a
 * aparência viaja junto. Quem escreve blocos por código com {@code setBlock} (construtores de nave, comandos) deve chamar
 * {@link #connectAround} depois de colocar todos, porque {@code setBlock} não calcula o estado do próprio bloco colocado.
 */
public final class ConnectedFaces {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        BY_DIRECTION.put(Direction.NORTH, NORTH);
        BY_DIRECTION.put(Direction.EAST, EAST);
        BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        BY_DIRECTION.put(Direction.WEST, WEST);
        BY_DIRECTION.put(Direction.UP, UP);
        BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    /** Diz se o vizinho de {@code self} no lado {@code side} se conecta a ele. */
    @FunctionalInterface
    public interface Rule {
        boolean connects(BlockState self, BlockState neighbor, Direction side);
    }

    private ConnectedFaces() {
    }

    /** A propriedade do lado dado. */
    public static BooleanProperty property(Direction side) {
        return BY_DIRECTION.get(side);
    }

    /** Regra padrão: conecta com outro bloco do MESMO tipo (qualquer estado). */
    public static Rule sameBlock() {
        return (self, neighbor, side) -> neighbor.is(self.getBlock());
    }

    /** Declara as seis propriedades (em {@code createBlockStateDefinition}). */
    public static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** O estado sem nenhuma conexão (para {@code registerDefaultState}). */
    public static BlockState disconnected(BlockState state) {
        for (BooleanProperty property : BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        return state;
    }

    /** O estado com as conexões calculadas a partir dos seis vizinhos no mundo. */
    public static BlockState connect(BlockState state, BlockGetter level, BlockPos pos, Rule rule) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values()) {
            neighbor.setWithOffset(pos, side);
            state = state.setValue(property(side), rule.connects(state, level.getBlockState(neighbor), side));
        }
        return state;
    }

    /** Depois que um vizinho mudou: o estado com o lado {@code side} atualizado (para {@code updateShape}). */
    public static BlockState update(BlockState state, Direction side, BlockState neighborState, Rule rule) {
        return state.setValue(property(side), rule.connects(state, neighborState, side));
    }

    /**
     * Recalcula as conexões do bloco em {@code pos} (se for do tipo {@code block}) e dos seis vizinhos do mesmo tipo, sem avisar ninguém
     * além do cliente. Para quem coloca blocos por código: chame uma vez por posição colocada, ou, depois de escrever tudo, para cada posição.
     *
     * @return true se algum estado mudou
     */
    public static boolean connectAround(net.minecraft.world.level.LevelAccessor level, BlockPos pos, Block block, Rule rule) {
        boolean changed = refresh(level, pos, block, rule);
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values()) {
            neighbor.setWithOffset(pos, side);
            changed |= refresh(level, neighbor, block, rule);
        }
        return changed;
    }

    private static boolean refresh(net.minecraft.world.level.LevelAccessor level, BlockPos pos, Block block, Rule rule) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(block)) {
            return false;
        }
        BlockState wanted = connect(current, level, pos, rule);
        if (wanted == current) {
            return false;
        }
        level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }
}
