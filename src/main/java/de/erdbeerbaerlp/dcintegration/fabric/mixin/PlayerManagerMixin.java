package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import com.mojang.authlib.GameProfile;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.Variables;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.UUID;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    /**
     * Handle whitelisting
     */
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    public void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        if (discord_instance == null) return;

        if (Configuration.instance().linking.whitelistMode && discord_instance.srv.isOnlineMode()) {
            try {
                if (!PlayerLinkController.isPlayerLinked(profile.getId())) {
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedCode.replace("%code%", "" + discord_instance.genLinkNumber(profile.getId()))));
                } else if (!Variables.discord_instance.canPlayerJoin(profile.getId())) {
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedRole));
                }
            } catch (IllegalStateException e) {
                cir.setReturnValue(Text.of("Please check " + Variables.discordDataDir + "LinkedPlayers.json\n\n" + e.toString()));
            }
        }
    }

    @Inject(at = @At(value = "TAIL"), method = "onPlayerConnect")
    private void onPlayerJoin(ClientConnection conn, ServerPlayerEntity p, CallbackInfo ci) {
        if (discord_instance != null) {
            if (PlayerLinkController.getSettings(null, p.getUuid()).hideFromDiscord) return;
            discord_instance.sendMessage(Localization.instance().playerJoin.replace("%player%", FabricMessageUtils.formatPlayerName(p)));

            // Fix link status (if user does not have role, give the role to the user, or vice versa)
            final Thread fixLinkStatus = new Thread(() -> {
                if (Configuration.instance().linking.linkedRoleID.equals("0")) return;
                final UUID uuid = p.getUuid();
                if (!PlayerLinkController.isPlayerLinked(uuid)) return;
                final Guild guild = discord_instance.getChannel().getGuild();
                final Role linkedRole = guild.getRoleById(Configuration.instance().linking.linkedRoleID);
                if (PlayerLinkController.isPlayerLinked(uuid)) {
                    final Member member = guild.getMemberById(PlayerLinkController.getDiscordFromPlayer(uuid));
                    if (!member.getRoles().contains(linkedRole))
                        guild.addRoleToMember(member, linkedRole).queue();
                }
            });
            fixLinkStatus.setDaemon(true);
            fixLinkStatus.start();
        }
    }
}
