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
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    private void onMergeClientSound(MergeClientSoundEvent event) {
        if (!eventsFired) {
            eventsFired = true;
        }

        short[] frame = VoicechatAudioQueue.pollNextFrame();
        if (frame != null) {
            event.mergeAudio(frame);
            mergeCount++;
        }
    }

    private void onClientSound(ClientSoundEvent event) {
        if (VoicechatAudioQueue.isPlaying()) {
            short[] frame = VoicechatAudioQueue.pollNextFrame();
            if (frame != null) {
                short[] existing = event.getRawAudio();
                if (existing != null && existing.length == frame.length) {
                    for (int i = 0; i < existing.length; i++) {
                        int mixed = existing[i] + frame[i];
                        existing[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
                    }
                    event.setRawAudio(existing);
                } else {
                    event.setRawAudio(frame);
                }
            }
        }
    }
}
