package ru.nullch.ottomaticfork;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Translucent Xbox-style on-screen gamepad for Otto Matic.
 *
 * Curved button captions along upper inner contour drawn via Path.addArc & Canvas.drawTextOnPath.
 */
public class GamepadOverlay extends View {
    static final int A = 1, B = 1 << 1, X = 1 << 2, Y = 1 << 3, LB = 1 << 4, RB = 1 << 5,
            START = 1 << 6, BACK = 1 << 7, R3 = 1 << 8, L3 = 1 << 9;

    private static final String[] LANG_CODES = {"en", "fr", "de", "es", "it", "sv", "ru"};

    private static final class Btn {
        final String label; String caption; final int mask, color, resId; final boolean round;
        float cx, cy, r;             // round buttons
        final RectF rect = new RectF(); // rectangular buttons
        int pointerId = -1;
        Btn(String l, int resId, String defaultCap, int m, int col, boolean round) {
            label = l; caption = defaultCap; this.resId = resId; mask = m; color = col; this.round = round;
        }
        boolean hit(float x, float y) {
            if (round) { float dx = x - cx, dy = y - cy; float rr = r * 1.25f; return dx * dx + dy * dy <= rr * rr; }
            RectF big = new RectF(rect); big.inset(-rect.height() * 0.25f, -rect.height() * 0.25f);
            return big.contains(x, y);
        }
    }

    private static final class Stick {
        int pointerId = -1;
        float ox, oy, x, y;          // origin & current finger position
        float vx, vy;                // -1..1
    }

    private final Btn bA = new Btn("A", R.string.pad_jump, "JUMP", A, 0xFF4CAF50, true);
    private final Btn bB = new Btn("B", R.string.pad_punch, "PUNCH", B, 0xFFE53935, true);
    private final Btn bX = new Btn("X", R.string.pad_shoot, "SHOOT", X, 0xFF1E88E5, true);
    private final Btn bLB = new Btn("LB", R.string.pad_prev, "PREV", LB, 0xFFCCCCCC, false);
    private final Btn bRB = new Btn("RB", R.string.pad_next, "NEXT", RB, 0xFFCCCCCC, false);
    private final Btn bStart = new Btn("START", R.string.pad_pause, "PAUSE", START, 0xFFCCCCCC, false);
    private final Btn bBack = new Btn("BACK", R.string.pad_back, "BACK", BACK, 0xFFCCCCCC, false);
    private final Btn bR3 = new Btn("R3", R.string.pad_cam, "CAM", R3, 0xFFCCCCCC, true);
    private final Btn[] buttons = { bA, bB, bX, bLB, bRB, bStart, bBack, bR3 };

    private final Stick left = new Stick(), right = new Stick();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arcPath = new Path();
    private final float density;
    private float stickRadius, defLeftX, defLeftY;
    private boolean padVisible = true;

    public GamepadOverlay(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setFocusable(false);
        updateLocalizedCaptions(ctx);
    }

    public void updateLocalizedCaptions(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("otto_prefs", Context.MODE_PRIVATE);
        int langId = prefs.getInt("selected_lang", 0);
        if (langId < 0 || langId >= LANG_CODES.length) langId = 0;

        Locale locale = new Locale(LANG_CODES[langId]);
        Configuration config = new Configuration(ctx.getResources().getConfiguration());
        config.setLocale(locale);
        Resources res = ctx.createConfigurationContext(config).getResources();

        for (Btn b : buttons) {
            try {
                b.caption = res.getString(b.resId);
            } catch (Exception ignored) {}
        }
        invalidate();
    }

