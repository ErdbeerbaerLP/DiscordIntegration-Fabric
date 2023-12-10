package de.erdbeerbaerlp.dcintegration.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.minecraftCommands.MCSubCommand;
import de.erdbeerbaerlp.dcintegration.common.minecraftCommands.McCommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MinecraftPermission;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricServerInterface;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;

public class McCommandDiscord {
    public McCommandDiscord(CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> l = CommandManager.literal("discord");
        if (Configuration.instance().ingameCommand.enabled) l.executes((ctx) -> {
            ctx.getSource().sendFeedback(() -> Texts.setStyleIfAbsent(Text.literal(Configuration.instance().ingameCommand.message),
                    Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(Configuration.instance().ingameCommand.hoverMessage)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Configuration.instance().ingameCommand.inviteURL))), false);
            return 0;
        }).requires((s) -> ((FabricServerInterface) DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(s.getPlayer(), MinecraftPermission.USER, MinecraftPermission.RUN_DISCORD_COMMAND));
        for (MCSubCommand cmd : McCommandRegistry.getCommands()) {
            l.then(CommandManager.literal(cmd.getName()));
        }
        dispatcher.register(l);
    }
}
