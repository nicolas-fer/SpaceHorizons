# Relatório: entidade-veículo pilotável no MC 26.3 (NeoForge 26.3.0.76, mapeamentos oficiais)

Legenda: **[C]** confirmado no código (fontes do `minecraft-patched-26.3.0.76-sources.jar` e do NeoForge 26.3.0.76). **[I]** inferência minha. Nada no projeto foi editado. Extraí as fontes para a pasta temporária (fora do projeto) em `C:\Users\nicol\AppData\Local\Temp\claude\E--git-Personal-Games-MineMods-SpaceHorizons\a4e627e3-157c-4438-91cf-69e4d19a2311\scratchpad\mc\src` e `...\scratchpad\neo\src`.

## 0. Resumo e recomendação

- **Autoridade:** servidor autoritativo, com o piloto montado na entidade e comandos vindos do cliente (Input vanilla mais um payload próprio). O servidor simula, envia o estado a cada tick e o cliente interpola. Atitude e câmera são previstas no cliente.
- **Por que não "como o barco":** a validação vanilla do veículo controlado pelo cliente (`handleMoveVehicle`) rejeita saltos de poucos blocos/tick num servidor dedicado ou LAN remoto (2.3). Ela só é ignorada para o dono do mundo (`isSingleplayerOwner()`), e o plano quer servidor dedicado depois.
- **O vanilla 26.3 já tem os ganchos para isso (todos confirmados):**
  - `isClientAuthoritative()` e `isLocalClientAuthoritative()` podem ser sobrescritos para devolver `false`.
  - O `Minecart` já é servidor-autoritativo e lê `player.getLastClientMoveIntent()`.
  - `isAlwaysTicking()`, `getMoveSimulationType()` (com `SERVER_AND_CLIENT`) e `LinearInterpolationHandler` existem.
- **O que quebra a 30–60 blocos/tick (seção 6):**
  1. O veículo só é rastreado pelo piloto se o chunk dele já foi enviado (`ChunkMap.isChunkTracked`).
  2. O veículo só ticka se o chunk dele estiver em `ENTITY_TICKING`.
  3. O cliente deixa de interpolar acima de 64 blocos de salto.
  4. Rotação de entidade é limitada a ±90° de pitch e quantizada em 1,4°.
- **Troca de dimensão:** `rocket.teleport(TeleportTransition)` do vanilla já faz tudo (desmonta, teleporta o jogador, recria a entidade por NBT, re-senta o jogador). Os problemas estão em volta (7 e 8).
- **Integração:** o `TravelServer.onPlayerTick` atual chamaria `player.teleport(...)` no piloto, e isso o ejeta do foguete (seção 8).

---

## 1. Montar, posicionar e desmontar

### 1.1 API do 26.3 (assinaturas confirmadas, `Entity.java`)

| Membro | Visib. | Observação |
|---|---|---|
| `startRiding(Entity)` | `public final boolean` | chama `startRiding(e,false,true)` |
| `startRiding(Entity, boolean force, boolean sendEventAndTriggers)` | `public boolean` (l.2504) | ver abaixo |
| `canRide(Entity vehicle)` | `protected` (no passageiro) | `!isShiftKeyDown() && boardingCooldown <= 0` |
| `canAddPassenger(Entity passenger)` | `protected` | padrão `passengers.isEmpty()` |
| `couldAcceptPassenger()` | `protected @Deprecated` | padrão `true` |
| `getControllingPassenger()` | `public @Nullable LivingEntity` | padrão `null` |
| `getPassengerAttachmentPoint(Entity, EntityDimensions, float)` | `protected Vec3` | padrão usa `PASSENGER` do `EntityType.Builder` |
| `positionRider(Entity, Entity.MoveFunction)` | `protected` | o `positionRider(Entity)` público é `final` |
| `getPassengerRidingPosition(Entity)` | `public Vec3` | `position() + getPassengerAttachmentPoint(...)` |
| `getVehicleAttachmentPoint(Entity vehicle)` | `public Vec3` | no passageiro |
| `onPassengerTurned(Entity)` | `public` | chamado por `Entity#turn` (mouse) |
| `getDismountLocationForPassenger(LivingEntity)` | `public Vec3` | usado só no servidor |
| `interact(Player, InteractionHand, Vec3)` | `public InteractionResult` | |
| `rideTick()` | `public` | no passageiro: `setDeltaMovement(ZERO); tick(); vehicle.positionRider(this)` |

`startRiding` falha se qualquer destas condições valer:
- `!entityToRide.couldAcceptPassenger()`;
- no servidor, `!entityToRide.getType().canSerialize()` (um tipo registrado com `noSave()` não pode ser montado);
- `EventHooks.canMountEntity(this, e, true)` cancelar o `EntityMountEvent`;
- sem `force`, `canRide(e) && e.canAddPassenger(this)` ser falso.

### 1.2 Fluxo de montagem

- No servidor, `interact(...)` chama `player.startRiding(this)`. No cliente devolve `SUCCESS`. É o padrão do `AbstractBoat.interact`, que também exige `isPickable()` verdadeiro.
- `ServerPlayer#startRiding(e, force, send)` (l.2347) sobrescreve e, depois do `super`, faz:
  - `e.positionRider(this)`;
  - `connection.teleport(new PositionMoveRotation(position(), Vec3.ZERO, 0, 0), Relative.ROTATION)`, que cria um `awaitingTeleport`;
  - `connection.send(new ClientboundSetPassengersPacket(e))`.
- No cliente, `handleSetEntityPassengersPacket` faz `ejectPassengers()` e depois `passenger.startRiding(vehicle, true, false)` (forçado). Se o veículo ainda não existe no cliente, só registra o aviso "Received passengers for unknown entity" e ignora.
- O cliente mostra o overlay `mount.onboard` com a tecla Shift (`ClientPacketListener` l.1165). É cosmético e não dá para trocar sem mixin.
- Posicionamento do piloto: sobrescreva `positionRider(Entity, MoveFunction)` ou `getPassengerAttachmentPoint(...)`. O padrão só rotaciona o anexo pelo `yRot` (`getDefaultPassengerAttachmentPoint`). Para um foguete com quaternion, calcule `position().add(q.transform(cockpitLocal))` e subtraia `passenger.getVehicleAttachmentPoint(this)`. Isso vale para o cockpit vindo do retrato.

