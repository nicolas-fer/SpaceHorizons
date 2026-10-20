package com.spacehorizons.client.rocket;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.LaunchPadBlock;
import com.spacehorizons.rocket.RocketDesign;
import com.spacehorizons.rocket.RocketDesignPayload;
import com.spacehorizons.rocket.RocketDesignRequestPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * O painel de projeto da plataforma de lançamento, no cliente (docs/PLANO_TIERS.md, seção 4). Aparece, em tempo real, enquanto se olha para
 * uma plataforma de lançamento de mão vazia; o clique direito de mão vazia nela FIXA o painel (fica aberto andando em volta, até 48 blocos,
 * mesmo com blocos na mão) e outro clique solta. O cliente pede o relatório ao servidor a cada 10 ticks ({@link RocketDesignRequestPayload});
 * quem calcula é o servidor, com o MESMO cálculo da montagem ({@link RocketDesign}). Os problemas são destacados no mundo com um contorno de
 * partículas no bloco (vermelho: impede a montagem; amarelo: aviso de projeto).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketDesignClient {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_design");
    /** Ticks entre dois pedidos ao servidor. */
    private static final int REQUEST_INTERVAL = 10;
    /** Ticks entre dois contornos de partículas. */
    private static final int OUTLINE_INTERVAL = 4;
    /** O painel fixado some se o jogador se afasta mais que isto da plataforma. */
    private static final double PIN_RANGE = 48.0;
    /** O relatório vale por este tempo (ticks) sem resposta nova. */
    private static final long REPORT_LIFETIME = 60;

    private static final int COLOR_BG = 0xB00C1218;
    private static final int COLOR_BORDER = 0xFF2C3E50;
    private static final int COLOR_TITLE = 0xFF4FC3F7;
    private static final int COLOR_LABEL = 0xFF9FB4C8;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_GOOD = 0xFF66E08A;
    private static final int COLOR_WARN = 0xFFFFC857;
    private static final int COLOR_BAD = 0xFFFF5A4F;
    private static final int COLOR_DIM = 0xFF6B7C8C;

    private static volatile @Nullable BlockPos pinned;
    private static volatile @Nullable DesignReport latest;
    private static volatile long latestAt;
    private static @Nullable BlockPos active;
    private static long lastRequest = Long.MIN_VALUE;

    private RocketDesignClient() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, RocketDesignClient::render);
        // o clique de mão vazia na plataforma fixa ou solta o painel (o bloco só chama este gancho; um servidor dedicado nunca o liga)
        LaunchPadBlock.CLIENT_PRESS = RocketDesignClient::onPress;
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(RocketDesignPayload.TYPE, (payload, context) -> {
            latest = payload.report();
            latestAt = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        });
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pinned = null;
        latest = null;
        active = null;
    }

    private static void onPress(BlockPos pad) {
        BlockPos current = pinned;
        pinned = current != null && current.equals(pad) ? null : pad;
        lastRequest = Long.MIN_VALUE;
    }

    /** Para o autoteste: fixa (ou solta, com null) o painel numa plataforma, como o clique faria. */
    public static void pinForTest(@Nullable BlockPos pad) {
        pinned = pad;
        lastRequest = Long.MIN_VALUE;
    }

    /** O último relatório recebido (para o autoteste logar o que o painel diz). */
    public static @Nullable DesignReport lastReport() {
        return latest;
    }

    /** A plataforma que o painel mostra agora, ou null. */
    public static @Nullable BlockPos activePad() {
        return active;
    }

    /** A plataforma cujo painel está aberto: a fixada (ao alcance) ou a que o jogador olha de mão vazia. */
    private static @Nullable BlockPos wantedPad(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos fixed = pinned;
        if (fixed != null) {
            if (mc.player.distanceToSqr(fixed.getX() + 0.5, fixed.getY() + 0.5, fixed.getZ() + 0.5) <= PIN_RANGE * PIN_RANGE
                    && mc.level.getBlockState(fixed).is(ModBlocks.LAUNCH_PAD.get())) {
                return fixed;
            }
            pinned = null;
        }
        if (mc.gui.screen() == null && mc.player.getMainHandItem().isEmpty()
                && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).is(ModBlocks.LAUNCH_PAD.get())) {
            return hit.getBlockPos();
        }
        return null;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pad = wantedPad(mc);
        if (!java.util.Objects.equals(pad, active)) {
            active = pad;
            lastRequest = Long.MIN_VALUE;
        }
        if (pad == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastRequest >= REQUEST_INTERVAL || now < lastRequest) {
            lastRequest = now;
            ClientPacketDistributor.sendToServer(new RocketDesignRequestPayload(pad));
        }
        DesignReport report = current(pad, now);
        if (report != null && now % OUTLINE_INTERVAL == 0) {
            outline(mc, report);
        }
    }

    /** O relatório da plataforma {@code pad}, se for dela e recente. */
    private static @Nullable DesignReport current(BlockPos pad, long now) {
        DesignReport r = latest;
        if (r == null || r.padX() != pad.getX() || r.padY() != pad.getY() || r.padZ() != pad.getZ()) {
            return null;
        }
        return Math.abs(now - latestAt) <= REPORT_LIFETIME ? r : null;
    }

    // ---------------------------------------------------------------- destaque no mundo

    /** O contorno de partículas de cada bloco com problema: vermelho se impede a montagem, amarelo se é aviso. */
    private static void outline(Minecraft mc, DesignReport report) {
        RandomSource random = mc.level.getRandom();
        for (DesignReport.Problem p : report.problems()) {
            DustParticleOptions dust = new DustParticleOptions(p.type().blocking() ? 0xFF3A30 : 0xFFD040, 1.2F);
            for (int axis = 0; axis < 3; axis++) {
                // as 12 arestas do cubo: 4 paralelas a cada eixo; um ponto aleatório em cada, a cada contorno
                for (int bits = 0; bits < 4; bits++) {
                    double[] d = new double[3];
                    int other = 0;
                    for (int i = 0; i < 3; i++) {
                        d[i] = i == axis ? random.nextDouble() : (bits >> other++) & 1;
                    }
                    double dx = d[0];
                    double dy = d[1];
                    double dz = d[2];
                    mc.level.addParticle(dust, p.x() + dx, p.y() + dy, p.z() + dz, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- painel

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pad = active;
        if (pad == null || mc.level == null) {
            return;
        }
        DesignReport r = current(pad, mc.level.getGameTime());
        Font font = mc.font;
        List<RocketPanelModel.Row> rows = r == null ? List.of() : RocketDesignModel.rows(r);
        List<RocketDesignModel.DestinationRow> destinations = r == null ? List.of() : RocketDesignModel.destinations(r);
        List<RocketDesignModel.ProblemLine> problems = r == null ? List.of() : RocketDesignModel.problems(r);

        int width = 214;
        int lineHeight = 10;
        int height = 16 + (r == null || !r.found() ? lineHeight : rows.size() * lineHeight + 4 + lineHeight + destinations.size() * lineHeight + 4);
        if (!problems.isEmpty()) {
            height += 4 + problems.size() * lineHeight;
        }
        if (r != null && r.found() && RocketDesignModel.missingForNext(r) != null) {
            height += lineHeight;
        }
        int x = g.guiWidth() - width - 8;
        int y = Math.max(8, (g.guiHeight() - height) / 2 - 20);
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, COLOR_BORDER);
        g.fill(x, y, x + width, y + height, COLOR_BG);
        Component title = Component.translatable(pinned != null ? "design.spacehorizons.title_pinned" : "design.spacehorizons.title");
        g.text(font, title, x + 5, y + 4, COLOR_TITLE);
        int ry = y + 16;
        if (r == null) {
            g.text(font, Component.translatable("design.spacehorizons.loading"), x + 5, ry, COLOR_LABEL);
            return;
        }
        if (!r.found()) {
            g.text(font, Component.translatable("design.spacehorizons.nothing"), x + 5, ry, COLOR_WARN);
            return;
        }
        for (RocketPanelModel.Row row : rows) {
            RocketPanel.row(g, font, x, ry, width, Component.translatable(row.labelKey()), row.value(), RocketPanel.toneColor(row.tone()));
            ry += lineHeight;
        }
        ry += 4;
        g.text(font, Component.translatable("design.spacehorizons.destinations"), x + 5, ry, COLOR_TITLE);
        ry += lineHeight;
        for (RocketDesignModel.DestinationRow d : destinations) {
            int color = switch (d.state()) {
                case BEST -> COLOR_GOOD;
                case REACHED -> COLOR_VALUE;
                case MISSING -> COLOR_DIM;
            };
            String mark = d.state() == RocketDesignModel.State.MISSING ? "  " : d.state() == RocketDesignModel.State.BEST ? "> " : "+ ";
            g.text(font, Component.literal(mark).append(Component.translatable(d.nameKey())), x + 5, ry, color);
            String need = d.required();
            g.text(font, need, x + width - 5 - font.width(need), ry, d.state() == RocketDesignModel.State.MISSING ? COLOR_DIM : COLOR_LABEL);
            ry += lineHeight;
        }
        String missing = RocketDesignModel.missingForNext(r);
        if (missing != null) {
            g.text(font, Component.translatable("design.spacehorizons.missing_next", missing), x + 5, ry, COLOR_WARN);
            ry += lineHeight;
        }
        if (r.destinationValue().ordinal() <= com.spacehorizons.rocket.MissionBudget.Destination.HOP.ordinal()) {
            g.text(font, Component.translatable(RocketDesignModel.headlineKey(r)), x + 5, ry, COLOR_BAD);
            ry += lineHeight;
        }
        if (!problems.isEmpty()) {
            ry += 4;
            for (RocketDesignModel.ProblemLine p : problems) {
                g.text(font, Component.translatable(p.key(), p.args()), x + 5, ry, p.blocking() ? COLOR_BAD : COLOR_WARN);
                ry += lineHeight;
            }
        }
    }
}
