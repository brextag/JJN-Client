package com.jjn.client;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnEconomyManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JjnClientMod implements ClientModInitializer {

    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("jjnclient");

    @Override
    public void onInitializeClient() {
        JjnConfig.get().load();
        JjnWaypoints.get().load();
        JjnFriendsManager.get().start();
        JjnEconomyManager.get().start();
        LOGGER.info("JJN Client v{} initialized for Minecraft 26.2", VERSION);
    }
}