### 1.3 Desmontar

- **Vanilla:** `Player#rideTick` no servidor faz `if (!clientSide && wantsToStopRiding() && isPassenger()) stopRiding()`. `wantsToStopRiding()` é `protected` e devolve `isShiftKeyDown()`. O Shift chega pelo `Input.shift()` em `handlePlayerInput` (`setShiftKeyDown(input.shift())`).
- **Veto confirmado:** `Entity#removeVehicle` chama `EventHooks.canMountEntity(this, oldVehicle, false)`. Cancelar `EntityMountEvent` (`isDismounting()`) impede a saída.
- **Mascarar o Shift:** `MovementInputUpdateEvent` (cliente, disparado em `LocalPlayer.aiStep` l.813 logo depois de `input.tick()`) expõe `ClientInput.keyPresses`, que é um campo público e reatribuível. Zerar `shift` ali impede o dismount vanilla e libera Shift para acelerador. O `LocalPlayer.isShiftKeyDown()` também lê `keyPresses`.
- **Chamadas internas que também passam pelo veto (precisam de bypass):**
  - `Entity#teleportCrossDimension` chama `ejectPassengers()`.
  - `PlayerList.remove` chama `player.stopRiding()` no logout, mas só depois de `save(player)`, então o dado salvo não é afetado.
  - Mudar para modo espectador chama `stopRiding()`.
- **Posição de saída:** `LivingEntity#stopRiding` chama `dismountVehicle`, que (no servidor, se o veículo não foi removido) usa `vehicle.getDismountLocationForPassenger(this)` e depois `dismountTo` → `teleportTo`. O padrão é o topo da caixa. Sobrescreva para devolver o ponto de pouso guardado e, em voo, `passenger.position()`.
- **Cooldown:** `Entity#removePassenger` põe `boardingCooldown = 60` no passageiro. O re-sentar usa `force=true`, então isso não atrapalha.

### 1.4 Salvar e carregar com o piloto

- `Entity#shouldBeSaved` devolve `false` para veículo com exatamente um passageiro jogador. A entidade vai no NBT do jogador (`RootVehicle` com `Attach` e `Entity`, via `ServerPlayer#saveParentVehicle`) e **não** no chunk.
- Todo autosave de jogador serializa o retrato inteiro (custo periódico).
- No login, `loadAndSpawnParentVehicle` faz `startRiding(vehicle, true, false)`. Se falhar, **descarta o veículo e os passageiros** (`vehicle.discard()`), perdendo o foguete. Portanto não cancele `EntityMountEvent` com `isMounting()` e mantenha `canSerialize()` verdadeiro.

---

## 2. Entrada do jogador e autoridade da posição

### 2.1 O que o cliente manda

- **`Input`** (`net.minecraft.world.entity.player.Input`) é um record com 7 booleanos: `forward, backward, left, right, jump, shift, sprint`. Vai em 1 byte de flags. Não há tecla de rolagem, acelerador ou Q/E.
- **`LocalPlayer#sendChanges`** (l.266–284) roda em `Minecraft.tick`, no passo "playerSync", no fim do tick do cliente:
  1. Se `keyPresses != lastSentInput`, envia `ServerboundPlayerInputPacket(Input)`. Isso acontece **também montado**.
  2. Se é passageiro, envia **todo tick** `ServerboundMovePlayerPacket.Rot(yRot, xRot, onGround, horizontalCollision)`.
  3. Só se `vehicle.isLocalInstanceAuthoritative()`, envia `ServerboundMoveVehiclePacket.fromEntity(vehicle)` (record com `PositionAndRotation` e `onGround`) e o comando de sprint.
- **Servidor, entrada:** `handlePlayerInput` faz `player.setLastClientInput(input)` e, se `hasClientLoaded()`, `setShiftKeyDown`. Leitura via `ServerPlayer#getLastClientInput()` ou `getLastClientMoveIntent()` (Vec3 com esquerda/frente em {-1,0,1}, usado pelo minecart).
- **Servidor, rotação:** `handleMovePlayer` com passageiro cai em `handlePlayerPositionChange`, ramo `isPassenger()`: `player.absSnapTo(posição atual, yRot, xRot)` e `getChunkSource().move(player)`. Ou seja, o servidor só aceita yaw/pitch do cliente, ignora posição e **renova tracking e tickets todo tick**. O yaw/pitch do piloto é um canal analógico gratuito (2 floats absolutos).

### 2.2 Quem manda na posição

```
Entity#isLocalInstanceAuthoritative()  final:  level.isClientSide() ? isLocalClientAuthoritative() : !isClientAuthoritative()
Entity#isClientAuthoritative()         public: controlador != null && controlador.isClientAuthoritative()
Entity#isLocalClientAuthoritative()    protected: controlador != null && controlador.isLocalClientAuthoritative()
Player#isClientAuthoritative() = true;  Player#isLocalClientAuthoritative() = isLocalPlayer()
```

- Qualquer entidade cujo `getControllingPassenger()` seja um `Player` é **autoritativa no cliente** (barco, cavalo etc.). O servidor só valida.
- `isControlledByLocalInstance` (do 1.21.1) **não existe mais** no 26.3.
- Se `getControllingPassenger()` devolver `null`, o veículo é servidor-autoritativo (minecart).
- Também dá para manter o piloto como controlador e sobrescrever `isClientAuthoritative()` e `isLocalClientAuthoritative()` para `false` (recomendado, 2.4).

### 2.3 Barco (cliente autoritativo) e a validação do servidor

