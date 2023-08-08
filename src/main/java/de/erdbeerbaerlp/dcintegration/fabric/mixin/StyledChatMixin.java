package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegrationMod;
import eu.pb4.styledchat.StyledChatUtils;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(StyledChatUtils.class)
public class StyledChatMixin {
    @Redirect(method = "modifyForSending", at = @At(value = "INVOKE", target = "Leu/pb4/styledchat/StyledChatUtils;formatMessage(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/util/registry/RegistryKey;)Lnet/minecraft/text/Text;"))
    private static Text message(SignedMessage msg, ServerCommandSource s, RegistryKey<MessageType> e) {
        if (e.equals(MessageType.CHAT))
            msg = DiscordIntegrationMod.handleChatMessage(msg, s.getPlayer());
        return StyledChatUtils.formatMessage(msg, s, e);
    }
}
