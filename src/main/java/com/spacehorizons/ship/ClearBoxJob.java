package com.spacehorizons.ship;

import java.util.List;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Limpa a caixa de uma nave em lotes (seções com blocos, sem soltar itens nem atualizar vizinhos), solta os tickets e esquece a nave
 * (a célula vai para a quarentena do alocador). Usado para desfazer uma conversão que não terminou: o servidor fechou no meio
 * ({@link ShipConversions#recover}) ou um job falhou.
 */
final class ClearBoxJob extends ConversionJob {
    private final MinecraftServer server;
    private final int shipId;
    private final String why;
    private final ServerLevel yard;
    private final ShipYard.Box box;
    private final List<BoxSections.Section> sections;
    private int next;
    private int cleared;

    ClearBoxJob(MinecraftServer server, int shipId, String why) {
        super("limpeza da caixa", 0.0);
        this.server = server;
        this.shipId = shipId;
        this.why = why;
        this.yard = ShipYard.level(server);
        ShipRecord ship = ShipRegistry.get(server).byId(shipId);
        this.box = ship == null ? null : ship.box();
        this.sections = this.box == null ? List.of() : BoxSections.of(this.box);
        if (ship != null) {
            ShipChunkTickets.hold(this.yard, ship, this.yard.getGameTime());
        }
    }

    @Override
    int shipId() {
        return this.shipId;
    }

    @Override
    int[] counts() {
        return new int[] {this.cleared, 0, 0};
    }

    @Override
    boolean step(MinecraftServer server, ConversionBudget budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (this.next < this.sections.size() && !budget.exhausted()) {
            BoxSections.Section s = this.sections.get(this.next++);
            LevelChunkSection section = BoxSections.sectionOf(this.yard, s);
            if (section.hasOnlyAir()) {
                budget.spend(1);
                continue;
            }
            budget.spend(BoxSections.SECTION_COST);
            int[] c = BoxSections.clip(this.box, s);
            for (int y = c[2]; y <= c[3]; y++) {
                for (int z = c[4]; z <= c[5]; z++) {
                    for (int x = c[0]; x <= c[1]; x++) {
                        p.set(x, y, z);
                        if (!this.yard.getBlockState(p).isAir()) {
                            BoxSections.clearAt(this.yard, p);
                            this.cleared++;
                        }
                    }
                }
            }
        }
        if (this.next < this.sections.size()) {
            return false;
        }
        finish();
        return true;
    }

    private void finish() {
        ShipRegistry registry = ShipRegistry.get(this.server);
        ShipRecord ship = registry.byId(this.shipId);
        if (ship != null) {
            ShipChunkTickets.release(this.yard, ship);
            registry.destroy(this.shipId);
        }
        this.notes.add("caixa limpa (" + this.why + "): " + this.cleared + " blocos removidos");
        SpaceHorizons.LOGGER.info("[ship-conv] nave {}: caixa limpa ({}), {} blocos removidos, célula liberada", this.shipId, this.why, this.cleared);
    }

    @Override
    void abort(MinecraftServer server, String reason) {
        SpaceHorizons.LOGGER.error("[ship-conv] a limpeza da caixa da nave {} falhou: {}", this.shipId, reason);
    }
}
