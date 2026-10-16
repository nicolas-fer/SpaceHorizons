package com.spacehorizons.client.space;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * O céu visto de dentro da dimensão de espaço aberto: sol e estrelas do vanilla com as direções certas, preto no
 * resto, sem lua do vanilla, sem névoa, sem nuvens nem chuva. Ligada por {@link BodyClient#init}.
 *
 * Os eixos da dimensão são os do spawn da Terra ({@link SpaceDimension}), então o sol do vanilla e o campo de estrelas
 * saem do mesmo cálculo do spawn da Terra, sem inclinação nem rotação extra: {@code SpaceSun.update(level, EARTH, 0, 0)}.
 * Só o sol e as estrelas são do céu do vanilla; a Terra e a Lua são as esferas do {@link BodyRenderer}.
 *
 * O handler de extração roda DEPOIS do da atmosfera (prioridade baixa): fora de um planeta ele só limpa o giro do sol
 * ({@code SpaceSun.clearView}), e aqui entram o que o espaço precisa.
 */
final class SpaceSkyClient {
    private SpaceSkyClient() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SpaceSkyClient::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(SpaceSkyClient::onRenderFog);
    }

    private static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        if (!SpaceDimension.is(event.getLevel())) {
            SpaceSun.setInSpace(false);
            return;
        }
        SpaceSun.setInSpace(true);
        SkyRenderState sky = event.getRenderState().skyRenderState;
        // ângulo do sol e das estrelas do spawn da Terra (a rotação das estrelas sai identidade: ver SpaceSun.update)
        SpaceSun.update(event.getLevel(), Planets.EARTH, 0.0, 0.0);
        sky.sunAngle = SpaceSun.skyAngle();
        sky.starAngle = SpaceSun.starAngle();
        sky.rainBrightness = 1.0F;
        sky.starBrightness = 1.0F;
        sky.skyColor = new Vector3f();
        sky.sunriseAndSunsetColor = new Vector4f();
        // sem lua do vanilla (SkyRendererMixin cancela o desenho) e sem o disco escuro do horizonte
        sky.moonAngle = (float) Math.PI;
        sky.shouldRenderDarkDisc = false;
    }

    /** Sem névoa no espaço: o mesmo valor do buffer "sem neblina" do vanilla (como o ramo de vácuo de AtmosphereFog). */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!(event.getEnvironment() instanceof AtmosphericFogEnvironment) || !SpaceDimension.is(event.getCamera().entity().level())) {
            return;
        }
        FogData fog = event.getFogData();
        fog.environmentalStart = Float.MAX_VALUE;
        fog.environmentalEnd = Float.MAX_VALUE;
        fog.renderDistanceStart = Float.MAX_VALUE;
        fog.renderDistanceEnd = Float.MAX_VALUE;
    }
}
