package com.spacehorizons.survival.oxygen;

import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.Planets;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sistema de vácuo e oxigênio (lado servidor + registros comuns).
 *
 * Por tick de cada jogador de servidor: descobre se ele está no vácuo (planeta sem atmosfera ou acima do topo dela,
 * pelo ponto dos olhos), soma as {@link OxygenSource}, avança a reserva ({@link OxygenMath#step}) e aplica o dano
 * {@code spacehorizons:vacuum} quando a reserva acaba. O estado vive num Data Attachment do jogador (salvo e
 * sincronizado com o próprio cliente). Criativo e espectador são isentos.
 *
 * Desligar: gamerule {@code spacehorizons:vacuum_damage} (padrão true). Escolhida em vez de config porque vale por
 * mundo, muda sem reiniciar (/gamerule) e já aparece na tela de regras do mundo; config seria global da instalação.
 */
public final class OxygenSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SpaceHorizons.MODID);
    private static final DeferredRegister<GameRule<?>> GAME_RULES =
            DeferredRegister.create(Registries.GAME_RULE, SpaceHorizons.MODID);

    /** Estado de oxigênio do jogador. Sem copyOnDeath: quem renasce volta com a reserva cheia. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<OxygenState>> OXYGEN = ATTACHMENTS.register("oxygen",
            () -> AttachmentType.builder((Supplier<OxygenState>) () -> OxygenState.FULL)
                    .serialize(OxygenState.CODEC)
                    .sync((holder, to) -> holder == to, OxygenState.STREAM_CODEC)
                    .build());

    /** Liga/desliga o sistema inteiro (reserva, HUD e dano). */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> VACUUM_DAMAGE_RULE = GAME_RULES.register("vacuum_damage",
            () -> new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                    GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    public static final ResourceKey<DamageType> VACUUM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "vacuum"));

    private OxygenSetup() {
    }

    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        GAME_RULES.register(modEventBus);
        OxygenSources.register(new OxygenDevSource());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> OxygenDevSource.forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> OxygenDevSource.clear());
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                tick(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
    }

    /** O sistema de vácuo e oxigênio está ligado neste mundo (regra {@code spacehorizons:vacuum_damage})? */
    public static boolean isEnabled(ServerLevel level) {
        return level.getGameRules().get(VACUUM_DAMAGE_RULE.get());
    }

    /** Isento de tudo (reserva sempre cheia, sem dano, sem frio): criativo, espectador ou morto. */
    public static boolean isExempt(ServerPlayer player) {
        return player.isCreative() || player.isSpectator() || !player.isAlive();
    }

    /** O jogador está no vácuo? Pontos de respiração: os olhos do jogador, não os pés. */
    public static boolean isInVacuum(ServerPlayer player) {
        return Planets.forLevel(player.level()).map(planet -> OxygenMath.isVacuum(planet, player.getEyeY())).orElse(false);
    }

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        boolean enabled = isEnabled(level);
        boolean exempt = isExempt(player);
        boolean vacuum = isInVacuum(player);
        // as fontes só são consultadas no vácuo e quando o jogador precisa delas (elas gastam o próprio estoque)
        int supply = enabled && !exempt && vacuum ? OxygenSources.total(player) : 0;

        OxygenState before = player.getData(OXYGEN);
        OxygenMath.Result result = OxygenMath.step(before, enabled, exempt, vacuum, supply, player.tickCount);
        // só grava (e sincroniza) quando algo mudou
        if (!result.state().equals(before)) {
            player.setData(OXYGEN, result.state());
        }
        if (result.damage() > 0.0F) {
            DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(VACUUM_DAMAGE));
            player.hurtServer(level, source, result.damage());
        }
    }

    // ---- comandos de desenvolvimento: /spacehorizons oxygen ... ----

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("oxygen")
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("reserva", IntegerArgumentType.integer(0, OxygenMath.MAX_RESERVE))
                                        .executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "reserva")))))
                        .then(Commands.literal("supply")
                                .then(Commands.argument("ligada", BoolArgumentType.bool())
                                        .executes(ctx -> supply(ctx.getSource(), BoolArgumentType.getBool(ctx, "ligada")))))));
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        OxygenState state = player.getData(OXYGEN);
        boolean vacuum = Planets.forLevel(player.level()).map(planet -> OxygenMath.isVacuum(planet, player.getEyeY())).orElse(false);
        source.sendSuccess(() -> Component.literal("Oxigênio: reserva=" + state.reserve() + "/" + OxygenMath.MAX_RESERVE
                + " vácuo=" + vacuum + " fonte=" + state.supplied() + " sistema=" + (state.enabled() ? "ligado" : "desligado")), false);
        return state.reserve();
    }

    private static int set(CommandSourceStack source, int reserve) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.setData(OXYGEN, new OxygenState(reserve, player.getData(OXYGEN).enabled(), player.getData(OXYGEN).supplied()));
        source.sendSuccess(() -> Component.literal("Reserva de oxigênio ajustada para " + reserve), true);
        return reserve;
    }

    private static int supply(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        OxygenDevSource.set(source.getPlayerOrException(), on);
        source.sendSuccess(() -> Component.literal("Fonte de oxigênio de teste " + (on ? "ligada" : "desligada")), true);
        return on ? 1 : 0;
    }
}
