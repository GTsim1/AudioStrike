package com.example.client;

import java.io.File;

public class SpotDLDownloader {
    public static boolean hasCheckedFfmpeg = false;
    public static volatile boolean isDownloading = false;

    public static void downloadLink(String link) {
        new Thread(() -> {
            try {
                isDownloading = true;
                File runDir = new File(System.getProperty("user.dir"));
                File killsoundsDir = new File(runDir, "killsounds");
                if (!killsoundsDir.exists()) {
                    killsoundsDir.mkdirs();
                }

                File ffmpegExe = new File(System.getProperty("user.home"), ".spotdl/ffmpeg.exe");
                File ffmpegBin = new File(System.getProperty("user.home"), ".spotdl/ffmpeg");
                if (!hasCheckedFfmpeg && !ffmpegExe.exists() && !ffmpegBin.exists()) {
                    System.out.println("Checking/Downloading FFmpeg for SpotDL...");
                    ProcessBuilder ffmpegPb = new ProcessBuilder("python", "-m", "spotdl", "--download-ffmpeg");
                    ffmpegPb.directory(runDir);
                    ffmpegPb.inheritIO();
                    Process ffmpegProcess = ffmpegPb.start();
                    ffmpegProcess.waitFor();
                }
                hasCheckedFfmpeg = true;

                System.out.println("Starting SpotDL download for: " + link);
                
                
                ProcessBuilder pb = new ProcessBuilder(
                    "python", "-m", "spotdl", link,
                    "--format", "wav",
                    "--ffmpeg-args", "-ac 1 -ar 48000",
                    "--output", "killsounds/{title}.{ext}"
                );
                pb.directory(runDir);
                pb.inheritIO();
                
                Process p = pb.start();
                int exitCode = p.waitFor();
                System.out.println("SpotDL exited with code: " + exitCode);
                
                if (exitCode == 0) {
                    System.out.println("Download complete! Check your gallery.");
                } else {
                    System.err.println("SpotDL download failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isDownloading = false;
            }
        }).start();
    }
}
