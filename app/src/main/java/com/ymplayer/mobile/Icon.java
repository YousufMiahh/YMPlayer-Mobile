package com.ymplayer.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Simple line icons drawn in code (no image files), so they stay sharp and follow the theme colour. */
public class Icon extends View {
    public enum Kind {
        HOME, LIVE, MOVIES, SERIES, SEARCH, SETTINGS, BACK, DOWN, HEART, HEART_FILLED, PLAY, PAUSE,
        BACK10, FWD10, SUBS, LIST, CLOSE, PLUS, CHECK, INFO, NEXT, PREV, FIT, TRAILER, RESTART
    }

    private Kind kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public Icon(Context c, Kind k, int color) {
        super(c);
        kind = k;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Ui.bold());
        setColor(color);
    }

    public void setKind(Kind k) {
        if (k == kind) return;
        kind = k;
        invalidate();
    }

    public Kind kind() { return kind; }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        text.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        if (size <= 0) return;
        float s = size / 24f;
        c.save();
        c.translate(getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - size) / 2f,
                getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - size) / 2f);
        c.scale(s, s);
        stroke.setStrokeWidth(2f);
        path.reset();
        switch (kind) {
            case HOME:
                path.moveTo(3, 11); path.lineTo(12, 3.5f); path.lineTo(21, 11);
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20); path.lineTo(18.5f, 20); path.lineTo(18.5f, 9.5f);
                path.moveTo(10, 20); path.lineTo(10, 14.5f); path.lineTo(14, 14.5f); path.lineTo(14, 20);
                c.drawPath(path, stroke);
                break;
            case LIVE:
                r.set(2.5f, 6.5f, 21.5f, 19); c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                path.moveTo(8, 2.5f); path.lineTo(12, 6.5f); path.lineTo(16, 2.5f);
                c.drawPath(path, stroke);
                break;
            case MOVIES:
                r.set(4, 3, 20, 21); c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                path.moveTo(8, 3); path.lineTo(8, 21); path.moveTo(16, 3); path.lineTo(16, 21);
                path.moveTo(4, 8); path.lineTo(8, 8); path.moveTo(4, 12); path.lineTo(8, 12); path.moveTo(4, 16); path.lineTo(8, 16);
                path.moveTo(16, 8); path.lineTo(20, 8); path.moveTo(16, 12); path.lineTo(20, 12); path.moveTo(16, 16); path.lineTo(20, 16);
                c.drawPath(path, stroke);
                break;
            case SERIES:
                r.set(3, 8, 21, 20.5f); c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                path.moveTo(5.5f, 4.5f); path.lineTo(18.5f, 4.5f);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(10.5f, 11.5f); path.lineTo(15, 14.25f); path.lineTo(10.5f, 17); path.close();
                c.drawPath(path, fill);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, stroke);
                c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, stroke);
                break;
            case SETTINGS:
                c.drawCircle(12, 12, 3, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(12 + (float) Math.cos(a) * 6.8f, 12 + (float) Math.sin(a) * 6.8f,
                            12 + (float) Math.cos(a) * 9.3f, 12 + (float) Math.sin(a) * 9.3f, stroke);
                }
                c.drawCircle(12, 12, 6.8f, stroke);
                break;
            case BACK:
                path.moveTo(15, 4.5f); path.lineTo(7.5f, 12); path.lineTo(15, 19.5f);
                c.drawPath(path, stroke);
                break;
            case NEXT:
                path.moveTo(9, 4.5f); path.lineTo(16.5f, 12); path.lineTo(9, 19.5f);
                c.drawPath(path, stroke);
                break;
            case PREV:
                path.moveTo(15, 4.5f); path.lineTo(7.5f, 12); path.lineTo(15, 19.5f);
                c.drawPath(path, stroke);
                break;
            case DOWN:
                path.moveTo(6, 9); path.lineTo(12, 15); path.lineTo(18, 9);
                c.drawPath(path, stroke);
                break;
            case HEART:
            case HEART_FILLED:
                heart();
                c.drawPath(path, kind == Kind.HEART_FILLED ? fill : stroke);
                break;
            case PLAY:
                path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close();
                stroke.setStrokeWidth(1.5f);
                c.drawPath(path, fill);
                c.drawPath(path, stroke);
                break;
            case PAUSE:
                r.set(6, 4, 10, 20); c.drawRoundRect(r, 1.2f, 1.2f, fill);
                r.set(14, 4, 18, 20); c.drawRoundRect(r, 1.2f, 1.2f, fill);
                break;
            case BACK10:
            case FWD10: {
                boolean fwd = kind == Kind.FWD10;
                r.set(3, 3, 21, 21);
                c.save();
                if (fwd) c.scale(-1, 1, 12, 12);
                c.drawArc(r, -80, 300, false, stroke);
                path.moveTo(12.5f, 0.5f); path.lineTo(9.5f, 3); path.lineTo(12.5f, 5.5f);
                c.drawPath(path, stroke);
                c.restore();
                text.setTextSize(7.5f);
                c.drawText(fwd ? "10" : "5", 12, 14.7f, text);
                break;
            }
            case SUBS:
                r.set(2.5f, 4.5f, 21.5f, 18); c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                path.moveTo(6.5f, 10.5f); path.lineTo(10, 10.5f); path.moveTo(12.5f, 10.5f); path.lineTo(17.5f, 10.5f);
                path.moveTo(6.5f, 14); path.lineTo(13, 14); path.moveTo(15.5f, 14); path.lineTo(17.5f, 14);
                c.drawPath(path, stroke);
                break;
            case LIST:
                for (int i = 0; i < 3; i++) {
                    float y = 6 + i * 6;
                    c.drawCircle(4.5f, y, 1.3f, fill);
                    c.drawLine(9, y, 20.5f, y, stroke);
                }
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, stroke);
                c.drawLine(18, 6, 6, 18, stroke);
                break;
            case PLUS:
                c.drawLine(12, 4.5f, 12, 19.5f, stroke);
                c.drawLine(4.5f, 12, 19.5f, 12, stroke);
                break;
            case CHECK:
                path.moveTo(4.5f, 12.5f); path.lineTo(9.5f, 17.5f); path.lineTo(19.5f, 6.5f);
                c.drawPath(path, stroke);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, stroke);
                c.drawLine(12, 11, 12, 16.5f, stroke);
                c.drawCircle(12, 7.6f, 1.2f, fill);
                break;
            case FIT:
                path.moveTo(3, 8); path.lineTo(3, 4); path.lineTo(7, 4);
                path.moveTo(17, 4); path.lineTo(21, 4); path.lineTo(21, 8);
                path.moveTo(21, 16); path.lineTo(21, 20); path.lineTo(17, 20);
                path.moveTo(7, 20); path.lineTo(3, 20); path.lineTo(3, 16);
                c.drawPath(path, stroke);
                break;
            case TRAILER:
                r.set(2.5f, 5, 21.5f, 19); c.drawRoundRect(r, 4, 4, stroke);
                path.moveTo(10, 9); path.lineTo(15, 12); path.lineTo(10, 15); path.close();
                c.drawPath(path, fill);
                break;
            case RESTART:
                r.set(4, 4, 20, 20);
                c.drawArc(r, -60, 300, false, stroke);
                path.moveTo(5, 2.5f); path.lineTo(5.3f, 8); path.lineTo(10.5f, 7);
                c.drawPath(path, stroke);
                break;
        }
        c.restore();
    }

    private void heart() {
        path.moveTo(12, 20.5f);
        path.cubicTo(4.5f, 15, 2.5f, 11.5f, 2.5f, 8.5f);
        path.cubicTo(2.5f, 5.5f, 4.8f, 3.5f, 7.3f, 3.5f);
        path.cubicTo(9.2f, 3.5f, 11, 4.6f, 12, 6.3f);
        path.cubicTo(13, 4.6f, 14.8f, 3.5f, 16.7f, 3.5f);
        path.cubicTo(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f);
        path.cubicTo(21.5f, 11.5f, 19.5f, 15, 12, 20.5f);
        path.close();
    }
}
