package de.erdbeerbaerlp.dcintegration.fabric.util;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordEventListener;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.ComponentUtils;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.common.util.ServerInterface;
import de.erdbeerbaerlp.dcintegration.common.util.Variables;
import de.erdbeerbaerlp.dcintegration.fabric.command.DCCommandSender;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
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

public class FabricServerInterface implements ServerInterface {
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
    public void sendMCMessage(Component msg) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        try {
            for (final ServerPlayerEntity p : l) {
                if (!Variables.discord_instance.ignoringPlayers.contains(p.getUuid()) && !(PlayerLinkController.isPlayerLinked(p.getUuid()) && PlayerLinkController.getSettings(null, p.getUuid()).ignoreDiscordChatIngame)) {
                    final Map.Entry<Boolean, Component> ping = ComponentUtils.parsePing(msg, p.getUuid(), p.getName().getString());
                    final String jsonComp = GsonComponentSerializer.gson().serialize(ping.getValue()).replace("\\\\n", "\n");
                    final Text comp = TextArgumentType.text().parse(new StringReader(jsonComp));
                    p.sendMessage(comp, false);
                    if (ping.getKey()) {
                        if (PlayerLinkController.getSettings(null, p.getUuid()).pingSound) {
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
    public void sendMCReaction(Member member, RestAction<Message> retrieveMessage, UUID targetUUID, EmojiUnion reactionEmote) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        for (final ServerPlayerEntity p : l) {
            if (p.getUuid().equals(targetUUID) && !Variables.discord_instance.ignoringPlayers.contains(p.getUuid()) && !PlayerLinkController.getSettings(null, p.getUuid()).ignoreDiscordChatIngame && !PlayerLinkController.getSettings(null, p.getUuid()).ignoreReactions) {

                final String emote = ":"+ reactionEmote.getName() + ":";// ? ":" + reactionEmote.getEmote().getName() + ":" : MessageUtils.formatEmoteMessage(new ArrayList<>(), reactionEmote.getEmoji());
                String outMsg = Localization.instance().reactionMessage.replace("%name%", member.getEffectiveName()).replace("%name2%", member.getUser().getAsTag()).replace("%emote%", emote);
                if (Localization.instance().reactionMessage.contains("%msg%"))
                    retrieveMessage.submit().thenAccept((m) -> {
                        String outMsg2 = outMsg.replace("%msg%", m.getContentDisplay());
                        sendReactionMCMessage(p, MessageUtils.formatEmoteMessage(m.getMentions().getCustomEmojis(), outMsg2));
                    });
                else sendReactionMCMessage(p, outMsg);
            }
        }
    }
    private void sendReactionMCMessage(ServerPlayerEntity target, String msg) {
        final Component msgComp = MinecraftSerializer.INSTANCE.serialize(msg.replace("\n", "\\n"), DiscordEventListener.mcSerializerOptions);
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
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            players.put(p.getUuid(), p.getDisplayName().getString().isEmpty() ? p.getName().getString() : p.getDisplayName().getString());
        }
        return players;
    }

    @Override
    public void sendMCMessage(String msg, UUID player) {
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
}
