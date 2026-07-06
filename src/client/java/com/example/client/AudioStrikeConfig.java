package com.example.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class AudioStrikeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "audiostrike.json");

    public boolean enableAnimation = true;
    public int maxCharacters = 12;
    public boolean showAllCharacters = false;
    public boolean showOtherPlayersSongs = true;
    public boolean broadcastMySong = true;
    public boolean showLikesOnNametag = true;

    private static AudioStrikeConfig INSTANCE;

    public static AudioStrikeConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, AudioStrikeConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
                INSTANCE = new AudioStrikeConfig();
            }
        } else {
            INSTANCE = new AudioStrikeConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
