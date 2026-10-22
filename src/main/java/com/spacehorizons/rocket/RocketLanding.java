package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.flight.FuelLoad;
import com.spacehorizons.space.flight.Propellant;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pouso de volta a blocos (a "desmontagem"): o yaw é arredondado ao quarto de volta mais próximo (só rotação em Y, os
 * estados passam por {@link BlockState#rotate}), o espaço é conferido ANTES de qualquer bloco ser colocado e os blocos
 * voltam em dois passes (sólidos primeiro, presos depois), com o NBT dos block entities no destino e o combustível devolvido
 * aos tanques. Diferente do Create, que destrói o que estiver no caminho: aqui só se substitui o que é substituível
 * (ar, grama, neve, água) e qualquer outra coisa recusa o pouso ({@link RocketException.Reason#SPACE_OCCUPIED}).
 */
public final class RocketLanding {
    /** Reposição: clientes avisados, sem recalcular a forma dos vizinhos (os estados guardados já são os finais). */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private RocketLanding() {
    }

    /**
     * Um bloco a recolocar: posição no mundo, estado, NBT (null sem block entity) e, se for tanque, o combustível dele ({@code tank}); se for motor
     * do Tier 2 que guarda carga, a carga ({@code charge}).
     */
    private record Placement(BlockPos pos, BlockState state, @Nullable CompoundTag nbt, int fuelMb, boolean tank, boolean charge) {
    }

    /** O canto mínimo da caixa que tem o centro em {@code center}, para uma caixa deste tamanho (arredonda ao bloco mais perto). */
    public static BlockPos originFor(Vec3 center, int sizeX, int sizeY, int sizeZ) {
        return new BlockPos((int) Math.round(center.x - sizeX / 2.0), (int) Math.round(center.y - sizeY / 2.0),
                (int) Math.round(center.z - sizeZ / 2.0));
    }

    /** Desmonta o foguete: tira o piloto, devolve os blocos no lugar da entidade e remove a entidade. */
    public static void land(ServerLevel level, RocketEntity rocket) throws RocketException {
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            throw new RocketException(RocketException.Reason.NO_ROCKET);
        }
        if (rocket.flight().mode() != FlightMode.LANDED) {
            throw new RocketException(RocketException.Reason.NOT_LANDED);
        }
        if (!rocket.canLand()) {
            throw new RocketException(RocketException.Reason.NOT_UPRIGHT, Math.round(rocket.tiltDegrees()));
        }
        int turns = PortraitGrid.quarterTurnsFromYaw(rocket.yawDegrees());
        RocketPortrait rotated = portrait.rotated(turns);
        BlockPos origin = originFor(rocket.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
        FuelLoad fuel = rocket.fuel().floor();

        List<Placement> plan = plan(level, rotated, origin, fuel);

        // o piloto sai ANTES de os blocos voltarem (o assento fica dentro do volume): em pé no chão ao lado da caixa
        AABB destination = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + rotated.sizeX,
                origin.getY() + rotated.sizeY, origin.getZ() + rotated.sizeZ);
        for (Entity passenger : List.copyOf(rocket.getPassengers())) {
            passenger.stopRiding();
            Vec3 spot = RocketDismount.find(level, destination, passenger.getType());
            Vec3 target = spot != null ? spot : new Vec3(destination.getCenter().x, destination.maxY, destination.getCenter().z);
            passenger.teleportTo(level, target.x, target.y, target.z, Set.of(), passenger.getYRot(), passenger.getXRot(), true);
        }

        EngineForming.suspended(() -> commit(level, plan));
        rocket.discard();
        SpaceHorizons.LOGGER.info("Foguete pousou em {}: {} blocos, giro de {} quarto(s) de volta, {} mB de combustível devolvidos",
                origin.toShortString(), rotated.blockCount(), turns, fuel);
    }

    /** Recoloca um retrato (já girado) com o canto mínimo em {@code origin}; usado pelo pouso e pela volta de uma montagem que falhou. */
    public static void place(ServerLevel level, RocketPortrait portrait, BlockPos origin, int fuelMb) throws RocketException {
        place(level, portrait, origin, FuelLoad.chemical(fuelMb));
    }

    /** Como acima, com a carga por combustível (tanques de cada tipo e a carga dos motores do Tier 2). */
    public static void place(ServerLevel level, RocketPortrait portrait, BlockPos origin, FuelLoad fuel) throws RocketException {
        List<Placement> plan = plan(level, portrait, origin, fuel);
        EngineForming.suspended(() -> commit(level, plan));
    }

    /**
     * Quanto devolver a cada bloco que guarda carga (posição local -> mB): o combustível de cada tipo repartido entre os tanques do tipo dele
     * ({@link RocketFuel#distribute}, por igual), o núcleo entre os motores nucleares e o hélio-3 entre os de fusão. Os blocos de motor ficam
     * com o sinal trocado (negativo = carga de motor, -1 - mB), para o plano saber o que é cada um.
     */
    private static java.util.Map<Long, Integer> shares(RocketPortrait portrait, FuelLoad fuel) {
        java.util.Map<Long, Integer> out = new java.util.HashMap<>();
        for (RocketBlockKind kind : RocketBlockKind.values()) {
            Propellant tankFuel = RocketSpecs.tankPropellant(kind);
            EngineType engine = EngineType.of(kind);
            Propellant charge = engine == null ? null : engine.chargePropellant();
            Propellant propellant = tankFuel != null ? tankFuel : charge;
            if (propellant == null) {
                continue;
            }
            List<BlockPos> positions = portrait.positionsOf(kind);
            int[] capacities = new int[positions.size()];
            java.util.Arrays.fill(capacities, tankFuel != null ? RocketSpecs.TANK_CAPACITY_MB : engine.chargePerBlockMb());
            int[] given = RocketFuel.distribute((int) Math.min(Integer.MAX_VALUE, Math.floor(fuel.get(propellant))), capacities);
            for (int i = 0; i < positions.size(); i++) {
                out.put(positions.get(i).asLong(), tankFuel != null ? given[i] : -1 - given[i]);
            }
        }
        return out;
    }

    /** Confere o espaço (chunks carregados, nada que não possa ser substituído) e prepara as colocações. Não toca no mundo. */
    private static List<Placement> plan(ServerLevel level, RocketPortrait portrait, BlockPos origin, FuelLoad fuel) throws RocketException {
        java.util.Map<Long, Integer> shares = shares(portrait, fuel);

        List<Placement> plan = new ArrayList<>(portrait.blockCount());
        for (int y = 0; y < portrait.sizeY; y++) {
            for (int z = 0; z < portrait.sizeZ; z++) {
                for (int x = 0; x < portrait.sizeX; x++) {
                    BlockState state = portrait.get(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos world = origin.offset(x, y, z);
                    if (!level.isInsideBuildHeight(world)) {
                        throw new RocketException(RocketException.Reason.SPACE_OCCUPIED, world.getX(), world.getY(), world.getZ());
                    }
                    if (!level.isLoaded(world)) {
                        throw new RocketException(RocketException.Reason.UNLOADED_CHUNK, world.getX(), world.getY(), world.getZ());
                    }
                    BlockState existing = level.getBlockState(world);
                    if (!existing.isAir() && !existing.canBeReplaced()) {
                        throw new RocketException(RocketException.Reason.SPACE_OCCUPIED, world.getX(), world.getY(), world.getZ());
                    }
                    BlockPos local = new BlockPos(x, y, z);
                    Integer share = shares.get(local.asLong());
                    boolean tank = share != null && share >= 0;
                    boolean charge = share != null && share < 0;
                    plan.add(new Placement(world, state, portrait.blockEntities().get(local.asLong()), share == null ? 0 : tank ? share : -1 - share,
                            tank, charge));
                }
            }
        }
        return plan;
    }

    private static void commit(ServerLevel level, List<Placement> plan) {
        // sólidos primeiro, presos depois (o inverso da remoção)
        for (boolean attached : new boolean[] {false, true}) {
            for (Placement p : plan) {
                if (RocketBlockOrder.isAttached(p.state()) != attached) {
                    continue;
                }
                BlockState existing = level.getBlockState(p.pos());
                if (!existing.isAir()) {
                    SpaceHorizons.LOGGER.info("Pouso do foguete substituiu {} em {}", existing.getBlock(), p.pos().toShortString());
                }
                level.setBlock(p.pos(), p.state(), PLACE_FLAGS);
                if (p.nbt() != null) {
                    BlockEntity loaded = BlockEntity.loadStatic(p.pos(), p.state(), p.nbt(), level.registryAccess());
                    if (loaded != null) {
                        level.setBlockEntity(loaded);
                    }
                }
                if (p.tank() && level.getBlockEntity(p.pos()) instanceof RocketFuelTankBlockEntity tank) {
                    tank.setFuel(p.fuelMb());
                } else if (p.charge() && level.getBlockEntity(p.pos()) instanceof EngineChargeBlockEntity engine) {
                    engine.setCharge(p.fuelMb());
                }
            }
        }
        // passe final: avisa os vizinhos e manda os dados dos block entities (o tanque mostra a carga ao olhar)
        for (Placement p : plan) {
            level.updateNeighborsAt(p.pos(), p.state().getBlock());
            if (p.nbt() != null || p.tank() || p.charge()) {
                level.sendBlockUpdated(p.pos(), p.state(), p.state(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
