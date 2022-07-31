package de.erdbeerbaerlp.dcintegration.fabric.mixin;


import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.regex.Pattern;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

@Mixin(value=ServerPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Handle possible timeout
     */
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void onDisconnect(final Text textComponent, CallbackInfo ci) {
        if (textComponent.equals(Text.translatable("disconnect.timeout")))
            DiscordIntegration.timeouts.add(this.player.getUuid());
    }


    // Just after the command is executed
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"),
            method = "onCommandExecution", locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onCommandExecuted(CommandExecutionC2SPacket packet, CallbackInfo ci) {

        final String command = packet.command().replaceFirst(Pattern.quote("/"), "");
        if (!Configuration.instance().commandLog.channelID.equals("0")) {
            if (!ArrayUtils.contains(Configuration.instance().commandLog.ignoredCommands, command.split(" ")[0]))
                discord_instance.sendMessage(Configuration.instance().commandLog.message
                        .replace("%sender%", player.getName().getString())
                        .replace("%cmd%", command)
                        .replace("%cmd-no-args%", command.split(" ")[0]), discord_instance.getChannel(Configuration.instance().commandLog.channelID));
        }
        if (discord_instance != null) {
            boolean raw = false;

            if (((command.startsWith("say")) && Configuration.instance().messages.sendOnSayCommand) || (command.startsWith("me") && Configuration.instance().messages.sendOnMeCommand)) {
                String msg = command.replace("say ", "");
                if (command.startsWith("say"))
                    msg = msg.replaceFirst("say ", "");
                if (command.startsWith("me")) {
                    raw = true;
                    msg = "*" + MessageUtils.escapeMarkdown(msg.replaceFirst("me ", "").trim()) + "*";
                }
                discord_instance.sendMessage(player.getName().getString(), player.getUuid().toString(), new DiscordMessage(null, msg, !raw), discord_instance.getChannel(Configuration.instance().advanced.chatOutputChannelID));
            }
        }

    }

    @Inject(at = @At(value = "HEAD"), method = "onDisconnected")
    private void onPlayerLeave(Text reason, CallbackInfo info) {
        if (DiscordIntegration.stopped) return; //Try to fix player leave messages after stop!
        if (PlayerLinkController.getSettings(null, player.getUuid()).hideFromDiscord) return;
        if (discord_instance != null && !DiscordIntegration.timeouts.contains(player.getUuid()))
            discord_instance.sendMessage(Localization.instance().playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
        else if (discord_instance != null && DiscordIntegration.timeouts.contains(player.getUuid())) {
            discord_instance.sendMessage(Localization.instance().playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
            DiscordIntegration.timeouts.remove(player.getUuid());
        }
    }
}
