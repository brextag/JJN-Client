package com.jjn.launcher;

public class InstallerTest {

    public static void main(String[] args) {
        System.out.println("Connecting to Mojang...");

        try {
            String latest =
                    MinecraftVersionService.getLatestRelease();

            System.out.println("Latest Minecraft release: " + latest);

            System.out.println("SUCCESS!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}