    public void setPadVisible(boolean v) {
        padVisible = v;
        if (!v) { release(); }
        setVisibility(v ? VISIBLE : GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float u = Math.min(w, h) / 100f;       // layout unit: 1% of the short side
        float r = 9.5f * u;                     // face-button radius
        float cx = w - 24f * u, cy = h - 22f * u;   // face-button cluster center
        float d = 1.45f * r;

        // Clean 3-button layout (X on left, A at bottom, B on right)
        bA.cx = cx;          bA.cy = cy + d * 0.85f; bA.r = r;
        bB.cx = cx + d;      bB.cy = cy - d * 0.35f; bB.r = r;
        bX.cx = cx - d;      bX.cy = cy - d * 0.35f; bX.r = r;
        bR3.cx = w - 7f * u; bR3.cy = h * 0.5f;     bR3.r = 5f * u;

        float bw = 17f * u, bh = 8f * u, m = 3f * u;
        bLB.rect.set(m, m, m + bw, m + bh);
        bRB.rect.set(w - m - bw, m, w - m, m + bh);
        float sw = 13f * u, sh = 6f * u;
        bBack.rect.set(w / 2f - sw - m / 2f, m, w / 2f - m / 2f, m + sh);
        bStart.rect.set(w / 2f + m / 2f, m, w / 2f + sw + m / 2f, m + sh);

        stickRadius = 15f * u;
        defLeftX = 20f * u + stickRadius; defLeftY = h - 20f * u - stickRadius * 0.2f;
        if (defLeftY > h - stickRadius - m) defLeftY = h - stickRadius - m;
    }

    private void release() {
        for (Btn b : buttons) b.pointerId = -1;
        left.pointerId = right.pointerId = -1;
        left.vx = left.vy = right.vx = right.vy = 0;
        push();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!padVisible) return false;
        final int action = e.getActionMasked();
        final int idx = e.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = e.getPointerId(idx);
                float x = e.getX(idx), y = e.getY(idx);
                Btn hit = null;
                for (Btn b : buttons) if (b.pointerId == -1 && b.hit(x, y)) { hit = b; break; }
                if (hit != null) {
                    hit.pointerId = id;
                } else if (x < getWidth() * 0.4f && left.pointerId == -1) {
                    left.pointerId = id; left.ox = left.x = x; left.oy = left.y = y;
                } else if (x >= getWidth() * 0.5f && right.pointerId == -1) {
                    right.pointerId = id; right.ox = right.x = x; right.oy = right.y = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (id == left.pointerId) { left.x = e.getX(i); left.y = e.getY(i); }
                    else if (id == right.pointerId) { right.x = e.getX(i); right.y = e.getY(i); }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (action == MotionEvent.ACTION_CANCEL) { release(); invalidate(); return true; }
                int id = e.getPointerId(idx);
                for (Btn b : buttons) if (b.pointerId == id) b.pointerId = -1;
                if (left.pointerId == id) left.pointerId = -1;
                if (right.pointerId == id) right.pointerId = -1;
                break;
            }
        }
        computeSticks();
        push();
        invalidate();
        return true;       // swallow everything: the game is driven by the virtual gamepad only
    }

    private void computeSticks() {
        stickAxis(left); stickAxis(right);
    }

    private void stickAxis(Stick s) {
        if (s.pointerId == -1) { s.vx = s.vy = 0; return; }
        float dx = s.x - s.ox, dy = s.y - s.oy;
        float len = (float) Math.hypot(dx, dy);
        float max = stickRadius;
        if (len > max) { dx *= max / len; dy *= max / len; s.ox += (s.x - s.ox) - dx; s.oy += (s.y - s.oy) - dy; }
        s.vx = Math.max(-1f, Math.min(1f, dx / max));
        s.vy = Math.max(-1f, Math.min(1f, dy / max));
    }

    private void push() {
        int mask = 0;
        for (Btn b : buttons) if (b.pointerId != -1) mask |= b.mask;
        try {
            OttoActivity.nativeSetPad(mask,
                    Math.round(left.vx * 32767f), Math.round(left.vy * 32767f),
                    Math.round(right.vx * 32767f), Math.round(right.vy * 32767f));
        } catch (UnsatisfiedLinkError ignored) {
            // native library not loaded yet (very early touch) - harmless
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!padVisible) return;
        // sticks
        drawStick(c, left, defLeftX, defLeftY);
        float rdx = getWidth() * 0.62f, rdy = getHeight() * 0.62f;
        if (right.pointerId != -1) drawStick(c, right, rdx, rdy);

        for (Btn b : buttons) {
            boolean down = b.pointerId != -1;
            fill.setColor(down ? 0x99FFFFFF : 0x40000000);
            stroke.setColor(down ? 0xFFFFFFFF : (b.color & 0x00FFFFFF) | 0xAA000000);
            text.setColor((b.color & 0x00FFFFFF) | 0xDD000000);
            if (b.round) {
                c.drawCircle(b.cx, b.cy, b.r, fill);
                c.drawCircle(b.cx, b.cy, b.r, stroke);

                // Draw Button Letter (A, B, X) in center
                text.setTextSize(b.r * 0.70f);
                c.drawText(b.label, b.cx, b.cy + text.getTextSize() * 0.35f, text);

                // Draw Curved Action Caption along upper inner arc of the circle
                if (!b.caption.isEmpty()) {
                    float margin = b.r * 0.22f;
                    RectF arcBounds = new RectF(b.cx - (b.r - margin), b.cy - (b.r - margin),
                            b.cx + (b.r - margin), b.cy + (b.r - margin));

                    arcPath.reset();
                    arcPath.addArc(arcBounds, 180f, 180f); // Upper arc from 180 deg (left) to 360 deg (right)

                    float maxArcLen = (float) Math.PI * (b.r - margin) * 0.85f;
                    float textSize = b.r * 0.28f;
                    text.setTextSize(textSize);
                    while (text.measureText(b.caption) > maxArcLen && textSize > 8f * density) {
                        textSize -= 0.5f * density;
                        text.setTextSize(textSize);
                    }

                    float arcLength = (float) Math.PI * (b.r - margin);
                    float hOffset = (arcLength - text.measureText(b.caption)) / 2f;
                    c.drawTextOnPath(b.caption, arcPath, Math.max(0, hOffset), 0f, text);
                }
            } else {
                float rad = b.rect.height() * 0.3f;
                c.drawRoundRect(b.rect, rad, rad, fill);
                c.drawRoundRect(b.rect, rad, rad, stroke);
                text.setTextSize(b.rect.height() * 0.45f);
                c.drawText(b.label, b.rect.centerX(), b.rect.centerY() + text.getTextSize() * 0.35f, text);
            }
        }
    }

    private void drawStick(Canvas c, Stick s, float defX, float defY) {
        float bx = s.pointerId != -1 ? s.ox : defX, by = s.pointerId != -1 ? s.oy : defY;
        float kx = bx + s.vx * stickRadius, ky = by + s.vy * stickRadius;
        fill.setColor(0x30FFFFFF); stroke.setColor(0x80FFFFFF);
        c.drawCircle(bx, by, stickRadius, fill);
        c.drawCircle(bx, by, stickRadius, stroke);
        fill.setColor(s.pointerId != -1 ? 0xAAFFFFFF : 0x55FFFFFF);
        c.drawCircle(kx, ky, stickRadius * 0.45f, fill);

        // Otto Matic Robot Head Silhouette inside knob
        drawOttoRobotHead(c, kx, ky, stickRadius * 0.28f, s.pointerId != -1 ? 0xDDFFFFFF : 0x99FFFFFF);
    }

    /**
     * Draws the silhouette of Otto Matic's iconic robot head:
     * - Rounded dome/trapezoid head
     * - Two robotic eyes
     * - Two antenna stalks pointing outwards with spherical tips
     */
    private void drawOttoRobotHead(Canvas c, float cx, float cy, float scale, int color) {
        fill.setColor(color);
        stroke.setColor(color);
        float origStrokeWidth = stroke.getStrokeWidth();
        stroke.setStrokeWidth(1.8f * density);

        // Head dome
        float hw = scale * 0.9f, hh = scale * 0.65f;
        RectF headRect = new RectF(cx - hw, cy - hh * 0.4f, cx + hw, cy + hh);
        c.drawRoundRect(headRect, scale * 0.25f, scale * 0.25f, fill);

        // Eyes
        int eyeColor = 0xFF121212;
        fill.setColor(eyeColor);
        float eyeOffset = hw * 0.45f;
        float eyeRadius = scale * 0.22f;
        c.drawCircle(cx - eyeOffset, cy, eyeRadius, fill);
        c.drawCircle(cx + eyeOffset, cy, eyeRadius, fill);
        fill.setColor(color);

        // Antennas
        float antStartX = hw * 0.5f;
        float antStartY = cy - hh * 0.35f;
        float antEndX = hw * 0.95f;
        float antEndY = cy - hh * 1.35f;

        c.drawLine(cx - antStartX, antStartY, cx - antEndX, antEndY, stroke);
        c.drawLine(cx + antStartX, antStartY, cx + antEndX, antEndY, stroke);

        // Antenna tip spheres
        c.drawCircle(cx - antEndX, antEndY, scale * 0.18f, fill);
        c.drawCircle(cx + antEndX, antEndY, scale * 0.18f, fill);

        stroke.setStrokeWidth(origStrokeWidth);
    }
}
