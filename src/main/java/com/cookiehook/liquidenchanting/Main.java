package com.cookiehook.liquidenchanting;

import com.cookiehook.liquidenchanting.init.ModItems;
import com.cookiehook.liquidenchanting.proxy.CommonProxy;
import com.cookiehook.liquidenchanting.util.Config;
import com.cookiehook.liquidenchanting.util.Reference;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class Main {

    @Mod.Instance
    public static Main instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configDir = new File(event.getModConfigurationDirectory() + "/" + Reference.MOD_ID);
        configDir.mkdir();
        Config.init(new File(configDir.getPath(), Reference.MOD_ID + ".cfg"));

        ModItems.registerItems();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.registerItemColor();
    }

}
