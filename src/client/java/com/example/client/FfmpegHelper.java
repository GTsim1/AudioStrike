package com.example.client;

import java.io.File;

public class FfmpegHelper {

    public static void cropAudio(String filename, double startSec, double durationSec) {
        new Thread(() -> {
            try {
                File runDir = new File(System.getProperty("user.dir"));
                File killsoundsDir = new File(runDir, "killsounds");
                File inputFile = new File(killsoundsDir, filename);
                
                if (!inputFile.exists()) {
                    System.err.println("Cannot crop, file not found: " + inputFile.getAbsolutePath());
                    return;
                }
                
                
                File tempFile = new File(killsoundsDir, "temp_crop_" + filename);
                
                File ffmpegExe = new File(System.getProperty("user.home"), ".spotdl/ffmpeg.exe");
                File ffmpegBin = new File(System.getProperty("user.home"), ".spotdl/ffmpeg");
                
                String ffmpegPath = "ffmpeg"; 
                if (ffmpegExe.exists()) ffmpegPath = ffmpegExe.getAbsolutePath();
                else if (ffmpegBin.exists()) ffmpegPath = ffmpegBin.getAbsolutePath();

                System.out.println("Starting ffmpeg crop: " + startSec + " for " + durationSec + "s");

                ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, 
                    "-y", 
                    "-i", inputFile.getAbsolutePath(), 
                    "-ss", String.valueOf(startSec), 
                    "-t", String.valueOf(durationSec),
                    "-ac", "1", 
                    "-ar", "48000", 
                    tempFile.getAbsolutePath()
                );
                
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                System.out.println("ffmpeg crop exited with code: " + exitCode);
                
                if (exitCode == 0 && tempFile.exists()) {
                    
                    inputFile.delete();
                    tempFile.renameTo(inputFile);
                    System.out.println("Crop successful, replaced original file.");
                } else {
                    if (tempFile.exists()) tempFile.delete();
                    System.err.println("Crop failed.");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static File ensureVoicechatFormat(File inputFile) {
        try {
            File vcFile = new File(inputFile.getParent(), "vc_" + inputFile.getName());
            if (vcFile.exists()) {
                return vcFile;
            }
            
            File ffmpegExe = new File(System.getProperty("user.home"), ".spotdl/ffmpeg.exe");
            File ffmpegBin = new File(System.getProperty("user.home"), ".spotdl/ffmpeg");
            String ffmpegPath = "ffmpeg";
            if (ffmpegExe.exists()) ffmpegPath = ffmpegExe.getAbsolutePath();
            else if (ffmpegBin.exists()) ffmpegPath = ffmpegBin.getAbsolutePath();

            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, 
                "-y", 
                "-i", inputFile.getAbsolutePath(), 
                "-ac", "1", 
                "-ar", "48000", 
                vcFile.getAbsolutePath()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && vcFile.exists()) {
                return vcFile;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return inputFile;
    }
}
