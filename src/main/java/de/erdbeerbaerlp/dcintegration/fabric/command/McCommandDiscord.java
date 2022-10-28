package de.erdbeerbaerlp.dcintegration.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.erdbeerbaerlp.dcintegration.common.addon.AddonLoader;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.io.IOException;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

public class McCommandDiscord {
    public McCommandDiscord(CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> l = CommandManager.literal("discord");
        if (Configuration.instance().ingameCommand.enabled) l.executes((ctx) -> {
            ctx.getSource().sendFeedback(Texts.setStyleIfAbsent(new LiteralText(Configuration.instance().ingameCommand.message),
                    Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(Configuration.instance().ingameCommand.hoverMessage)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Configuration.instance().ingameCommand.inviteURL))), false);
            return 0;
        });
        l.then(CommandManager.literal("ignore").executes((ctx) -> {
            ctx.getSource().sendFeedback(
                    Text.of(discord_instance.togglePlayerIgnore(ctx.getSource().getPlayer().getUuid()) ? Localization.instance().commands.commandIgnore_unignore : Localization.instance().commands.commandIgnore_ignore), true);
            return 0;
        })).then(CommandManager.literal("link").executes((ctx) -> {
            if (Configuration.instance().linking.enableLinking && discord_instance.srv.isOnlineMode() && !Configuration.instance().linking.whitelistMode) {
                if (PlayerLinkController.isPlayerLinked(ctx.getSource().getPlayer().getUuid())) {
                    ctx.getSource().sendFeedback(Text.of(Formatting.RED + Localization.instance().linking.alreadyLinked.replace("%player%", discord_instance.getJDA().getUserById(PlayerLinkController.getDiscordFromBedrockPlayer(ctx.getSource().getPlayer().getUuid())).getAsTag())), false);
                    return 0;
                }
                final int r = discord_instance.genLinkNumber(ctx.getSource().getPlayer().getUuid());
                ctx.getSource().sendFeedback(Texts.setStyleIfAbsent(new LiteralText(Localization.instance().linking.linkMsgIngame.replace("%num%", r + "").replace("%prefix%", "/")), Style.EMPTY.withFormatting(Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "" + r)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(Localization.instance().linking.hoverMsg_copyClipboard)))), false);
            } else {
                ctx.getSource().sendFeedback(Text.of(Formatting.RED + Localization.instance().commands.subcommandDisabled), false);
            }
            return 0;
        })).then(CommandManager.literal("reload").requires((p) -> p.hasPermissionLevel(4)).executes((ctx) -> {
            try {
                Configuration.instance().loadConfig();
            } catch (IOException e) {
                ctx.getSource().sendFeedback(Texts.setStyleIfAbsent(new LiteralText(e.getMessage()),Style.EMPTY.withFormatting(Formatting.RED)),true);
                e.printStackTrace();
            }
            AddonLoader.reloadAll();
            ctx.getSource().sendFeedback(Text.of(Localization.instance().commands.configReloaded), true);
            return 0;
        }));
        dispatcher.register(l);
    }
}
