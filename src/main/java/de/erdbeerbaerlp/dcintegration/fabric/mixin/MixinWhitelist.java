package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import com.mojang.authlib.GameProfile;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.Variables;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

@Mixin(PlayerManager.class)
public class MixinWhitelist {
    /**
     * Handle whitelisting
     */
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    public void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir){
        if (discord_instance == null) return;

        if (Configuration.instance().linking.whitelistMode && discord_instance.srv.isOnlineMode()) {
            try {
                if (!PlayerLinkController.isPlayerLinked(profile.getId())) {
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedCode.replace("%code%",""+ discord_instance.genLinkNumber(profile.getId()))));
                }else if(!Variables.discord_instance.canPlayerJoin(profile.getId())){
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedRole));
                }
            } catch (IllegalStateException e) {
                cir.setReturnValue(Text.of("Please check " + Variables.discordDataDir + "LinkedPlayers.json\n\n" + e.toString()));
            }
        }
    }
}
