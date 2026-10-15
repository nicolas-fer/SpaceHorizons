package com.spacehorizons.client.atmosphere;

import java.util.Optional;

import com.spacehorizons.client.space.SpaceSun;

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
            // cloudEnd fica como veio: sem fade as nuvens vanilla (camada a milhares de blocos) apareceriam no espaço
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
     * Põe o sol real no céu, escurece o disco do céu, apaga o brilho de nascer/pôr do sol e liga as estrelas. O estado do
     * céu é extraído pelo vanilla antes deste evento e consumido só no render, então é o ponto certo para editar.
     *
     * A cor do céu, o brilho de nascer/pôr do sol e a luz do chão já vêm do sol do ponto: os atributos de ambiente são
     * avaliados no tick efetivo do SunModel (AttributeTrackSamplerMixin). Aqui entram o disco (ângulo e giro de azimute,
     * que o SkyRendererMixin aplica) e o campo de estrelas, direto do SunModel, sem a quantização em ticks do atributo.
     */
    static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        SkyRenderState sky = event.getRenderState().skyRenderState;
        Optional<AtmosphereSample> sample = AtmosphereSample.of(event.getLevel(), event.getCamera().position().y);
        if (sample.isEmpty()) {
            SpaceSun.clearView();
            return;
        }
        AtmosphereModel model = sample.get().model();
        // posição do jogador (e não da Camera): no primeiro quadro depois de uma troca de dimensão a Camera ainda guarda a do mundo antigo
        net.minecraft.world.entity.Entity cameraEntity = event.getCamera().entity();
        SpaceSun.update(event.getLevel(), sample.get().planet(),
                cameraEntity != null ? cameraEntity.getX() : event.getCamera().position().x,
                cameraEntity != null ? cameraEntity.getZ() : event.getCamera().position().z);
        boolean synthesizedSky = false;
        if (!model.present() && sky.skybox == DimensionType.Skybox.NONE) {
            synthesizedSky = true;
            // mundo sem ar cujo tipo de dimensão não tem céu: liga o céu do overworld, preto e estrelado
            sky.skybox = DimensionType.Skybox.OVERWORLD;
            sky.skyColor = new Vector3f();
            sky.sunriseAndSunsetColor = new Vector4f();
            sky.rainBrightness = 1.0F;
            sky.starBrightness = 1.0F;
            sky.moonAngle = (float) Math.PI; // sem lua vanilla (o corpo de verdade é desenhado por client/space)
        }
        if (sky.skybox == DimensionType.Skybox.OVERWORLD) {
            // sol único do sistema: o disco do vanilla segue a mesma direção que ilumina corpos e malha (o giro de azimute,
            // para latitudes altas, vem do SkyRendererMixin); estrelas fixas no espaço
            sky.sunAngle = SpaceSun.skyAngle();
            sky.starAngle = SpaceSun.starAngle();
        }
        if (synthesizedSky || sky.skybox != DimensionType.Skybox.OVERWORLD || sky.skyColor == null) {
            return;
        }
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
