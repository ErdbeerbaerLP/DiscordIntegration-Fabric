package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
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

import java.util.UUID;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.INSTANCE;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(at = @At(value = "TAIL"), method = "onDeath")
    private void onPlayerDeath(DamageSource s, CallbackInfo info) {
        ServerPlayerEntity p = (ServerPlayerEntity) (Object) this;

        if (DiscordIntegration.INSTANCE != null) {
            if (LinkManager.isPlayerLinked(p.getUuid()) && LinkManager.getLink(null, p.getUuid()).settings.hideFromDiscord)
                return;
            final Text deathMessage = s.getDeathMessage(p);
            final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(deathMessage, p.getWorld());
            if (!Localization.instance().playerDeath.isBlank())
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.deathMessage.asEmbed) {
                    final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", p.getUuid().toString()).replace("%uuid_dashless%", p.getUuid().toString().replace("-", "")).replace("%name%", p.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
                    if(!Configuration.instance().embedMode.deathMessage.customJSON.isBlank()){
                        final EmbedBuilder b = Configuration.instance().embedMode.deathMessage.toEmbedJson(Configuration.instance().embedMode.deathMessage.customJSON
                                .replace("%uuid%", p.getUuid().toString())
                                .replace("%uuid_dashless%", p.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(p))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%deathMessage%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", ""))
                                .replace("%playerColor%", ""+ TextColors.generateFromUUID(p.getUuid()).getRGB())
                        );
                        if (embed != null) {
                            b.addBlankField(false);
                            b.addField(embed.getTitle() + " *(" + embed.getFooter().getText() + ")*", embed.getDescription(), false);
                        }
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.deathsChannelID));
                    }else {
                        final EmbedBuilder b = Configuration.instance().embedMode.deathMessage.toEmbed();
                        b.setDescription(":skull: " + Localization.instance().playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", "")));
                        if (embed != null) {
                            b.addBlankField(false);
                            b.addField(embed.getTitle() + " *(" + embed.getFooter().getText() + ")*", embed.getDescription(), false);
                        }
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.deathsChannelID));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(embed, Localization.instance().playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", ""))), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.deathsChannelID));
        }
    }
}
