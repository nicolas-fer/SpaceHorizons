package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.content.connected.ConnectedFaces;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Monta o foguete no mundo: busca (lógica pura em {@link RocketAssembly}), monta o retrato, valida TUDO e só então remove
 * os blocos, em dois passes como o Create (primeiro os que precisam de apoio, depois os sólidos) e com um passe final
 * de notificação. A entidade nasce no mesmo lugar, com o centro da caixa como posição.
 */
public final class RocketAssembler {
    /** Remoção sem atualizar vizinhos (nada solta ou quebra no meio da operação) e sem soltar itens. */
    private static final int REMOVE_FLAGS = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /** O limite do cliente para um pacote de plugin (1 MiB). */
    public static final int MAX_PACKET_BYTES = 1 << 20;
    /**
     * Até este tamanho o retrato vai INTEIRO no pacote de spawn do foguete (o pacote também leva dados da entidade, então fica uma folga
     * do limite do cliente); acima, vai em partes ({@link PortraitParts}), um pacote por parte, depois do spawn.
     */
    public static final int INLINE_PORTRAIT_BYTES = MAX_PACKET_BYTES - 64 * 1024;
    /**
     * Teto do retrato codificado (a nave da D2 tem até {@link RocketAssembly#MAX_BLOCKS} blocos; com baús cheios, ~1,65 KB cada, dá
     * ~13 MB no pior caso). Acima disso a montagem é recusada ({@link RocketException.Reason#PACKET_TOO_LARGE}): é memória do servidor e
     * de cada cliente, e tempo de envio.
     */
    public static final int MAX_PORTRAIT_BYTES = 16 << 20;

    private RocketAssembler() {
    }

