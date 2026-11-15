package com.pastinha.joguinho;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

/** Carrega e guarda em cache todos os sprites (PNG) e a fonte da pasta assets. */
final class Assets {
    private final Context ctx;
    private final HashMap<String, Bitmap> cache = new HashMap<>();
    final Typeface font;

    Assets(Context c) {
        ctx = c;
        Typeface f;
        try {
            f = Typeface.createFromAsset(c.getAssets(), "fonts/future.ttf");
        } catch (Exception e) {
            f = Typeface.DEFAULT_BOLD;
        }
        font = f;
    }

    /** Le todos os PNG das pastas usadas, para nao travar durante o jogo. */
    void preload() {
        String[] dirs = {"ships", "damage", "enemies", "lasers", "meteors", "fx",
                "explosion", "powerups", "ui", "bg"};
        for (String d : dirs) {
            try {
                String[] files = ctx.getAssets().list(d);
                if (files == null) continue;
                for (String f : files) {
                    if (f.endsWith(".png")) get(d + "/" + f);
                }
            } catch (Exception ignored) {
            }
        }
    }

    Bitmap get(String path) {
        Bitmap b = cache.get(path);
        if (b != null) return b;
        try (InputStream in = ctx.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {
        }
        if (b == null) {
            // arquivo ausente: devolve um bitmap vazio de 2x2 para nunca dar crash
            b = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        }
        cache.put(path, b);
        return b;
    }

    /** Sequencia numerada, ex: seq("explosion/regularExplosion%02d.png", 0, 8). */
    Bitmap[] seq(String fmt, int from, int to) {
        Bitmap[] r = new Bitmap[to - from + 1];
        for (int i = from; i <= to; i++) {
            r[i - from] = get(String.format(Locale.US, fmt, i));
        }
        return r;
    }
}
