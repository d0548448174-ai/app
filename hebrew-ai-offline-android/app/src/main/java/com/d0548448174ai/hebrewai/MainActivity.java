package com.d0548448174ai.hebrewai;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import android.content.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText prompt; TextView output; Spinner mode;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        TextView title=new TextView(this); title.setText("🤖 AI עברית אופליין"); title.setTextSize(26); title.setTextColor(Color.rgb(40,40,50)); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,70));
        TextView info=new TextView(this); info.setText("מכין את המנוע המקומי…"); info.setGravity(Gravity.CENTER); root.addView(info,new LinearLayout.LayoutParams(-1,55));
        mode=new Spinner(this); String[] modes={"כתוב סיפור","ספר בדיחה","כתוב לפי נושא","שפר טקסט"}; mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes)); root.addView(mode);
        prompt=new EditText(this); prompt.setHint("למשל: סיפור מצחיק על ילד שמגלה רובוט"); prompt.setGravity(Gravity.TOP|Gravity.RIGHT); prompt.setMinLines(4); root.addView(prompt,new LinearLayout.LayoutParams(-1,0,1));
        Button go=new Button(this); go.setText("✨ צור עם AI"); root.addView(go,new LinearLayout.LayoutParams(-1,60));
        output=new TextView(this); output.setText("התוצאה תופיע כאן"); output.setTextSize(18); output.setTextIsSelectable(true); output.setGravity(Gravity.RIGHT); output.setPadding(10,20,10,20);
        ScrollView scroll=new ScrollView(this); scroll.addView(output); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        go.setOnClickListener(v -> generate());
        setContentView(root);
        info.setText("מצב בסיסי מוכן — מנוע GGUF יתחבר בשלב הבא");
    }
    void generate(){
        String p=prompt.getText().toString().trim();
        if(p.isEmpty()){output.setText("כתוב נושא או בקשה.");return;}
        String m=mode.getSelectedItem().toString();
        output.setText("⏳ מכין תשובה…\n\n");
        new Handler().postDelayed(() -> output.setText("בקשה התקבלה: "+m+"\n\n"+p+"\n\nהמנוע המקומי יפיק כאן את הטקסט לאחר שילוב מודל GGUF."),300);
    }
}
