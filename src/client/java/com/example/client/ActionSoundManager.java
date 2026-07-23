package com.example.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ActionSoundManager {

    public enum ActionType {
        KILL_SOMEONE("Kill Player", 10000),
        DAMAGE_DEALT("Deal Damage", 1000),
        GETS_DAMAGED("Take Damage", 500),
        PLACE_BLOCK("Place Block", 500),
        BREAK_BLOCK("Break Block", 500);

        public final String displayName;
        public final long defaultDurationMs;

        ActionType(String displayName, long defaultDurationMs) {
            this.displayName = displayName;
            this.defaultDurationMs = defaultDurationMs;
        }
    }

    private static final Map<ActionType, String> actionSounds = new HashMap<>();
    private static int currentPlayInstance = 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    public static void init() {
        configFile = new File(System.getProperty("user.dir"), "config/audiostrike_actions.json");
        load();
        
        
        if (!MediaManager.activeKillSoundFile.isEmpty()) {
            if (!actionSounds.containsKey(ActionType.KILL_SOMEONE)) {
                assignSound(ActionType.KILL_SOMEONE, MediaManager.activeKillSoundFile);
            }
        }
    }

    public static void assignSound(ActionType action, String soundFile) {
        if (soundFile == null || soundFile.isEmpty()) {
            actionSounds.remove(action);
        } else {
            actionSounds.put(action, soundFile);
        }
        save();
    }

    public static String getSoundForAction(ActionType action) {
        return actionSounds.getOrDefault(action, "");
    }

    public static void playActionSound(ActionType action) {
        String soundFile = getSoundForAction(action);
        if (soundFile.isEmpty()) {
            return;
        }

        final int currentId = ++currentPlayInstance;
        
        LocalSoundPlayer.playKillSound(soundFile);
        VoicechatAudioQueue.playSound(soundFile);

        new Thread(() -> {
            try {
                Thread.sleep(action.defaultDurationMs);
                if (currentPlayInstance == currentId) {
                    LocalSoundPlayer.stopSound();
                    VoicechatAudioQueue.stop();
                }
            } catch (InterruptedException e) {}
        }).start();

        Minecraft client = Minecraft.getInstance();
        if (client.player != null && action == ActionType.KILL_SOMEONE) {
            client.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§d[SpotifyMod] §a★ Played " + soundFile + " for " + action.displayName + " §a★")
            );
        }
    }

    private static void load() {
        if (!configFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<ActionType, String>>() {}.getType();
            Map<ActionType, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                actionSounds.clear();
                actionSounds.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        if (configFile == null) return;
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(actionSounds, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
