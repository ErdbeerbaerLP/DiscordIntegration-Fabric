package de.erdbeerbaerlp.dcintegration.fabric.command;

import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;


public class DCCommandSender extends ServerCommandSource {
    private final CompletableFuture<InteractionHook> cmdMsg;
    private CompletableFuture<Message> cmdMessage;
    public final StringBuilder message = new StringBuilder();

    public DCCommandSender(CompletableFuture<InteractionHook> cmdMsg, User user, MinecraftServer server) {
        super(CommandOutput.DUMMY, new Vec3d(0, 0, 0), new Vec2f(0, 0), server.getOverworld(), 4, user.getAsTag(), Text.of(user.getAsTag()), server, null);
        this.cmdMsg = cmdMsg;
    }

    public DCCommandSender(MinecraftServer server) {
        super(CommandOutput.DUMMY, new Vec3d(0, 0, 0), new Vec2f(0, 0), server.getOverworld(), 4, "DiscordIntegration", Text.of("Discord Integration"), server, null);
        this.cmdMsg = null;
    }


    private static String textComponentToDiscordMessage(Text component) {
        if (component == null) return "";
        return MessageUtils.convertMCToMarkdown(component.getString());
    }

    @Override
    public void sendFeedback(Supplier<Text> feedbackSupplier, boolean broadcastToOps) {
        message.append(textComponentToDiscordMessage(feedbackSupplier.get())).append("\n");
        if (cmdMsg != null)
            if (cmdMessage == null)
                cmdMsg.thenAccept((msg) -> {
                    cmdMessage = msg.editOriginal(message.toString().trim()).submit();
                });
            else
                cmdMessage.thenAccept((msg) -> {
                    cmdMessage = msg.editMessage(message.toString().trim()).submit();
                });
    }

    @Override
    public void sendError(Text message) {
        this.sendFeedback(() -> message, false);
    }
}