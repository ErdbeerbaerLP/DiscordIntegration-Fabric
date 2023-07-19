package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(at = @At(value = "TAIL"), method = "onDeath")
    private void onPlayerDeath(DamageSource s, CallbackInfo info) {
        ServerPlayerEntity p = (ServerPlayerEntity) (Object) this;

        if (LinkManager.isPlayerLinked(p.getUuid()) && LinkManager.getLink(null, p.getUuid()).settings.hideFromDiscord)
            return;
        if (DiscordIntegration.INSTANCE != null) {
            final Text deathMessage = s.getDeathMessage(p);
            final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(deathMessage);
            if (!Localization.instance().playerDeath.isBlank())
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.deathMessage.asEmbed) {

                    final EmbedBuilder b = Configuration.instance().embedMode.deathMessage.toEmbed();
                    b.setDescription(":skull: "+Localization.instance().playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", "")));
                    if(embed != null){
                        b.addBlankField(false);
                        b.addField(embed.getTitle()+" *("+embed.getFooter().getText()+")*",embed.getDescription(),false);
                    }
                    DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.deathsChannelID));
                } else
                    DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(embed, Localization.instance().playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", ""))), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.deathsChannelID));
        }
    }
}