- **Barco:** `AbstractBoat.tick` (l.236–251): se `isLocalInstanceAuthoritative()`, simula (`floatBoat`, `controlBoat` no cliente, `move(SELF, DM)`); senão faz `setDeltaMovement(ZERO)`. Como o controlador é jogador, o servidor fica no `else` e o cliente simula. `LocalPlayer.rideTick` alimenta `boat.setInput(left, right, forward, backward)`.
- **`ServerGamePacketListenerImpl#handleMoveVehicle`** (l.452–544), pontos confirmados:
  - Valores NaN/inf desconectam o jogador (`invalid_vehicle_movement`).
  - Ignora o pacote enquanto `updateAwaitingTeleport()` (aguardando ack de teleporte) ou sem `hasClientLoaded()`.
  - Clamps: ±3e7 horizontal, ±2e7 vertical.
  - **"Moved too quickly":** `movedDist − expectedDist > 100.0`, em blocos², sendo `movedDist = |alvo − vehicleFirstGoodPos|²` e `expectedDist = |vehicle.getDeltaMovement()|²` do servidor, e só se `!isSingleplayerOwner()`. Resposta: `ClientboundMoveVehiclePacket.fromEntity(vehicle)`, e o cliente faz `absSnapTo` (rubber band).
  - Depois faz `vehicle.move(MoverType.PLAYER, delta)` e compara com o alvo. Erro² > 0,0625 conta como "moved wrongly" (reverte se `fail && noCollision(oldAABB)` ou `isEntityCollidingWithAnythingNew`). Aceito, faz `absSnapTo`, `chunkSource.move(player)` e `handlePlayerKnownMovement`.
  - **Chute por flutuar:** `clientVehicleIsFloating` e, depois de `getMaximumFlyingTicks(vehicle)` ticks (80 × max(0,08/gravidade, 1)), `disconnect("flying")`. Não vale se `server.allowFlight()`, `vehicle.isFlyingVehicle()` ou `vehicle.isNoGravity()`. Gravidade < 1e-5 dá `Integer.MAX_VALUE`.
- **Margem a alta velocidade:** com DM do servidor = 0 (barco), qualquer salto de ~10 blocos/tick é rejeitado em MP. Mesmo com DM = velocidade anterior, `(v+Δv)² − v² ≤ 100` exige Δv ≲ 1,6 b/t² a 30 b/t e ≲ 0,83 b/t² a 60 b/t. **[I]** Isso é viável se o DM do servidor acompanhar o último delta reportado, mas é frágil, e o servidor não validaria combustível nem empuxo.
- **Jogador a pé:** o mixin atual `ServerGamePacketListenerImplMixin` (`shouldCheckPlayerMovement`) só cobre jogador a pé. Montado, a posição do jogador nem é lida (2.1). Esse mixin fica irrelevante para o piloto.

### 2.4 Servidor autoritativo: o que ligar e armadilhas

Sobrescrever no foguete (todos `[C]` quanto à existência e visibilidade):

```java
@Override public boolean isClientAuthoritative() { return false; }           // public
@Override protected boolean isLocalClientAuthoritative() { return false; }   // protected
@Override public boolean isAlwaysTicking() { return true; }                  // 6.2
@Override public boolean isNoGravity() { return true; }                      // gravidade vanilla off
@Override public boolean isFlyingVehicle() { return true; }
@Override public Vec3 getKnownMovement() { return getDeltaMovement(); }      // ver armadilha
@Override public @Nullable LivingEntity getControllingPassenger() {
    return getFirstPassenger() instanceof Player p ? p : null;
}
```

Efeitos confirmados:
- No cliente `isLocalInstanceAuthoritative()` fica `false`:
  - `LocalPlayer.sendChanges` deixa de enviar `ServerboundMoveVehiclePacket`;
  - `handleEntityPositionSync`, `handleMoveEntity` e `handleTeleportEntity` tratam o foguete como entidade remota (interpolam);
  - o `ClientboundMoveVehiclePacket` é ignorado (l.2176).
- **Armadilha:** `Entity#getKnownMovement()` devolve `controller.getKnownMovement()` se o controlador for `Player`. Para `ServerPlayer`, quando `vehicle.getControllingPassenger() == this`, devolve `lastKnownClientMovement`, que só é atualizado por pacotes de movimento do cliente. Resultado: o foguete reportaria velocidade **zero e velha**. Isso afeta `PositionMoveRotation.of(entity)` e qualquer teleporte. Sobrescreva `getKnownMovement()` como acima.
- **Armadilha:** `Entity#setXRot` faz clamp em ±90° e `ServerEntity` empacota rotação em byte (`Mth.packDegrees`, passo de 1,4°). Orientação completa de foguete precisa de um quaternion próprio (seção 7).
- **Opcional (predição):** `getMoveSimulationType()` devolve `AUTHORITATIVE_SIDE` por padrão. `PrimedTnt`, `ItemEntity`, `FallingBlockEntity` e `ExperienceOrb` usam `SERVER_AND_CLIENT`. Com ele, `Entity.recordMovement` chama `interpolationHandler.applyPredictedMovement(delta)`, ajustando o alvo de interpolação com o movimento previsto no cliente (`AbstractInterpolationHandler`). **[I]** É o caminho vanilla para dead-reckoning; precisa de `move(MoverType.SELF, dm)` no `tick()` do cliente, e o ajuste só entra se `level.noCollision(entity, caixa)` passar.
- **Interpolação:** o padrão é `InterpolationHandler.NO_OP`. `Entity.commonTick()` faz `setOldPosAndRot()` e depois `getInterpolation().interpolate()`. Sem handler, o cliente "pula" a posição a cada pacote e não há suavização por frame. Sobrescreva `createInterpolationHandler()` para `LinearInterpolationHandler.create(this, 1 ou 2)`. Com 0 passos, `AbstractInterpolationHandler.interpolateTo` faz `snapTo`, que também zera o histórico e não suaviza.

### 2.5 Limites de sincronização (confirmados)

