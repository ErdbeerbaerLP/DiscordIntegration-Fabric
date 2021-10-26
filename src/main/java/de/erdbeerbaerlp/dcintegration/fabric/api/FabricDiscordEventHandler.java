package de.erdbeerbaerlp.dcintegration.fabric.api;

import de.erdbeerbaerlp.dcintegration.common.api.DiscordEventHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class FabricDiscordEventHandler extends DiscordEventHandler {
    public abstract boolean onMcChatMessage(Text txt, ServerPlayerEntity player);
}
