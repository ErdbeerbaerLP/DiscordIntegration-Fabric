package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public class AdvancementMixin {
    @Shadow
    ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;onStatusUpdate(Lnet/minecraft/advancement/Advancement;)V"))
    public void advancement(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (DiscordIntegration.INSTANCE == null) return;
        if (LinkManager.isPlayerLinked(owner.getUuid())&&LinkManager.getLink(null, owner.getUuid()).settings.hideFromDiscord) return;
        if (advancement != null && advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceToChat())
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
