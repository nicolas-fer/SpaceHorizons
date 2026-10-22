package com.spacehorizons.ship;

import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.block.ShipImpulseEngineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Monta uma nave no chão a partir da ponte ({@code ship_bridge}), com o clique agachado de mão vazia fora do estaleiro: a busca
 * ({@link ShipAssembly}) e a validação são as da montagem do foguete ({@link RocketAssembler}), e a remoção e a entidade também. A nave
 * precisa de um motor para decolar ({@link ShipLiftoff}: bocal de impulso para baixo ou motor de foguete); sem ele, a montagem é recusada
 * antes de qualquer bloco sair. A entidade nasce como nave ({@link RocketEntity#isShip}) e o jogador que montou senta nela.
 */
public final class ShipAssembler {
    private ShipAssembler() {
    }

    /** O clique agachado na ponte: monta a nave, senta o jogador e conta o que aconteceu. */
    public static void assembleFor(ServerLevel level, BlockPos bridge, ServerPlayer player) {
        ShipAnchors.Anchor anchor = ShipAnchors.at(level, bridge);
        if (anchor != null) {
            // nave ancorada no ar (spike S6): monta de novo sem chão, o jogador senta e quem estava em pé fica no convés
            try {
                ShipAnchors.unanchor(level, anchor, player);
            } catch (RocketException e) {
                player.sendSystemMessage(e.component());
            }
            return;
        }
        try {
            RocketEntity ship = assemble(level, bridge, player);
            RocketPortrait portrait = ship.portrait();
            player.sendSystemMessage(Component.translatable("message.spacehorizons.ship.assembled", portrait.blockCount(),
                    String.format(Locale.ROOT, "%.1f", portrait.stats().dryMassT()),
                    String.format(Locale.ROOT, "%.0f", ship.fuelMb())));
            player.startRiding(ship, true, true);
        } catch (RocketException e) {
            player.sendSystemMessage(e.component());
        }
    }

    /**
     * Monta a nave cuja ponte está em {@code bridge}. Lança {@link RocketException} sem tocar no mundo se algo recusar a montagem
     * (inclusive a falta de motor para decolar).
     */
    public static RocketEntity assemble(ServerLevel level, BlockPos bridge, ServerPlayer player) throws RocketException {
        return assemble(level, bridge, player, false);
    }

    /**
     * Como acima; {@code airborne}: a nave está ancorada no ar (spike S6) e é montada sem exigir a plataforma de lançamento. {@code player} pode
     * ser null (o comando de dev {@code ship unanchor}).
     */
    public static RocketEntity assemble(ServerLevel level, BlockPos bridge, @Nullable ServerPlayer player, boolean airborne) throws RocketException {
        RocketAssembly.Grid grid = RocketAssembler.worldGrid(level, ShipKinds::assemblyKind);
        RocketAssembly.Result found = airborne ? ShipAssembly.scanAirborne(grid, bridge.getX(), bridge.getY(), bridge.getZ())
                : ShipAssembly.scan(grid, bridge.getX(), bridge.getY(), bridge.getZ());
        ShipLiftoff.Nozzles nozzles = nozzles(level, found);
        double liftoffThrust = ShipLiftoff.require(nozzles, found.census());
        SpaceHorizons.LOGGER.info("Nave montada em {}: empuxo de decolagem {}", bridge.toShortString(), liftoffThrust);
        RocketEntity ship = RocketAssembler.assemble(level, bridge, player, found, true);
        ship.setNozzlesDown(nozzles.down());
        return ship;
    }

    /** Os motores de impulso da nave por sentido do bocal (o de cima e o horizontal não decolam a nave). */
    private static ShipLiftoff.Nozzles nozzles(ServerLevel level, RocketAssembly.Result found) {
        int up = 0;
        int down = 0;
        int horizontal = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (RocketAssembly.Cell cell : found.cells()) {
            BlockState state = level.getBlockState(cursor.set(cell.x(), cell.y(), cell.z()));
            if (!state.is(ModBlocks.SHIP_IMPULSE_ENGINE.get())) {
                continue;
            }
            Direction nozzle = state.getValue(ShipImpulseEngineBlock.FACING);
            if (nozzle == Direction.DOWN) {
                down++;
            } else if (nozzle == Direction.UP) {
                up++;
            } else {
                horizontal++;
            }
        }
        return new ShipLiftoff.Nozzles(up, down, horizontal);
    }
}