| Item | Valor | Efeito a 30–60 b/t |
|---|---|---|
| `ClientboundSetEntityMotionPacket` | `Vec3.LP_STREAM_CODEC` (`LpVec3`). Clamp ±1,7179869183E10 por eixo, 15 bits por eixo normalizado por `ceil(max)`. Resolução ≈ 2·ceil(vmax)/32766 (≈0,0037 b/t a 60). O 26.3 não tem mais o limite antigo de ±3,9 | Sem limite prático |
| `ClientboundAddEntityPacket` | posição em 3 doubles, movimento em LpVec3, rotação em bytes | Posição exata |
| `ClientboundMoveEntityPacket` (delta) | short/4096, cerca de ±8 blocos. Acima disso `VecDeltaCodec.tryEncode` devolve null e o servidor manda `ClientboundEntityPositionSyncPacket` (3 doubles, 2 floats, bool) | Automático acima de 8 b/t; cerca de 40 B/tick por observador |
| Intervalo de update | `EntityType.Builder.updateInterval` padrão **3** ticks | A 60 b/t seriam saltos de 180 blocos. Use `.updateInterval(1)` |
| Interpolação no cliente | `handleEntityPositionSync` e `setValuesFromPositionPacket`: se `distSq > 4096` (> 64 blocos) faz `snapTo` em vez de interpolar. Exige também `level.isTickingEntity(entity)` | 60 b/t cabe; 64+ b/t, ou um tick perdido, vira solavanco |
| Rotação | `Mth.packDegrees` (byte), pitch limitado a ±90° | Usar dado sincronizado de quaternion |
| Tracking | `clientTrackingRange` padrão **5** chunks; alcance efetivo `min(max(range, passageiros)·16 · escala do servidor, viewDistance·16)`, medido só em x,z | Observadores externos: aumente o range |

---

## 3. Teclas extras por payload próprio (padrão do mod)

O mod já registra `registrar("1").playToClient(TYPE, CODEC)` em `TravelServer` e o handler cliente em `RegisterClientPayloadHandlersEvent` (`TravelClient`). Para cliente→servidor:
- **Registrar:** `playToServer(type, codec, handler)` (`PayloadRegistrar` l.60). O handler roda na thread principal por padrão (`HandlerThread.MAIN`).
- **Enviar:** a classe é `net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload)`. O `PacketDistributor.sendToServer` não existe mais; o `PacketDistributor` do 26.3 só cobre servidor→cliente.
- **Codec:** o tipo do `StreamCodec<? super RegistryFriendlyByteBuf, T>` aceita `StreamCodec<ByteBuf, T>`, como o `TravelTransferPayload` já faz.

```java
public record RocketControlPayload(float pitch, float yaw, float roll, float throttle, byte flags)
        implements CustomPacketPayload {
    public static final Type<RocketControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_control"));
    public static final StreamCodec<ByteBuf, RocketControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RocketControlPayload::pitch,
            ByteBufCodecs.FLOAT, RocketControlPayload::yaw,
            ByteBufCodecs.FLOAT, RocketControlPayload::roll,
            ByteBufCodecs.FLOAT, RocketControlPayload::throttle,
            ByteBufCodecs.BYTE,  RocketControlPayload::flags,        // bit0 SAS, bit1 estágio, bit2 pedir desembarque...
            RocketControlPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

// comum (servidor)
event.registrar("1").playToServer(RocketControlPayload.TYPE, RocketControlPayload.CODEC, (p, ctx) -> {
    if (ctx.player() instanceof ServerPlayer sp
            && sp.getVehicle() instanceof RocketEntity r && r.getControllingPassenger() == sp) {
        r.applyControl(p);          // sanear: clamp -1..1, throttle 0..1, guardar tick de recebimento
    }
});

// cliente
@SubscribeEvent static void onClientTick(ClientTickEvent.Post e) {
    LocalPlayer p = Minecraft.getInstance().player;
    if (p == null || !(p.getVehicle() instanceof RocketEntity)) return;
    RocketControlPayload now = RocketControls.sample();   // KeyMappings + MovementInputUpdateEvent
    if (!now.equals(last) || ++sinceSend >= 5) { ClientPacketDistributor.sendToServer(now); ... }
}
```

- **Teclas:** `RegisterKeyMappingsEvent` (mod bus, cliente) com `event.register(new KeyMapping(name, keysym, category))`. Categoria própria via `registerCategory(new KeyMapping.Category(id))`. A API direta `KeyMapping.Category.register` está `@Deprecated` no Neo.
- **Entrada vanilla reaproveitável sem payload:** W/S/A/D (`forward/backward/left/right`), `jump`, `shift`, `sprint`. Convenção KSP: Shift/Ctrl para acelerador. Para isso mascare `shift` e `sprint` em `MovementInputUpdateEvent` (campo `ClientInput.keyPresses`), capturando o valor antes. Q/E (rolagem), Z/X, T (SAS) exigem `KeyMapping` próprio mais payload. A roda do mouse está em `InputEvent.MouseScrollingEvent` (cancelável).
- **Envio:** `ClientTickEvent.Post` dispara na mesma janela do `sendChanges` (mesma latência do Input). **[I]** Envie quando mudar e com heartbeat a cada ~5 ticks. No servidor, zere os comandos se passarem mais de ~10 ticks sem pacote (rede ou alt-tab).
- **Mouse:** o `Entity#turn` do jogador chama `vehicle.onPassengerTurned(player)`. Esse gancho captura os deltas do mouse sem mixin: acumule o delta como olhar livre e reponha a rotação do jogador. O vanilla `Entity#turn` mexe em `xRot/yRot/xRotO/yRotO`; reponha todos. O yaw/pitch do jogador continua indo ao servidor por `ServerboundMovePlayerPacket.Rot`, mas o servidor não precisa dele.

---

## 4. Câmera (cliente)

### 4.1 Pontos de gancho confirmados

- `Camera.alignWithEntity(partialTicks)`:
  1. dispara `ViewportEvent.ComputeCameraAngles(camera, pt, entity.getViewYRot(pt), entity.getViewXRot(pt), roll=0)`;
  2. chama `setRotation(yaw, pitch, roll)`;
  3. posição = jogador interpolado (`xo→x`) + `eyeHeight`;
  4. se terceira pessoa, `move(-getMaxZoom(distância), 0, 0)` ao longo do vetor de câmera.
