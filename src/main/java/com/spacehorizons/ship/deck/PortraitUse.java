package com.spacehorizons.ship.deck;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.PortraitChange;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * O clique direito num bloco do retrato da nave (servidor): com a nave em movimento os blocos são dados da entidade e o vanilla não os enxerga,
 * então o clique chega por {@link DeckUsePayload} e o que o bloco faria é refeito aqui sobre o retrato:
 * <ul>
 *   <li>portas, alçapões e portões de cerca alternam o estado no retrato (o delta chega aos clientes, a colisão do convés usa o estado novo);</li>
 *   <li>baús, barris e afins abrem um menu vanilla sobre um contêiner proxy ({@link PortraitContainers});</li>
 *   <li>o assento ({@code ship_seat}) e a ponte (cockpit) sentam o jogador como passageiro da nave ({@link RocketEntity#sitAt});</li>
 *   <li>as outras máquinas (fornalha, bancada, alavanca...) mostram o aviso de que a nave precisa estar ancorada, pousada ou no espaço;</li>
 *   <li>o resto (o casco, e as máquinas de um foguete visto de fora) faz o que o clique na nave sempre fez: quem está fora senta como piloto.</li>
 * </ul>
 */
public final class PortraitUse {
    /** Quanto além do alcance do bloco o clique ainda vale (o atraso da rede e o balanço da nave). */
    private static final double REACH_SLACK = 3.0;

    /** Avisos de máquina com a nave em movimento enviados (diagnóstico do autoteste). */
    public static final java.util.concurrent.atomic.AtomicInteger MACHINE_WARNINGS = new java.util.concurrent.atomic.AtomicInteger();

    private PortraitUse() {
    }

    /** O pacote de clique chegou (thread do servidor). */
    public static void onUse(ServerPlayer player, DeckUsePayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(payload.rocketId()) instanceof RocketEntity rocket) || rocket.isRemoved()) {
            return;
        }
        RocketPortrait portrait = rocket.portrait();
        BlockPos pos = BlockPos.of(payload.pos());
        if (portrait.isEmpty() || !portrait.inside(pos.getX(), pos.getY(), pos.getZ()) || portrait.get(pos).isAir() || player.isSpectator()) {
            return;
        }
        Vector3d eye = PortraitContainers.eyeLocal(player, rocket);
        double reach = player.blockInteractionRange() + REACH_SLACK;
        double dx = eye.x - (pos.getX() + 0.5);
        double dy = eye.y - (pos.getY() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > reach * reach) {
            SpaceHorizons.LOGGER.warn("[deck] clique de {} em {} longe demais ({} blocos): ignorado", player.getPlainTextName(), pos, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return;
        }
        use(player, rocket, pos);
    }

    /** O que o clique em {@code pos} faz. Devolve se algo foi feito (inclusive o aviso). */
    public static boolean use(ServerPlayer player, RocketEntity rocket, BlockPos pos) {
        RocketPortrait portrait = rocket.portrait();
        BlockState state = portrait.get(pos);
        if (isSeat(portrait, pos, state)) {
            return rocket.sitAt(player, pos);
        }
        if (state.getBlock() instanceof DoorBlock door) {
            if (!door.type().canOpenByHand()) {
                return false;
            }
            return toggle(rocket, pos, state);
        }
        if (state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
            if (state.is(Blocks.IRON_TRAPDOOR)) {
                return false;
            }
            return toggle(rocket, pos, state);
        }
        if (player.level() instanceof ServerLevel level && PortraitContainers.supports(level, pos, state, portrait.blockEntities().get(pos.asLong()))) {
            return PortraitContainers.open(player, rocket, pos);
        }
        if (isMachine(state) && (DeckCrew.isCrew(player) || rocket.isShip())) {
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.deck.needs_anchor"));
            MACHINE_WARNINGS.incrementAndGet();
            return true;
        }
        if (!DeckCrew.isCrew(player) && !player.isSecondaryUseActive() && !player.isPassenger()) {
            // o casco: o clique na nave de sempre (senta como piloto)
            return rocket.interact(player, InteractionHand.MAIN_HAND, Vec3.atCenterOf(pos)).consumesAction();
        }
        return false;
    }

    /** Assento do retrato: o bloco {@code ship_seat} ou a ponte (o cockpit da nave). */
    public static boolean isSeat(RocketPortrait portrait, BlockPos pos, BlockState state) {
        return state.is(ModBlocks.SHIP_SEAT.get()) || pos.equals(portrait.cockpit());
    }

    /** Bloco que o vanilla trata como máquina ou interface (não funciona com a nave em movimento). */
    static boolean isMachine(BlockState state) {
        return state.hasBlockEntity() || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof CraftingTableBlock || state.getBlock() instanceof AnvilBlock;
    }

    /** Algum tripulante a pé ficaria dentro de um sólido com estas mudanças aplicadas ao retrato? */
    private static boolean crewInTheWay(RocketEntity rocket, List<PortraitChange> changes) {
        List<DeckState> crew = DeckCrew.statesOn(rocket.getUUID());
        if (crew.isEmpty()) {
            return false;
        }
        DeckColliders after = rocket.portrait().withChanges(java.util.UUID.randomUUID(), changes).deckColliders();
        for (DeckState s : crew) {
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(s.pos.x - 0.3, s.pos.y, s.pos.z - 0.3, s.pos.x + 0.3, s.pos.y + 1.8, s.pos.z + 0.3);
            if (DeckCollision.penetration(box, after) > DeckMoveCheck.MAX_PENETRATION) {
                return true;
            }
        }
        return false;
    }

    /** Alterna aberto/fechado (porta: as duas metades) e aplica ao retrato. */
    private static boolean toggle(RocketEntity rocket, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return false;
        }
        boolean open = !state.getValue(BlockStateProperties.OPEN);
        List<PortraitChange> changes = new ArrayList<>(2);
        changes.add(PortraitChange.state(pos, state.setValue(BlockStateProperties.OPEN, open)));
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            boolean lower = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
            BlockPos otherPos = lower ? pos.above() : pos.below();
            BlockState other = rocket.portrait().inside(otherPos.getX(), otherPos.getY(), otherPos.getZ()) ? rocket.portrait().get(otherPos) : Blocks.AIR.defaultBlockState();
            if (other.is(state.getBlock()) && other.hasProperty(BlockStateProperties.OPEN)) {
                changes.add(PortraitChange.state(otherPos, other.setValue(BlockStateProperties.OPEN, open)));
            }
        }
        if (!open && crewInTheWay(rocket, changes)) {
            // fechar a porta sobre um tripulante o deixaria dentro do sólido (o servidor recusaria os estados dele): a porta fica aberta
            return false;
        }
        rocket.changePortrait(changes);
        SpaceHorizons.LOGGER.info("[deck] {} {} da nave {} em {}", state.getBlock().getName().getString(), open ? "aberta" : "fechada", rocket.getId(), pos);
        return true;
    }
}
