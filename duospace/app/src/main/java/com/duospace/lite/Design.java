package com.duospace.lite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Design {
    static final int BACK = Color.rgb(11, 16, 33);
    static final int SURFACE = Color.rgb(22, 29, 52);
    static final int TEXT = Color.rgb(234, 238, 255);
    static final int MUTED = Color.rgb(158, 171, 204);
    static final int ACCENT = Color.rgb(112, 133, 255);
    private Design() {}

    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
    static LinearLayout column(Context c, int pad) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,pad),dp(c,pad),dp(c,pad),dp(c,pad));
        return l;
    }
    static TextView title(Context c, String s, int size) {
        TextView t = new TextView(c); t.setText(s); t.setTextColor(TEXT);
        t.setTextSize(size); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    static TextView copy(Context c, String s, int size) {
        TextView t = new TextView(c); t.setText(s); t.setTextColor(MUTED); t.setTextSize(size);
        t.setLineSpacing(0,1.16f); return t;
    }
    static Button button(Context c, String text) {
        Button b = new Button(c); b.setText(text); b.setTextColor(TEXT);
        b.setTextSize(13); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));
        b.setBackground(shape(ACCENT,dp(c,14))); return b;
    }
    static LinearLayout.LayoutParams lp(Context c, int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(c,w), h < 0 ? h : dp(c,h));
        p.topMargin = dp(c,top); return p;
    }
    static void hideNavigationContrast(View v) { v.setBackgroundColor(BACK); }
}
