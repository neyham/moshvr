package com.termux.view;

/**
 * Quest thumbstick → terminal rows. Y-up is negative on Android joysticks.
 * Do not feed SOURCE_MOUSE AXIS_Y through this — that axis is a pixel position.
 */
public final class StickScroll {
    public static final float DEADZONE = 0.28f;
    /** Horizon repeats stick-as-wheel at ~100 Hz; cap so herdr stays readable. */
    public static final long MIN_TICK_MS = 50L;

    private StickScroll() {}

    public static float strongestHorizontal(float x, float hatX, float hscroll) {
        float best = x;
        if (Math.abs(hatX) > Math.abs(best)) best = hatX;
        if (Math.abs(hscroll) > Math.abs(best)) best = hscroll;
        return best;
    }

    /** Stick axes are [-1, 1]. Pointer AXIS_X is a pixel and must not switch tabs. */
    public static float axisIfStick(float value) {
        return Math.abs(value) <= 1.5f ? value : 0f;
    }

    /** Horizontal wins unless the stick is clearly more vertical. */
    public static boolean isHorizontalScroll(float hscroll, float vscroll) {
        float h = Math.abs(hscroll);
        float v = Math.abs(vscroll);
        return h >= DEADZONE && h + 0.05f >= v;
    }

    public static float strongestVertical(float y, float rz, float ry, float hatY) {
        float best = y;
        if (Math.abs(rz) > Math.abs(best)) best = rz;
        if (Math.abs(ry) > Math.abs(best)) best = ry;
        if (Math.abs(hatY) > Math.abs(best)) best = hatY;
        return best;
    }

    /** Negative rows = scroll up (stick forward / up). */
    public static int rowsForAxis(float axis) {
        float mag = Math.abs(axis);
        if (mag < DEADZONE) return 0;
        return axis < 0 ? -1 : 1;
    }

    public static long delayMs(float axis) {
        float mag = Math.abs(axis);
        if (mag > 0.85f) return 70L;
        if (mag > 0.55f) return 110L;
        return 160L;
    }

    public static boolean acceptTick(long lastAt, long now) {
        return now - lastAt >= MIN_TICK_MS;
    }

    public static int rowsForDpad(int keyCode) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) return -1;
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) return 1;
        return 0;
    }

    /** Negative = previous tab (stick left). */
    public static int tabsForAxis(float axis) {
        float mag = Math.abs(axis);
        if (mag < DEADZONE) return 0;
        return axis < 0 ? -1 : 1;
    }

    public static int tabsForDpad(int keyCode) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) return -1;
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return 1;
        return 0;
    }

    /** When both axes move, keep vertical as scroll so tab focus is never stolen. */
    public static boolean preferVertical(float x, float y) {
        return Math.abs(y) > Math.abs(x) + 0.08f;
    }
}