- `Camera.setRotation(float,float,float)` é `protected` e faz `rotation.rotationYXZ(π − yaw, −pitch, −roll)`, atualizando `forwards/up/left`.
- A distância em terceira pessoa vem de `CalculateDetachedCameraDistanceEvent#setDistance(float)`. Para veículo não-`LivingEntity` o padrão é 4 blocos (`CAMERA_DISTANCE` do jogador).
- `Options#setCameraType(CameraType)` força `THIRD_PERSON_BACK` ao montar.
- Os renderizadores do mod (`BodyRenderer`, `PlanetMeshRenderer`) usam `camera.viewRotationMatrix`, que vem do quaternion. `AtmosphereLimbRenderer` e `AtmosphereFog` só usam posição. Aparentemente o roll já funciona.

### 4.2 Duas formas de girar a câmera com o foguete

- **A) Só evento, sem mixin.** Converta o quaternion de câmera em YXZ com `Quaternionf.getEulerAnglesYXZ(Vector3f)` (existe no JOML 1.10.9 do projeto). A câmera do MC reconstrói `Ry(π−yaw)·Rx(−pitch)·Rz(−roll)`, então `yaw = 180° − eul.y`, `pitch = −eul.x`, `roll = −eul.z`. **[I]** Os sinais precisam de teste unitário de ida e volta com `rotationYXZ(π−y, −p, −r)`, como já se faz com a lógica pura do mod. Perto de pitch ±90 yaw e roll ficam degenerados, mas a rotação composta continua exata; só valores intermediários ficam instáveis (isso só importa para HUDs que leiam `Camera.xRot/yRot`).

```java
@SubscribeEvent static void onCamera(ViewportEvent.ComputeCameraAngles e) {
    if (!(Minecraft.getInstance().player.getVehicle() instanceof RocketEntity r)) return;
    Quaternionf q = r.attitude(e.getPartialTick(), new Quaternionf());          // slerp(qOld, q, pt)
    q.mul(FREE_LOOK_Q).mul(COCKPIT_Q);                                          // COCKPIT_Q: rotateX(+90°) = olhar ao longo do nariz +Y local
    Vector3f eul = q.getEulerAnglesYXZ(new Vector3f());
    e.setYaw((float) Math.toDegrees(Math.PI - eul.y));
    e.setPitch((float) -Math.toDegrees(eul.x));
    e.setRoll((float) -Math.toDegrees(eul.z));
}
```

- **B) Mixin no quaternion.** `@Inject(method = "setRotation(FFF)V", at = @At("TAIL"))` em `Camera`, com `@Shadow rotation/forwards/up/left/matrixPropertiesDirty`. Dá o quaternion exato. **[I]** Em modo espelhado (`CameraType.isMirrored()`) o vanilla chama `setRotation` de novo, então aplique `rotateY(π)` nesse caso. O `CameraMixin` atual só mexe no plano distante; pode ser estendido.

- **Terceira pessoa:** `CalculateDetachedCameraDistanceEvent.setDistance(raioDoFoguete · k)`. A câmera orbita o assento do piloto, não o centro do foguete. **[I]** Para orbitar o centro seria preciso mexer em `setPosition`.
- **Cosmético:** o jogador sentado é renderizado como entidade separada e fica sempre em pé. Com o foguete rolando ele atravessa o casco; esconda o modelo ou rotacione-o no render. O `LocalPlayer` também continua mostrando a mão em primeira pessoa.

---

## 5. Troca de dimensão com passageiro

### 5.1 `Entity#teleport(TeleportTransition)` (l.3177) e o jogador

`TeleportTransition` é um record: `(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, PostTeleportTransition post)`. Há construtores curtos (o mod usa o de 6 argumentos) e `withPosition`, `withRotation`, `transitionAsPassenger()`. Constantes do post: `DO_NOTHING`, `PLAY_PORTAL_SOUND`, `PLACE_PORTAL_TICKET`.

| Chamada | Resultado |
|---|---|
| `player.teleport(t)` com piloto montado e `asPassenger=false` | `Entity#teleport` / `ServerPlayer#teleport` fazem `removeVehicle()` primeiro. **O piloto sai do foguete.** O foguete fica para trás sem passageiro |
| `rocket.teleport(t)` na mesma dimensão | Para cada passageiro, `passenger.teleport(calculatePassengerTransition)` (como passageiro, continua montado). Depois `teleportSetPosition` e `sendTeleportTransitionToPlayers` (ao controlador vai a transição; aos demais, o estado atual) |
| `rocket.teleport(t)` cruzando dimensão (`teleportCrossDimension`) | Sequência abaixo |

`teleportCrossDimension` (l.3209–3243), em ordem:
1. `ejectPassengers()`. Cada `stopRiding` chama `getDismountLocationForPassenger` e `dismountTo`, e o servidor manda `ClientboundSetPassengersPacket` vazio.
2. Cada passageiro faz `teleport(calculatePassengerTransition(...))` como passageiro. O offset relativo ao veículo e o desvio de olhar são preservados. `ServerPlayer#teleport` envia `ClientboundRespawnPacket` (flag 3, mantém atributos e dados) e `ClientboundPlayerPositionPacket`, remove o jogador do nível antigo e o adiciona ao novo.
3. `getType().create(newLevel, EntitySpawnReason.DIMENSION_TRAVEL)`, depois `newEntity.restoreFrom(old)`, que faz `saveWithoutId` seguido de `load`. **A entidade nova nasce da serialização completa**: retrato, orientação, combustível, tudo precisa estar em `addAdditionalSaveData/readAdditionalSaveData`. O `id` muda (o UUID persiste).
4. `old.removeAfterChangingDimensions()` (`CHANGED_DIMENSION`), `newEntity.teleportSetPosition(...)` (posição, velocidade e rotação da transição), `newLevel.addDuringTeleport(newEntity)`.
5. Cada novo passageiro faz `startRiding(newEntity, true, false)`. O `ServerPlayer#startRiding` envia de novo o teleporte (novo `awaitingTeleport`) e o `SetPassengers`.

