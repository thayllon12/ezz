package com.pastinha.joguinho;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import java.util.HashMap;

/** Efeitos (SoundPool) e musica (MediaPlayer). Todos os arquivos vem dos packs CC0 da Kenney. */
final class Sound {
    private static final String[] SFX = {
            "shoot1.ogg", "shoot2.ogg", "shoot3.ogg", "explode_small.ogg", "explode_big.ogg",
            "bomb.ogg", "hit.ogg", "enemy_hit.ogg", "shield.ogg", "warning.ogg", "powerup.ogg",
            "heal.ogg", "coin.ogg", "levelup.ogg", "click.wav", "back.wav", "start.wav"
    };

    private final Context ctx;
    private SoundPool pool;
    private final HashMap<String, Integer> ids = new HashMap<>();
    private final HashMap<String, Long> last = new HashMap<>();
    private MediaPlayer mp;
    private String curName;
    private String wantName;
    private boolean wantLoop = true;
    private float wantVol = 0.5f;
    private boolean hostPaused = false;
    volatile boolean enabled;

    Sound(Context c, boolean enabled) {
        ctx = c;
        this.enabled = enabled;
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(aa).build();
            for (String f : SFX) {
                try (AssetFileDescriptor afd = ctx.getAssets().openFd("sfx/" + f)) {
                    ids.put(f.substring(0, f.lastIndexOf('.')), pool.load(afd, 1));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            pool = null;
        }
    }

    void play(String key, float vol, float rate) {
        if (!enabled || pool == null) return;
        Integer id = ids.get(key);
        if (id == null) return;
        long now = System.currentTimeMillis();
        Long l = last.get(key);
        if (l != null && now - l < 45) return; // evita empilhar o mesmo som
        last.put(key, now);
        pool.play(id, vol, vol, 1, 0, rate);
    }

    void play(String key) {
        play(key, 0.8f, 1f);
    }

    void music(String name, boolean loop, float vol) {
        wantName = name;
        wantLoop = loop;
        wantVol = vol;
        apply();
    }

    void setEnabled(boolean on) {
        enabled = on;
        apply();
    }

    void setHostPaused(boolean p) {
        hostPaused = p;
        apply();
    }

    private synchronized void apply() {
        try {
            if (!enabled || hostPaused || wantName == null) {
                if (mp != null && mp.isPlaying()) mp.pause();
                return;
            }
            if (mp != null && wantName.equals(curName)) {
                mp.setVolume(wantVol, wantVol);
                if (!mp.isPlaying()) mp.start();
                return;
            }
            releasePlayer();
            AssetFileDescriptor afd = ctx.getAssets().openFd("music/" + wantName);
            mp = new MediaPlayer();
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setLooping(wantLoop);
            mp.setVolume(wantVol, wantVol);
            mp.prepare();
            mp.start();
            curName = wantName;
        } catch (Exception e) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        try {
            if (mp != null) mp.release();
        } catch (Exception ignored) {
        }
        mp = null;
        curName = null;
    }

    synchronized void release() {
        releasePlayer();
        if (pool != null) pool.release();
        pool = null;
    }
}
