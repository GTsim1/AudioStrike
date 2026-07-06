package com.example.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MediaManager {
    private static Process process;
    private static BufferedWriter writer;
    private static Thread readerThread;
    
    // Nested Session Class
    public static class MediaSession {
        public String id;
        public String name;
        public String iconPath;
        public boolean isActive;
        public Identifier iconIdentifier;
        public boolean iconNeedsReload = false;
        
        public MediaSession(String id, String name, String iconPath, boolean isActive) {
            this.id = id;
            this.name = name;
            this.iconPath = iconPath;
            this.isActive = isActive;
        }
    }
    
    public static java.util.List<MediaSession> activeSessions = new java.util.ArrayList<>();
    
    // Spotify Search results & persistent kill song states
    public static class SearchResult {
        public String title;
        public String artist;
        public String uri;
        
        public SearchResult(String title, String artist, String uri) {
            this.title = title;
            this.artist = artist;
            this.uri = uri;
        }
    }
    public static java.util.List<SearchResult> searchResults = new java.util.ArrayList<>();
    public static String activeKillSoundFile = "";
    public static String lastSearchError = "";
    public static int lastAttackedEntityId = -1;
    public static long lastAttackTime = 0;
    public static boolean isStoredSongsActive = false;
    public static int storedSongsIndex = 0;
    public static int cardX = 10;
    public static int cardY = 10;
    public static int cardWidth = 280;
    public static int cardHeight = 130;

    // Media States
    public static boolean hasSession = false;
    public static String title = "";
    public static String artist = "";
    public static String source = "";
    public static boolean isPlaying = false;
    public static double position = 0;
    public static double duration = 0;
    public static double playbackSpeed = 1.0;
    public static long timelineUpdateTime = 0;
    public static String artworkPath = "";
    
    public static int artworkWidth = 0;
    public static int artworkHeight = 0;
    
    public static Identifier currentArtworkIdentifier = null;
    private static boolean artworkNeedsReload = false;
    
    public static java.util.Set<String> likedSongs = new java.util.HashSet<>();
    private static File likedSongsFile;
    
    private static final Object lock = new Object();

    public static void start() {
        try {
            File runDir = Minecraft.getInstance().gameDirectory;
            File configDir = new File(runDir, "config");
            
            File helperDir = new File(configDir, "audiostrike_helper");
            if (!helperDir.exists()) {
                helperDir.mkdirs();
            }
            File helperExe = new File(helperDir, "MediaHelper.exe");
            
            String currentVersion = "1.0.1";
            File versionFile = new File(helperDir, "version.txt");
            boolean needsExtract = true;
            if (helperExe.exists() && versionFile.exists()) {
                try {
                    String v = new String(Files.readAllBytes(versionFile.toPath())).trim();
                    if (v.equals(currentVersion)) needsExtract = false;
                } catch (Exception e) {}
            }
            
            if (needsExtract) {
                InputStream is = MediaManager.class.getResourceAsStream("/assets/modid/MediaHelper.zip");
                if (is == null) {
                    System.err.println("MediaHelper.zip not found in resources!");
                    return;
                }
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        File outFile = new File(helperDir, entry.getName());
                        if (entry.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            outFile.getParentFile().mkdirs();
                            Files.copy(zis, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                Files.write(versionFile.toPath(), currentVersion.getBytes());
            }
            
            likedSongsFile = new File(configDir, "spotify_liked_songs.txt");
            if (likedSongsFile.exists()) {
                try {
                    java.util.List<String> lines = Files.readAllLines(likedSongsFile.toPath());
                    likedSongs.addAll(lines);
                } catch (Exception e) {}
            }
            
            ProcessBuilder pb = new ProcessBuilder(helperExe.getAbsolutePath(), runDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();
            
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            
            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                             if (json.has("Error")) {
                                synchronized (lock) {
                                    lastSearchError = json.get("Error").getAsString();
                                    searchResults.clear();
                                }
                                System.err.println("MediaHelper error: " + lastSearchError);
                                continue;
                            }
                            if (json.has("Log")) {
                                System.out.println("MediaHelper log: " + json.get("Log").getAsString());
                                continue;
                            }
                            
                            if (json.has("SearchResult")) {
                                JsonArray resultsArray = json.getAsJsonArray("SearchResult");
                                java.util.List<SearchResult> list = new java.util.ArrayList<>();
                                for (JsonElement elem : resultsArray) {
                                    JsonObject item = elem.getAsJsonObject();
                                    list.add(new SearchResult(
                                        item.get("Title").getAsString(),
                                        item.get("Artist").getAsString(),
                                        item.get("Uri").getAsString()
                                    ));
                                }
                                synchronized (lock) {
                                    searchResults = list;
                                    lastSearchError = "";
                                }
                                continue;
                            }
                            
                            synchronized (lock) {
                                // Parse active sessions list
                                if (json.has("Sessions")) {
                                    JsonArray sessionsArray = json.getAsJsonArray("Sessions");
                                    java.util.List<MediaSession> newSessionsList = new java.util.ArrayList<>();
                                    for (JsonElement elem : sessionsArray) {
                                        JsonObject sessJson = elem.getAsJsonObject();
                                        String sId = sessJson.get("Id").getAsString();
                                        String sName = sessJson.get("Name").getAsString();
                                        String sIconPath = sessJson.get("IconPath").getAsString();
                                        boolean sIsActive = sessJson.get("IsActive").getAsBoolean();
                                        
                                        MediaSession existing = null;
                                        for (MediaSession old : activeSessions) {
                                            if (old.id.equals(sId)) {
                                                existing = old;
                                                break;
                                            }
                                        }
                                        
                                        MediaSession session = new MediaSession(sId, sName, sIconPath, sIsActive);
                                        if (existing != null) {
                                            session.iconIdentifier = existing.iconIdentifier;
                                            if (!sIconPath.equals(existing.iconPath) || existing.iconIdentifier == null) {
                                                session.iconNeedsReload = true;
                                            }
                                        } else {
                                            session.iconNeedsReload = true;
                                        }
                                        newSessionsList.add(session);
                                    }
                                    activeSessions = newSessionsList;
                                } else {
                                    activeSessions.clear();
                                }

                                 if (isStoredSongsActive) {
                                     updateStoredSongsState();
                                 } else {
                                     hasSession = json.get("HasSession").getAsBoolean();
                                     if (hasSession) {
                                         String newTitle = json.get("Title").getAsString();
                                         String newArtist = json.get("Artist").getAsString();
                                         boolean songChanged = !newTitle.equals(title) || !newArtist.equals(artist);
                                         
                                         title = newTitle;
                                         artist = newArtist;
                                         source = json.get("Source").getAsString();
                                         isPlaying = json.get("IsPlaying").getAsBoolean();
                                         position = json.get("Position").getAsDouble();
                                         duration = json.get("Duration").getAsDouble();
                                         playbackSpeed = json.get("PlaybackSpeed").getAsDouble();
                                         timelineUpdateTime = json.get("TimelineUpdateTime").getAsLong();
                                         
                                         MediaControlScreen.isFavorited = likedSongs.contains(title);
                                         
                                         if (songChanged) {
                                             artworkPath = "";
                                             currentArtworkIdentifier = null;
                                             artworkNeedsReload = true;
                                             artworkWidth = 0;
                                             artworkHeight = 0;
                                             
                                             if (MediaControlScreen.isMicActive) {
                                                 String matched = MediaControlScreen.getMatchedFile();
                                                 if (matched != null) {
                                                     MediaControlScreen.currentMicFile = matched;
                                                     VoicechatAudioQueue.stop();
                                                     VoicechatAudioQueue.playSound(matched);
                                                 } else {
                                                     MediaControlScreen.currentMicFile = "";
                                                     VoicechatAudioQueue.stop();
                                                 }
                                             }
                                         }
                                         
                                         String newArtwork = json.get("ArtworkPath").getAsString();
                                         if (!newArtwork.isEmpty() && !newArtwork.equals(artworkPath)) {
                                             artworkPath = newArtwork;
                                             artworkNeedsReload = true;
                                         }
                                     } else {
                                         title = "";
                                         artist = "";
                                         source = "";
                                         isPlaying = false;
                                         position = 0;
                                         duration = 0;
                                         playbackSpeed = 1.0;
                                         timelineUpdateTime = 0;
                                         artworkPath = "";
                                         currentArtworkIdentifier = null;
                                         artworkWidth = 0;
                                         artworkHeight = 0;
                                         
                                         if (MediaControlScreen.isMicActive) {
                                             MediaControlScreen.currentMicFile = "";
                                             VoicechatAudioQueue.stop();
                                         }
                                     }
                                 }
                            }
                        } catch (Exception e) {
                            // JSON parsing error or empty line
                        }
                    }
                } catch (IOException e) {
                    System.err.println("MediaHelper communication error: " + e.getMessage());
                }
            });
            readerThread.setName("MediaHelper Reader");
            readerThread.setDaemon(true);
            readerThread.start();
            
            System.out.println("MediaHelper companion process launched successfully.");
        } catch (Exception e) {
            System.err.println("Failed to start MediaHelper: " + e.getMessage());
        }
    }

    public static boolean isSpotifyConfigured() {
        File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
        File configFile = new File(configDir, "spotify_mod.properties");
        if (!configFile.exists()) {
            return false;
        }
        try {
            java.util.Properties props = new java.util.Properties();
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            }
            String id = props.getProperty("spotify_client_id", "");
            String secret = props.getProperty("spotify_client_secret", "");
            return !id.trim().isEmpty() && !secret.trim().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
    private static int killSoundInstanceId = 0;

    public static void onKillRegistered(net.minecraft.world.entity.LivingEntity entity) {
        ActionSoundManager.playActionSound(ActionSoundManager.ActionType.KILL_SOMEONE);
    }

    public static void stop() {
        if (process != null && process.isAlive()) {
            sendCommand("exit");
            process.destroy();
        }
    }

    public static void sendCommand(String cmd) {
        if (isStoredSongsActive) {
            handleLocalCommand(cmd);
            return;
        }
        if (writer != null) {
            try {
                writer.write(cmd + "\n");
                writer.flush();
            } catch (IOException e) {
                System.err.println("Failed to send command to MediaHelper: " + e.getMessage());
            }
        }
    }
    
    public static void toggleLike() {
        if (title.isEmpty()) return;
        
        String currentSong = title;
        if (artist != null && !artist.isEmpty() && !artist.equals("Local Playlist")) {
            currentSong += " - " + artist;
        }
        
        // Send a global like instead of saving locally!
        ServerTracker.sendLike(currentSong);
        
        // Update the visual heart to be pink immediately
        MediaControlScreen.isFavorited = true;
    }

    public static void updateArtworkTexture(Minecraft client) {
        boolean reload = false;
        String path = "";
        synchronized (lock) {
            if (artworkNeedsReload) {
                artworkNeedsReload = false;
                reload = true;
                path = artworkPath;
            }
        }

        if (reload) {
            if (path == null || path.isEmpty()) {
                currentArtworkIdentifier = null;
            } else {
                final String finalPath = path;
                client.execute(() -> {
                    try {
                        File file = new File(finalPath);
                        if (file.exists()) {
                            try (FileInputStream fis = new FileInputStream(file)) {
                                NativeImage image = NativeImage.read(fis);
                                artworkWidth = image.getWidth();
                                artworkHeight = image.getHeight();
                                DynamicTexture texture = new DynamicTexture(() -> "spotify_artwork", image);
                                texture.upload();
                                
                                Identifier id = Identifier.fromNamespaceAndPath("modid", "dynamic/artwork");
                                client.getTextureManager().register(id, texture);
                                currentArtworkIdentifier = id;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to register artwork texture: " + e.getMessage());
                    }
                });
            }
        }
        
        // Update session icons
        java.util.List<MediaSession> sessionsToUpdate = new java.util.ArrayList<>();
        synchronized (lock) {
            for (MediaSession session : activeSessions) {
                if (session.iconNeedsReload && !session.iconPath.isEmpty()) {
                    session.iconNeedsReload = false;
                    sessionsToUpdate.add(session);
                }
            }
        }
        
        for (MediaSession session : sessionsToUpdate) {
            client.execute(() -> {
                try {
                    File file = new File(session.iconPath);
                    if (file.exists()) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            NativeImage image = NativeImage.read(fis);
                            DynamicTexture texture = new DynamicTexture(() -> "spotify_icon_" + session.id, image);
                            texture.upload();
                            
                            String pathSafeId = session.id.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
                            Identifier id = Identifier.fromNamespaceAndPath("modid", "dynamic/icon/" + pathSafeId);
                            client.getTextureManager().register(id, texture);
                            session.iconIdentifier = id;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to register icon texture for " + session.name + ": " + e.getMessage());
                }
            });
        }
    }

    public static void updateStoredSongsState() {
        java.util.List<String> sounds = com.example.client.LocalSoundPlayer.getAvailableSounds();
        if (sounds.isEmpty()) {
            hasSession = true;
            title = "No downloaded songs";
            artist = "Use Setup to download sounds";
            source = "Stored songs";
            isPlaying = false;
            position = 0;
            duration = 0;
            return;
        }
        
        if (storedSongsIndex < 0 || storedSongsIndex >= sounds.size()) {
            storedSongsIndex = 0;
        }
        
        String sound = sounds.get(storedSongsIndex);
        boolean songChanged = !sound.equals(title);
        hasSession = true;
        title = sound;
        artist = "Local Playlist";
        source = "Stored songs";
        isPlaying = com.example.client.LocalSoundPlayer.isClipPlaying();
        
        if (songChanged && MediaControlScreen.isMicActive) {
            MediaControlScreen.currentMicFile = sound;
            VoicechatAudioQueue.stop();
            VoicechatAudioQueue.playSound(sound);
        }
        
        if (sound.equals(com.example.client.LocalSoundPlayer.currentPlayingFile)) {
            position = com.example.client.LocalSoundPlayer.getClipPosition();
            duration = com.example.client.LocalSoundPlayer.getClipDuration();
        } else {
            position = 0;
            duration = 0;
        }
    }

    private static void handleLocalCommand(String cmd) {
        java.util.List<String> sounds = com.example.client.LocalSoundPlayer.getAvailableSounds();
        if (sounds.isEmpty()) return;

        if (storedSongsIndex < 0 || storedSongsIndex >= sounds.size()) {
            storedSongsIndex = 0;
        }

        String sound = sounds.get(storedSongsIndex);

        if (cmd.equals("toggle")) {
            if (com.example.client.LocalSoundPlayer.isClipPlaying() && sound.equals(com.example.client.LocalSoundPlayer.currentPlayingFile)) {
                com.example.client.LocalSoundPlayer.stopSound();
            } else {
                com.example.client.LocalSoundPlayer.playKillSound(sound);
            }
        } else if (cmd.equals("next")) {
            storedSongsIndex = (storedSongsIndex + 1) % sounds.size();
            com.example.client.LocalSoundPlayer.playKillSound(sounds.get(storedSongsIndex));
        } else if (cmd.equals("prev")) {
            storedSongsIndex = (storedSongsIndex - 1 + sounds.size()) % sounds.size();
            com.example.client.LocalSoundPlayer.playKillSound(sounds.get(storedSongsIndex));
        } else if (cmd.startsWith("seek ")) {
            try {
                int seekSecs = Integer.parseInt(cmd.substring(5));
                com.example.client.LocalSoundPlayer.seekClip(seekSecs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (cmd.equals("repeat")) {
            com.example.client.LocalSoundPlayer.setLooping(com.example.client.MediaControlScreen.isRepeatEnabled);
        }
    }

    public static double getCurrentTrackPosition() {
        if (isStoredSongsActive) {
            updateStoredSongsState();
            return position;
        }
        boolean playing;
        double pos;
        double speed;
        long time;
        double dur;
        synchronized (lock) {
            playing = isPlaying;
            pos = position;
            speed = playbackSpeed;
            time = timelineUpdateTime;
            dur = duration;
        }
        if (!playing) {
            return pos;
        }
        long elapsed = System.currentTimeMillis() - time;
        double elapsedSeconds = (elapsed / 1000.0) * speed;
        double currentPos = pos + elapsedSeconds;
        return Math.min(currentPos, dur);
    }

    public static void saveLayout() {
        java.io.File configFile = new java.io.File(System.getProperty("user.dir"), "config/spotify_layout.json");
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            java.io.FileWriter writer = new java.io.FileWriter(configFile);
            writer.write(String.format("{\"x\": %d, \"y\": %d, \"w\": %d, \"h\": %d}", cardX, cardY, cardWidth, cardHeight));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadLayout() {
        java.io.File configFile = new java.io.File(System.getProperty("user.dir"), "config/spotify_layout.json");
        if (configFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                if (json.has("x")) cardX = json.get("x").getAsInt();
                if (json.has("y")) cardY = json.get("y").getAsInt();
                if (json.has("w")) cardWidth = json.get("w").getAsInt();
                if (json.has("h")) cardHeight = json.get("h").getAsInt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
