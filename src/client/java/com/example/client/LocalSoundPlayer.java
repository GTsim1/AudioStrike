package com.example.client;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class LocalSoundPlayer {
    private static Clip clip;
    public static volatile String currentPlayingFile = "";
    public static volatile float previewVolume = 1.0f;

    public static void playKillSound(String filename) {
        playKillSound(filename, false);
    }

    public static void playKillSound(String filename, boolean allowResume) {
        new Thread(() -> {
            try {
                File runDir = new File(System.getProperty("user.dir"));
                File killsoundsDir = new File(runDir, "killsounds");
                File soundFile = new File(killsoundsDir, filename);
                
                if (!soundFile.exists()) {
                    System.err.println(filename + " not found in killsounds folder.");
                    return;
                }

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                AudioFormat format = audioStream.getFormat();
                DataLine.Info info = new DataLine.Info(Clip.class, format);

                synchronized (LocalSoundPlayer.class) {
                    if (allowResume && filename.equals(currentPlayingFile) && clip != null && clip.isOpen()) {
                        clip.start();
                        return;
                    }

                    if (clip != null && clip.isOpen()) {
                        clip.stop();
                        clip.close();
                    }

                    clip = (Clip) AudioSystem.getLine(info);
                    clip.open(audioStream);
                    
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            synchronized (LocalSoundPlayer.class) {
                                if (filename.equals(currentPlayingFile)) {
                                    currentPlayingFile = "";
                                }
                            }
                        }
                    });

                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (Math.log10(Math.max(previewVolume, 0.0001f)) * 20.0f);
                        gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                    }
                    currentPlayingFile = filename;
                    clip.start();
                }
                System.out.println("Playing " + filename + "...");

            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
                synchronized (LocalSoundPlayer.class) {
                    if (filename.equals(currentPlayingFile)) {
                        currentPlayingFile = "";
                    }
                }
            }
        }).start();
    }

    public static void playPreview(String filename, double startSec) {
        new Thread(() -> {
            try {
                File runDir = new File(System.getProperty("user.dir"));
                File killsoundsDir = new File(runDir, "killsounds");
                File soundFile = new File(killsoundsDir, filename);
                
                if (!soundFile.exists()) {
                    return;
                }

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                AudioFormat format = audioStream.getFormat();
                DataLine.Info info = new DataLine.Info(Clip.class, format);

                synchronized (LocalSoundPlayer.class) {
                    if (clip != null && clip.isOpen()) {
                        clip.stop();
                        clip.close();
                    }

                    clip = (Clip) AudioSystem.getLine(info);
                    clip.open(audioStream);
                    
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            synchronized (LocalSoundPlayer.class) {
                                if (filename.equals(currentPlayingFile)) {
                                    currentPlayingFile = "";
                                }
                            }
                        }
                    });

                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (Math.log10(Math.max(previewVolume, 0.0001f)) * 20.0f);
                        gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                    }
                    clip.setMicrosecondPosition((long)(startSec * 1_000_000.0));
                    currentPlayingFile = filename;
                    clip.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void stopSound() {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen()) {
                clip.stop();
                clip.close();
            }
            currentPlayingFile = "";
        }
    }

    public static void pauseSound() {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        }
    }

    public static void setLooping(boolean loop) {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen()) {
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    clip.loop(0);
                }
            }
        }
    }
    public static void setVolume(float volume) {
        previewVolume = Math.max(0.0f, Math.min(1.0f, volume));
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen() && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log10(Math.max(previewVolume, 0.0001f)) * 20.0f);
                gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
            }
        }
    }

    public static java.util.List<String> getAvailableSounds() {
        java.util.List<String> sounds = new java.util.ArrayList<>();
        File killsoundsDir = new File(System.getProperty("user.dir"), "killsounds");
        if (killsoundsDir.exists() && killsoundsDir.isDirectory()) {
            File[] files = killsoundsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
            if (files != null) {
                for (File f : files) {
                    sounds.add(f.getName());
                }
            }
        }
        return sounds;
    }

    public static boolean isClipPlaying() {
        synchronized (LocalSoundPlayer.class) {
            return clip != null && clip.isRunning();
        }
    }

    public static double getClipPosition() {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen()) {
                return clip.getMicrosecondPosition() / 1000000.0;
            }
            return 0;
        }
    }

    public static double getClipDuration() {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen()) {
                return clip.getMicrosecondLength() / 1000000.0;
            }
            return 0;
        }
    }

    public static void seekClip(int seconds) {
        synchronized (LocalSoundPlayer.class) {
            if (clip != null && clip.isOpen()) {
                clip.setMicrosecondPosition((long) seconds * 1000000L);
            }
        }
    }
}
