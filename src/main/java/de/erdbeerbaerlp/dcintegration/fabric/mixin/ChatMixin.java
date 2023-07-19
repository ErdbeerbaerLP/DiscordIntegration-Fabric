package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegrationMod;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(value= ServerPlayNetworkHandler.class)
public class ChatMixin {
    /**
     * Handle chat messages
     */
    @Redirect(method = "handleDecoratedMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V"))
    public void chatMessage(PlayerManager instance, SignedMessage signedMessage, ServerPlayerEntity sender, MessageType.Parameters params) {
        signedMessage = DiscordIntegrationMod.handleChatMessage(signedMessage, sender);
        instance.broadcast(signedMessage, sender, params);

    }
}
