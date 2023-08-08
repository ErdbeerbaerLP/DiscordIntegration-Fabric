package de.erdbeerbaerlp.dcintegration.fabric.mixin;


import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegrationMod;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ServerPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Handle possible timeout
     */
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void onDisconnect(final Text textComponent, CallbackInfo ci) {
        if (textComponent.equals(new TranslatableText("disconnect.timeout")))
            DiscordIntegrationMod.timeouts.add(this.player.getUuid());
    }

    @Inject(at = @At(value = "HEAD"), method = "onDisconnected")
    private void onPlayerLeave(Text reason, CallbackInfo info) {
        if (DiscordIntegrationMod.stopped) return; //Try to fix player leave messages after stop!
        if (LinkManager.isPlayerLinked(player.getUuid()) && LinkManager.getLink(null, player.getUuid()).settings.hideFromDiscord)
            return;
        final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", player.getUuid().toString()).replace("%uuid_dashless%", player.getUuid().toString().replace("-", "")).replace("%name%", player.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
        if (DiscordIntegration.INSTANCE != null && !DiscordIntegrationMod.timeouts.contains(player.getUuid())) {
            if (!Localization.instance().playerLeave.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.playerLeaveMessages.asEmbed) {
                    if (!Configuration.instance().embedMode.playerLeaveMessages.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbedJson(Configuration.instance().embedMode.playerLeaveMessages.customJSON
                                .replace("%uuid%", player.getUuid().toString())
                                .replace("%uuid_dashless%", player.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(player))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%playerColor%", "" + TextColors.generateFromUUID(player.getUuid()).getRGB())
                        );
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    } else {
                        EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbed();
                        b = b.setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                                .setDescription(Localization.instance().playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
            }
        } else if (DiscordIntegration.INSTANCE != null && DiscordIntegrationMod.timeouts.contains(player.getUuid())) {
            if (!Localization.instance().playerTimeout.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.playerLeaveMessages.asEmbed) {
                    EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbed();
                    b = b.setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                            .setDescription(Localization.instance().playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
                    DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
            }
            DiscordIntegrationMod.timeouts.remove(player.getUuid());
        }
    }
}
