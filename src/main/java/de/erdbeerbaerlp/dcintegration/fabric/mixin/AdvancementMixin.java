package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(PlayerAdvancementTracker.class)
public class AdvancementMixin {
    @Shadow
    private ServerPlayerEntity owner;

    @Redirect(method = "grantCriterion", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/Advancement;getRewards()Lnet/minecraft/advancement/AdvancementRewards;"))
    public AdvancementRewards advancement(Advancement advancement) {
        if (DiscordIntegration.INSTANCE == null) return null;
        if (LinkManager.isPlayerLinked(owner.getUuid()) && LinkManager.getLink(null, owner.getUuid()).settings.hideFromDiscord)
            return null;
        if (advancement != null && advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceToChat()) {

            if (!Localization.instance().advancementMessage.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.advancementMessage.asEmbed) {
                    final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", owner.getUuid().toString()).replace("%uuid_dashless%", owner.getUuid().toString().replace("-", "")).replace("%name%", owner.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
                    if (!Configuration.instance().embedMode.advancementMessage.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.advancementMessage.toEmbedJson(Configuration.instance().embedMode.advancementMessage.customJSON
                                .replace("%uuid%", owner.getUuid().toString())
                                .replace("%uuid_dashless%", owner.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(owner))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%advName%", Formatting.strip(advancement.getDisplay().getTitle().getString()))
                                .replace("%advDesc%", Formatting.strip(advancement.getDisplay().getDescription().getString()))
                                .replace("%playerColor%", "" + TextColors.generateFromUUID(owner.getUuid()).getRGB())
                        );
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    } else {
                        EmbedBuilder b = Configuration.instance().embedMode.advancementMessage.toEmbed();
                        b = b.setAuthor(FabricMessageUtils.formatPlayerName(owner), null, avatarURL)
                                .setDescription(Localization.instance().advancementMessage.replace("%player%",
                                                Formatting.strip(FabricMessageUtils.formatPlayerName(owner))).replace("%name%",
                                                Formatting.strip(advancement
                                                        .getDisplay()
                                                        .getTitle()
                                                        .getString()))
                                        .replace("%desc%",
                                                Formatting.strip(advancement
                                                        .getDisplay()
                                                        .getDescription()
                                                        .getString()))
                                        .replace("\\n", "\n"));
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().advancementMessage.replace("%player%",
                                    Formatting.strip(FabricMessageUtils.formatPlayerName(owner)))
                            .replace("%name%",
                                    Formatting.strip(advancement
                                            .getDisplay()
                                            .getTitle()
                                            .getString()))
                            .replace("%desc%",
                                    Formatting.strip(advancement
                                            .getDisplay()
                                            .getDescription()
                                            .getString()))
                            .replace("\\n", "\n"));
            }
        }


        return advancement.getRewards();
    }
}