- `CommonHooks.onTravelToDimension` (`EntityTravelToDimensionEvent`, cancelável) dispara para o foguete **e** para cada passageiro.
- O jogador fica montado no servidor ao fim. No cliente, ele só remonta quando chegam o `AddEntity` do foguete e o `SetPassengers`.
- **Lacuna no cliente:** o `AddEntity` só sai quando o piloto "rastreia" o foguete (6.1), ou seja, só depois que o chunk de chegada foi enviado. O `SetPassengers` mandado por `startRiding` chega antes de a entidade existir. O cliente registra "unknown entity", e o `sendPairingData` reenvia o `SetPassengers` quando o foguete for pareado (confirmado em `ServerEntity.sendPairingData`). Nesses ticks o `LocalPlayer` não está montado e cai com a física vanilla.
- **Ack do teleporte do re-sentar:** `handleAcceptTeleportPacket` (l.569) faz `player.absSnapTo(awaitingPositionFromClient)`, a posição de quando o `startRiding` rodou. Se o foguete andou 60 b/t por 2 ou 3 ticks até o ack, o jogador é recolocado nessa posição velha e `handlePlayerPositionChange` chama `chunkSource.move(player)` com ela. **[I]** Isso pode fazer o servidor desparear o foguete por um tick (distância x,z > alcance). Mitigação: deixar o foguete parado (velocidade zero) logo após o re-sentar, até `!player.isChangingDimension()` (zerado no ack) e o jogador estar na lista de `ChunkMap.getPlayersWatching(rocket)`.

### 5.2 Pré-carregamento do destino

- `ServerChunkCache.addTicketAndLoadWithRadius(TicketType, ChunkPos, int)` devolve um `CompletableFuture<?>`. Exige `type.doesLoad()` e `!canExpireIfUnloaded()`. O `PORTAL` serve (flags 15, timeout de 300 ticks).
- `addTicketWithRadius(type, pos, r)` cria ticket de nível `33 − r`. `ENTITY_TICKING` é nível ≤ 31, então **raio ≥ 2** deixa o chunk central em ticking.
- `TeleportTransition.PLACE_PORTAL_TICKET` como post-transition chama `entity.placePortalTicket(BlockPos.containing(entity.position()))` (PORTAL raio 3), mas **depois** da chegada.
- O destino precisa de ticket que mantenha a dimensão ativa (`FLAG_KEEP_DIMENSION_ACTIVE`, presente em PORTAL, FORCED e ENDER_PEARL).

### 5.3 Fluxo recomendado

```java
// servidor, na esfera de influência; ticket já pedido e carregado
PacketDistributor.sendToPlayer(pilot, new TravelTransferPayload(px, py, pz, yRot, xRot)); // ANTES de teleport
TeleportTransition t = new TeleportTransition(toLevel, new Vec3(x,y,z), new Vec3(vx,vy,vz),
        yRot, xRot, TeleportTransition.PLACE_PORTAL_TICKET);
Entity novo = rocket.teleport(t);   // devolve a NOVA entidade (outro id), ou null se vetado
```

- Para o `TravelTransferPayload`, use a posição do **piloto** (foguete + offset do assento), não a do foguete. O mixin do cliente aplica o `snapTo` ao `LocalPlayer` novo.
- A ordem de pacotes é confirmada: payload, depois `SetPassengers` vazio, depois `ClientboundRespawnPacket`. A expiração de 3 s do `TravelClientState` dá folga.
- **Alternativa manual [I]**, mais controlada, no espírito do plano do Create:
  1. criar a entidade nova;
  2. teleportar o jogador sem veículo;
  3. só quando `chunkMap.getPlayersWatching(newRocket)` contiver o jogador, chamar `player.startRiding(newRocket, true, false)`.
  Isso evita o aviso "unknown entity" e a queda do cliente.
- Para `EntityMountEvent`, o bypass do veto de desmontagem deve ser explícito (flag em `ThreadLocal`) durante a troca.

---

## 6. Tracking, ticking e chunks (o que acontece a 60 b/t)

### 6.1 Tracking (quem vê o foguete)

`ChunkMap.TrackedEntity#updatePlayer(player)` (l.1415):
- visível se `distância x,z ≤ min(alcance efetivo, viewDistance·16)` **e** `entity.broadcastToPlayer(player)` **e** `ChunkMap.isChunkTracked(player, chunkX, chunkZ)`;
- `isChunkTracked` = `player.getChunkTrackingView().contains(x, z)` **e** `!player.connection.chunkSender.isPending(chunk)`.

Para o **piloto**, a distância é ≈ 0. Só a condição do chunk pode falhar: o chunk onde o foguete está pode estar na fila de envio ou ainda não enviado.

Se falhar, `removePlayer` manda `ClientboundRemoveEntitiesPacket` do foguete **com o piloto montado**:
- `handleRemoveEntities` guarda `removedPlayerVehicleId` (o vanilla tolera: `handleTeleportEntity` aplica ao jogador, e `handleAddEntity` limpa);
- o cliente desmonta o piloto, que passa a sofrer física própria até o re-pareamento.

`PlayerChunkSender` (confirmado):
- `MAX_CHUNKS_PER_TICK = 64`, `START_CHUNKS_PER_TICK = 9`, lotes sem ack no máximo 10;
- o envio é "mais próximo primeiro";
- conexão de memória (singleplayer) não corta o lote.

Um foguete a 60 b/t cruza ~3,75 chunks/tick. Com view distance 12 seriam ~79 chunks novos/tick, acima do teto de 64. **[I]** Em planeta a geração do `PlanetChunkGenerator` também não acompanha. O `Autopilot` atual contorna isso com `holdForChunks` e teleporte por tick.

Mitigações:
- `updateInterval(1)` e `clientTrackingRange` alto (por exemplo 16) para observadores externos.
- Manter a velocidade horizontal baixa na atmosfera.
- No espaço (dimensão vazia) reduzir a view distance (o `Autopilot` já faz isso globalmente, ver 8).
- **[I]** Mixin em `ChunkMap.isChunkTracked(ServerPlayer, int, int)` (público) devolvendo `true` para o chunk do veículo do próprio jogador (`player.getVehicle() instanceof RocketEntity`). Isso elimina o desemparelhamento do piloto por construção.

### 6.2 Ticking (quem executa o foguete)

