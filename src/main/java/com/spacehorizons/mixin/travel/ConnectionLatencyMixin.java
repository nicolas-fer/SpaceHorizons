package com.spacehorizons.mixin.travel;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import com.spacehorizons.network.NetLatency;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Latência simulada para o autoteste do convés ({@link NetLatency}): com um atraso ligado, cada pacote recebido pela conexão espera esse tempo
 * antes de ser tratado, em ordem. As duas pontas do servidor integrado passam por aqui, então o atraso vale para a ida e para a volta. Com o
 * atraso em zero e sem pacote esperando, não faz nada.
 */
@Mixin(Connection.class)
abstract class ConnectionLatencyMixin {
    @Shadow
    protected abstract void channelRead0(ChannelHandlerContext ctx, Packet<?> packet);

    @Unique
    private final ArrayDeque<Object[]> spacehorizons$delayed = new ArrayDeque<>();

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$delay(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (NetLatency.replaying()) {
            return;
        }
        int ms = NetLatency.delayMs();
        if (ms <= 0 && this.spacehorizons$delayed.isEmpty()) {
            return;
        }
        this.spacehorizons$delayed.addLast(new Object[] {System.nanoTime() + ms * 1_000_000L, ctx, packet});
        ctx.executor().schedule(() -> this.spacehorizons$drain(), ms, TimeUnit.MILLISECONDS);
        ci.cancel();
    }

    /** Entrega, em ordem, os pacotes cujo prazo já passou. */
    @Unique
    private void spacehorizons$drain() {
        long now = System.nanoTime();
        while (!this.spacehorizons$delayed.isEmpty() && (long) this.spacehorizons$delayed.peekFirst()[0] <= now) {
            Object[] entry = this.spacehorizons$delayed.pollFirst();
            NetLatency.setReplaying(true);
            try {
                this.channelRead0((ChannelHandlerContext) entry[1], (Packet<?>) entry[2]);
            } finally {
                NetLatency.setReplaying(false);
            }
        }
    }
}
