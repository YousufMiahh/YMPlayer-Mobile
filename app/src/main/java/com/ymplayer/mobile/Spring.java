package com.ymplayer.mobile;

import android.view.Choreographer;

/**
 * A spring, the way Apple describes it: a damping ratio (1 = settles with no overshoot, lower =
 * bouncier) and a response in seconds (how quickly it gets there). Unlike a timed animation it
 * starts from wherever the value is now, at whatever speed it's already moving, so a drag hands
 * over to it with no seam and it can be grabbed again at any moment.
 */
final class Spring implements Choreographer.FrameCallback {
    interface Value {
        float get();
        void set(float v);
    }

    private final Value value;
    private float target, velocity;
    private double k, c;
    private long last;
    private boolean running;
    private Runnable onEnd;

    Spring(Value value) { this.value = value; }

    /** Go to `to`, starting at `velocity` (units per second): damping 1.0 and 0.35 s unless told otherwise. */
    Spring to(float to, float velocity, float damping, float response, Runnable onEnd) {
        this.target = to;
        this.velocity = velocity;
        double w = 2 * Math.PI / Math.max(0.05, response);
        k = w * w;
        c = 2 * damping * w;
        this.onEnd = onEnd;
        if (!running) {
            running = true;
            last = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
        return this;
    }

    Spring to(float to, float velocity, Runnable onEnd) { return to(to, velocity, 1f, 0.35f, onEnd); }

    /** Stop where it is (a finger has taken hold of it). */
    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    boolean running() { return running; }

    float velocity() { return velocity; }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        double dt = last == 0 ? 1 / 60.0 : Math.min(0.05, (nanos - last) / 1e9);
        last = nanos;
        float x = value.get();
        // A few small steps per frame keep stiff springs stable
        int n = 4;
        double h = dt / n, xv = x, vv = velocity;
        for (int i = 0; i < n; i++) {
            double a = -k * (xv - target) - c * vv;
            vv += a * h;
            xv += vv * h;
        }
        velocity = (float) vv;
        if (Math.abs(xv - target) < 0.5 && Math.abs(vv) < 20) {
            value.set(target);
            running = false;
            if (onEnd != null) onEnd.run();
            return;
        }
        value.set((float) xv);
        Choreographer.getInstance().postFrameCallback(this);
    }

    /**
     * Where a flick would come to rest (Apple's projection, the same as scrolling):
     * position + project(velocity).
     */
    static float project(float velocityPerSecond) {
        float d = 0.998f;
        return (velocityPerSecond / 1000f) * d / (1 - d);
    }
}
