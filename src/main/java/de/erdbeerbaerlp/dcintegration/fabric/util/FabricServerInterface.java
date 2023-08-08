package de.erdbeerbaerlp.dcintegration.fabric.util;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.com.vdurmont.emoji.EmojiParser;
import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.TextReplacementConfig;
import dcshadow.net.kyori.adventure.text.event.ClickEvent;
import dcshadow.net.kyori.adventure.text.event.HoverEvent;
import dcshadow.net.kyori.adventure.text.format.Style;
import dcshadow.net.kyori.adventure.text.format.TextColor;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import dcshadow.net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.ComponentUtils;
import de.erdbeerbaerlp.dcintegration.common.util.McServerInterface;
import de.erdbeerbaerlp.dcintegration.common.util.MinecraftPermission;
import de.erdbeerbaerlp.dcintegration.fabric.command.DCCommandSender;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FabricServerInterface implements McServerInterface{
    private final MinecraftServer server;

    public FabricServerInterface(MinecraftServer minecraftServer) {

        this.server = minecraftServer;
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayerCount();
    }

    @Override
    public int getOnlinePlayers() {
        return server.getCurrentPlayerCount();
    }

    @Override
    public void sendIngameMessage(Component msg) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        try {
            for (final ServerPlayerEntity p : l) {
                if (!DiscordIntegration.INSTANCE.ignoringPlayers.contains(p.getUuid()) && !(LinkManager.isPlayerLinked(p.getUuid()) && LinkManager.getLink(null, p.getUuid()).settings.ignoreDiscordChatIngame)) {
                    final Map.Entry<Boolean, Component> ping = ComponentUtils.parsePing(msg, p.getUuid(), p.getName().getString());
                    final String jsonComp = GsonComponentSerializer.gson().serialize(ping.getValue()).replace("\\\\n", "\n");
                    final Text comp = TextArgumentType.text().parse(new StringReader(jsonComp));
                    p.sendMessage(comp, false);
                    if (ping.getKey()) {
                        if (LinkManager.isPlayerLinked(p.getUuid())&&LinkManager.getLink(null, p.getUuid()).settings.pingSound) {
                            p.networkHandler.sendPacket(new PlaySoundS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, p.getPos().x,p.getPos().y,p.getPos().z, 1, 1, server.getOverworld().getSeed()));
                        }
                    }
                }
            }
            //Send to server console too
            final String jsonComp = GsonComponentSerializer.gson().serialize(msg).replace("\\\\n", "\n");
            final Text comp = TextArgumentType.text().parse(new StringReader(jsonComp));
            server.sendMessage(comp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendIngameReaction(Member member, RestAction<Message> retrieveMessage, UUID targetUUID, EmojiUnion reactionEmote) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        for (final ServerPlayerEntity p : l) {
            if (p.getUuid().equals(targetUUID) && !DiscordIntegration.INSTANCE.ignoringPlayers.contains(p.getUuid()) && (LinkManager.isPlayerLinked(p.getUuid())&&!LinkManager.getLink(null, p.getUuid()).settings.ignoreDiscordChatIngame && !LinkManager.getLink(null, p.getUuid()).settings.ignoreReactions)) {

                final String emote = reactionEmote.getType() == Emoji.Type.UNICODE ? EmojiParser.parseToAliases(reactionEmote.getName()) : ":" + reactionEmote.getName() + ":";

                Style.Builder memberStyle = Style.style();
                if (Configuration.instance().messages.discordRoleColorIngame)
                    memberStyle = memberStyle.color(TextColor.color(member.getColorRaw()));

                final Component user = Component.text(member.getEffectiveName()).style(memberStyle
                        .clickEvent(ClickEvent.suggestCommand("<@" + member.getId() + ">"))
                        .hoverEvent(HoverEvent.showText(Component.text(Localization.instance().discordUserHover.replace("%user#tag%", member.getUser().getAsTag()).replace("%user%", member.getEffectiveName()).replace("%id%", member.getUser().getId())))));
                final TextReplacementConfig userReplacer = ComponentUtils.replaceLiteral("%user%", user);
                final TextReplacementConfig emoteReplacer = ComponentUtils.replaceLiteral("%emote%", emote);

                final Component out = LegacyComponentSerializer.legacySection().deserialize(Localization.instance().reactionMessage)
                        .replaceText(userReplacer).replaceText(emoteReplacer);

                if (Localization.instance().reactionMessage.contains("%msg%"))
                    retrieveMessage.submit().thenAccept((m) -> {
                        final String msg = FabricMessageUtils.formatEmoteMessage(m.getMentions().getCustomEmojis(), m.getContentDisplay());
                        final TextReplacementConfig msgReplacer = ComponentUtils.replaceLiteral("%msg%", msg);
                        sendReactionMCMessage(p, out.replaceText(msgReplacer));
                    });
                else sendReactionMCMessage(p, out);
            }
        }
    }
    private void sendReactionMCMessage(ServerPlayerEntity target, Component msgComp) {
        final String jsonComp = GsonComponentSerializer.gson().serialize(msgComp).replace("\\\\n", "\n");
        try {
            final Text comp = TextArgumentType.text().parse(new StringReader(jsonComp));
            target.sendMessage(comp, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void runMcCommand(String cmd, CompletableFuture<InteractionHook> cmdMsg, User user) {
        final DCCommandSender s = new DCCommandSender(cmdMsg, user, server);
        if (s.hasPermissionLevel(4)) {
            try {
                server.getCommandManager().getDispatcher().execute(cmd.trim(), s);
            } catch (CommandSyntaxException e) {
                s.sendError(Text.of(e.getMessage()));
            }

        } else
            s.sendError(Text.of("Sorry, but the bot has no permissions...\nAdd this into the servers ops.json:\n```json\n {\n   \"uuid\": \"" + Configuration.instance().commands.senderUUID + "\",\n   \"name\": \"DiscordFakeUser\",\n   \"level\": 4,\n   \"bypassesPlayerLimit\": false\n }\n```"));

    }

    @Override
    public HashMap<UUID, String> getPlayers() {
        final HashMap<UUID, String> players = new HashMap<>();
        for (final ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            players.put(p.getUuid(), p.getDisplayName().getString().isEmpty() ? p.getName().getString() : p.getDisplayName().getString());
        }
        return players;
    }

    @Override
    public void sendIngameMessage(String msg, UUID player) {
        final ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null)
            p.sendMessage( Text.of(msg));
    }

    @Override
    public boolean isOnlineMode() {
        return Configuration.instance().bungee.isBehindBungee || server.isOnlineMode();
    }

    @Override
    public String getNameFromUUID(UUID uuid) {
        return server.getSessionService().fillProfileProperties(new GameProfile(uuid,""),false).getName();
    }

    @Override
    public String getLoaderName() {
        return "Fabric";
    }

    @Override
    public boolean playerHasPermissions(UUID player, String... permissions) {
        return false;
    }

    @Override
    public boolean playerHasPermissions(UUID player, MinecraftPermission... permissions) {
        return McServerInterface.super.playerHasPermissions(player, permissions);
    }
}
