from pathlib import Path
import re

# Start from the already-verified v0.9.1 behavioral correction.
base = Path('.github/scripts/v091_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v091_fix.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.2 patch failed for {label}: matched {n}')
    s = s2


# Make all ordinary number/punctuation/symbol keys use the same 17.68 visual base
# as Cangjie / English / Zhuyin. Page-switch and functional keys stay independent.
new_numbers = r'''    private void drawNumbers(Canvas c){
        for(int i=0;i<10;i++){
            float x=18+i*116.4f;
            String v=String.valueOf((i+1)%10);
            drawInputKey(c,x,0,x+96,122,v,17.68f,"NUMBER",v);
        }
        String[] row2=mode==Mode.ENGLISH?en2:cn2;
        for(int i=0;i<10;i++){
            float x=18+i*116.4f;
            drawInputKey(c,x,155,x+96,282,row2[i],17.68f,"PUNCT",row2[i]);
        }
        String[] row3=mode==Mode.ENGLISH?
                new String[]{"🔣",".",",","?","!","'"}:
                new String[]{"🔣","。","，","、","？","！","．"};
        float step=mode==Mode.ENGLISH?158f:136f, width=mode==Mode.ENGLISH?138f:116f;
        for(int i=0;i<row3.length;i++){
            float x=18+i*step;
            if(i==0) drawFixedKey(c,x,318,x+width,444,row3[i],21,"SYM",row3[i]);
            else drawInputKey(c,x,318,x+width,444,row3[i],17.68f,"PUNCT",row3[i]);
        }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawAltBottom(c);
    }
'''
sub1(r'    private void drawNumbers\(Canvas c\)\{.*?\n    \}\n(?=    private void drawSymbols)', new_numbers, 'number page text sizing')

new_symbols = r'''    private void drawSymbols(Canvas c){
        for(int i=0;i<10;i++){
            float x=18+i*116.4f;
            drawInputKey(c,x,0,x+96,122,sy1[i],17.68f,"PUNCT",sy1[i]);
        }
        String[] row2=mode==Mode.ENGLISH?syE2:syC2;
        float cell=IW/row2.length;
        for(int i=0;i<row2.length;i++){
            float x=i*cell+10;
            drawInputKey(c,x,155,x+cell-20,282,row2[i],17.68f,"PUNCT",row2[i]);
        }
        String[] row3=mode==Mode.ENGLISH?
                new String[]{"123",".",",","?","!","'"}:
                new String[]{"123","…","，","^_^","？","！","'"};
        float step=mode==Mode.ENGLISH?158f:136f,width=mode==Mode.ENGLISH?138f:116f;
        for(int i=0;i<row3.length;i++){
            float x=18+i*step;
            String action=i==0?"NUM":((mode!=Mode.ENGLISH&&i==3)?"KAO":"PUNCT");
            if(i==0) drawFixedKey(c,x,318,x+width,444,row3[i],20,"NUM",row3[i]);
            else drawInputKey(c,x,318,x+width,444,row3[i],17.68f,action,row3[i]);
        }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawAltBottom(c);
    }
'''
sub1(r'    private void drawSymbols\(Canvas c\)\{.*?\n    \}\n(?=    private void drawAltBottom)', new_symbols, 'symbol page text sizing')

# Candidate phrases get real visual separation. Single-character candidates stay dense,
# while 2-4+ character phrases receive proportionally wider cells. A light divider is
# only drawn around phrase candidates, so the normal single-character row stays clean.
new_candidates = r'''    private float candidateWeight(String item){
        int cps=item.codePointCount(0,item.length());
        if(cps<=1) return 1f;
        if(cps==2) return 1.45f;
        if(cps==3) return 1.8f;
        return 2.2f;
    }

    private void drawCandidates(Canvas c){
        int h=candH(),rows=expanded?3:1;
        p.setColor(expanded?Color.rgb(232,233,237):BG);
        c.drawRect(0,0,getWidth(),h*rows,p);

        List<String> items=candidates();
        float arrowW=dp(46), usable=getWidth()-arrowW;
        int pageSize=7*rows;
        if(candOff>=items.size()) candOff=0;
        int n=Math.max(0,Math.min(pageSize,items.size()-candOff));

        for(int row=0;row<rows;row++){
            int rowStart=row*7;
            int rowCount=Math.min(7,n-rowStart);
            if(rowCount<=0) break;

            float totalWeight=0f;
            for(int j=0;j<rowCount;j++) totalWeight+=candidateWeight(items.get(candOff+rowStart+j));
            float left=0f;

            for(int j=0;j<rowCount;j++){
                String item=items.get(candOff+rowStart+j);
                float w=(j==rowCount-1)?(usable-left):(usable*candidateWeight(item)/totalWeight);
                RectF r=new RectF(left,row*h,left+w,(row+1)*h);

                int cps=item.codePointCount(0,item.length());
                float sz=cps<=1?22f:(cps==2?19f:(cps==3?16.5f:14.5f));
                t.setTextSize(dp(sz*candScale()));
                float maxText=Math.max(dp(18),r.width()-dp(18));
                float measured=t.measureText(item);
                if(measured>maxText&&measured>0f) t.setTextSize(t.getTextSize()*(maxText/measured));
                t.setTextAlign(Paint.Align.CENTER);
                t.setColor(Color.BLACK);
                android.graphics.Rect b=new android.graphics.Rect();
                t.getTextBounds(item,0,item.length(),b);
                c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                hits.add(new Hit(r,"CAND",item));

                if(j<rowCount-1){
                    String next=items.get(candOff+rowStart+j+1);
                    boolean phrase=cps>1||next.codePointCount(0,next.length())>1;
                    if(phrase){
                        p.setColor(Color.rgb(198,199,204));
                        c.drawRect(r.right-dp(.5f),row*h+dp(9),r.right+dp(.5f),(row+1)*h-dp(9),p);
                    }
                }
                left+=w;
            }
        }

        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h*rows);
        drawTextCentered(c,expanded?"⌃":"⌄",ar,21);
        hits.add(new Hit(ar,"EXPAND",""));
    }
'''
sub1(r'    private void drawCandidates\(Canvas c\)\{.*?\n    \}\n(?=\n    private void drawFooter)', new_candidates, 'candidate phrase spacing')

java_path.write_text(s, encoding='utf-8')

# v0.9.2 metadata.
gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 12', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.2'", g, count=1)
gradle.write_text(g, encoding='utf-8')

main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
m = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.2｜一致性修正版");', m, count=1)
m = re.sub(
    r'intro\.setText\("[^"]*"\);',
    'intro.setText("修正數字與符號頁的基準字級，現在倉頡、English、注音、數字與一般符號真正共用同一套 70%～100% 文字大小；功能圖示維持獨立大小。常用詞候選改為依詞長自動分配寬度並增加淡分隔，避免前後詞黏在一起。其餘 v0.9.1 的詞句、誤觸提示、Emoji／顏文字功能全部保留。");',
    m,
    count=1,
)
main.write_text(m, encoding='utf-8')

assert 'drawInputKey(c,x,0,x+96,122,v,17.68f' in s
assert 'candidateWeight' in s
assert 'Color.rgb(198,199,204)' in s
assert "versionCode 12" in g and "versionName '0.9.2'" in g
print('v0.9.2 consistency and candidate spacing correction applied')
