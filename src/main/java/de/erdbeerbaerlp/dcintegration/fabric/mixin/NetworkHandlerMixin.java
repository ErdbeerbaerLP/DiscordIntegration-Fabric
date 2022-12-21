package de.erdbeerbaerlp.dcintegration.fabric.mixin;


import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