- Loop principal (`ServerLevel.tick` l.448–476): para cada entidade em `entityTickList`, só ticka se `entity instanceof ServerPlayer || distanceManager.inEntityTickingRange(chunkPos)`. Passageiros são ticados pelo veículo (`tickNonPassenger` → `tickPassenger` → `rideTick`).
- `isAlwaysTicking()` faz `getEffectiveStatus` devolver `TICKING` no `PersistentEntitySectionManager` e no `TransientEntitySectionManager` (cliente), mas o loop do servidor **ainda exige** `inEntityTickingRange` para o foguete. `Player.isAlwaysTicking()` já é `true`.
- **Se o chunk do foguete não estiver em `ENTITY_TICKING`, o foguete não ticka e o piloto, que depende dele, também fica parado.** `ChunkMap.tick` ainda sincroniza se a seção mudou.
- Tickets de simulação do jogador só são atualizados em `ServerChunkCache.move(player)`, chamado por `handleMovePlayer` (o `Rot` de passageiro serve). O ticket de jogador vale `31 − simulationDistance`, ou seja, os chunks ticking ficam a ≤ `simulationDistance` do ticket.
- **O `Autopilot` reduz a distância de simulação a 2 acima de 800 blocos.** **[I]** A 3,75 chunks/tick o foguete sai do raio antes de o ticket do jogador se mover. Mitigação, usando o padrão do `ThrownEnderpearl` (`ServerPlayer.placeEnderPearlTicket`): a cada tick, `level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunkNaFrenteDoFoguete, 2)` (timeout de 40 ticks, flags LOADING + SIMULATION + KEEP_DIM_ACTIVE) em k = 0, 1, 2 ticks à frente, extrapolando a velocidade. `PORTAL` (300 ticks) também serve.
- Se o chunk à frente não está carregado, o foguete atravessa o terreno sem colisão (o servidor de colisões vanilla trata chunk não carregado como vazio) e fica sem tick. O espaço vazio dispensa isso.

### 6.3 Resumo do que acontece fora do alcance

| Situação | Efeito |
|---|---|
| Foguete fora do alcance de observadores (x,z > `min(range·16, view·16)`) | Esses jogadores recebem `RemoveEntities`; o piloto não é afetado (distância 0) |
| Chunk do foguete pendente ou não enviado ao piloto | Vanilla desmonta o piloto no cliente (6.1) |
| Chunk fora de `ENTITY_TICKING` | Foguete não ticka |
| Salto > 64 blocos num pacote | Cliente faz `snapTo` (sem interpolação) |
| Servidor lento | Posição só avança por tick, sem recuperação |

---

## 7. Arquitetura recomendada

### 7.1 Servidor autoritativo, com predição de atitude

Por tick do servidor, ordem confirmada nos fluxos acima:
- processam-se os pacotes (`Input`, `Rot`, `RocketControlPayload`);
- `ServerLevel.tick` ticka o foguete e depois `tickPassenger` do piloto, que chama `rideTick` e `positionRider`;
- `ChunkMap.tick` roda `ServerEntity.sendChanges`, enviando `ClientboundEntityPositionSyncPacket`, `ClientboundSetEntityMotionPacket` (se a velocidade mudou) e `ClientboundSetEntityDataPacket` (se o dado de atitude mudou).

Fluxo:
1. **Cliente:** `MovementInputUpdateEvent` (captura e mascara Shift/Ctrl) → `ClientTickEvent.Post` envia `RocketControlPayload` → `Minecraft.tick` "playerSync" envia `Input` se mudou e o `Rot`.
2. **Servidor:** o foguete integra atitude, empuxo, gravidade (do `SolarSystem`), combustível e SOI. Sincroniza posição, velocidade e atitude.
3. **Cliente:** interpola posição (`LinearInterpolationHandler`, 1 a 2 passos). A atitude do próprio piloto é prevista localmente (integrador igual ao do servidor, entrada imediata) e reconciliada com o valor sincronizado. A velocidade vem do `SetEntityMotion`.

Atributos de entidade:

```java
EntityType.Builder.of(RocketEntity::new, MobCategory.MISC)
    .sized(w, h)                 // caixa base; o retrato real vem de getDimensions/refreshDimensions
    .clientTrackingRange(16)
    .updateInterval(1)
    .build(ResourceKey.create(Registries.ENTITY_TYPE, id));   // 26.x: build(ResourceKey)
```

- **Atitude:** `SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.QUATERNION)` (existe, `Quaternionfc`, codec `ByteBufCodecs.QUATERNIONF`; também `VECTOR3`). `set` marca sujo se `!Objects.equals(novo, atual)`, então entregue uma cópia (`new Quaternionf(q)`). `ServerEntity.sendChanges` envia o dado no mesmo tick da posição quando sujo (`entityData.isDirty()`). No cliente use `onSyncedDataUpdated` para guardar `qOld ← q` antes de atribuir e interpolar com slerp por frame.
- **Dimensões:** `getDimensions(Pose)` é `public` (sobrescreva com o tamanho do retrato) e `refreshDimensions()` reaplica. Para tamanho maior que 4 blocos o vanilla não faz `fudgePositionAfterSizeChange`.
- **Spawn:** `IEntityWithComplexSpawn` (`writeSpawnData(RegistryFriendlyByteBuf)` / `readSpawnData`). O NeoForge manda como `AdvancedAddEntityPayload` (array de bytes sem limite próprio). Alternativa: `IEntityExtension#sendPairingData(ServerPlayer, Consumer<CustomPacketPayload>)`, para retratos grandes em partes. O plano já prevê o limite de ~1 MiB.
- **Rastreamento por observadores externos:** o mesmo `AddEntity` mais pareamento; um jogador que entra no alcance recebe o retrato inteiro.
- **Interação:** `isPickable()` verdadeiro, `interact(...)` como o barco. `canBeCollidedWith` fica `false` (o plano não quer colisor de entidade).

### 7.2 Por que não cliente autoritativo (barco)

Prós: zero latência perceptível, pipeline vanilla. Contras confirmados:
- validação em MP (2.3) rejeita saltos de ~10 blocos/tick, e a margem de Δv a 60 b/t é de 0,83 b/t²;
- `vehicle.move(MoverType.PLAYER, delta)` roda no servidor por pacote;
- o servidor não valida combustível, empuxo ou SOI;
- exige duplicar a física no cliente e reconciliar, e a troca de dimensão é iniciada pelo servidor de qualquer jeito;
- observadores veem só o que o servidor recebe.

