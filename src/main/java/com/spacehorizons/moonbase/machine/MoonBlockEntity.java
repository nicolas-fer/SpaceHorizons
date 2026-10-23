package com.spacehorizons.moonbase.machine;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.moonbase.MoonTriggers;
import com.spacehorizons.moonbase.energy.EnergyMath;
import com.spacehorizons.moonbase.energy.EnergyMath.Role;
import com.spacehorizons.moonbase.room.PressurizedRooms;
import com.spacehorizons.moonbase.room.RoomPressure;
import com.spacehorizons.moonbase.room.RoomScan;
import com.spacehorizons.moonbase.room.RoomSpace;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SunModel;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.survival.fuel.CanisterFuel;
import com.spacehorizons.survival.fuel.FuelCanisterItem;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity único dos blocos de energia e das máquinas da base lunar ({@link MachineKind}). Cada tick: o painel gera, todo
 * bloco que não é máquina empurra energia para os vizinhos ({@link EnergyMath#flow}) e a máquina do tipo trabalha. A lógica de
 * números é pura ({@link MoonMachineMath}, {@link RoomPressure}, {@link RoomScan}); aqui só se liga aos slots, ao mundo e ao
 * estado aceso do bloco.
 *
 * O contêiner tem 3 slots (ver {@link MachineKind}). Automação: funil por qualquer lado coloca nos slots de entrada; só sai pelo
 * lado de baixo o que é produto pronto (tanque ou galão cheio). Ao quebrar o bloco os itens dos slots caem; os reservatórios
 * internos (oxigênio, hidrogênio, combustível) e a energia se perdem.
 */
public class MoonBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    /** Itens que alimentam a eletrólise, a refinaria e o gerador de oxigênio (gelo lunar, gelo e gelo compactado do vanilla). */
    public static final TagKey<Item> ICE_FEED = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            Identifier.fromNamespaceAndPath(com.spacehorizons.SpaceHorizons.MODID, "ice_feed"));

    /** Índices do {@link ContainerData} sincronizado com a tela. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_TOTAL = 2;
    public static final int DATA_STATUS = 3;
    public static final int DATA_OXYGEN = 4;
    public static final int DATA_FLUID = 5;
    public static final int DATA_PRESSURE = 6;
    public static final int DATA_COUNT = 7;

    private static final int SAVE_INTERVAL_TICKS = 20;
    private static final int[] ALL_SLOTS = {0, 1, 2};
    private static final int[] NO_SLOTS = {};

    private final MachineKind kind;
    private NonNullList<ItemStack> items = NonNullList.withSize(MachineKind.SLOT_COUNT, ItemStack.EMPTY);
    private int energy;
    private int progress;
    private int oxygen;
    private int fluidMb;
    private int pressure;
    private MachineStatus status = MachineStatus.IDLE;

    // transitórios (não salvos)
    private int generation;
    private double sunUp;
    private boolean sunKnown;
    private int lastVolume;
    private @Nullable LongOpenHashSet lastCells;
    private boolean registered;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> Math.min(energy, Short.MAX_VALUE);
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> totalTicks();
                case DATA_STATUS -> status.ordinal();
                case DATA_OXYGEN -> Math.min(oxygen, Short.MAX_VALUE);
                case DATA_FLUID -> Math.min(fluidMb, Short.MAX_VALUE);
                case DATA_PRESSURE -> pressure;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // só o cliente (menu) escreve aqui; o servidor é a fonte da verdade
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MoonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOON.get(), pos, state);
        this.kind = state.getBlock() instanceof MoonBlock block ? block.kind() : MachineKind.SOLAR_PANEL;
    }

    public MachineKind kind() {
        return kind;
    }

    public int energy() {
        return energy;
    }

    public int pressure() {
        return pressure;
    }

    public int oxygen() {
        return oxygen;
    }

    public int fluidMb() {
        return fluidMb;
    }

    public int progress() {
        return progress;
    }

    public MachineStatus status() {
        return status;
    }

    /** UE gerados no último tick (painel). */
    public int generation() {
        return generation;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    /** Define a energia (testes e comandos de desenvolvimento). */
    public void setEnergy(int value) {
        this.energy = Math.max(0, Math.min(kind.capacity(), value));
        setChanged();
    }

    private int totalTicks() {
        return switch (kind) {
            case ELECTROLYZER -> MoonMachineMath.ELECTROLYSIS_TICKS;
            case LUNAR_REFINERY -> MoonMachineMath.REFINERY_TICKS;
            case HELIUM3_EXTRACTOR -> MoonMachineMath.EXTRACTOR_TICKS;
            default -> 0;
        };
    }

    // ---- persistência ----

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(MachineKind.SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy = Math.max(0, Math.min(kind.capacity(), input.getIntOr("energy", 0)));
        progress = Math.max(0, input.getIntOr("progress", 0));
        oxygen = Math.max(0, input.getIntOr("oxygen", 0));
        fluidMb = Math.max(0, input.getIntOr("fluid_mb", 0));
        pressure = Math.max(0, Math.min(RoomPressure.MAX_PRESSURE, input.getIntOr("pressure", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy", energy);
        output.putInt("progress", progress);
        output.putInt("oxygen", oxygen);
        output.putInt("fluid_mb", fluidMb);
        output.putInt("pressure", pressure);
    }

    @Override
    public void setRemoved() {
        unregisterRoom();
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        unregisterRoom();
        super.preRemoveSideEffects(pos, state);
    }

    private void unregisterRoom() {
        if (registered && level != null) {
            PressurizedRooms.remove(level, worldPosition);
            registered = false;
        }
    }

    // ---- tick ----

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, MoonBlockEntity be) {
        boolean changed = be.step(level, pos);
        boolean lit = be.isLit();
        if (state.getValue(MoonBlock.LIT) != lit) {
            state = state.setValue(MoonBlock.LIT, lit);
            level.setBlockAndUpdate(pos, state);
        }
        if (changed && level.getGameTime() % SAVE_INTERVAL_TICKS == 0) {
            setChanged(level, pos, state);
        }
    }

    private boolean isLit() {
        return switch (kind) {
            case SOLAR_PANEL -> generation > 0;
            case OXYGEN_GENERATOR -> status == MachineStatus.PRESSURIZED;
            case HELIUM3_EXTRACTOR, ELECTROLYZER, LUNAR_REFINERY -> status == MachineStatus.WORKING;
            default -> false;
        };
    }

    /** Um tick completo; devolve se algo mudou (para marcar o bloco como sujo). */
    private boolean step(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        switch (kind) {
            case SOLAR_PANEL -> changed |= tickSolar(level, pos);
            case OXYGEN_GENERATOR -> changed |= tickGenerator(level, pos);
            case HELIUM3_EXTRACTOR -> changed |= tickExtractor(level);
            case ELECTROLYZER -> changed |= tickElectrolyzer();
            case LUNAR_REFINERY -> changed |= tickRefinery();
            default -> {
            }
        }
        changed |= pushEnergy(level, pos);
        return changed;
    }

    /** Empurra energia a cada vizinho por face (a regra está em {@link EnergyMath#flow}). */
    private boolean pushEnergy(ServerLevel level, BlockPos pos) {
        if (kind.role() == Role.CONSUMER || energy <= 0) {
            return false;
        }
        boolean moved = false;
        for (Direction direction : Direction.values()) {
            if (energy <= 0) {
                break;
            }
            if (level.getBlockEntity(pos.relative(direction)) instanceof MoonBlockEntity other) {
                int amount = EnergyMath.flow(kind.role(), energy, kind.capacity(), other.kind.role(), other.energy, other.kind.capacity());
                if (amount > 0) {
                    energy -= amount;
                    other.energy += amount;
                    moved = true;
                }
            }
        }
        return moved;
    }

    // ---- painel solar ----

    private boolean tickSolar(ServerLevel level, BlockPos pos) {
        if (!sunKnown || level.getGameTime() % 20 == 0) {
            sunUp = sunHeight(level, pos);
            sunKnown = true;
        }
        generation = EnergyMath.solarOutput(sunUp, level.canSeeSky(pos.above()));
        int before = energy;
        energy = EnergyMath.add(energy, kind.capacity(), generation);
        return energy != before;
    }

    /** Altura do sol no ponto (seno da elevação) no relógio do espaço; no espaço aberto o sol está sempre alto. */
    private static double sunHeight(Level level, BlockPos pos) {
        var planet = Planets.forLevel(level);
        if (planet.isEmpty()) {
            return SpaceDimension.is(level) ? 1.0 : 0.0;
        }
        double[] sun = new double[3];
        SunModel.localDirection(SolarSystem.skyBodyOf(planet.get()), pos.getX() + 0.5, pos.getZ() + 0.5, SpaceClock.ticks(level), sun);
        return sun[1];
    }

    // ---- gerador de oxigênio ----

    private boolean tickGenerator(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        ItemStack tank = items.get(0);
        ItemStack ice = items.get(1);
        if (OxygenStorage.isContainer(tank) && oxygen < RoomPressure.BUFFER_CAPACITY) {
            int moved = MoonMachineMath.transfer(OxygenStorage.get(tank), RoomPressure.BUFFER_CAPACITY - oxygen,
                    MoonMachineMath.GENERATOR_TANK_DRAW_PER_TICK);
            if (moved > 0) {
                OxygenStorage.extract(tank, moved);
                oxygen += moved;
                changed = true;
            }
        }
        if (isIce(ice) && oxygen + MoonMachineMath.GENERATOR_O2_PER_ICE <= RoomPressure.BUFFER_CAPACITY) {
            ice.shrink(1);
            oxygen += MoonMachineMath.GENERATOR_O2_PER_ICE;
            changed = true;
        }
        if ((level.getGameTime() + pos.asLong()) % RoomPressure.SCAN_INTERVAL_TICKS == 0) {
            scanRoom(level, pos);
            changed = true;
        }
        return changed;
    }

    /** Varre a sala em volta e avança a pressão; registra a sala para o sistema de oxigênio. */
    private void scanRoom(ServerLevel level, BlockPos pos) {
        RoomSpace space = new RoomSpace(level);
        RoomScan.Result result = new RoomScan.Result(RoomScan.Status.BLOCKED, 0, new LongOpenHashSet());
        for (Direction direction : Direction.values()) {
            BlockPos start = pos.relative(direction);
            if (space.at(start.getX(), start.getY(), start.getZ()) == RoomScan.Cell.AIR) {
                result = RoomScan.scan(space, start.getX(), start.getY(), start.getZ());
                break;
            }
        }
        if (result.sealed()) {
            lastCells = result.cells();
            lastVolume = result.volume();
        }
        boolean starvedPower = energy < RoomPressure.POWER_PER_SCAN;
        boolean starvedAir = oxygen < RoomPressure.maintenance(lastVolume);
        RoomPressure.Result next = RoomPressure.step(result.status(), result.volume(), pressure, oxygen, energy);
        pressure = next.pressure();
        oxygen = next.oxygen();
        energy = next.energy();
        status = switch (result.status()) {
            case LEAK -> MachineStatus.LEAK;
            case TOO_BIG -> MachineStatus.TOO_BIG;
            case BLOCKED -> MachineStatus.BLOCKED;
            case SEALED -> starvedPower ? MachineStatus.NO_POWER : starvedAir ? MachineStatus.NO_OXYGEN
                    : next.breathable() ? MachineStatus.PRESSURIZED : MachineStatus.PRESSURIZING;
        };
        if (pressure > 0 && lastCells != null) {
            PressurizedRooms.put(level, pos, lastCells, pressure);
            registered = true;
            if (status == MachineStatus.PRESSURIZED) {
                for (var player : level.players()) {
                    if (lastCells.contains(BlockPos.containing(player.getEyePosition()).asLong())) {
                        MoonTriggers.SEALED_ROOM.get().trigger(player);
                    }
                }
            }
        } else {
            PressurizedRooms.remove(level, pos);
            registered = false;
        }
    }

    // ---- extrator de hélio-3 ----

    private boolean tickExtractor(ServerLevel level) {
        ItemStack input = items.get(0);
        ItemStack canister = items.get(2);
        boolean hasInput = isRegolith(input);
        boolean canisterPresent = isCanister(canister, Propellant.HELIUM3);
        boolean room = canisterPresent && CanisterFuel.space(FuelCanisterItem.getFuel(canister)) >= MoonMachineMath.HELIUM3_PER_REGOLITH_MB;
        boolean powered = energy >= MoonMachineMath.EXTRACTOR_POWER;
        boolean moon = isMoon(level);
        MachineStatus now;
        if (!hasInput) {
            now = MachineStatus.NO_INPUT;
        } else if (!moon) {
            now = MachineStatus.WRONG_PLANET;
        } else if (!canisterPresent) {
            now = MachineStatus.NO_INPUT;
        } else if (!room) {
            now = MachineStatus.OUTPUT_FULL;
        } else if (!powered) {
            now = MachineStatus.NO_POWER;
        } else {
            now = MachineStatus.WORKING;
        }
        boolean working = now == MachineStatus.WORKING;
        if (working) {
            energy -= MoonMachineMath.EXTRACTOR_POWER;
        }
        MoonMachineMath.Step stepped = MoonMachineMath.advance(working, progress, MoonMachineMath.EXTRACTOR_TICKS);
        progress = stepped.progress();
        if (stepped.done()) {
            input.shrink(1);
            FuelCanisterItem.setFuel(canister, FuelCanisterItem.getFuel(canister) + MoonMachineMath.HELIUM3_PER_REGOLITH_MB);
        }
        boolean changed = now != status || working || stepped.done();
        status = now;
        return changed;
    }

    // ---- eletrolisador ----

    private boolean tickElectrolyzer() {
        ItemStack ice = items.get(0);
        boolean hasIce = isIce(ice);
        boolean room = MoonMachineMath.canElectrolyze(fluidMb, oxygen);
        boolean powered = energy >= MoonMachineMath.ELECTROLYSIS_POWER;
        MachineStatus now = !hasIce ? MachineStatus.NO_INPUT : !room ? MachineStatus.OUTPUT_FULL
                : !powered ? MachineStatus.NO_POWER : MachineStatus.WORKING;
        boolean working = now == MachineStatus.WORKING;
        if (working) {
            energy -= MoonMachineMath.ELECTROLYSIS_POWER;
        }
        MoonMachineMath.Step stepped = MoonMachineMath.advance(working, progress, MoonMachineMath.ELECTROLYSIS_TICKS);
        progress = stepped.progress();
        boolean changed = now != status || working;
        if (stepped.done()) {
            ice.shrink(1);
            fluidMb += MoonMachineMath.ICE_H2_MB;
            oxygen += MoonMachineMath.ICE_O2_UNITS;
            changed = true;
        }
        // saídas: oxigênio para o tanque do slot 1, hidrogênio para o galão do slot 2
        ItemStack tank = items.get(1);
        if (OxygenStorage.isContainer(tank) && oxygen > 0) {
            int space = OxygenAmounts.acceptable(OxygenStorage.get(tank), OxygenStorage.capacity(tank), Integer.MAX_VALUE);
            int moved = MoonMachineMath.transfer(oxygen, space, MoonMachineMath.O2_OUTPUT_PER_TICK);
            if (moved > 0) {
                OxygenStorage.insert(tank, moved);
                oxygen -= moved;
                changed = true;
            }
        }
        ItemStack canister = items.get(2);
        if (isCanister(canister, Propellant.HYDROGEN) && fluidMb > 0) {
            int moved = MoonMachineMath.transfer(fluidMb, CanisterFuel.space(FuelCanisterItem.getFuel(canister)), MoonMachineMath.H2_OUTPUT_PER_TICK);
            if (moved > 0) {
                FuelCanisterItem.setFuel(canister, FuelCanisterItem.getFuel(canister) + moved);
                fluidMb -= moved;
                changed = true;
            }
        }
        status = now;
        return changed;
    }

    // ---- refinaria lunar ----

    private boolean tickRefinery() {
        ItemStack ice = items.get(0);
        boolean hasIce = isIce(ice);
        boolean room = MoonMachineMath.canRefine(fluidMb);
        boolean powered = energy >= MoonMachineMath.REFINERY_POWER;
        MachineStatus now = !hasIce ? MachineStatus.NO_INPUT : !room ? MachineStatus.OUTPUT_FULL
                : !powered ? MachineStatus.NO_POWER : MachineStatus.WORKING;
        boolean working = now == MachineStatus.WORKING;
        if (working) {
            energy -= MoonMachineMath.REFINERY_POWER;
        }
        MoonMachineMath.Step stepped = MoonMachineMath.advance(working, progress, MoonMachineMath.REFINERY_TICKS);
        progress = stepped.progress();
        boolean changed = now != status || working;
        if (stepped.done()) {
            ice.shrink(1);
            fluidMb += MoonMachineMath.REFINERY_FUEL_PER_ICE_MB;
            changed = true;
        }
        ItemStack canister = items.get(2);
        if (isCanister(canister, Propellant.CHEMICAL) && fluidMb > 0) {
            int moved = MoonMachineMath.transfer(fluidMb, CanisterFuel.space(FuelCanisterItem.getFuel(canister)), MoonMachineMath.FUEL_OUTPUT_PER_TICK);
            if (moved > 0) {
                FuelCanisterItem.setFuel(canister, FuelCanisterItem.getFuel(canister) + moved);
                fluidMb -= moved;
                changed = true;
            }
        }
        status = now;
        return changed;
    }

    // ---- classificação de itens ----

    public static boolean isIce(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ICE_FEED);
    }

    /** Bloco de onde se extrai hélio-3 (a mesma tag do clique do galão: regolito lunar, pedra, cascalho...). */
    public static boolean isRegolith(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem block
                && block.getBlock().defaultBlockState().is(FuelCanisterItem.HELIUM3_SOURCES);
    }

    private static boolean isCanister(ItemStack stack, Propellant propellant) {
        return !stack.isEmpty() && FuelCanisterItem.propellantOf(stack) == propellant;
    }

    private static boolean isMoon(Level level) {
        return Planets.forLevel(level).map(p -> p.id().equals(Planets.MOON.id())).orElse(false);
    }

    /** Texto curto para a mensagem de clique nos blocos sem menu (painel, bateria, cabo). */
    public Component describe() {
        return switch (kind) {
            case SOLAR_PANEL -> Component.translatable("message.spacehorizons.solar_panel", generation, energy, kind.capacity());
            case BATTERY -> Component.translatable("message.spacehorizons.battery", energy, kind.capacity());
            default -> Component.translatable("message.spacehorizons.power_cable", energy, kind.capacity());
        };
    }

    // ---- Container ----

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.spacehorizons." + kind.id());
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MoonMachineMenu(containerId, inventory, kind, this, dataAccess);
    }

    @Override
    public int getContainerSize() {
        return MachineKind.SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!kind.slotUsed(slot)) {
            return false;
        }
        return switch (kind) {
            case OXYGEN_GENERATOR -> slot == 0 ? OxygenStorage.isContainer(stack) : isIce(stack);
            case HELIUM3_EXTRACTOR -> slot == 0 ? isRegolith(stack) : isCanister(stack, Propellant.HELIUM3);
            case ELECTROLYZER -> switch (slot) {
                case 0 -> isIce(stack);
                case 1 -> OxygenStorage.isContainer(stack);
                default -> isCanister(stack, Propellant.HYDROGEN);
            };
            case LUNAR_REFINERY -> slot == 0 ? isIce(stack) : isCanister(stack, Propellant.CHEMICAL);
            default -> false;
        };
    }

    // ---- WorldlyContainer (funil e automação) ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return kind.hasMenu() ? ALL_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // só o produto pronto (tanque ou galão cheio) sai, e só por baixo
        if (side != Direction.DOWN || !kind.slotIsOutput(slot)) {
            return false;
        }
        if (OxygenStorage.isContainer(stack)) {
            return OxygenStorage.get(stack) >= OxygenStorage.capacity(stack);
        }
        return stack.getItem() instanceof FuelCanisterItem && FuelCanisterItem.getFuel(stack) >= CanisterFuel.CAPACITY;
    }

    /** Item de gelo lunar do mod (para testes e receitas). */
    public static ItemStack iceStack(int count) {
        return new ItemStack(ModItems.LUNAR_ICE.get(), count);
    }
}
