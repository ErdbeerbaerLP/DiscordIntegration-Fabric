package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegration;
import net.minecraft.network.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;
import java.util.function.Function;


@Mixin(value= ServerPlayNetworkHandler.class)
public class ChatMixin {
    @Shadow
    public ServerPlayerEntity player;
    /**
     * Handle chat messages
     */
    @Redirect(method = "handleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"))
    public void chatMessage(PlayerManager instance, Text txt, Function<ServerPlayerEntity, Text> playerMessageFactory, MessageType playerMessageType, UUID sender) {
        txt = DiscordIntegration.handleChatMessage(txt, player);
        instance.broadcast(txt, playerMessageFactory, playerMessageType, sender);

    }
}
