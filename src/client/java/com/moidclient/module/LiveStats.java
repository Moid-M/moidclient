package com.moidclient.module;

/**
 * Snapshot of live game values fed into module preview() methods.
 * Gathered once per second on the client tick and broadcast to the
 * Web dashboard alongside the preview strings.
 */
public final class LiveStats {
    public final int ping;
    public final int fps;
    public final int cpsLeft;
    public final int cpsRight;
    public final boolean w;
    public final boolean a;
    public final boolean s;
    public final boolean d;
    public final boolean space;
    public final boolean shift;
    public final boolean lmb;
    public final boolean rmb;

    public LiveStats(int ping, int fps, int cpsLeft, int cpsRight,
                     boolean w, boolean a, boolean s, boolean d,
                     boolean space, boolean shift, boolean lmb, boolean rmb) {
        this.ping = ping;
        this.fps = fps;
        this.cpsLeft = cpsLeft;
        this.cpsRight = cpsRight;
        this.w = w;
        this.a = a;
        this.s = s;
        this.d = d;
        this.space = space;
        this.shift = shift;
        this.lmb = lmb;
        this.rmb = rmb;
    }

    public boolean anyKeyPressed() {
        return w || a || s || d || space || shift || lmb || rmb;
    }
}
