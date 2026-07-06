package com.example.client;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;

public class SpotifyVoicechatPlugin implements VoicechatPlugin {
    public static volatile boolean pluginLoaded = false;
    public static volatile boolean eventsFired = false;
    public static volatile int mergeCount = 0;

    @Override
    public String getPluginId() {
        return "spotify_mod";
    }

    @Override
    public void initialize(VoicechatApi api) {
        pluginLoaded = true;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, this::onMergeClientSound);
    }

    private void onMergeClientSound(MergeClientSoundEvent event) {
        if (!eventsFired) {
            eventsFired = true;
        }

        boolean shouldPlay = (MediaControlScreen.isMicActive && MediaManager.isPlaying) || MediaControlScreen.isDirectMicActive;
        if (shouldPlay) {
            short[] frame = VoicechatAudioQueue.pollNextFrame();
            if (frame != null) {
                float volume = LocalSoundPlayer.previewVolume;
                if (volume != 1.0f) {
                    for (int i = 0; i < frame.length; i++) {
                        int val = (int) (frame[i] * volume);
                        frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, val));
                    }
                }
                event.mergeAudio(frame);
                mergeCount++;
            } else {
                if (MediaControlScreen.isDirectMicActive) {
                    MediaControlScreen.isDirectMicActive = false;
                    MediaControlScreen.currentMicFile = "";
                }
            }
        }
    }
}
