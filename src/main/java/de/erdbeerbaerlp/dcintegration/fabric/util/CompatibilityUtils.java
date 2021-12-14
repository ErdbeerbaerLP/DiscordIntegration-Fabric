package de.erdbeerbaerlp.dcintegration.fabric.util;

import net.fabricmc.loader.api.FabricLoader;

public class CompatibilityUtils {

    /**
     * @return true if mod with id 'styledchat' is present.
     */
    public static boolean styledChatLoaded() {
       return FabricLoader.getInstance().isModLoaded("styledchat");
    }
}
