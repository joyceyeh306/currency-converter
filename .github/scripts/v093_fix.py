from pathlib import Path
import re

# Build on the verified v0.9.2 runtime patch chain.
base = Path('.github/scripts/v092_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v092_fix.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.3 patch failed for {label}: matched {n}')
    s = s2


# Every visible label inside a keyboard key follows the same 70-100% key text scale.
old_fixed = '    private void drawFixedKey(Canvas c,float x1,float y1,float x2,float y2,String label,float size,String action,String value){ drawKey(c,x1,y1,x2,y2,label,size,action,value,false); }'
new_fixed = '    private void drawFixedKey(Canvas c,float x1,float y1,float x2,float y2,String label,float size,String action,String value){ drawKey(c,x1,y1,x2,y2,label,size,action,value,true); }'
if old_fixed not in s:
    raise SystemExit('v0.9.3 patch failed: drawFixedKey not found')
s = s.replace(old_fixed, new_fixed, 1)

# English keycaps visually follow Shift/Caps state, while action values remain lowercase.
new_english = r'''    private void drawEnglish(Canvas c){
        boolean upper=shift||caps;
        for(int i=0;i<10;i++){
            float x=18+i*116.4f;
            String label=upper?q1[i].toUpperCase(Locale.ROOT):q1[i];
            drawInputKey(c,x,0,x+96,122,label,17.68f,"LETTER",q1[i]);
        }
        for(int i=0;i<9;i++){
            float x=76+i*114.8f;
            String label=upper?q2[i].toUpperCase(Locale.ROOT):q2[i];
            drawInputKey(c,x,155,x+96,282,label,17.68f,"LETTER",q2[i]);
        }
        drawFixedKey(c,18,318,145,444,caps?"⇧•":"⇧",25,"SHIFT","");
        for(int i=0;i<7;i++){
            float x=172+i*116.2f;
            String label=upper?q3[i].toUpperCase(Locale.ROOT):q3[i];
            drawInputKey(c,x,318,x+96,444,label,17.68f,"LETTER",q3[i]);
        }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawBottom(c,"123","A");
    }
'''
sub1(r'    private void drawEnglish\(Canvas c\)\{.*?\n    \}\n(?=    private void drawZhuyin)', new_english, 'English Shift keycaps')

# The small mode mark inside the bottom key area follows the same scale.
s = s.replace('t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(842),sy(626),t);',
              't.setTextSize(dp(12*keyScale())); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(842),sy(626),t);', 1)

# Mic, emoji/kaomoji cells, category tabs and keyboard-return icons are interactive key content too.
s = s.replace('drawTextCentered(c,"🎙️",mic,22);', 'drawTextCentered(c,"🎙️",mic,22*keyScale());', 1)
s = s.replace('drawTextCentered(c,cats[i],r,13);', 'drawTextCentered(c,cats[i],r,13*keyScale());', 1)
s = s.replace('drawTextCentered(c,list[emojiOff+i],r,25);', 'drawTextCentered(c,list[emojiOff+i],r,25*keyScale());', 1)
s = s.replace('drawTextCentered(c,kaos[kaoOff+i],r,15);', 'drawTextCentered(c,kaos[kaoOff+i],r,15*keyScale());', 1)
s = s.replace('drawTextCentered(c,"⌨",back,22);', 'drawTextCentered(c,"⌨",back,22*keyScale());')

# A typo rescue is only appropriate when the entered Cangjie code has no normal candidate.
# Do not pad a valid complete code with nearby-key guesses just to fill the strip.
s = s.replace('if(out.size()<7) out.addAll(cangjieTypoCandidates(cjCode));',
              'if(out.isEmpty()) out.addAll(cangjieTypoCandidates(cjCode));', 1)