Só valeria para singleplayer, onde `isSingleplayerOwner()` desliga a checagem. O plano 3a inclui servidor dedicado como pendência.

### 7.3 Variante com dead-reckoning

**[I]** Se a interpolação pura parecer lenta, use `getMoveSimulationType() = SERVER_AND_CLIENT` e `move(MoverType.SELF, dm)` no `tick()` do cliente. Para a visão em primeira pessoa a latência de posição quase não importa no espaço (nada de referência por perto). Importa na decolagem e no pouso.

---

## 8. Integração com o código existente (conflitos confirmados)

- **`TravelServer.onPlayerTick`** (todos os jogadores): chama `TravelTransfers.checkSphereOfInfluence(player, body)`, que termina em `player.teleport(transition)`, e `FlightBoost.update`. Para o piloto montado isso **ejeta o jogador** (`teleport` com `asPassenger=false` faz `removeVehicle`). Pule jogadores com `getVehicle() instanceof RocketEntity`. O SOI do foguete deve rodar na entidade, usando `rocket.getDeltaMovement()` e não `player.getKnownMovement()` (2.4).
- **`TravelTransfers.isReady`** usa `hasClientLoaded() && !isChangingDimension()`. Para o foguete use a mesma regra em relação ao piloto.
- **`BodyTransfer.compute`** devolve `x,y,z,vx,vy,vz,yRot,xRot` no referencial do corpo novo. Para o foguete converta também o quaternion de atitude (o payload só leva yaw/pitch). **[I]** O referencial da atitude em coordenadas da dimensão (plana, curvatura só no render) pode mudar entre corpos; precisa de decisão.
- **`Autopilot.adjustDistances`** muda a view e a simulation distance **globais** do `PlayerList` para 2 acima de 800 blocos. Isso afeta todos os jogadores e, como visto, o ticking do foguete (6.2). No espaço, o ideal é distância por dimensão ou por jogador.
- **`ClientPacketListenerMixin`**: só trata o respawn seamless. Não precisa mudar para o foguete, mas o `applyTo(player)` usa a posição do payload (5.3).
- **`ServerGamePacketListenerImplMixin`** continua útil para o piloto ejetado a pé, não para o piloto montado.

---

## 9. Riscos e perguntas em aberto

### Riscos

1. **Desemparelhamento do veículo** (6.1): piloto desmonta no cliente quando o chunk do foguete está pendente. Precisa de mixin em `isChunkTracked` ou garantia de que os chunks à frente já foram enviados.
2. **Foguete sem tick** (6.2): em `simulationDistance` baixa ou a alta velocidade o foguete sai do raio de `ENTITY_TICKING`. Exige tickets à frente em cada tick.
3. **Re-sentar após troca de dimensão** (5.1): aviso "unknown entity", lacuna sem montar, `absSnapTo` velho no ack e desemparelhamento transitório. Mitigar com fluxo manual ou foguete parado até o ack.
4. **Limite de 64 blocos na interpolação** (2.5): acima de 64 b/t, ou com um tick de servidor perdido, o cliente dá `snapTo` e a câmera engasga. Dead-reckoning (7.3) ou velocidade ≤ 60.
5. **Rotação** (2.4): `setXRot` com clamp ±90° e byte de 1,4°. O quaternion em dados sincronizados é obrigatório, e a câmera em Euler (4.2 A) tem ambiguidade de yaw/roll perto da vertical.
6. **Dismount por Shift** (1.3) e **`getKnownMovement`** (2.4): ambos exigem override ou veto explícito, senão o piloto sai do foguete ou o estado vem zerado.
7. **Persistência** (1.4): o retrato vive só no NBT do jogador. Cada autosave o regrava; se o `startRiding` do login falhar, o foguete é descartado.
8. **Custo de serialização** no cruzamento de dimensão: `restoreFrom` faz `saveWithoutId` e `load` de até 1.024 blocos no mesmo tick.
9. **Observadores externos**: precisam de range de tracking e `IEntityWithComplexSpawn`; sem isso veem um foguete sem blocos.
10. **Modelo do jogador** em pé dentro de um foguete que rola (cosmético); **mensagem de montagem** vanilla ("pressione Shift") no cliente.
11. **Distant Horizons e Sodium/Iris** com câmera rolada: não verifiquei; os renderizadores do mod usam `viewRotationMatrix`, o que sugere compatibilidade.

### Perguntas em aberto (decidir no protótipo 2 da 3a)

- Velocidade horizontal máxima aceitável na atmosfera: depende do custo de geração do `PlanetChunkGenerator`, que não medi.
- Predição: só interpolação, ou `SERVER_AND_CLIENT` com dead-reckoning? Medir a sensação com latência artificial.
- Aceitar o mixin em `ChunkMap.isChunkTracked`, ou manter a velocidade baixa o bastante para dispensá-lo?
- Esquema de teclas: Shift e Ctrl como acelerador (com mascaramento) ou teclas próprias?
- Dimensão de espaço: que view distance usar, e se isso é global (como no `Autopilot`) ou por jogador.
- A atitude no cruzamento de corpos: manter em coordenadas da dimensão ou reorientar pelo vetor "para cima" do novo corpo?
- Como tratar o desembarque seguro (posição de pouso guardada) e o pedido de desembarque em voo (payload com bit de flag).

### Arquivos relevantes do projeto

- `E:\git\Personal\Games\MineMods\SpaceHorizons\src\main\java\com\spacehorizons\space\travel\TravelServer.java`
- `...\space\travel\TravelTransfers.java`
- `...\space\travel\TravelTransferPayload.java`
- `...\space\travel\Autopilot.java`
- `...\space\travel\FlightBoost.java`
- `...\client\travel\TravelClient.java`
- `...\client\travel\TravelClientState.java`
- `...\mixin\travel\ClientPacketListenerMixin.java`
- `...\mixin\travel\ServerGamePacketListenerImplMixin.java`
- `...\mixin\CameraMixin.java`