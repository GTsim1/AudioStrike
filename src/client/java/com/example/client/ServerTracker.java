package com.example.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTracker {
    // This needs to be set to the user's Hugging Face Space URL! Use wss:// for secure WebSockets
    public static String API_URL = "wss://gtsim-audiostrike-serve.hf.space";
    
    // Map of Username -> Current Song
    public static final ConcurrentHashMap<String, String> activeUsersOnServer = new ConcurrentHashMap<>();
    
    private static Thread trackerThread;
    private static boolean isRunning = false;
    private static WebSocket webSocket;
    private static String currentConnectedServer = "";
    private static String lastSentSong = null;

    public static void start() {
        if (isRunning) return;
        isRunning = true;
        
        trackerThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // We check if we are actually connected to a multiplayer server
                    Minecraft client = Minecraft.getInstance();
                    if (client != null && client.getCurrentServer() != null && client.player != null) {
                        String username = client.getUser().getName();
                        String serverIp = client.getCurrentServer().ip;
                        
                        // Get current song playing, or an empty string if nothing is playing
                        String currentSong = "";
                        if (AudioStrikeConfig.getInstance().broadcastMySong) {
                            if (MediaManager.isPlaying || MediaManager.title.length() > 0) {
                                currentSong = MediaManager.title;
                                if (MediaManager.artist != null && !MediaManager.artist.isEmpty() && !MediaManager.artist.equals("Local Playlist")) {
                                    currentSong += " - " + MediaManager.artist;
                                }
                                
                                // Privacy Filter: Never broadcast YouTube videos or Twitch streams
                                String lowerSong = currentSong.toLowerCase();
                                if (lowerSong.contains("- youtube") || lowerSong.contains("youtube.com") || lowerSong.contains("twitch.tv")) {
                                    currentSong = "";
                                }
                            }
                        }
                        
                        // If we joined a new server, or websocket disconnected, connect!
                        if (!serverIp.equals(currentConnectedServer) || webSocket == null) {
                            connectWebSocket(username, serverIp);
                            currentConnectedServer = serverIp;
                            lastSentSong = null; // force update of song
                        }
                        
                        // If we are connected, and our song changed, send an update!
                        if (webSocket != null && !currentSong.equals(lastSentSong)) {
                            JsonObject payload = new JsonObject();
                            payload.addProperty("song", currentSong);
                            webSocket.sendText(payload.toString(), true);
                            lastSentSong = currentSong;
                        }
                    } else {
                        // If we are in the main menu or singleplayer, disconnect and clear the list
                        if (webSocket != null) {
                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnected").join();
                            webSocket = null;
                        }
                        currentConnectedServer = "";
                        activeUsersOnServer.clear();
                    }
                    
                    // Sleep for 1 second before checking if song changed
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break; // Thread interrupted, likely shutting down
                } catch (Exception e) {
                    System.err.println("Error in ServerTracker: " + e.getMessage());
                    try {
                        Thread.sleep(3000); // Sleep even on error to prevent spamming
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Stopping").join();
                webSocket = null;
            }
        });
        
        trackerThread.setName("AudioStrike ServerTracker");
        trackerThread.setDaemon(true);
        trackerThread.start();
    }
    
    public static void stop() {
        isRunning = false;
        if (trackerThread != null) {
            trackerThread.interrupt();
        }
    }

    private static void connectWebSocket(String username, String serverIp) {
        if (API_URL.contains("YOUR-HUGGINGFACE-SPACE-URL")) {
            return; // Don't connect until they set up their API URL!
        }
        
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting").join();
            } catch (Exception e) {}
        }
        
        try {
            // e.g. wss://space.hf.space/ws/hypixel.net/GTsim
            String wsUrl = API_URL + "/ws/" + serverIp + "/" + username;
            
            HttpClient client = HttpClient.newHttpClient();
            webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    StringBuilder messageBuilder = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuilder.append(data);
                        if (last) {
                            String message = messageBuilder.toString();
                            messageBuilder.setLength(0); // Reset for next message
                            
                            try {
                                JsonObject responseJson = JsonParser.parseString(message).getAsJsonObject();
                                if (responseJson.has("players")) {
                                    JsonArray players = responseJson.getAsJsonArray("players");
                                    
                                    // Temporarily store the newly fetched active users
                                    ConcurrentHashMap<String, String> newUsers = new ConcurrentHashMap<>();
                                    
                                    for (JsonElement element : players) {
                                        JsonObject playerObj = element.getAsJsonObject();
                                        String uName = playerObj.get("username").getAsString();
                                        String sName = playerObj.get("song").getAsString();
                                        newUsers.put(uName, sName);
                                    }
                                    
                                    // Swap the maps
                                    activeUsersOnServer.clear();
                                    activeUsersOnServer.putAll(newUsers);
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to parse websocket message: " + e.getMessage());
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        ServerTracker.webSocket = null;
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("WebSocket Error: " + error.getMessage());
                        ServerTracker.webSocket = null;
                    }
                }).join();
        } catch (Exception e) {
            System.err.println("Failed to connect WebSocket: " + e.getMessage());
            webSocket = null;
        }
    }
}
