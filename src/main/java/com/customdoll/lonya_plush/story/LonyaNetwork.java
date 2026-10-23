package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.DollMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LonyaNetwork {
    public record Request(int entity, long token, int revision, int action, int value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DollMod.MODID, "story_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.entity); b.writeLong(p.token); b.writeVarInt(p.revision); b.writeVarInt(p.action); b.writeVarInt(p.value);
        }, b -> new Request(b.readVarInt(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DollMod.MODID, "story_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b,p) -> b.writeUtf(p.json,8192), b -> new Snapshot(b.readUtf(8192)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    /** Filled by the client setup event, never resolves a client class on dedicated servers. */
    public static java.util.function.Consumer<String> CLIENT = json -> { };
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("lonya-story-3");
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) ->
                context.enqueueWork(() -> LonyaStoryService.handle((ServerPlayer)context.player(), packet)));
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet, context) ->
                context.enqueueWork(() -> CLIENT.accept(packet.json)));
    }
    public static void send(ServerPlayer player, String json) {
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer))
            PacketDistributor.sendToPlayer(player, new Snapshot(json));
    }
}
