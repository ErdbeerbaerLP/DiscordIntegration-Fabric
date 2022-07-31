package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(at = @At(value = "TAIL"), method = "onDeath")
    private void onPlayerDeath(DamageSource s, CallbackInfo info) {
        ServerPlayerEntity p = (ServerPlayerEntity) (Object) this;

        if (PlayerLinkController.getSettings(null, p.getUuid()).hideFromDiscord) return;
        if (discord_instance != null) {
            final Text deathMessage = s.getDeathMessage(p);
            final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(deathMessage);
            discord_instance.sendMessage(new DiscordMessage(embed, Localization.instance().playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", ""))), discord_instance.getChannel(Configuration.instance().advanced.deathsChannelID));
        }
    }
}
