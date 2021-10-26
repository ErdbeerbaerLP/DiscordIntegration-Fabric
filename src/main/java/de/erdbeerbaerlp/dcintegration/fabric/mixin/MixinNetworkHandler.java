package de.erdbeerbaerlp.dcintegration.fabric.mixin;


import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.fabric.api.FabricDiscordEventHandler;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.minecraft.network.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Function;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinNetworkHandler {
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Handle chat messages
     */
    @Redirect(method="handleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"))
    public void chatMessage(PlayerManager instance, Text txt, Function<ServerPlayerEntity, Text> playerMessageFactory, MessageType playerMessageType, UUID sender){
        if (PlayerLinkController.getSettings(null, player.getUuid()).hideFromDiscord){
            instance.broadcast(txt, playerMessageFactory, playerMessageType, sender);
            return;
        }

        Text finalTxt = txt;
        if (discord_instance.callEvent((e) -> {
            if (e instanceof FabricDiscordEventHandler) {
                return ((FabricDiscordEventHandler) e).onMcChatMessage(finalTxt, player);
            }
            return false;
        })) {
            instance.broadcast(txt, playerMessageFactory, playerMessageType, sender);
            return;
        }

        String text = MessageUtils.escapeMarkdown(((String)((TranslatableText)txt).getArgs()[1]));
        final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(txt);
        if (discord_instance != null) {
            TextChannel channel = discord_instance.getChannel(Configuration.instance().advanced.chatOutputChannelID);
            if (channel == null) {
                instance.broadcast(txt, playerMessageFactory, playerMessageType, sender);
                return;
            }
            discord_instance.sendMessage(FabricMessageUtils.formatPlayerName(player), player.getUuid().toString(), new DiscordMessage(embed, text, true), channel);
            final String json = Text.Serializer.toJson(txt);
            Component comp = GsonComponentSerializer.gson().deserialize(json);
            final String editedJson = GsonComponentSerializer.gson().serialize(MessageUtils.mentionsToNames(comp, channel.getGuild()));

            txt = Text.Serializer.fromJson(editedJson);
        }
        instance.broadcast(txt, playerMessageFactory, playerMessageType, sender);
    }

    /**
     * Handle possible timeout
     */
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void onDisconnect(final Text textComponent, CallbackInfo ci) {
        if (textComponent.equals(new TranslatableText("disconnect.timeout")))
            DiscordIntegration.timeouts.add(this.player.getUuid());
    }
}
