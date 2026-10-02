package com.example.sudoku;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.print.*;
import android.print.pdf.PrintedPdfDocument;
import java.util.*;

public class MainActivity extends Activity {
    Board board;
    final Random rnd = new Random();
    int[][] solution = new int[9][9], user = new int[9][9];
    boolean[][] fixed = new boolean[9][9], wrong = new boolean[9][9];
    int selectedR=-1, selectedC=-1, mistakes=0, hints=0;
    String difficulty="בינוני";
    TextView status;

    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    GradientDrawable rounded(int color,int radius){ GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    TextView text(String s,float size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    Button button(String s){
        Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(Color.rgb(48,55,86),16));return b;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,13,25));getWindow().setNavigationBarColor(Color.rgb(10,13,25));
        buildUi();
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(5),dp(12),dp(10));root.setBackgroundColor(Color.rgb(10,13,25));

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("🧩  סודוקו קסום",23,Color.WHITE,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));
        Button newGame=button("✨ חדש");header.addView(newGame,new LinearLayout.LayoutParams(dp(88),dp(46)));
        newGame.setOnClickListener(v->newGame());
        root.addView(header);

        LinearLayout levelButtons=new LinearLayout(this);
        String[] difficultyLevels={"קל","בינוני","קשה","מומחה"};
        for(String d:difficultyLevels){
            Button b=button(d);levelButtons.addView(b,new LinearLayout.LayoutParams(0,dp(43),1));
            b.setOnClickListener(v->{difficulty=d;newGame();});
        }
        root.addView(levelButtons,new LinearLayout.LayoutParams(-1,dp(48)));

        board=new Board(this);root.addView(board,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        status=text("",12,Color.rgb(190,199,225),true);
        actions.addView(status,new LinearLayout.LayoutParams(0,dp(46),1));
        Button hint=button("💡 רמז");actions.addView(hint,new LinearLayout.LayoutParams(dp(82),dp(46)));
        hint.setOnClickListener(v->giveHint());
        Button booklet=button("🖨 30 חידות");actions.addView(booklet,new LinearLayout.LayoutParams(dp(105),dp(46)));
        booklet.setOnClickListener(v->printBooklet());
        root.addView(actions);

        GridLayout keypad=new GridLayout(this);keypad.setColumnCount(5);
        for(int n=1;n<=9;n++) addKey(keypad,String.valueOf(n),n);
        addKey(keypad,"⌫",0);
        root.addView(keypad,new LinearLayout.LayoutParams(-1,dp(96)));

        setContentView(root);newGame();
    }

    void addKey(GridLayout grid,String label,int value){
        Button b=button(label);GridLayout.LayoutParams p=new GridLayout.LayoutParams();
        p.width=0;p.height=dp(44);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        p.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(b,p);b.setOnClickListener(v->enter(value));
    }

    void newGame(){
        solution=new int[9][9];generate(solution);user=copy(solution);
        int holes=difficulty.equals("קל")?36:difficulty.equals("בינוני")?45:difficulty.equals("קשה")?52:58;
        int removed=0;while(removed<holes){int r=rnd.nextInt(9),c=rnd.nextInt(9);if(user[r][c]!=0){user[r][c]=0;removed++;}}
        fixed=new boolean[9][9];wrong=new boolean[9][9];
        for(int r=0;r<9;r++)for(int c=0;c<9;c++)fixed[r][c]=user[r][c]!=0;
        mistakes=0;hints=0;selectedR=selectedC=-1;updateStatus();board.invalidate();
    }

    void updateStatus(){status.setText("רמה: "+difficulty+"   •   טעויות: "+mistakes+"   •   רמזים: "+hints);}

    boolean generate(int[][] g){
        for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(g[r][c]==0){
            ArrayList<Integer> nums=new ArrayList<>();for(int n=1;n<=9;n++)nums.add(n);Collections.shuffle(nums,rnd);
            for(int n:nums)if(valid(g,r,c,n)){g[r][c]=n;if(generate(g))return true;g[r][c]=0;}
            return false;
        }return true;
    }
    boolean valid(int[][]g,int r,int c,int n){
        for(int i=0;i<9;i++)if(g[r][i]==n||g[i][c]==n)return false;
        int br=r/3*3,bc=c/3*3;for(int i=br;i<br+3;i++)for(int j=bc;j<bc+3;j++)if(g[i][j]==n)return false;
        return true;
    }
    int[][] copy(int[][]a){int[][]x=new int[9][9];for(int r=0;r<9;r++)System.arraycopy(a[r],0,x[r],0,9);return x;}

    void enter(int n){
        if(selectedR<0||fixed[selectedR][selectedC])return;
        if(n==0){user[selectedR][selectedC]=0;wrong[selectedR][selectedC]=false;}
        else{
            user[selectedR][selectedC]=n;
            if(n!=solution[selectedR][selectedC]){wrong[selectedR][selectedC]=true;mistakes++;}
            else wrong[selectedR][selectedC]=false;
        }
        updateStatus();board.invalidate();checkSolved();
    }

    void giveHint(){
        if(selectedR<0||fixed[selectedR][selectedC])return;
        user[selectedR][selectedC]=solution[selectedR][selectedC];wrong[selectedR][selectedC]=false;hints++;
        updateStatus();board.invalidate();checkSolved();
    }

    void checkSolved(){
        for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(user[r][c]!=solution[r][c])return;
        new AlertDialog.Builder(this).setTitle("🎉 אלוף!")
            .setMessage("פתרת את החידה!\nטעויות: "+mistakes+"  •  רמזים: "+hints)
            .setPositiveButton("חידה חדשה",(d,w)->newGame()).setNegativeButton("סגור",null).show();
    }

    class Board extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Board(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float size=Math.min(getWidth()-dp(4),getHeight()-dp(8))/9f;
            float left=(getWidth()-size*9)/2f,top=(getHeight()-size*9)/2f;
            p.setStyle(Paint.Style.FILL);
            for(int r=0;r<9;r++)for(int c=0;c<9;c++){
                int color=Color.rgb(24,29,50);
                if(selectedR>=0 && (r==selectedR||c==selectedC||(r/3==selectedR/3&&c/3==selectedC/3)))color=Color.rgb(37,46,76);
                if(r==selectedR&&c==selectedC)color=Color.rgb(84,67,145);
                if(wrong[r][c])color=Color.rgb(105,28,44);
                p.setColor(color);canvas.drawRect(left+c*size,top+r*size,left+(c+1)*size,top+(r+1)*size,p);
                if(user[r][c]!=0){
                    p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size*.50f);
                    p.setTypeface(fixed[r][c]?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
                    p.setColor(wrong[r][c]?Color.rgb(255,105,125):(fixed[r][c]?Color.WHITE:Color.rgb(145,226,255)));
                    canvas.drawText(String.valueOf(user[r][c]),left+(c+.5f)*size,top+(r+.68f)*size,p);
                }
            }
            p.setStyle(Paint.Style.STROKE);
            for(int i=0;i<=9;i++){
                p.setStrokeWidth(dp(i%3==0?3:1));p.setColor(i%3==0?Color.rgb(225,230,245):Color.rgb(83,91,122));
                canvas.drawLine(left,top+i*size,left+9*size,top+i*size,p);
                canvas.drawLine(left+i*size,top,left+i*size,top+9*size,p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float size=Math.min(getWidth()-dp(4),getHeight()-dp(8))/9f;
            float left=(getWidth()-size*9)/2f,top=(getHeight()-size*9)/2f;
            int c=(int)((e.getX()-left)/size),r=(int)((e.getY()-top)/size);
            if(r>=0&&r<9&&c>=0&&c<9){selectedR=r;selectedC=c;invalidate();}
            return true;
        }
    }

    void printBooklet(){
        PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
        pm.print("חוברת סודוקו קסום - 30 חידות",new PrintDocumentAdapter(){
            PrintedPdfDocument pdf;
            public void onLayout(PrintAttributes attrs,PrintAttributes old,LayoutResultCallback cb,Bundle extras){
                pdf=new PrintedPdfDocument(MainActivity.this,attrs);
                cb.onLayoutFinished(new PrintDocumentInfo.Builder("sudoku-30.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(30).build(),true);
            }
            public void onWrite(PageRange[] pages,ParcelFileDescriptor dest,android.os.CancellationSignal cancel,WriteResultCallback cb){
                try{
                    for(int k=0;k<30;k++){
                        int[][] puzzle=new int[9][9];generate(puzzle);
                        int holes=34+(k%4)*7,removed=0;
                        while(removed<holes){int r=rnd.nextInt(9),c=rnd.nextInt(9);if(puzzle[r][c]!=0){puzzle[r][c]=0;removed++;}}
                        android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,k).create());
                        Canvas c=page.getCanvas();drawPdfPage(c,puzzle,k+1,(k%4==0?"קל":k%4==1?"בינוני":k%4==2?"קשה":"מומחה"));
                        pdf.finishPage(page);
                    }
                    pdf.writeTo(dest);cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                }catch(Exception ex){cb.onWriteFailed(ex.toString());}finally{pdf.close();}
            }
        },null);
    }

    void drawPdfPage(Canvas c,int[][]g,int number,String level){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(30,35,60));
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(28);c.drawText("🧩 סודוקו קסום",297,55,p);
        p.setTextSize(14);c.drawText("חידה "+number+"   •   "+level,297,82,p);
        float size=420,x=87.5f,y=120,q=size/9;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawRect(x,y,x+size,y+size,p);
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<=9;i++){p.setStrokeWidth(i%3==0?3:1);p.setColor(i%3==0?Color.rgb(35,40,65):Color.GRAY);
            c.drawLine(x,y+i*q,x+size,y+i*q,p);c.drawLine(x+i*q,y,x+i*q,y+size,p);}
        p.setStyle(Paint.Style.FILL);p.setTextSize(q*.50f);p.setColor(Color.rgb(30,35,55));
        for(int r=0;r<9;r++)for(int col=0;col<9;col++)if(g[r][col]>0)c.drawText(String.valueOf(g[r][col]),x+(col+.5f)*q,y+(r+.68f)*q,p);
        p.setTextSize(11);p.setColor(Color.DKGRAY);c.drawText("זמן: __________    שם: ____________________",297,600,p);
        p.setTextSize(12);c.drawText("בהצלחה! ✨",297,770,p);
    }
}