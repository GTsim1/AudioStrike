package com.example.client;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VoicechatAudioQueue {
    private static final ConcurrentLinkedQueue<short[]> queue = new ConcurrentLinkedQueue<>();
    private static final AudioFormat targetFormat = new AudioFormat(48000.0f, 16, 1, true, false);

    private static volatile int currentSessionId = 0;

    public static void playSound(String filename) {
        playSound(filename, 0.0);
    }

    public static void playSound(String filename, double startSec) {
        final int sessionId = ++currentSessionId;
        new Thread(() -> {
            try {
                queue.clear();
                File runDir = new File(System.getProperty("user.dir"));
                File soundFile = new File(new File(runDir, "killsounds"), filename);

                
                if (!soundFile.exists()) {
                    System.err.println("[SpotifyMod-Queue] Voice chat sound file not found: " + soundFile.getAbsolutePath());
                    return;
                }

                soundFile = FfmpegHelper.ensureVoicechatFormat(soundFile);

                AudioInputStream sourceStream = AudioSystem.getAudioInputStream(soundFile);
                AudioFormat sourceFormat = sourceStream.getFormat();
                
                AudioInputStream targetStream;
                try {
                    targetStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
                } catch (IllegalArgumentException e) {
                    targetStream = sourceStream;
                }

                
                long bytesToSkip = (long) (startSec * 96000.0);
                bytesToSkip = (bytesToSkip / 2) * 2; 
                if (bytesToSkip > 0 && sessionId == currentSessionId) {
                    long skipped = 0;
                    while (skipped < bytesToSkip && sessionId == currentSessionId) {
                        long r = targetStream.skip(bytesToSkip - skipped);
                        if (r <= 0) break;
                        skipped += r;
                    }
                }

                
                
                byte[] byteBuffer = new byte[960 * 2];
                int bytesRead;
                while (sessionId == currentSessionId && (bytesRead = targetStream.read(byteBuffer)) != -1) {
                    if (bytesRead > 0) {
                        int samples = bytesRead / 2;
                        short[] shortBuffer = new short[samples];
                        ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer);
                        
                        if (sessionId != currentSessionId) break;
                        
                        
                        if (samples < 960) {
                            short[] padded = new short[960];
                            System.arraycopy(shortBuffer, 0, padded, 0, samples);
                            queue.add(padded);
                        } else {
                            queue.add(shortBuffer);
                        }
                    }
                }
                targetStream.close();
                if (targetStream != sourceStream) {
                    sourceStream.close();
                }
            } catch (UnsupportedAudioFileException | IOException e) {
                System.err.println("[SpotifyMod-Queue] ERROR loading audio: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public static short[] pollNextFrame() {
        return queue.poll();
    }
    
    public static void stop() {
        queue.clear();
    }

    public static boolean isPlaying() {
        return !queue.isEmpty();
    }
}