# Prediction candidates show only what is still to be committed. Example:
# 已輸入「非」 -> 顯示「常」而不是「非常」；已輸入「沒」 -> 顯示「關係」。
new_next = r'''    private String continuationOnly(String prev,String item){
        if(item==null||item.isEmpty()||prev==null||prev.isEmpty()||isPunctuation(item)) return item;
        if(item.length()>prev.length()&&item.startsWith(prev)) return item.substring(prev.length());
        String tail=prev.substring(prev.length()-1);
        if(item.length()>tail.length()&&item.startsWith(tail)) return item.substring(tail.length());
        return item;
    }
    private void addContinuations(LinkedHashSet<String> out,String prev,List<String> src){
        if(src==null) return;
        for(String item:src){
            String c=continuationOnly(prev,item);
            if(c!=null&&!c.isEmpty()) out.add(c);
        }
    }
    private List<String> withLearnedNext(List<String> base){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(learningEnabled()&&!lastCommitted.isEmpty()) addContinuations(out,lastCommitted,learnedNext(lastCommitted));
        addContinuations(out,lastCommitted,phraseSuggestions(lastCommitted));
        out.addAll(sentencePunctuation(lastCommitted));
        out.addAll(base);
        return new ArrayList<>(out);
    }
'''
old_next = '    private List<String> withLearnedNext(List<String> base){ LinkedHashSet<String> out=new LinkedHashSet<>(); if(learningEnabled()&&!lastCommitted.isEmpty()) out.addAll(learnedNext(lastCommitted)); out.addAll(phraseSuggestions(lastCommitted)); out.addAll(sentencePunctuation(lastCommitted)); out.addAll(base); return new ArrayList<>(out); }\n'
if old_next not in s:
    raise SystemExit('v0.9.3 patch failed: withLearnedNext source not found')
s = s.replace(old_next, new_next, 1)

# While actually composing Chinese, candidate characters always occupy fixed 1/7-width
# cells from the left. This prevents 2-4 exact candidates from stretching across the row.
new_candidates = r'''    private void drawCandidates(Canvas c){
        int h=candH(),rows=expanded?3:1;
        p.setColor(expanded?Color.rgb(232,233,237):BG);
        c.drawRect(0,0,getWidth(),h*rows,p);

        List<String> items=candidates();
        float arrowW=dp(46), usable=getWidth()-arrowW;
        int pageSize=7*rows;
        if(candOff>=items.size()) candOff=0;
        int n=Math.max(0,Math.min(pageSize,items.size()-candOff));
        boolean composing=(mode==Mode.CANGJIE&&!cjCode.isEmpty())||(mode==Mode.ZHUYIN&&!zyCode.isEmpty());

        if(composing){
            float cw=usable/7f;
            for(int i=0;i<n;i++){
                int row=i/7,col=i%7;
                RectF r=new RectF(col*cw,row*h,(col+1)*cw,(row+1)*h);
                String item=items.get(candOff+i);
                t.setTextSize(dp(22*candScale()));
                t.setTextAlign(Paint.Align.CENTER);
                t.setColor(Color.BLACK);
                android.graphics.Rect b=new android.graphics.Rect();
                t.getTextBounds(item,0,item.length(),b);
                c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                hits.add(new Hit(r,"CAND",item));
            }
        }else{
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
        }

        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h*rows);
        drawTextCentered(c,expanded?"⌃":"⌄",ar,21);
        hits.add(new Hit(ar,"EXPAND",""));
    }
'''
sub1(r'    private void drawCandidates\(Canvas c\)\{.*?\n    \}\n(?=\n    private void drawFooter)', new_candidates, 'fixed left composition candidates')

java_path.write_text(s, encoding='utf-8')

# v0.9.3 metadata.
gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 13', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.3'", g, count=1)
gradle.write_text(g, encoding='utf-8')

main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
m = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.3｜候選與按鍵一致性修正版");', m, count=1)
m = re.sub(
    r'intro\.setText\("[^"]*"\);',
    'intro.setText("所有鍵帽內文字、數字、符號與 Emoji／功能圖示現在都會跟著同一個 70%～100% 按鍵文字大小設定調整；English 按 Shift 後鍵面同步顯示大寫。常用詞候選只顯示尚未輸入的接續內容；倉頡／注音正在組字時，候選固定大小並由左向右排列，少量完整碼候選不再平均撐滿整列。誤觸字根提示只在沒有正常候選時出現。");',
    m,
    count=1,
)
main.write_text(m, encoding='utf-8')

assert 'drawKey(c,x1,y1,x2,y2,label,size,action,value,true); }' in s
assert 'boolean upper=shift||caps;' in s
assert 'continuationOnly' in s
assert 'boolean composing=' in s
assert 'if(out.isEmpty()) out.addAll(cangjieTypoCandidates(cjCode));' in s
assert '22*keyScale()' in s
assert "versionCode 13" in g and "versionName '0.9.3'" in g
print('v0.9.3 candidate and key consistency correction applied')
