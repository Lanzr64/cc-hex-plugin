package dev.lz.cchexplugin;

import dan200.computercraft.api.ComputerCraftAPI;
import net.minecraftforge.fml.common.Mod;

@Mod(CcHexPlugin.MOD_ID)
public final class CcHexPlugin {
    public static final String MOD_ID = "cc_hex_plugin";

    public CcHexPlugin() {
        ComputerCraftAPI.registerAPIFactory(HexIotaApi::new);
    }
}