    /** O mundo como a busca da montagem o vê (a mesma para a montagem e para o painel de projeto da plataforma, {@link RocketDesign}). */
    public static RocketAssembly.Grid worldGrid(net.minecraft.world.level.Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            cursor.set(x, y, z);
            if (!level.isInsideBuildHeight(cursor)) {
                return RocketBlockKind.FORBIDDEN;
            }
            if (!level.isLoaded(cursor)) {
                return RocketBlockKind.UNLOADED;
            }
            return RocketKinds.classify(level, cursor, level.getBlockState(cursor));
        };
    }

    /** O clique no cockpit: monta e conta ao jogador o que aconteceu. */
    public static void assembleFor(ServerLevel level, BlockPos cockpit, Player player) {
        try {
            RocketEntity rocket = assemble(level, cockpit, player);
            RocketPortrait portrait = rocket.portrait();
            player.sendSystemMessage(Component.translatable("message.spacehorizons.rocket.assembled", portrait.blockCount(),
                    String.format(java.util.Locale.ROOT, "%.1f", portrait.stats().dryMassT()),
                    String.format(java.util.Locale.ROOT, "%.0f", rocket.fuelMb())));
        } catch (RocketException e) {
            player.sendSystemMessage(e.component());
        }
    }

    /**
     * Monta o foguete cujo cockpit está em {@code cockpit}. Lança {@link RocketException} sem tocar no mundo se algo
     * recusar a montagem. {@code player} (pode ser null, como no comando de teste) é conferido contra a proteção de spawn.
     */
    public static RocketEntity assemble(ServerLevel level, BlockPos cockpit, @Nullable Player player) throws RocketException {
        RocketAssembly.Result found = RocketAssembly.scan(worldGrid(level), cockpit.getX(), cockpit.getY(), cockpit.getZ());

        if (player != null) {
            for (RocketAssembly.Cell cell : found.cells()) {
                if (!level.mayInteract(player, new BlockPos(cell.x(), cell.y(), cell.z()))) {
                    throw new RocketException(RocketException.Reason.NOT_ALLOWED, cell.x(), cell.y(), cell.z());
                }
            }
        }

        // o retrato: grade densa, paleta e NBT dos block entities (sem x/y/z), nas coordenadas locais da caixa
        PortraitGrid grid = PortraitGrid.empty(found.sizeX(), found.sizeY(), found.sizeZ());
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        paletteIndex.put(palette.get(0), 0);
        Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectLinkedOpenHashMap<>();
        int fuel = 0;
        Map<Long, Multiblocks.EngineRole> engineRoles = Multiblocks.engineRoles(found.structures().engines());
        LongOpenHashSet tankCells = new LongOpenHashSet();
        for (RocketAssembly.Cell cell : found.cells()) {
            if (cell.kind() == RocketBlockKind.FUEL_TANK) {
                tankCells.add(RocketAssembly.pack(cell.x(), cell.y(), cell.z()));
            }
        }
        for (RocketAssembly.Cell cell : found.cells()) {
            BlockPos world = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = canonical(level.getBlockState(world), cell, engineRoles, tankCells);
            int index = paletteIndex.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            int lx = cell.x() - found.minX();
            int ly = cell.y() - found.minY();
            int lz = cell.z() - found.minZ();
            grid.set(lx, ly, lz, index);
            if (state.hasBlockEntity() && level.getBlockEntity(world) instanceof BlockEntity blockEntity) {
                CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
                blockEntities.put(BlockPos.asLong(lx, ly, lz), tag);
                if (blockEntity instanceof RocketFuelTankBlockEntity tank) {
                    fuel += tank.fuelMb();
                }
            }
        }
        BlockPos localCockpit = new BlockPos(cockpit.getX() - found.minX(), cockpit.getY() - found.minY(), cockpit.getZ() - found.minZ());
        RocketPortrait portrait = new RocketPortrait(UUID.randomUUID(), grid, palette, blockEntities, localCockpit);
        if (RocketPortrait.encodedSize(portrait) > MAX_PORTRAIT_BYTES) {
            throw new RocketException(RocketException.Reason.PACKET_TOO_LARGE);
        }

        // validado: a partir daqui o mundo é alterado
        RocketEntity rocket = ModEntities.ROCKET.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (rocket == null) {
            throw new IllegalStateException("não foi possível criar a entidade do foguete");
        }
        rocket.setPortrait(portrait);
        rocket.setFuelMb(fuel);
        rocket.setPos(found.minX() + found.sizeX() / 2.0, found.minY() + found.sizeY() / 2.0, found.minZ() + found.sizeZ() / 2.0);

        EngineForming.suspended(() -> removeBlocks(level, found.cells()));
        if (!level.addFreshEntity(rocket)) {
            // não deveria acontecer; devolve os blocos para não perder o foguete
            SpaceHorizons.LOGGER.error("Falha ao adicionar a entidade do foguete; devolvendo os blocos");
            try {
                RocketLanding.place(level, portrait, new BlockPos(found.minX(), found.minY(), found.minZ()), fuel);
            } catch (RocketException e) {
                SpaceHorizons.LOGGER.error("Falha ao devolver os blocos do foguete: {}", e.getMessage());
            }
            throw new IllegalStateException("não foi possível adicionar a entidade do foguete");
        }
        SpaceHorizons.LOGGER.info("Foguete montado em {}: {} blocos, caixa {}x{}x{}, {} mB de combustível", cockpit.toShortString(),
                portrait.blockCount(), found.sizeX(), found.sizeY(), found.sizeZ(), fuel);
        return rocket;
    }

    /**
     * O estado do bloco como o retrato o guarda, refeito a partir das estruturas que a montagem achou: o motor leva o papel que a partição
     * lhe dá (mestre, parte ou avulso) e o tanque, as conexões com os tanques vizinhos. Assim um foguete montado por comando (que não
     * passa pela colocação do jogador) também aparece formado em voo, e o que o censo conta é o que o retrato mostra.
     */
    static BlockState canonical(BlockState state, RocketAssembly.Cell cell, Map<Long, Multiblocks.EngineRole> engineRoles, LongOpenHashSet tankCells) {
        if (cell.kind() == RocketBlockKind.ENGINE && state.hasProperty(RocketEngineBlock.FORM)) {
            return EngineForming.stateFor(state, engineRoles.getOrDefault(RocketAssembly.pack(cell.x(), cell.y(), cell.z()), Multiblocks.EngineRole.SOLO));
        }
        if (cell.kind() == RocketBlockKind.FUEL_TANK && state.hasProperty(ConnectedFaces.UP)) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                state = state.setValue(ConnectedFaces.property(side),
                        tankCells.contains(RocketAssembly.pack(cell.x() + side.getStepX(), cell.y() + side.getStepY(), cell.z() + side.getStepZ())));
            }
        }
        return state;
    }

    /**
     * Remove os blocos em dois passes (presos primeiro) sem atualizar vizinhos, e depois notifica tudo de uma vez, já com
     * o espaço vazio (assim nada preso solta no meio).
     */
    private static void removeBlocks(ServerLevel level, List<RocketAssembly.Cell> cells) {
        List<BlockPos> positions = new ArrayList<>(cells.size());
        List<BlockState> olds = new ArrayList<>(cells.size());
        for (boolean attached : new boolean[] {true, false}) {
            for (RocketAssembly.Cell cell : cells) {
                BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                BlockState old = level.getBlockState(pos);
                if (RocketBlockOrder.isAttached(old) != attached) {
                    continue;
                }
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
                positions.add(pos);
                olds.add(old);
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            LevelChunk chunk = level.getChunkAt(pos);
            level.markAndNotifyBlock(pos, chunk, olds.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, 512);
        }
    }
}
