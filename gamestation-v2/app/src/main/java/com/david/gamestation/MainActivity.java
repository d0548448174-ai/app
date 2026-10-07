package com.david.gamestation;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    GameStore store;
    GameView view;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff050912);
        store=new GameStore(this);
        view=new GameView(this);
        setContentView(view);
        immersive();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public void pickApk() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,77);
    }

    @Override protected void onActivityResult(int r,int c,Intent d) {
        super.onActivityResult(r,c,d);
        if(r==77 && c==RESULT_OK && d!=null && d.getData()!=null) store.importApk(d.getData(),view);
    }

    public void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if(view.page==1){view.backToHome();}else super.onBackPressed();
    }
}
