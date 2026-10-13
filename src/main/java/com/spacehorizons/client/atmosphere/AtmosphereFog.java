package com.spacehorizons.client.atmosphere;

import java.util.Optional;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * Neblina e céu em função da altitude. Só mexe na neblina atmosférica: água, lava, neve em pó,
 * cegueira e escuridão têm ambiente próprio no vanilla e ficam intactos.
 */
final class AtmosphereFog {
    private AtmosphereFog() {
    }

    /** Empurra as distâncias de neblina para longe e as anula acima da atmosfera. */
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!(event.getEnvironment() instanceof AtmosphericFogEnvironment)) {
            return;
        }
        Camera camera = event.getCamera();
        Optional<AtmosphereSample> sample = AtmosphereSample.of(camera.entity().level(), camera.position().y);
        if (sample.isEmpty()) {
            return;
        }
        AtmosphereModel model = sample.get().model();
        double altitude = sample.get().altitude();
        FogData fog = event.getFogData();
        if (model.isVacuum(altitude)) {
            // mesmo valor do buffer "sem neblina" do vanilla
            fog.environmentalStart = Float.MAX_VALUE;
            fog.environmentalEnd = Float.MAX_VALUE;
            fog.renderDistanceStart = Float.MAX_VALUE;
            fog.renderDistanceEnd = Float.MAX_VALUE;
            fog.cloudEnd = Float.MAX_VALUE;
            return;
        }
        float factor = (float) model.fogDistanceFactor(altitude);
        if (factor <= 1.0F) {
            return;
        }
        // skyEnd fica: o disco do céu mistura com a cor da neblina, que escurece junto com o céu
        fog.environmentalStart *= factor;
        fog.environmentalEnd *= factor;
        fog.renderDistanceStart *= factor;
        fog.renderDistanceEnd *= factor;
        fog.cloudEnd *= factor;
    }

    /** Escurece a cor da neblina junto com o céu, para o horizonte também ir a preto no espaço. */
    static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        Optional<AtmosphereSample> sample = AtmosphereSample.of(camera.entity().level(), camera.position().y);
        if (sample.isEmpty()) {
            return;
        }
        float brightness = (float) sample.get().model().skyBrightness(sample.get().altitude());
        if (brightness >= 1.0F) {
            return;
        }
        event.setRed(event.getRed() * brightness);
        event.setGreen(event.getGreen() * brightness);
        event.setBlue(event.getBlue() * brightness);
    }

    /**
     * Escurece o disco do céu, apaga o brilho de nascer/pôr do sol e liga as estrelas. O estado do céu é
     * extraído pelo vanilla antes deste evento e consumido só no render, então é o ponto certo para editar.
     */
    static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        SkyRenderState sky = event.getRenderState().skyRenderState;
        if (sky.skybox != DimensionType.Skybox.OVERWORLD || sky.skyColor == null) {
            return;
        }
        Optional<AtmosphereSample> sample = AtmosphereSample.of(event.getLevel(), event.getCamera().position().y);
        if (sample.isEmpty()) {
            return;
        }
        AtmosphereModel model = sample.get().model();
        double altitude = sample.get().altitude();
        float brightness = (float) model.skyBrightness(altitude);
        if (brightness >= 1.0F) {
            return;
        }
        sky.skyColor = new Vector3f(sky.skyColor).mul(brightness);
        sky.starBrightness = Math.max(sky.starBrightness, (float) model.starVisibility(altitude));
        Vector4fc sunrise = sky.sunriseAndSunsetColor;
        if (sunrise != null) {
            sky.sunriseAndSunsetColor = new Vector4f(sunrise.x(), sunrise.y(), sunrise.z(), sunrise.w() * brightness);
        }
    }
}
