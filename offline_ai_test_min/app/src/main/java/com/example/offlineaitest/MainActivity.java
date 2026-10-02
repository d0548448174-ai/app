package com.example.offlineaitest;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 48, 36, 36);
        root.setBackgroundColor(Color.rgb(247, 244, 255));

        TextView title = new TextView(this);
        title.setText("🤖 AI אופליין — בדיקה");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(42, 33, 56));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView status = new TextView(this);
        status.setText("✅ האפליקציה עצמה פעילה\n\nגרסת בדיקה ללא מודל AI");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 45;
        root.addView(status, sp);

        EditText input = new EditText(this);
        input.setHint("כתוב משהו לבדיקה");
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = 35;
        root.addView(input, ip);

        Button button = new Button(this);
        button.setText("בדוק שהאפליקציה עובדת");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 15;
        root.addView(button, bp);

        TextView result = new TextView(this);
        result.setTextSize(17);
        result.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = 25;
        root.addView(result, rp);

        button.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            result.setText(text.isEmpty()
                ? "✅ האפליקציה עובדת.\nלא הוזן טקסט."
                : "✅ האפליקציה עובדת!\nקיבלתי: " + text);
        });

        setContentView(root);
    }
}
