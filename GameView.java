package com.pastinha.joguinho;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Joguinho - shooter vertical 2D.
 * Todos os graficos, efeitos e sons vem de packs CC0 (Kenney) na pasta assets.
 * O codigo so posiciona, anima e toca esses arquivos; nada e desenhado por codigo.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ---------- estados ----------
    static final int MENU = 0, SELECT = 1, PLAY = 2, PAUSE = 3, OVER = 4;
    // ---------- botoes ----------
    static final int B_PLAY = 1, B_SHIPS = 2, B_SOUND = 3, B_BACK = 4, B_PAUSE = 5, B_BOMB = 6,
            B_RESUME = 7, B_RESTART = 8, B_MENU = 9, B_AGAIN = 10, B_CARD = 100;
    // ---------- inimigos ----------
    static final int K_DRONE = 0, K_ZIG = 1, K_SHOOT = 2, K_KAMI = 3, K_TANK = 4, K_UFO = 5, K_BOSS = 6;
    // ---------- itens ----------
    static final int P_SHIELD = 0, P_WEAPON = 1, P_HEAL = 2, P_BOMB = 3, P_COIN = 4;

    // ---------- dados das naves ----------
    static final String[] SHIP_NAME = {"FALCAO", "TITA", "RAIO"};
    static final String[] SHIP_COLOR = {"blue", "green", "orange"};
    static final String[] SHIP_LASER = {"laserBlue01", "laserGreen01", "laserBlue02"};
    static final float[] SHIP_HP = {100f, 150f, 70f};
    static final float[] SHIP_FIRE = {0.17f, 0.25f, 0.11f};
    static final float[] SHIP_DMG = {1f, 1.6f, 0.75f};
    static final float[] SHIP_SPD = {1f, 0.8f, 1.25f};
    static final int[] SHIP_UNLOCK = {0, 3000, 8000};

    static final float W = 720f;
    static final float PR = 26f; // raio de colisao do jogador
    static final int DARK = 0xFF22304A;

    // ---------- entidades ----------
    static class Bullet {
        float x, y, vx, vy, dmg, r, rot;
        Bitmap bmp;
        boolean mine;
    }

    static class Enemy {
        int kind, score, phase;
        float x, y, vx, vy, hp, maxHp, r, t, shoot, flash, sc, baseX, hoverY, spin, rot;
        Bitmap bmp;
        boolean boss, dead;
    }

    static class Rock {
        float x, y, vx, vy, rot, vr, hp, r, flash;
        int size;
        Bitmap bmp;
        boolean dead;
    }

    static class Pickup {
        int kind;
        float x, y, t;
        boolean dead;
    }

    static class Fx {
        Bitmap[] frames;
        Bitmap bmp;
        float x, y, vx, vy, t, life, s0, s1, rot, vr, a0;
        boolean add;
    }

    static class Pop {
        String s;
        float x, y, t;
        int color;
    }

    static class Btn {
        int id;
        String label, color;
        float x, y, w, h, textSize;
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    static class Touch {
        int type, id;
        float x, y;
        Touch(int t, int i, float x, float y) {
            type = t; id = i; this.x = x; this.y = y;
        }
    }

    // ---------- sistema ----------
    final Assets A;
    final Sound snd;
    final SharedPreferences prefs;
    final Random rnd = new Random();
    Thread thread;
    volatile boolean running;
    volatile float sc = 1f;
    volatile int state = MENU;
    volatile boolean pauseRequest, backRequest;
    float H = 1280f, ti = 20f;
    int sw = 1, sh = 1, insetTick;
    float tick;
    final ConcurrentLinkedQueue<Touch> touches = new ConcurrentLinkedQueue<>();
    final ArrayList<Btn> btns = new ArrayList<>();
    Btn pressed;
    int pressedId = -1, moveId = -1;
    float offX, offY;

    // ---------- tintas ----------
    final Paint pSpr = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    final Paint pAdd = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Rect s9 = new Rect();
    final RectF d9 = new RectF();

    // ---------- sprites mais usados ----------
    Bitmap bPanelRect, bPanelGlass, bBtnSq, bLight, bMuzzle, bSpark, bStar4, bSmoke, bBlack, bEnemyLaser,
            bNumX, bBg;
    Bitmap[] numerals = new Bitmap[10];
    Bitmap[] expReg, expSonic, fireFrames;
    final HashMap<String, Bitmap[]> pills = new HashMap<>();
    final String[] pickupPath = {"powerups/powerupBlue_shield.png", "powerups/powerupGreen_bolt.png",
            "powerups/pill_green.png", "powerups/powerupRed_star.png", "powerups/star_gold.png"};
    final float[] pickupScale = {1.5f, 1.5f, 2f, 1.5f, 1.4f};

    // ---------- fundo ----------
    float bgY;
    final int NSTARS = 55;
    float[] stX = new float[NSTARS], stY = new float[NSTARS], stV = new float[NSTARS], stS = new float[NSTARS];
    int[] stI = new int[NSTARS];

    // ---------- listas de jogo ----------
    final ArrayList<Bullet> bullets = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Rock> rocks = new ArrayList<>();
    final ArrayList<Pickup> pickups = new ArrayList<>();
    final ArrayList<Fx> fxs = new ArrayList<>();
    final ArrayList<Pop> pops = new ArrayList<>();

    // ---------- progresso ----------
    int hi, shipIdx;
    boolean soundOn;
    int score, combo, wave, lives, weapon, bombs;
    float comboT, hp, maxHp, shieldT, invulT, hitFlash, fireT, bombCD, respT;
    float px, py, tx, ty, roll;
    boolean alive, newRecord, bossAlive;
    String unlockMsg = "";
    float shakeT, shakeAmp, flashT, bannerT, bannerMax, hintT;
    String bannerText = "";
    boolean bannerBoss;
    float startDelay, spawnTimer, rockT, clearT;
    int toSpawn;
    boolean bossPending, waveActive;
    Bitmap bPlayer, bPlayerLaser;
    Enemy boss;

    public GameView(Context ctx) {
        super(ctx);
        A = new Assets(ctx);
        prefs = ctx.getSharedPreferences("joguinho", Context.MODE_PRIVATE);
        hi = prefs.getInt("hi", 0);
        shipIdx = prefs.getInt("ship", 0);
        if (shipIdx < 0 || shipIdx > 2 || hi < SHIP_UNLOCK[shipIdx]) shipIdx = 0;
        soundOn = prefs.getBoolean("sound", true);
        snd = new Sound(ctx, soundOn);
        pAdd.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        pText.setTypeface(A.font);
        pText.setStrokeJoin(Paint.Join.ROUND);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    // ================= ciclo de vida =================
    @Override
    public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread = new Thread(this, "game-loop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        sw = w;
        sh = hh;
        sc = w / W;
        H = hh / sc;
        for (int i = 0; i < NSTARS; i++) resetStar(i, true);
        layout();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try {
            if (thread != null) thread.join(1500);
        } catch (InterruptedException ignored) {
        }
        thread = null;
    }

    void onHostPause() {
        pauseRequest = true;
        snd.setHostPaused(true);
    }

    void onHostResume() {
        snd.setHostPaused(false);
    }

    void onHostDestroy() {
        snd.release();
    }

    boolean onBack() {
        if (state == MENU) return false;
        backRequest = true;
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int i = e.getActionIndex();
        float s = sc;
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                touches.add(new Touch(0, e.getPointerId(i), e.getX(i) / s, e.getY(i) / s));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < e.getPointerCount(); k++)
                    touches.add(new Touch(1, e.getPointerId(k), e.getX(k) / s, e.getY(k) / s));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                touches.add(new Touch(2, e.getPointerId(i), e.getX(i) / s, e.getY(i) / s));
                break;
            case MotionEvent.ACTION_CANCEL:
                touches.add(new Touch(3, -1, 0, 0));
                break;
            default:
                break;
        }
        return true;
    }

    // ================= loop principal =================
    @Override
    public void run() {
        loadSprites();
        if (state == MENU) snd.music("menu.ogg", true, 0.45f);
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1e9f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;
            readInsets();
            update(dt);
            Canvas c = null;
            try {
                SurfaceHolder h = getHolder();
                c = Build.VERSION.SDK_INT >= 26 ? h.lockHardwareCanvas() : h.lockCanvas();
                if (c != null) render(c);
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try {
                        getHolder().unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
            long used = (System.nanoTime() - now) / 1000000L;
            if (used < 16) {
                try {
                    Thread.sleep(16 - used);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    void loadSprites() {
        A.preload();
        bPanelRect = A.get("ui/panel_rectangle.png");
        bPanelGlass = A.get("ui/panel_glass.png");
        bBtnSq = A.get("ui/button_square.png");
        bLight = A.get("fx/light_01.png");
        bMuzzle = A.get("fx/muzzle_01.png");
        bSpark = A.get("fx/spark_03.png");
        bStar4 = A.get("fx/star_04.png");
        bSmoke = A.get("fx/smoke_05.png");
        bBlack = A.get("bg/black.png");
        bEnemyLaser = A.get("lasers/laserRed07.png");
        bNumX = A.get("ui/numeralX.png");
        for (int i = 0; i < 10; i++) numerals[i] = A.get("ui/numeral" + i + ".png");
        expReg = A.seq("explosion/regularExplosion%02d.png", 0, 8);
        expSonic = A.seq("explosion/sonicExplosion%02d.png", 0, 8);
        fireFrames = A.seq("fx/fire%02d.png", 2, 4);
        bBg = A.get("bg/darkPurple.png");
        pickShip();
    }

    @SuppressWarnings("deprecation")
    void readInsets() {
        if (++insetTick % 30 != 1) return;
        float t = 20f * sc;
        try {
            WindowInsets wi = getRootWindowInsets();
            if (wi != null) {
                t = Math.max(t, wi.getSystemWindowInsetTop());
                if (Build.VERSION.SDK_INT >= 28 && wi.getDisplayCutout() != null) {
                    t = Math.max(t, wi.getDisplayCutout().getSafeInsetTop());
                }
            }
        } catch (Exception ignored) {
        }
        ti = t / sc;
    }

    void pickShip() {
        bPlayer = A.get("ships/playerShip" + (shipIdx + 1) + "_" + SHIP_COLOR[shipIdx] + ".png");
        bPlayerLaser = A.get("lasers/" + SHIP_LASER[shipIdx] + ".png");
    }

    void resetStar(int i, boolean anywhere) {
        stX[i] = rnd.nextFloat() * W;
        stY[i] = anywhere ? rnd.nextFloat() * H : -20f;
        float depth = rnd.nextFloat();
        stV[i] = 40f + depth * 220f;
        stS[i] = 0.35f + depth * 0.9f;
        stI[i] = rnd.nextInt(3);
    }

    // ================= layout de botoes =================
    Btn addBtn(int id, String label, String color, float x, float y, float w, float h, float ts) {
        Btn b = new Btn();
        b.id = id; b.label = label; b.color = color;
        b.x = x; b.y = y; b.w = w; b.h = h; b.textSize = ts;
        btns.add(b);
        return b;
    }

    void layout() {
        btns.clear();
        pressed = null;
        pressedId = -1;
        float cx = W / 2f;
        switch (state) {
            case MENU: {
                float y = H * 0.52f;
                addBtn(B_PLAY, "JOGAR", "Green", cx - 250, y, 500, 120, 56);
                addBtn(B_SHIPS, "NAVES", "Blue", cx - 250, y + 146, 500, 100, 44);
                addBtn(B_SOUND, soundOn ? "SOM LIGADO" : "SOM MUDO", soundOn ? "Yellow" : "Red",
                        cx - 250, y + 266, 500, 100, 40);
                break;
            }
            case SELECT: {
                for (int i = 0; i < 3; i++) {
                    Btn b = addBtn(B_CARD + i, "", "Grey", 60, 190 + i * 294, 600, 270, 0);
                }
                addBtn(B_BACK, "VOLTAR", "Red", cx - 200, 190 + 3 * 294 + 12, 400, 96, 40);
                break;
            }
            case PLAY: {
                addBtn(B_PAUSE, "", "Grey", cx - 40, ti + 16, 80, 80, 0);
                addBtn(B_BOMB, "", "Grey", W - 156, H - 178, 132, 132, 0);
                break;
            }
            case PAUSE: {
                float py0 = H / 2f - 360f;
                addBtn(B_RESUME, "CONTINUAR", "Green", cx - 220, py0 + 150, 440, 96, 38);
                addBtn(B_RESTART, "REINICIAR", "Blue", cx - 220, py0 + 270, 440, 96, 38);
                addBtn(B_SOUND, soundOn ? "SOM LIGADO" : "SOM MUDO", soundOn ? "Yellow" : "Red",
                        cx - 220, py0 + 390, 440, 96, 36);
                addBtn(B_MENU, "MENU", "Red", cx - 220, py0 + 510, 440, 96, 38);
                break;
            }
            case OVER: {
                float py0 = H / 2f - 430f;
                addBtn(B_AGAIN, "JOGAR DE NOVO", "Green", cx - 220, py0 + 600, 440, 96, 36);
                addBtn(B_MENU, "MENU", "Blue", cx - 220, py0 + 716, 440, 96, 38);
                break;
            }
            default:
                break;
        }
    }

    void setState(int s) {
        state = s;
        moveId = -1;
        layout();
    }

    // ================= acoes =================
    void action(int id) {
        if (id >= B_CARD && id < B_CARD + 3) {
            int i = id - B_CARD;
            if (hi >= SHIP_UNLOCK[i]) {
                shipIdx = i;
                prefs.edit().putInt("ship", i).apply();
                pickShip();
                snd.play("start");
            } else {
                snd.play("back");
            }
            return;
        }
        switch (id) {
            case B_PLAY:
            case B_AGAIN:
            case B_RESTART:
                snd.play("start");
                startGame();
                break;
            case B_SHIPS:
                snd.play("click");
                setState(SELECT);
                break;
            case B_SOUND:
                soundOn = !soundOn;
                prefs.edit().putBoolean("sound", soundOn).apply();
                snd.setEnabled(soundOn);
                snd.play("click");
                layout();
                break;
            case B_BACK:
                snd.play("back");
                setState(MENU);
                break;
            case B_PAUSE:
                snd.play("click");
                setState(PAUSE);
                break;
            case B_RESUME:
                snd.play("click");
                setState(PLAY);
                break;
            case B_MENU:
                snd.play("back");
                toMenu();
                break;
            default:
                break;
        }
    }

    void toMenu() {
        clearWorld();
        setState(MENU);
        snd.music("menu.ogg", true, 0.45f);
    }

    void clearWorld() {
        bullets.clear(); enemies.clear(); rocks.clear(); pickups.clear(); fxs.clear(); pops.clear();
        boss = null; bossAlive = false;
    }

    void startGame() {
        clearWorld();
        pickShip();
        score = 0; combo = 0; comboT = 0; wave = 0; lives = 3; weapon = 1; bombs = 1;
        maxHp = SHIP_HP[shipIdx]; hp = maxHp;
        shieldT = 2.5f; invulT = 2.5f; fireT = 0.3f; bombCD = 0; hitFlash = 0;
        px = W / 2f; py = H - 220f; tx = px; ty = py; roll = 0;
        alive = true; newRecord = false; unlockMsg = "";
        startDelay = 1.2f; waveActive = false; toSpawn = 0; bossPending = false;
        rockT = 4f; clearT = 0; shakeT = 0; flashT = 0; bannerT = 0; hintT = 5f;
        bgBitmapForWave();
        setState(PLAY);
        snd.music("game.ogg", true, 0.4f);
    }

    void bgBitmapForWave() {
        String[] bg = {"darkPurple", "blue", "purple", "black"};
        bBg = A.get("bg/" + bg[Math.max(0, (wave - 1) / 3) % 4] + ".png");
    }

    void gameOver() {
        int oldHi = hi;
        newRecord = score > hi;
        if (newRecord) {
            hi = score;
            prefs.edit().putInt("hi", hi).apply();
        }
        unlockMsg = "";
        for (int i = 1; i < 3; i++) {
            if (oldHi < SHIP_UNLOCK[i] && hi >= SHIP_UNLOCK[i]) unlockMsg = "NAVE LIBERADA: " + SHIP_NAME[i];
        }
        setState(OVER);
        snd.music("gameover.ogg", false, 0.5f);
    }

    // ================= toque =================
    void processTouch(Touch t) {
        switch (t.type) {
            case 0: { // down
                Btn b = null;
                for (int i = btns.size() - 1; i >= 0; i--) {
                    if (btns.get(i).contains(t.x, t.y)) { b = btns.get(i); break; }
                }
                if (b != null) {
                    if (b.id == B_BOMB) {
                        useBomb();
                    } else if (pressed == null) {
                        pressed = b;
                        pressedId = t.id;
                    }
                } else if (state == PLAY && moveId < 0) {
                    moveId = t.id;
                    offX = px - t.x;
                    offY = Math.max(-300f, Math.min(-120f, py - t.y));
                    tx = t.x + offX;
                    ty = t.y + offY;
                }
                break;
            }
            case 1: // move
                if (t.id == moveId && state == PLAY) {
                    tx = t.x + offX;
                    ty = t.y + offY;
                }
                break;
            case 2: { // up
                if (t.id == moveId) moveId = -1;
                if (pressed != null && t.id == pressedId) {
                    Btn b = pressed;
                    pressed = null;
                    pressedId = -1;
                    if (b.contains(t.x, t.y)) action(b.id);
                }
                break;
            }
            default:
                moveId = -1;
                pressed = null;
                pressedId = -1;
                break;
        }
    }

    // ================= utilitarios =================
    static float sin(float a) { return (float) Math.sin(a); }
    static float cos(float a) { return (float) Math.cos(a); }
    static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
    static boolean hit(float ax, float ay, float ar, float bx, float by, float br) {
        float dx = ax - bx, dy = ay - by, r = ar + br;
        return dx * dx + dy * dy < r * r;
    }
    float aimAngle(float x, float y) {
        return alive ? (float) Math.atan2(py - y, px - x) : (float) (Math.PI / 2);
    }
    Bitmap enemyBmp(String color, int n) { return A.get("enemies/enemy" + color + n + ".png"); }

    // ================= update =================
    void update(float dt) {
        tick += dt;
        Touch t;
        while ((t = touches.poll()) != null) processTouch(t);
        if (pauseRequest) {
            pauseRequest = false;
            if (state == PLAY) setState(PAUSE);
        }
        if (backRequest) {
            backRequest = false;
            switch (state) {
                case PLAY: setState(PAUSE); break;
                case PAUSE: setState(PLAY); break;
                case SELECT: setState(MENU); break;
                case OVER: toMenu(); break;
                default: break;
            }
        }
        if (state != PAUSE) {
            bgY = (bgY + dt * 60f) % 256f;
            for (int i = 0; i < NSTARS; i++) {
                stY[i] += stV[i] * dt;
                if (stY[i] > H + 20f) resetStar(i, false);
            }
        }
        if (state == PLAY) {
            updatePlay(dt);
        } else if (state == OVER) {
            updateFx(dt);
        }
    }

    void updateFx(float dt) {
        if (shakeT > 0) shakeT -= dt;
        if (flashT > 0) flashT -= dt;
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            f.t += dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.rot += f.vr * dt;
            if (f.t >= f.life) fxs.remove(i);
        }
        for (int i = pops.size() - 1; i >= 0; i--) {
            Pop p = pops.get(i);
            p.t += dt;
            p.y -= 55f * dt;
            if (p.t > 1.0f) pops.remove(i);
        }
    }

    void updatePlay(float dt) {
        updateFx(dt);
        if (bannerT > 0) bannerT -= dt;
        if (hintT > 0) hintT -= dt;
        if (bombCD > 0) bombCD -= dt;
        if (comboT > 0) {
            comboT -= dt;
            if (comboT <= 0) combo = 0;
        }

        // ---- jogador ----
        if (alive) {
            float k = Math.min(1f, dt * 16f * SHIP_SPD[shipIdx]);
            px += (tx - px) * k;
            py += (ty - py) * k;
            px = clamp(px, 45f, W - 45f);
            py = clamp(py, ti + 200f, H - 70f);
            tx = clamp(tx, 45f, W - 45f);
            ty = clamp(ty, ti + 200f, H - 70f);
            roll += (clamp((tx - px) * 0.06f, -14f, 14f) - roll) * Math.min(1f, dt * 10f);
            fireT -= dt;
            if (fireT <= 0) {
                shootPlayer();
                fireT = SHIP_FIRE[shipIdx];
            }
            if (shieldT > 0) shieldT -= dt;
            if (invulT > 0) invulT -= dt;
            if (hitFlash > 0) hitFlash -= dt;
        } else {
            respT -= dt;
            if (respT <= 0) {
                if (lives > 0) respawn(); else gameOver();
            }
        }

        // ---- tiros ----
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.y < -90 || b.y > H + 90 || b.x < -90 || b.x > W + 90) {
                bullets.remove(i);
                continue;
            }
            if (b.mine) {
                boolean used = false;
                for (int j = 0; j < enemies.size() && !used; j++) {
                    Enemy e = enemies.get(j);
                    if (!e.dead && hit(b.x, b.y, b.r, e.x, e.y, e.r)) {
                        damageEnemy(e, b.dmg, b.x, b.y);
                        used = true;
                    }
                }
                for (int j = 0; j < rocks.size() && !used; j++) {
                    Rock r = rocks.get(j);
                    if (!r.dead && hit(b.x, b.y, b.r, r.x, r.y, r.r)) {
                        damageRock(r, b.dmg, b.x, b.y);
                        used = true;
                    }
                }
                if (used) bullets.remove(i);
            } else if (alive && hit(b.x, b.y, b.r, px, py, PR - 6f)) {
                hurtPlayer(b.dmg);
                bullets.remove(i);
            }
        }

        // ---- inimigos ----
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.dead) { enemies.remove(i); continue; }
            updateEnemy(e, dt);
            if (e.dead) { enemies.remove(i); continue; }
            if (e.y > H + 140 || e.x < -200 || e.x > W + 200) { enemies.remove(i); continue; }
            if (alive && hit(e.x, e.y, e.r * 0.85f, px, py, PR)) {
                hurtPlayer(e.boss ? 35f : 25f);
                if (!e.boss) killEnemy(e);
            }
        }

        // ---- meteoros ----
        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock r = rocks.get(i);
            if (r.dead) { rocks.remove(i); continue; }
            r.x += r.vx * dt;
            r.y += r.vy * dt;
            r.rot += r.vr * dt;
            if (r.flash > 0) r.flash -= dt;
            if (r.y > H + 120 || r.x < -150 || r.x > W + 150) { rocks.remove(i); continue; }
            if (alive && hit(r.x, r.y, r.r * 0.9f, px, py, PR)) {
                hurtPlayer(r.size == 2 ? 24f : (r.size == 1 ? 12f : 6f));
                breakRock(r, false);
            }
        }

        // ---- itens ----
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            p.t += dt;
            p.y += 110f * dt;
            p.x += sin(p.t * 3f) * 30f * dt;
            if (alive) {
                float dx = px - p.x, dy = py - p.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 190f && d > 1f) {
                    p.x += dx / d * 430f * dt;
                    p.y += dy / d * 430f * dt;
                }
                if (hit(p.x, p.y, 26f, px, py, PR + 6f)) {
                    collect(p);
                    pickups.remove(i);
                    continue;
                }
            }
            if (p.y > H + 60) pickups.remove(i);
        }

        // ---- ondas ----
        if (startDelay > 0) {
            startDelay -= dt;
            if (startDelay <= 0) startWave();
        } else {
            rockT -= dt;
            if (rockT <= 0) {
                spawnRock(2 + rnd.nextInt(2) > 2 ? 2 : 1, -1f, -1f);
                rockT = Math.max(1.3f, 3.6f - wave * 0.1f) + rnd.nextFloat() * 1.6f;
                if (bossAlive) rockT += 3f;
            }
            if (waveActive) {
                spawnTimer -= dt;
                if (spawnTimer <= 0 && (toSpawn > 0 || bossPending)) {
                    if (bossPending) {
                        spawnBoss();
                        bossPending = false;
                        spawnTimer = 3f;
                    } else {
                        spawnEnemy();
                        toSpawn--;
                        spawnTimer = Math.max(0.35f, 1.15f - wave * 0.04f) + rnd.nextFloat() * 0.35f;
                    }
                }
                if (toSpawn <= 0 && !bossPending && enemies.isEmpty()) {
                    waveActive = false;
                    clearT = 2.4f;
                    snd.play("levelup", 0.7f, 1.1f);
                    score += 100 * wave;
                    hp = Math.min(maxHp, hp + 12f);
                    popText("BONUS +" + (100 * wave), W / 2f, H * 0.42f, 0xFF8CFF9E);
                    if (bossAlive) { bossAlive = false; boss = null; }
                }
            } else {
                clearT -= dt;
                if (clearT <= 0) startWave();
            }
        }
    }

    void respawn() {
        alive = true;
        hp = maxHp;
        px = W / 2f; py = H - 200f; tx = px; ty = py;
        invulT = 3f;
        shieldT = 3f;
        snd.play("shield", 0.8f, 0.8f);
    }

    void startWave() {
        wave++;
        bgBitmapForWave();
        waveActive = true;
        boolean bw = wave % 5 == 0;
        bossPending = bw;
        toSpawn = bw ? 3 + (wave / 5) * 2 : Math.min(40, 5 + wave * 2);
        spawnTimer = 1.2f;
        bannerBoss = bw;
        bannerText = bw ? "ALERTA CHEFE" : "ONDA " + wave;
        bannerT = bannerMax = 2.4f;
        if (bw) {
            snd.play("warning", 1f, 1f);
            snd.music("boss.ogg", true, 0.45f);
        } else {
            snd.play("levelup", 0.6f, 1f);
            snd.music("game.ogg", true, 0.4f);
        }
    }

    // ================= jogador =================
    void shootPlayer() {
        float d = SHIP_DMG[shipIdx];
        float y = py - 30f;
        switch (weapon) {
            case 1:
                pb(px, y, 0f, d);
                break;
            case 2:
                pb(px - 14, y, 0f, d * .8f); pb(px + 14, y, 0f, d * .8f);
                break;
            case 3:
                pb(px, y - 8, 0f, d * .8f); pb(px - 20, y, -9f, d * .7f); pb(px + 20, y, 9f, d * .7f);
                break;
            case 4:
                pb(px - 12, y - 6, 0f, d * .75f); pb(px + 12, y - 6, 0f, d * .75f);
                pb(px - 28, y, -12f, d * .65f); pb(px + 28, y, 12f, d * .65f);
                break;
            default:
                pb(px, y - 10, 0f, d * .8f);
                pb(px - 18, y - 4, -7f, d * .7f); pb(px + 18, y - 4, 7f, d * .7f);
                pb(px - 34, y + 2, -16f, d * .6f); pb(px + 34, y + 2, 16f, d * .6f);
                break;
        }
        snd.play("shoot" + (shipIdx + 1), 0.3f, 1f);
        Fx f = fx(bMuzzle, null, px, y - 6f, 0.09f, 0.10f, 0.16f, 0.9f, true);
        f.vy = -40f;
    }

    void pb(float x, float y, float angDeg, float dmg) {
        Bullet b = new Bullet();
        float a = (float) Math.toRadians(angDeg);
        b.x = x; b.y = y;
        b.vx = sin(a) * 950f;
        b.vy = -cos(a) * 950f;
        b.mine = true; b.dmg = dmg; b.r = 12f; b.rot = angDeg;
        b.bmp = bPlayerLaser;
        bullets.add(b);
    }

    void eb(float x, float y, float ang, float spd) {
        Bullet b = new Bullet();
        b.x = x; b.y = y;
        b.vx = cos(ang) * spd;
        b.vy = sin(ang) * spd;
        b.mine = false;
        b.dmg = 10f + Math.min(10f, wave / 2f);
        b.r = 11f;
        b.rot = (float) Math.toDegrees(ang) - 90f;
        b.bmp = bEnemyLaser;
        bullets.add(b);
    }

    void hurtPlayer(float d) {
        if (!alive || invulT > 0) return;
        if (shieldT > 0) {
            snd.play("shield", 0.7f, 1.2f);
            fx(bLight, null, px, py, 0.25f, 0.5f, 0.9f, 0.9f, true);
            invulT = 0.15f;
            return;
        }
        hp -= d;
        hitFlash = 0.3f;
        invulT = 0.5f;
        shakeT = 0.3f; shakeAmp = 10f;
        combo = 0;
        snd.play("hit", 0.9f, 1f);
        sparks(px, py - 10f, 6);
        if (hp <= 0) killPlayer();
    }

    void killPlayer() {
        alive = false;
        lives--;
        weapon = Math.max(1, weapon - 1);
        combo = 0;
        respT = 1.7f;
        explode(px, py, 1.9f);
        fx(null, expSonic, px, py, 0.6f, 1.6f, 3.4f, 1f, false);
        snd.play("explode_big", 1f, 0.9f);
        shakeT = 0.6f; shakeAmp = 16f;
    }

    void useBomb() {
        if (state != PLAY || !alive || bombs <= 0 || bombCD > 0) return;
        bombs--;
        bombCD = 1f;
        snd.play("bomb", 1f, 1f);
        fx(null, expSonic, px, py, 0.75f, 3.5f, 9f, 1f, false);
        flashT = 0.5f;
        shakeT = 0.6f; shakeAmp = 14f;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.dead) damageEnemy(e, e.boss ? 20f : 14f, e.x, e.y);
        }
        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock r = rocks.get(i);
            if (!r.dead && r.y > -40) breakRock(r, true);
        }
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            if (!b.mine) {
                if (rnd.nextInt(3) == 0) fx(bSpark, null, b.x, b.y, 0.3f, 0.12f, 0.25f, 1f, true);
                bullets.remove(i);
            }
        }
    }

    void collect(Pickup p) {
        fx(bStar4, null, p.x, p.y, 0.4f, 0.1f, 0.4f, 1f, true);
        switch (p.kind) {
            case P_SHIELD:
                shieldT = 8f;
                snd.play("shield", 0.9f, 1f);
                popText("ESCUDO", p.x, p.y, 0xFF6FD3FF);
                break;
            case P_WEAPON:
                if (weapon < 5) {
                    weapon++;
                    popText("ARMA NIVEL " + weapon, p.x, p.y, 0xFF9CFF6B);
                } else {
                    score += 500;
                    popText("+500", p.x, p.y, 0xFFFFD54F);
                }
                snd.play("powerup", 0.9f, 1f);
                break;
            case P_HEAL:
                hp = Math.min(maxHp, hp + 40f);
                snd.play("heal", 0.9f, 1f);
                popText("REPARO", p.x, p.y, 0xFF8CFF9E);
                break;
            case P_BOMB:
                bombs = Math.min(5, bombs + 1);
                snd.play("powerup", 0.9f, 1.3f);
                popText("BOMBA", p.x, p.y, 0xFFFF7A7A);
                break;
            default:
                int pts = 100 * (1 + Math.min(4, combo / 5));
                score += pts;
                snd.play("coin", 0.7f, 1f);
                popText("+" + pts, p.x, p.y, 0xFFFFD54F);
                break;
        }
    }

    void dropPickup(float x, float y, int kind) {
        Pickup p = new Pickup();
        p.kind = kind; p.x = x; p.y = y;
        pickups.add(p);
    }

    // ================= inimigos =================
    int pickKind() {
        float[] w = {4f, wave >= 2 ? 3f : 0f, wave >= 3 ? 3f : 0f, wave >= 4 ? 2f : 0f,
                wave >= 6 ? 1.5f : 0f, wave >= 3 ? 1f : 0f};
        float sum = 0;
        for (float v : w) sum += v;
        float r = rnd.nextFloat() * sum;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return i;
        }
        return 0;
    }

    void spawnEnemy() {
        Enemy e = new Enemy();
        e.kind = pickKind();
        e.sc = 0.95f;
        e.y = -60f;
        e.x = 60f + rnd.nextFloat() * (W - 120f);
        e.baseX = clamp(e.x, 180f, W - 180f);
        e.shoot = 0.8f + rnd.nextFloat();
        int n = 1 + rnd.nextInt(3);
        switch (e.kind) {
            case K_DRONE:
                e.bmp = enemyBmp("Black", n);
                e.hp = 1f + wave / 5; e.vy = Math.min(320f, 150f + wave * 5f); e.score = 50;
                break;
            case K_ZIG:
                e.bmp = enemyBmp("Blue", n);
                e.hp = 2f + wave / 6; e.vy = Math.min(240f, 120f + wave * 3f); e.score = 90;
                break;
            case K_SHOOT:
                e.bmp = enemyBmp("Green", n);
                e.hp = 3f + wave / 4; e.hoverY = 140f + rnd.nextFloat() * 170f; e.score = 140;
                break;
            case K_KAMI:
                e.bmp = enemyBmp("Red", n);
                e.hp = 2f + wave / 8; e.score = 110;
                break;
            case K_TANK: {
                String[] c = {"Black", "Blue", "Green", "Red"};
                e.bmp = enemyBmp(c[rnd.nextInt(4)], 4);
                e.hp = 9f + wave / 2; e.vy = 70f; e.sc = 1.2f; e.hoverY = 130f + rnd.nextFloat() * 100f;
                e.score = 300; e.shoot = 1.5f;
                break;
            }
            default: {
                String[] u = {"Blue", "Green", "Red", "Yellow"};
                e.kind = K_UFO;
                e.bmp = A.get("enemies/ufo" + u[rnd.nextInt(4)] + ".png");
                e.hp = 4f + wave / 5; e.vy = 110f; e.score = 200; e.sc = 0.95f;
                break;
            }
        }
        e.maxHp = e.hp;
        e.r = e.bmp.getWidth() * 0.4f * e.sc;
        enemies.add(e);
    }

    void spawnBoss() {
        Enemy e = new Enemy();
        int lvl = Math.max(1, wave / 5);
        String[] n = {"Red", "Black", "Blue", "Green"};
        e.kind = K_BOSS;
        e.boss = true;
        e.bmp = enemyBmp(n[(lvl - 1) % 4], 5);
        e.sc = 2.6f;
        e.maxHp = e.hp = 55f + lvl * 30f;
        e.x = W / 2f; e.y = -220f;
        e.r = e.bmp.getWidth() * 0.38f * e.sc;
        e.score = 2500 * lvl;
        e.shoot = 2f;
        e.hoverY = 2f;
        enemies.add(e);
        boss = e;
        bossAlive = true;
    }

    void updateEnemy(Enemy e, float dt) {
        e.t += dt;
        if (e.flash > 0) e.flash -= dt;
        float spd = 1f + Math.min(0.8f, wave * 0.03f);
        switch (e.kind) {
            case K_DRONE:
                e.y += e.vy * dt;
                break;
            case K_ZIG:
                e.y += e.vy * dt;
                e.x = e.baseX + sin(e.t * 2.4f) * 130f;
                break;
            case K_SHOOT:
                if (e.y < e.hoverY) {
                    e.y += 150f * dt;
                } else {
                    e.x = e.baseX + sin(e.t * 1.1f) * (W * 0.28f);
                    if (e.t > 12f) e.y += 220f * dt;
                }
                e.shoot -= dt;
                if (e.shoot <= 0 && e.y > 40f) {
                    eb(e.x, e.y + e.r * 0.7f, aimAngle(e.x, e.y), 330f * spd);
                    e.shoot = 2.0f / spd + rnd.nextFloat() * 0.6f;
                }
                break;
            case K_KAMI:
                if (e.t < 0.8f || !alive) {
                    e.y += (alive ? 120f : 320f) * dt;
                } else {
                    float dx = px - e.x, dy = py - e.y;
                    float d = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    e.vx += (dx / d * 420f - e.vx) * dt * 2.2f;
                    e.vy += (dy / d * 420f - e.vy) * dt * 2.2f;
                    e.x += e.vx * dt;
                    e.y += e.vy * dt;
                }
                e.rot = clamp(-e.vx * 0.08f, -35f, 35f);
                break;
            case K_TANK:
                if (e.y < e.hoverY) {
                    e.y += e.vy * dt;
                } else {
                    e.x += sin(e.t * 0.7f) * 70f * dt;
                    if (e.t > 14f) e.y += 120f * dt;
                }
                e.shoot -= dt;
                if (e.shoot <= 0 && e.y > 40f) {
                    float a = aimAngle(e.x, e.y);
                    for (int k = -1; k <= 1; k++) eb(e.x, e.y + e.r * 0.6f, a + k * 0.3f, 300f * spd);
                    e.shoot = 2.3f / spd;
                }
                break;
            case K_UFO:
                e.y += e.vy * dt;
                e.x = e.baseX + sin(e.t * 3.2f) * 180f;
                e.rot = sin(e.t * 3.2f) * 12f;
                e.shoot -= dt;
                if (e.shoot <= 0 && e.y > 30f) {
                    eb(e.x, e.y + e.r * 0.5f, aimAngle(e.x, e.y), 360f * spd);
                    e.shoot = 1.8f / spd;
                }
                break;
            default:
                updateBoss(e, dt);
                break;
        }
    }

    void updateBoss(Enemy e, float dt) {
        if (e.y < 190f) {
            e.y += 100f * dt;
            return;
        }
        e.x = W / 2f + sin(e.t * 0.8f) * (W / 2f - 190f);
        e.y = 190f + sin(e.t * 1.3f) * 30f;
        float ratio = e.hp / e.maxHp;
        int ph = ratio > 0.66f ? 0 : (ratio > 0.33f ? 1 : 2);
        if (ph != e.phase) {
            e.phase = ph;
            shakeT = 0.5f; shakeAmp = 12f;
            snd.play("explode_big", 1f, 0.8f);
            explode(e.x, e.y, 1.6f);
        }
        e.shoot -= dt;
        if (e.shoot <= 0) {
            float a = aimAngle(e.x, e.y);
            if (ph == 0) {
                for (int k = -1; k <= 1; k++) eb(e.x, e.y + 60f, a + k * 0.28f, 300f);
                e.shoot = 1.0f;
            } else if (ph == 1) {
                int n = 14;
                for (int i = 0; i < n; i++) eb(e.x, e.y + 40f, e.spin + i * 6.2831853f / n, 230f);
                e.spin += 0.2f;
                eb(e.x, e.y + 60f, a, 340f);
                e.shoot = 1.4f;
            } else {
                e.spin += 0.55f;
                eb(e.x, e.y + 40f, e.spin, 260f);
                eb(e.x, e.y + 40f, e.spin + 3.1415927f, 260f);
                e.shoot = 0.16f;
            }
            snd.play("shoot3", 0.25f, 0.8f);
        }
        if (ph == 2) {
            e.hoverY -= dt;
            if (e.hoverY <= 0) {
                float a = aimAngle(e.x, e.y);
                for (int k = -2; k <= 2; k++) eb(e.x, e.y + 60f, a + k * 0.2f, 330f);
                e.hoverY = 1.8f;
            }
        }
    }

    void damageEnemy(Enemy e, float dmg, float x, float y) {
        if (e.dead) return;
        e.hp -= dmg;
        e.flash = 0.07f;
        fx(bSpark, null, x, y, 0.2f, 0.07f, 0.16f, 1f, true).rot = rnd.nextFloat() * 360f;
        if (e.hp <= 0) killEnemy(e);
        else snd.play("enemy_hit", 0.35f, 0.9f + rnd.nextFloat() * 0.3f);
    }

    void killEnemy(Enemy e) {
        if (e.dead) return;
        e.dead = true;
        addScore(e.score, e.x, e.y);
        if (e.boss) {
            for (int i = 0; i < 9; i++) {
                final float ox = (rnd.nextFloat() - 0.5f) * e.r * 1.6f;
                final float oy = (rnd.nextFloat() - 0.5f) * e.r * 1.2f;
                Fx f = explode(e.x + ox, e.y + oy, 1.2f + rnd.nextFloat() * 0.8f);
                f.t = -i * 0.12f; // explosoes em cadeia
            }
            fx(null, expSonic, e.x, e.y, 0.8f, 2f, 6f, 1f, false).t = -0.5f;
            snd.play("explode_big", 1f, 0.7f);
            flashT = 0.6f;
            shakeT = 0.9f; shakeAmp = 16f;
            dropPickup(e.x - 50f, e.y, P_WEAPON);
            dropPickup(e.x, e.y, P_SHIELD);
            dropPickup(e.x + 50f, e.y, P_BOMB);
            for (int i = bullets.size() - 1; i >= 0; i--) if (!bullets.get(i).mine) bullets.remove(i);
            bossAlive = false;
            boss = null;
            snd.music("game.ogg", true, 0.4f);
            return;
        }
        float s = e.kind == K_TANK ? 1.5f : 1f;
        explode(e.x, e.y, s);
        snd.play(e.kind == K_TANK ? "explode_big" : "explode_small", 0.8f, 0.95f + rnd.nextFloat() * 0.2f);
        if (e.kind == K_UFO) {
            int[] k = {P_SHIELD, P_WEAPON, P_HEAL, P_BOMB};
            dropPickup(e.x, e.y, k[rnd.nextInt(4)]);
        } else {
            float r = rnd.nextFloat();
            if (r < 0.08f) dropPickup(e.x, e.y, rnd.nextInt(4));
            else if (r < 0.28f) dropPickup(e.x, e.y, P_COIN);
        }
    }

    void addScore(int base, float x, float y) {
        int mult = 1 + Math.min(4, combo / 5);
        int p = base * mult;
        score += p;
        combo++;
        comboT = 2.5f;
        popText("+" + p, x, y, mult > 1 ? 0xFFFFD54F : 0xFFFFFFFF);
    }

    void popText(String s, float x, float y, int color) {
        if (pops.size() > 14) pops.remove(0);
        Pop p = new Pop();
        p.s = s; p.x = x; p.y = y; p.color = color;
        pops.add(p);
    }

    // ================= meteoros =================
    final String[] ROCK_BIG = {"meteorBrown_big1", "meteorBrown_big2", "meteorBrown_big3", "meteorBrown_big4",
            "meteorGrey_big1", "meteorGrey_big2", "meteorGrey_big3", "meteorGrey_big4"};
    final String[] ROCK_SMALL = {"meteorBrown_med1", "meteorBrown_med3", "meteorGrey_med1", "meteorGrey_med2",
            "meteorBrown_small1", "meteorBrown_small2", "meteorGrey_small1", "meteorGrey_small2"};
    final String[] ROCK_TINY = {"meteorBrown_tiny1", "meteorBrown_tiny2", "meteorGrey_tiny1", "meteorGrey_tiny2"};

    void spawnRock(int size, float x, float y) {
        Rock r = new Rock();
        r.size = size;
        String[] src = size == 2 ? ROCK_BIG : (size == 1 ? ROCK_SMALL : ROCK_TINY);
        r.bmp = A.get("meteors/" + src[rnd.nextInt(src.length)] + ".png");
        r.hp = size == 2 ? 5f + wave / 10 : (size == 1 ? 2f : 1f);
        r.r = r.bmp.getWidth() * 0.42f;
        r.x = x < 0 ? 50f + rnd.nextFloat() * (W - 100f) : x;
        r.y = y < 0 ? -80f : y;
        r.vx = (rnd.nextFloat() - 0.5f) * 140f;
        r.vy = 120f + rnd.nextFloat() * 100f + wave * 4f;
        r.vr = (rnd.nextFloat() - 0.5f) * 220f;
        rocks.add(r);
    }

    void damageRock(Rock r, float dmg, float x, float y) {
        r.hp -= dmg;
        r.flash = 0.06f;
        fx(bSpark, null, x, y, 0.2f, 0.06f, 0.13f, 1f, true);
        if (r.hp <= 0) breakRock(r, true);
        else snd.play("enemy_hit", 0.25f, 0.7f);
    }

    void breakRock(Rock r, boolean byPlayer) {
        if (r.dead) return;
        r.dead = true;
        explode(r.x, r.y, r.size == 2 ? 0.8f : (r.size == 1 ? 0.5f : 0.3f));
        snd.play("explode_small", r.size == 2 ? 0.6f : 0.35f, r.size == 2 ? 0.8f : 1.1f);
        if (byPlayer) {
            score += r.size == 2 ? 20 : (r.size == 1 ? 10 : 5);
            if (r.size == 2 && rnd.nextFloat() < 0.12f) dropPickup(r.x, r.y, P_COIN);
        }
        if (r.size > 0) {
            for (int i = 0; i < 2; i++) {
                spawnRock(r.size - 1, r.x + (i == 0 ? -14f : 14f), r.y);
                Rock c = rocks.get(rocks.size() - 1);
                c.vx = (i == 0 ? -1 : 1) * (60f + rnd.nextFloat() * 80f);
            }
        }
    }

    // ================= efeitos visuais =================
    Fx fx(Bitmap bmp, Bitmap[] frames, float x, float y, float life, float s0, float s1, float a0, boolean add) {
        Fx f = new Fx();
        f.bmp = bmp; f.frames = frames; f.x = x; f.y = y; f.life = life;
        f.s0 = s0; f.s1 = s1; f.a0 = a0; f.add = add;
        if (fxs.size() > 160) fxs.remove(0);
        fxs.add(f);
        return f;
    }

    Fx explode(float x, float y, float scale) {
        Fx f = fx(null, expReg, x, y, 0.6f, scale, scale * 1.15f, 1f, false);
        f.rot = rnd.nextFloat() * 360f;
        sparks(x, y, 4 + (int) (scale * 3f));
        Fx s = fx(bSmoke, null, x, y, 0.9f, 0.08f * scale, 0.28f * scale, 0.5f, false);
        s.vy = 30f;
        s.vr = 40f;
        if (scale > 1.2f) {
            fx(bLight, null, x, y, 0.3f, 0.35f * scale, 0.7f * scale, 0.9f, true);
            if (shakeT < 0.25f) { shakeT = 0.25f; shakeAmp = 6f; }
        }
        return f;
    }

    void sparks(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 6.2831853f;
            float v = 90f + rnd.nextFloat() * 260f;
            Fx f = fx(bSpark, null, x, y, 0.35f + rnd.nextFloat() * 0.3f, 0.06f + rnd.nextFloat() * 0.06f, 0.02f, 1f, true);
            f.vx = cos(a) * v;
            f.vy = sin(a) * v;
            f.rot = rnd.nextFloat() * 360f;
        }
    }

    // ================= desenho: utilitarios =================
    Bitmap bStarOn, bStarOff, bLife, bShield;
    final Bitmap[] bDmg = new Bitmap[3];
    final Bitmap[] bStars = new Bitmap[3];
    int cachedShip = -1;

    void ensureShip() {
        if (cachedShip == shipIdx && bStarOn != null) return;
        cachedShip = shipIdx;
        bLife = A.get("ui/playerLife" + (shipIdx + 1) + "_" + SHIP_COLOR[shipIdx] + ".png");
        for (int d = 0; d < 3; d++) bDmg[d] = A.get("damage/playerShip" + (shipIdx + 1) + "_damage" + (d + 1) + ".png");
        bShield = A.get("fx/shield3.png");
        bStarOn = A.get("powerups/star_gold.png");
        bStarOff = A.get("powerups/star_silver.png");
        for (int i = 0; i < 3; i++) bStars[i] = A.get("fx/star" + (i + 1) + ".png");
    }

    void spr(Canvas c, Bitmap b, float x, float y, float s, float rot, Paint p) {
        c.save();
        c.translate(x, y);
        if (rot != 0f) c.rotate(rot);
        c.scale(s, s);
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, p);
        c.restore();
    }

    void sprA(Canvas c, Bitmap b, float x, float y, float s, float rot, float alpha, boolean add) {
        Paint p = add ? pAdd : pSpr;
        p.setAlpha((int) (clamp(alpha, 0f, 1f) * 255f));
        spr(c, b, x, y, s, rot, p);
        p.setAlpha(255);
    }

    void nine(Canvas c, Bitmap b, float x, float y, float w, float h, int cs, float cd, int alpha) {
        int bw = b.getWidth(), bh = b.getHeight();
        int[] sx = {0, cs, bw - cs, bw};
        int[] sy = {0, cs, bh - cs, bh};
        float[] dx = {x, x + cd, x + w - cd, x + w};
        float[] dy = {y, y + cd, y + h - cd, y + h};
        pSpr.setAlpha(alpha);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                s9.set(sx[i], sy[j], sx[i + 1], sy[j + 1]);
                d9.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
                c.drawBitmap(b, s9, d9, pSpr);
            }
        }
        pSpr.setAlpha(255);
    }

    void pill(Canvas c, String col, float x, float y, float w, float h, int alpha) {
        Bitmap[] pl = pills.get(col);
        if (pl == null) {
            pl = new Bitmap[]{A.get("ui/" + col + "_bar_round_large_l.png"),
                    A.get("ui/" + col + "_bar_round_large_m.png"),
                    A.get("ui/" + col + "_bar_round_large_r.png")};
            pills.put(col, pl);
        }
        float cap = h * 0.5f;
        pSpr.setAlpha(alpha);
        d9.set(x, y, x + cap, y + h);
        c.drawBitmap(pl[0], null, d9, pSpr);
        d9.set(x + cap - 0.6f, y, x + w - cap + 0.6f, y + h);
        c.drawBitmap(pl[1], null, d9, pSpr);
        d9.set(x + w - cap, y, x + w, y + h);
        c.drawBitmap(pl[2], null, d9, pSpr);
        pSpr.setAlpha(255);
    }

    void bar(Canvas c, String col, float x, float y, float w, float h, float frac) {
        pill(c, "Grey", x, y, w, h, 255);
        frac = clamp(frac, 0f, 1f);
        if (frac <= 0.01f) return;
        pill(c, col, x, y, Math.max(h, w * frac), h, 255);
    }

    void text(Canvas c, String s, float x, float y, float size, int fill, int align, boolean outline) {
        pText.setTextSize(size);
        pText.setTextAlign(align == 0 ? Paint.Align.LEFT : (align == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT));
        if (outline) {
            pText.setStyle(Paint.Style.STROKE);
            pText.setStrokeWidth(Math.max(2f, size * 0.16f));
            pText.setColor(((fill >>> 24) << 24) | 0x0A0F1E);
            c.drawText(s, x, y, pText);
        }
        pText.setStyle(Paint.Style.FILL);
        pText.setColor(fill);
        c.drawText(s, x, y, pText);
    }

    /** Numeros desenhados com os sprites numeral0..9 do pack. align: 0 esq, 1 centro, 2 dir. */
    void drawNum(Canvas c, long n, float x, float y, float s, int align) {
        String str = Long.toString(n);
        float gw = 20.5f * s;
        float total = str.length() * gw;
        float sx = align == 0 ? x : (align == 1 ? x - total / 2f : x - total);
        for (int i = 0; i < str.length(); i++) {
            Bitmap b = numerals[str.charAt(i) - '0'];
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            d9.set(sx + i * gw, y - bh / 2f, sx + i * gw + bw, y + bh / 2f);
            c.drawBitmap(b, null, d9, pSpr);
        }
    }

    void drawBox(Canvas c, Bitmap b, float x, float y, float w, float h) {
        d9.set(x, y, x + w, y + h);
        c.drawBitmap(b, null, d9, pSpr);
    }

    void dim(Canvas c, int alpha) {
        pSpr.setAlpha(alpha);
        d9.set(0, 0, W, H);
        c.drawBitmap(bBlack, null, d9, pSpr);
        pSpr.setAlpha(255);
    }

    void thruster(Canvas c, Bitmap ship, float x, float y, float s, float alpha) {
        Bitmap f = fireFrames[(int) (tick * 16f) % fireFrames.length];
        float sy = s * (1.1f + 0.3f * sin(tick * 45f));
        float off = ship.getWidth() * 0.2f * s;
        float ey = y + ship.getHeight() * 0.5f * s - 10f * s;
        pAdd.setAlpha((int) (clamp(alpha, 0f, 1f) * 255f));
        for (int k = -1; k <= 1; k += 2) {
            c.save();
            c.translate(x + k * off, ey);
            c.scale(s * 1.1f, -sy);
            c.drawBitmap(f, -f.getWidth() / 2f, -f.getHeight(), pAdd);
            c.restore();
        }
        pAdd.setAlpha(255);
    }

    void drawShipIdx(Canvas c, int idx, float x, float y, float s, float alpha) {
        Bitmap b = A.get("ships/playerShip" + (idx + 1) + "_" + SHIP_COLOR[idx] + ".png");
        thruster(c, b, x, y, s, alpha);
        sprA(c, b, x, y, s, 0f, alpha, false);
    }

    void drawBtnPill(Canvas c, Btn b) {
        boolean pr = b == pressed;
        float k = pr ? 0.95f : 1f;
        float w = b.w * k, h = b.h * k;
        float x = b.x + (b.w - w) / 2f;
        float y = b.y + (b.h - h) / 2f + (pr ? 3f : 0f);
        pill(c, "Grey", x, y + 8f, w, h, 120);
        pill(c, b.color, x, y, w, h, 255);
        text(c, b.label, x + w / 2f, y + h / 2f + b.textSize * 0.36f, b.textSize, 0xFFFFFFFF, 1, true);
    }

    void drawButtons(Canvas c) {
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            if (b.textSize > 0f) drawBtnPill(c, b);
        }
    }

    // ================= desenho: telas =================
    void render(Canvas c) {
        ensureShip();
        c.save();
        c.scale(sc, sc);
        drawBackground(c);
        switch (state) {
            case MENU:
                drawMenu(c);
                break;
            case SELECT:
                drawSelect(c);
                break;
            default:
                drawWorld(c);
                drawHud(c);
                if (state == PAUSE) drawPause(c);
                else if (state == OVER) drawOver(c);
                break;
        }
        c.restore();
    }

    void drawBackground(Canvas c) {
        for (float y = bgY - 256f; y < H; y += 256f) {
            for (float x = 0; x < W; x += 256f) {
                d9.set(x, y, x + 257f, y + 257f);
                c.drawBitmap(bBg, null, d9, pSpr);
            }
        }
        for (int i = 0; i < NSTARS; i++) {
            sprA(c, bStars[stI[i]], stX[i], stY[i], stS[i], 0f, 0.35f + stS[i] * 0.45f, false);
        }
    }

    void drawMenu(Canvas c) {
        float cx = W / 2f;
        float bob = sin(tick * 2f) * 6f;
        text(c, "JOGUINHO", cx, H * 0.16f + bob, 104, 0xFFFFFFFF, 1, true);
        text(c, "DEFESA ESPACIAL", cx, H * 0.16f + 62f + bob, 36, 0xFFFFD54F, 1, true);
        float sy = H * 0.355f + sin(tick * 1.6f) * 10f;
        sprA(c, bLight, cx, sy, 1.2f, 0f, 0.35f, true);
        drawShipIdx(c, shipIdx, cx, sy, 2.1f, 1f);
        text(c, SHIP_NAME[shipIdx], cx, sy + 110f, 30, 0xFFFFFFFF, 1, true);
        drawButtons(c);
        panelRecord(c, cx - 200f, H - 190f);
        text(c, "ARTE E AUDIO CC0 - KENNEY.NL", cx, H - 22f, 17, 0xB0FFFFFF, 1, true);
    }

    void panelRecord(Canvas c, float x, float y) {
        nine(c, bPanelRect, x, y, 400f, 120f, 24, 18f, 215);
        text(c, "RECORDE", x + 200f, y + 38f, 24, 0xFFFFD54F, 1, true);
        drawNum(c, hi, x + 200f, y + 80f, 2.4f, 1);
    }

    void drawSelect(Canvas c) {
        text(c, "ESCOLHA A NAVE", W / 2f, 120f, 52, 0xFFFFFFFF, 1, true);
        String[] statName = {"VIDA", "DANO", "TIRO"};
        String[] statCol = {"Green", "Red", "Yellow"};
        for (int i = 0; i < 3; i++) {
            Btn b = btns.get(i);
            boolean open = hi >= SHIP_UNLOCK[i];
            boolean pr = b == pressed;
            float x = b.x, y = b.y + (pr ? 3f : 0f);
            nine(c, bPanelGlass, x, y, b.w, b.h, 36, 28f, 255);
            drawShipIdx(c, i, x + 125f, y + 135f, 1.5f, open ? 1f : 0.35f);
            text(c, SHIP_NAME[i], x + 245f, y + 70f, 38, DARK, 0, false);
            if (open) {
                float[] v = {SHIP_HP[i] / 150f, SHIP_DMG[i] / 1.6f, 0.11f / SHIP_FIRE[i]};
                for (int r = 0; r < 3; r++) {
                    float by = y + 106f + r * 44f;
                    text(c, statName[r], x + 245f, by + 18f, 20, DARK, 0, false);
                    bar(c, statCol[r], x + 345f, by, 225f, 24f, v[r]);
                }
            } else {
                text(c, "BLOQUEADA", x + 245f, y + 150f, 30, 0xFFD32F2F, 0, false);
                text(c, "ALCANCE " + SHIP_UNLOCK[i] + " PONTOS", x + 245f, y + 192f, 21, DARK, 0, false);
            }
            if (open && i == shipIdx) {
                pill(c, "Yellow", x + b.w - 175f, y + 22f, 150f, 40f, 255);
                text(c, "EM USO", x + b.w - 100f, y + 50f, 21, DARK, 1, false);
            }
        }
        drawButtons(c);
        text(c, "RECORDE " + hi, W / 2f, H - 40f, 24, 0xFFFFD54F, 1, true);
    }

    void drawPlayer(Canvas c) {
        float s = 1.15f;
        float alpha = (invulT > 0f && ((int) (tick * 14f) % 2 == 0)) ? 0.4f : 1f;
        thruster(c, bPlayer, px, py, s, alpha);
        sprA(c, bPlayer, px, py, s, roll, alpha, false);
        float fr = hp / maxHp;
        int d = fr < 0.2f ? 2 : (fr < 0.4f ? 1 : (fr < 0.66f ? 0 : -1));
        if (d >= 0) sprA(c, bDmg[d], px, py, s, roll, alpha, false);
        if (d >= 1 && ((int) (tick * 10f) % 3 == 0)) {
            fx(bSmoke, null, px + (rnd.nextFloat() - 0.5f) * 30f, py + 10f, 0.7f, 0.05f, 0.16f, 0.45f, false).vy = 90f;
        }
        if (shieldT > 0f) {
            boolean blink = shieldT < 2f && ((int) (tick * 12f) % 2 == 0);
            if (!blink) sprA(c, bShield, px, py, 1.05f + 0.05f * sin(tick * 8f), tick * 40f, 0.9f, true);
        }
        if (hitFlash > 0f) sprA(c, bPlayer, px, py, s, roll, hitFlash * 2f, true);
    }

    void drawFx(Canvas c, Fx f) {
        if (f.t < 0f) return;
        float p = Math.min(1f, f.t / f.life);
        float s = f.s0 + (f.s1 - f.s0) * p;
        float a;
        Bitmap b;
        if (f.frames != null) {
            b = f.frames[Math.min(f.frames.length - 1, (int) (p * f.frames.length))];
            a = f.a0 * (p > 0.8f ? (1f - p) / 0.2f : 1f);
        } else {
            b = f.bmp;
            a = f.a0 * (1f - p);
        }
        sprA(c, b, f.x, f.y, s, f.rot, a, f.add);
    }

    void drawWorld(Canvas c) {
        c.save();
        if (shakeT > 0f) {
            float a = shakeAmp * Math.min(1f, shakeT / 0.4f);
            c.translate((rnd.nextFloat() - 0.5f) * a * 2f, (rnd.nextFloat() - 0.5f) * a * 2f);
        }
        for (int i = 0; i < rocks.size(); i++) {
            Rock r = rocks.get(i);
            spr(c, r.bmp, r.x, r.y, 1f, r.rot, pSpr);
            if (r.flash > 0f) sprA(c, r.bmp, r.x, r.y, 1f, r.rot, 0.7f, true);
        }
        for (int i = 0; i < pickups.size(); i++) {
            Pickup p = pickups.get(i);
            sprA(c, bLight, p.x, p.y, 0.22f, 0f, 0.55f, true);
            spr(c, A.get(pickupPath[p.kind]), p.x, p.y, pickupScale[p.kind] * (1f + 0.08f * sin(p.t * 6f)), 0f, pSpr);
        }
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            spr(c, e.bmp, e.x, e.y, e.sc, e.rot, pSpr);
            if (e.flash > 0f) sprA(c, e.bmp, e.x, e.y, e.sc, e.rot, 0.7f, true);
        }
        for (int i = 0; i < bullets.size(); i++) {
            Bullet b = bullets.get(i);
            if (b.mine) {
                sprA(c, bLight, b.x, b.y, 0.11f, 0f, 0.45f, true);
                spr(c, b.bmp, b.x, b.y, 1.15f, b.rot, pSpr);
            } else {
                sprA(c, bLight, b.x, b.y, 0.14f, 0f, 0.6f, true);
                spr(c, b.bmp, b.x, b.y, 1.4f, b.rot, pSpr);
            }
        }
        if (alive) drawPlayer(c);
        for (int i = 0; i < fxs.size(); i++) drawFx(c, fxs.get(i));
        for (int i = 0; i < pops.size(); i++) {
            Pop p = pops.get(i);
            int a = (int) (clamp(1.2f - p.t, 0f, 1f) * 255f);
            text(c, p.s, p.x, p.y, 26, (a << 24) | (p.color & 0xFFFFFF), 1, true);
        }
        c.restore();
        if (flashT > 0f) sprA(c, bLight, W / 2f, H / 2f, 6f, 0f, flashT * 2f, true);
    }

    void drawBtnSquare(Canvas c, Btn b, float alpha) {
        boolean pr = b == pressed;
        float k = pr ? 0.94f : 1f;
        float w = b.w * k, h = b.h * k;
        pSpr.setAlpha((int) (alpha * 255f));
        drawBox(c, bBtnSq, b.x + (b.w - w) / 2f, b.y + (b.h - h) / 2f, w, h);
        pSpr.setAlpha(255);
    }

    void drawHud(Canvas c) {
        float t = ti;
        // ---- painel esquerdo: vidas, arma, vida e escudo ----
        nine(c, bPanelRect, 14f, t + 12f, 304f, 140f, 24, 18f, 215);
        float x0 = 28f;
        spr(c, bLife, x0 + 22f, t + 44f, 1.4f, 0f, pSpr);
        spr(c, bNumX, x0 + 62f, t + 46f, 1.3f, 0f, pSpr);
        drawNum(c, Math.max(0, lives), x0 + 76f, t + 45f, 1.7f, 0);
        for (int i = 0; i < 5; i++) {
            spr(c, i < weapon ? bStarOn : bStarOff, x0 + 150f + i * 28f, t + 45f, 0.95f, 0f, pSpr);
        }
        float fr = hp / maxHp;
        bar(c, fr > 0.6f ? "Green" : (fr > 0.3f ? "Yellow" : "Red"), x0, t + 68f, 276f, 28f, fr);
        text(c, "VIDA", x0 + 14f, t + 89f, 18, 0xFFFFFFFF, 0, true);
        bar(c, "Blue", x0, t + 106f, 276f, 24f, shieldT / 8f);
        text(c, "ESCUDO", x0 + 14f, t + 125f, 16, 0xFFFFFFFF, 0, true);

        // ---- painel direito: pontos, onda, combo ----
        float rx = 406f;
        nine(c, bPanelRect, rx, t + 12f, 300f, 140f, 24, 18f, 215);
        text(c, "PONTOS", rx + 18f, t + 40f, 19, 0xFFFFD54F, 0, true);
        drawNum(c, score, rx + 282f, t + 76f, 2.0f, 2);
        text(c, "ONDA " + Math.max(1, wave), rx + 18f, t + 130f, 24, 0xFFFFFFFF, 0, true);
        if (combo >= 2) {
            pill(c, "Yellow", rx + 150f, t + 104f, 134f, 34f, 255);
            text(c, "COMBO " + combo, rx + 217f, t + 128f, 17, DARK, 1, false);
        }

        // ---- botoes de jogo ----
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            if (b.id == B_PAUSE) {
                drawBtnSquare(c, b, 1f);
                text(c, "II", b.x + b.w / 2f, b.y + b.h / 2f + 13f, 34, DARK, 1, false);
            } else if (b.id == B_BOMB) {
                float al = bombs > 0 ? 1f : 0.5f;
                drawBtnSquare(c, b, al);
                sprA(c, A.get("powerups/powerupRed_star.png"), b.x + b.w / 2f, b.y + 50f, 1.9f, 0f, al, false);
                spr(c, bNumX, b.x + b.w / 2f - 20f, b.y + 100f, 1.2f, 0f, pSpr);
                drawNum(c, bombs, b.x + b.w / 2f - 6f, b.y + 100f, 1.5f, 0);
            }
        }

        // ---- barra do chefe ----
        if (bossAlive && boss != null) {
            text(c, "CHEFE", W / 2f, t + 186f, 22, 0xFFFF8A80, 1, true);
            bar(c, "Red", W / 2f - 260f, t + 196f, 520f, 28f, boss.hp / boss.maxHp);
        }

        // ---- aviso de onda ----
        if (bannerT > 0f && state == PLAY) {
            float el = bannerMax - bannerT;
            float pop = 1f + Math.max(0f, 1f - el / 0.3f) * 0.3f;
            float a = clamp(Math.min(bannerT / 0.5f, el / 0.15f), 0f, 1f);
            int col = ((int) (a * 255f) << 24) | (bannerBoss ? 0xFF5252 : 0xFFFFFF);
            text(c, bannerText, W / 2f, H * 0.36f, 74 * pop, col, 1, true);
        }
        if (hintT > 0f && state == PLAY) {
            int a = (int) (clamp(hintT, 0f, 1f) * 255f);
            text(c, "ARRASTE PARA MOVER", W / 2f, H * 0.62f, 36, (a << 24) | 0xFFFFFF, 1, true);
            text(c, "O TIRO E AUTOMATICO", W / 2f, H * 0.62f + 46f, 26, (a << 24) | 0xFFD54F, 1, true);
        }
    }

    void drawPause(Canvas c) {
        dim(c, 175);
        float cx = W / 2f, py0 = H / 2f - 360f;
        nine(c, bPanelGlass, cx - 270f, py0, 540f, 720f, 36, 28f, 255);
        pill(c, "Yellow", cx - 190f, py0 + 34f, 380f, 90f, 255);
        text(c, "PAUSA", cx, py0 + 96f, 46, 0xFFFFFFFF, 1, true);
        drawButtons(c);
    }

    void drawOver(Canvas c) {
        dim(c, 190);
        float cx = W / 2f, py0 = H / 2f - 430f;
        nine(c, bPanelGlass, cx - 290f, py0, 580f, 860f, 36, 28f, 255);
        pill(c, "Red", cx - 230f, py0 + 34f, 460f, 94f, 255);
        text(c, "FIM DE JOGO", cx, py0 + 98f, 44, 0xFFFFFFFF, 1, true);
        text(c, "PONTOS", cx, py0 + 190f, 28, DARK, 1, false);
        drawNum(c, score, cx, py0 + 250f, 3.4f, 1);
        text(c, "RECORDE", cx, py0 + 336f, 24, DARK, 1, false);
        drawNum(c, hi, cx, py0 + 388f, 2.2f, 1);
        if (newRecord) {
            float k = 1f + 0.06f * sin(tick * 8f);
            text(c, "NOVO RECORDE!", cx, py0 + 462f, 38 * k, 0xFFE67E00, 1, false);
        }
        text(c, "ONDA ALCANCADA: " + Math.max(1, wave), cx, py0 + 518f, 26, DARK, 1, false);
        if (!unlockMsg.isEmpty()) text(c, unlockMsg, cx, py0 + 566f, 24, 0xFF2E9E4F, 1, false);
        drawButtons(c);
    }
}
