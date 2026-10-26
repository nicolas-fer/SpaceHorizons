package com.spacehorizons.client.rocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Malhas por retrato de foguete, assadas uma vez em worker thread. Só o render thread mexe no mapa.
 * Os UVs vêm do atlas de blocos: depois de recarregar recursos o atlas muda, então o cache é limpo
 * (o EntityRenderDispatcher recria todos os renderers na recarga, então o construtor do RocketRenderer limpa).
 */
final class RocketMeshCache {
    private final Map<UUID, CompletableFuture<PortraitMesh>> meshes = new HashMap<>();
    /** A malha do retrato anterior, mostrada enquanto a do retrato novo (chave) ainda assa. */
    private final Map<UUID, PortraitMesh> stopgap = new HashMap<>();

    /** null enquanto a malha ainda assa (o foguete simplesmente não aparece nesses frames). */
    @Nullable PortraitMesh get(RocketPortrait portrait) {
        CompletableFuture<PortraitMesh> future = this.meshes.get(portrait.id());
        if (future == null) {
            Minecraft minecraft = Minecraft.getInstance();
            // opções lidas aqui, no render thread; o worker só recebe valores
            boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
            boolean cutoutLeaves = minecraft.options.cutoutLeaves().get();
            BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
            BlockColors colors = minecraft.getBlockColors();
            future = CompletableFuture.supplyAsync(() -> PortraitMesh.bake(portrait, ambientOcclusion, cutoutLeaves, models, colors),
                    Util.backgroundExecutor());
            future.exceptionally(t -> {
                SpaceHorizons.LOGGER.error("Falha ao assar a malha do foguete {}", portrait.id(), t);
                return null;
            });
            this.meshes.put(portrait.id(), future);
        }
        if (future.isDone() && !future.isCompletedExceptionally()) {
            this.stopgap.remove(portrait.id());
            return future.join();
        }
        return this.stopgap.get(portrait.id());
    }

    /** Quantas malhas assadas (ou assando); para o autoteste. */
    int size() {
        return this.meshes.size();
    }

    /**
     * Um retrato foi trocado por outro (porta aberta, conteúdo de baú): esquece a malha do antigo (sem isto cada clique deixaria uma malha inteira
     * na memória) mas a mostra no lugar do novo até ele terminar de assar, para a nave não sumir por alguns quadros.
     */
    void replaced(UUID oldId, UUID newId) {
        CompletableFuture<PortraitMesh> old = this.meshes.remove(oldId);
        if (old != null && old.isDone() && !old.isCompletedExceptionally()) {
            this.stopgap.put(newId, old.join());
        } else if (this.stopgap.containsKey(oldId)) {
            this.stopgap.put(newId, this.stopgap.remove(oldId));
        }
    }

    void clear() {
        this.stopgap.clear();
        this.meshes.clear();
    }
}
