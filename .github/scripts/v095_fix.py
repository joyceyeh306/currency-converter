from pathlib import Path
import base64
import re

# Build on the verified v0.9.4 runtime patch chain, including the repaired settings note.
base = Path('.github/scripts/v094_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v094_fix.py', 'exec'), {'__name__': '__main__'})
note_fix = Path('.github/scripts/v094_note_fix.py').read_text(encoding='utf-8')
exec(compile(note_fix, '.github/scripts/v094_note_fix.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')

# ---------------------------------------------------------------------------
# 1) Built-in Taiwan Traditional Chinese oriented common-character baseline.
#    Personal learning remains the first sort key, so the user's own habits
#    can always overtake the built-in baseline.
# ---------------------------------------------------------------------------
common_chars = (
    '的一是不了人在有我他這個們中來上大為和國地到以說時要就出會可也你妳對生能而子那得於著下自之年過發後作裡用道行所然家種事成方多經麼去法學如都同現當沒動面起看定天分還進好小部其些主樣理心她本前開但因只從想實日軍者意無力它與長把機十民第公此已工使情明性知全三又關點正業外將兩高間由問很最重並物手應戰向頭文體政美相見被利什二等產或新己制身果加西斯月話合回特代內信表化老給世位次度門任常先海通教兒原東聲提立及比員解水名真論處走義各入幾口認條平系氣題活更別打女變四總何電數安少報才結反受目太量再感建務做接必場件計管期市直資命山金指許統區保至隊形社便空決治展馬科司五基眼書非則聽白界達光放強即像難且權思完設式色路記南品住告類求據程北邊張該交規萬取望覺術領共確傳師觀清今院讓識候帶導運笑飛風步改收根造言聯持組每車親極林服快辦議往元英士證近失轉夫令準布始怎呢存未遠叫台臺單影具字愛流備連調深商算質團集百需價花華城石級整府離況請技際約示復病息究線似官火精滿支視消越器容照須九增研寫稱企八功嗎包片史乎查輕易早曾除找裝廣顯吧阿標談吃圖念六引歷首醫局專費號盡另周較注語考落青隨選列紅響雖推參古眾構房半節土投某案黑維劃致陳足態護七興派孩驗責營星夠章音跟志底站嚴例防族供效續施留講型料終答緊黃絕察母段依批群項故按河米圍江害雙境客紀採舉父密低朝友訴止細願千值仍男錢網熱助育屬坐限船臉職速樂否剛毛狀率甚獨球般普怕校苦創假久錯承印晚蘭試股拿腦預誰益陽若哪微繼送急血驚傷素藥適波夜省初喜衛源食險待述陸習置居勞財環排福納歡雷警獲模充負雲停木游龍樹疑層冷洲略竟句室異激漢村策演簡卡判擔州靜退既衣您積餘痛檢差富靈協角佔配徵修皮勝降階審堅善媽讀啊超免壓銀買養懷執副亂追幫宣歲航優怪香田鐵控稅左右份穿藝背陣草腳概惡塊頓敢守酒島戶洋哥款靠評版寶座景顧弟登貨互付慢換聞危忙核暗姐介壞討麗良序升監臨亮露永呼味野架域沙掉括魚雜誤灣吉減編肯測屋跑夢散溫困漸救貴缺樓縣移娘朋畫班智亦耳恩短掌恐遺固席謝遇康幸均銷鐘詩藏趕劇票損忽巨端探湖錄葉春鄉附吸予禮港雨呀板庭婦歸睛飯額含順輸搖招婚脫補督油療旅材逐莫筆鮮詞擇尋廠睡博煙授諾倫岸賣載健堂旁宮喝借君禁陰園謀避抓榮姑孫逃牙束跳頂玉鎮雪午練迫爺篇肉嘴館遍凡礎洞牛寧紙諸訓私莊祖絲翻暴森握戲隱熟骨訪弱蒙歌店鬼軟典欲伙遭盤爸擴蓋弄雄穩憶刺擁徒染冰忍'
)
insert_after = '    private static final String RECENT_SEP = "\\u001F";\n'
if insert_after not in s:
    raise SystemExit('v0.9.5 patch failed: common rank insertion point not found')
s = s.replace(insert_after, insert_after + '    private static final String COMMON_CHARS = "' + common_chars + '";\n', 1)

learning_anchor = '    private boolean learningEnabled() { return settings.getBoolean("learning_enabled",true); }\n'
rank_methods = r'''
    private int builtinCommonRank(String text){
        if(text==null||text.isEmpty()||text.codePointCount(0,text.length())!=1) return 0;
        int i=COMMON_CHARS.indexOf(text);
        return i<0?0:(COMMON_CHARS.length()-i);
    }
    private int personalFrequency(String text){ return learningEnabled()?prefs.getInt("f_"+text,0):0; }
    private List<String> rankCommon(List<String> src){
        ArrayList<String> out=new ArrayList<>(new LinkedHashSet<>(src));
        out.sort((a,b)->{
            int fa=personalFrequency(a),fb=personalFrequency(b);
            if(fa!=fb) return Integer.compare(fb,fa);
            int ra=builtinCommonRank(a),rb=builtinCommonRank(b);
            if(ra!=rb) return Integer.compare(rb,ra);
            return 0;
        });
        return out;
    }
    private List<String> commonHome(){
        return Arrays.asList("的","我","是","了","不","在","有","妳","你","這","人","要","好","就","也","會","可","說","到","都","很","能","沒","想","看","來","去","還");
    }
    private List<String> topLearned(int limit){
        if(!learningEnabled()) return Collections.emptyList();
        ArrayList<String> out=new ArrayList<>();
        for(String k:prefs.getAll().keySet()){
            if(!k.startsWith("f_")||prefs.getInt(k,0)<=0) continue;
            String text=k.substring(2);
            if(text.isEmpty()||text.codePointCount(0,text.length())>4||isPunctuation(text)) continue;
            out.add(text);
        }
        out.sort(Comparator.comparingInt((String x)->-prefs.getInt("f_"+x,0)));
        if(out.size()>limit) return new ArrayList<>(out.subList(0,limit));
        return out;
    }
'''
if learning_anchor not in s:
    raise SystemExit('v0.9.5 patch failed: learning method insertion point not found')
s = s.replace(learning_anchor, learning_anchor + rank_methods, 1)

# Idle Chinese candidates: contextual continuation first, then personal global favourites,
# then a built-in common-character baseline.
old_next = r'''    private List<String> withLearnedNext(List<String> base){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(learningEnabled()&&!lastCommitted.isEmpty()) addContinuations(out,lastCommitted,learnedNext(lastCommitted));
        addContinuations(out,lastCommitted,phraseSuggestions(lastCommitted));
        out.addAll(sentencePunctuation(lastCommitted));
        out.addAll(base);
        return new ArrayList<>(out);
    }
'''
new_next = r'''    private List<String> withLearnedNext(List<String> base){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(learningEnabled()&&!lastCommitted.isEmpty()) addContinuations(out,lastCommitted,learnedNext(lastCommitted));
        addContinuations(out,lastCommitted,phraseSuggestions(lastCommitted));
        out.addAll(sentencePunctuation(lastCommitted));
        out.addAll(topLearned(14));
        out.addAll(base);
        return new ArrayList<>(out);
    }
'''
if old_next not in s:
    raise SystemExit('v0.9.5 patch failed: learned-next block not found')
s = s.replace(old_next,new_next,1)

# Base ranking applies even before the user has history. Personal frequency remains first.
old_boost = '    private List<String> boost(List<String> src){ ArrayList<String> out=new ArrayList<>(src); if(learningEnabled()) out.sort(Comparator.comparingInt((String s)->-prefs.getInt("f_"+s,0))); return out; }\n'
new_boost = '    private List<String> boost(List<String> src){ return rankCommon(src); }\n'
if old_boost not in s:
    raise SystemExit('v0.9.5 patch failed: boost block not found')
s = s.replace(old_boost,new_boost,1)

old_cj_rank = '''                if(learningEnabled()){\n                    ArrayList<String> ranked=new ArrayList<>(merged);\n                    ranked.sort(Comparator.comparingInt((String x)->-prefs.getInt("f_"+x,0)));\n                    merged.clear(); merged.addAll(ranked);\n                }\n'''
new_cj_rank = '''                ArrayList<String> ranked=new ArrayList<>(rankCommon(new ArrayList<>(merged)));\n                merged.clear(); merged.addAll(ranked);\n'''
if old_cj_rank not in s:
    raise SystemExit('v0.9.5 patch failed: v0.9.4 Cangjie rank block not found')
s = s.replace(old_cj_rank,new_cj_rank,1)

# Use the same built-in + personal ordering in Zhuyin, merging exact and prefix candidates.
old_zy = '''        if(mode==Mode.ZHUYIN){\n            if(zyCode.isEmpty()) return withLearnedNext(Arrays.asList("的","我","是","了","不","在","有","妳","你","這","人","要","好","就","也"));\n            LinkedHashSet<String> out=new LinkedHashSet<>(); List<String> ex=zy.get(zyCode); if(ex!=null) out.addAll(boost(ex)); ArrayList<String> pr=new ArrayList<>(); for(Map.Entry<String,List<String>> e:zy.entrySet()) if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) pr.addAll(e.getValue()); out.addAll(boost(pr)); return new ArrayList<>(out);\n        }\n'''
new_zy = '''        if(mode==Mode.ZHUYIN){\n            if(zyCode.isEmpty()) return withLearnedNext(commonHome());\n            LinkedHashSet<String> merged=new LinkedHashSet<>();\n            List<String> ex=zy.get(zyCode); if(ex!=null) merged.addAll(ex);\n            for(Map.Entry<String,List<String>> e:zy.entrySet()) if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) merged.addAll(e.getValue());\n            return rankCommon(new ArrayList<>(merged));\n        }\n'''
if old_zy not in s:
    raise SystemExit('v0.9.5 patch failed: Zhuyin candidate block not found')
s = s.replace(old_zy,new_zy,1)

s = s.replace('if(cjCode.isEmpty()) return withLearnedNext(Arrays.asList("的","嗎","為","成","過","變","法","我","妳","你","是","有","在","不","人"));',
              'if(cjCode.isEmpty()) return withLearnedNext(commonHome());',1)

# ---------------------------------------------------------------------------
# 2) Spacebar temporary-English gesture: DOWN toggles both directions.
# ---------------------------------------------------------------------------
old_space = '''        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){\n            float vth=Math.max(dp(36),downHit.r.height()*0.22f);\n            if(ady>vth&&ady>adx*1.35f){\n                if(dy<0&&mode!=Mode.ENGLISH){ feedback(); enterTemporaryEnglish(); downHit=null; return true; }\n                if(dy>0&&temporaryEnglish){ feedback(); exitTemporaryEnglish(); downHit=null; return true; }\n            }\n            float th=Math.max(dp(42),downHit.r.width()*0.16f);\n            if(adx>th&&adx>ady*1.5f){ feedback(); temporaryEnglish=false; if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; }\n        }\n'''
new_space = '''        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){\n            float vth=Math.max(dp(36),downHit.r.height()*0.22f);\n            if(dy>0&&ady>vth&&ady>adx*1.35f){\n                if(temporaryEnglish){ feedback(); exitTemporaryEnglish(); downHit=null; return true; }\n                if(mode!=Mode.ENGLISH){ feedback(); enterTemporaryEnglish(); downHit=null; return true; }\n            }\n            float th=Math.max(dp(42),downHit.r.width()*0.16f);\n            if(adx>th&&adx>ady*1.5f){ feedback(); temporaryEnglish=false; if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; }\n        }\n'''
if old_space not in s:
    raise SystemExit('v0.9.5 patch failed: temporary English gesture block not found')
s = s.replace(old_space,new_space,1)

# Explicitly make the lower-left mode/page labels follow key text scale.
s = s.replace('drawFixedKey(c,18,518,145,647,left,22,"NUM","");', 'drawInputKey(c,18,518,145,647,left,22,"NUM","");',1)
s = s.replace('drawFixedKey(c,18,518,145,647,s,s.length()>3?16:20,"MAIN","");', 'drawInputKey(c,18,518,145,647,s,s.length()>3?16:20,"MAIN","");',1)

# ---------------------------------------------------------------------------
# 3) More complete Emoji and kaomoji collections.
# ---------------------------------------------------------------------------
emoji_insert = r'''
    private final String[] emojiActivity = {
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳","🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️","🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖️","🏵️","🎗️","🎫","🎟️","🎪","🤹","🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🪘","🎷","🎺","🪗","🎸","🪕","🎻","🎲","♟️","🎯","🎳","🎮","🧩"
    };
    private final String[] emojiFlags = {
            "🇹🇼","🇯🇵","🇰🇷","🇺🇸","🇬🇧","🇫🇷","🇩🇪","🇮🇹","🇨🇭","🇦🇹","🇳🇴","🇸🇪","🇫🇮","🇩🇰","🇮🇸","🇨🇦","🇦🇺","🇳🇿","🇸🇬","🇹🇭","🇻🇳","🇵🇭","🇲🇾","🇮🇩","🇮🇳","🇪🇸","🇵🇹","🇳🇱","🇧🇪","🇨🇿","🇵🇱","🇬🇷","🇹🇷","🇦🇪","🇿🇦","🇧🇷","🇲🇽","🇦🇷","🇨🇱","🇵🇪","🇭🇰","🇲🇴","🇮🇪","🇱🇺","🇭🇺","🇭🇷","🇸🇮","🇸🇰","🇷🇴","🇧🇬","🇪🇪","🇱🇻","🇱🇹","🇺🇦","🇮🇱","🇪🇬","🇲🇦","🇰🇪","🇳🇵","🇲🇳","🏳️","🏴","🏁","🚩","🏳️‍🌈","🏳️‍⚧️"
    };
'''
anchor = '    private final String[] emojiSymbols = {\n'
if anchor not in s:
    raise SystemExit('v0.9.5 patch failed: emoji insertion point not found')
s = s.replace(anchor, emoji_insert + anchor,1)

old_emoji_items = '        if(emojiCategory==2) return emojiPeople; if(emojiCategory==3) return emojiAnimals; if(emojiCategory==4) return emojiFood; if(emojiCategory==5) return emojiTravel; if(emojiCategory==6) return emojiObjects; if(emojiCategory==7) return emojiSymbols; return emojiFaces;\n'
new_emoji_items = '        if(emojiCategory==2) return emojiPeople; if(emojiCategory==3) return emojiAnimals; if(emojiCategory==4) return emojiFood; if(emojiCategory==5) return emojiTravel; if(emojiCategory==6) return emojiObjects; if(emojiCategory==7) return emojiActivity; if(emojiCategory==8) return emojiSymbols; if(emojiCategory==9) return emojiFlags; return emojiFaces;\n'
if old_emoji_items not in s:
    raise SystemExit('v0.9.5 patch failed: emojiItems mapping not found')
s = s.replace(old_emoji_items,new_emoji_items,1)

s = s.replace('String[] cats={"最近","表情","人物","動物","食物","旅行","物品","符號"}; float catH=dp(40),catW=getWidth()/8f;',
              'String[] cats={"最近","表情","人物","動物","食物","旅行","物品","活動","符號","旗幟"}; float catH=dp(40),catW=getWidth()/10f;',1)
s = s.replace('for(int i=0;i<8;i++){ RectF r=new RectF(i*catW,0,(i+1)*catW,catH);',
              'for(int i=0;i<10;i++){ RectF r=new RectF(i*catW,0,(i+1)*catW,catH);',1)
s = s.replace('c.drawText("分類搜尋｜上下滑查看更多",dp(14),catH+dp(22),t);',
              'c.drawText("分類瀏覽｜上下滑查看更多",dp(14),catH+dp(22),t);',1)
s = s.replace('if(r.size()>42) r=new ArrayList<>(r.subList(0,42));', 'if(r.size()>70) r=new ArrayList<>(r.subList(0,70));',1)

# Add useful kaomoji without changing the existing page interaction.
old_kao_tail = '"(=^･ω･^=)","U・ᴥ・U","(•ө•)♡"\n    };'
new_kao_tail = '"(=^･ω･^=)","U・ᴥ・U","(•ө•)♡",\n            "(*≧ω≦)","(๑˃ᴗ˂)ﻭ","٩(｡•́‿•̀｡)۶","(っ´▽`)っ","(づ ◕‿◕ )づ","(づ￣ ³￣)づ","(♡˙︶˙♡)","(｡♥‿♥｡)","(灬♥ω♥灬)","(⁄ ⁄•⁄ω⁄•⁄ ⁄)","(*/ω＼*)","(*/▽＼*)",\n            "(⊙_⊙)","Σ(°△°|||)︴","(⊙﹏⊙)","(╯︵╰,)","(ಥ_ಥ)","(｡•́︿•̀｡)","(╬▔皿▔)","( `д´ )","(¬‿¬)","(¬_¬ )","(￣﹃￣)","(～﹃～)~zZ","(－_－) zzZ",\n            "(ง •̀ω•́)ง✧","ᕙ(⇀‸↼‶)ᕗ","ᕦ(ò_óˇ)ᕤ","(•̀ᴗ•́)و ̑̑","(｡•̀ᴗ-)✧","(￣▽￣)ノ","ヾ(•ω•`)o","ヾ(´･ω･｀)","(｡･∀･)ﾉﾞ","( ´ ▽ ` )ﾉ","(￣ω￣)","(=ↀωↀ=)","ฅ( ̳• ·̫ • ̳ฅ)","ʕ•́ᴥ•̀ʔっ","U ´꓃ ` U"\n    };'
if old_kao_tail not in s:
    raise SystemExit('v0.9.5 patch failed: kaomoji tail not found')
s = s.replace(old_kao_tail,new_kao_tail,1)

java_path.write_text(s,encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) v0.9.5 metadata + settings help.
# ---------------------------------------------------------------------------
gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 15', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.5'", g, count=1)
gradle.write_text(g,encoding='utf-8')

main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
m = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.5｜常用字與操作修正版");', m, count=1)
m = re.sub(r'intro\.setText\("[^"]*"\);',
           'intro.setText("新增台灣繁中取向的內建常用字基礎順位：還沒有個人學習資料時，常用字也會優先；使用一段時間後，個人使用頻率仍可超車內建順位。倉頡與注音候選都套用這套邏輯。空白鍵改為向下滑一次暫時切到 English，再向下滑一次回原中文模式；Emoji／顏文字內容也再擴充。語音輸入仍留到下一階段。");',
           m,count=1)
m = m.replace('所有鍵帽內文字、數字、符號、Emoji 與功能圖示同步調整；按鍵外框大小不變',
              '所有鍵帽內文字、數字、符號、Emoji 與功能圖示同步調整，包含左下角「倉頡／123／注音／ABC」；按鍵外框大小不變',1)
note_java = 'note.setText("倉頡版本：第三代。\\n空白鍵向左滑：倉頡 → English → 注音 → 倉頡；向右滑為反方向。正在組字時不切換。\\n空白鍵向下滑：從倉頡或注音暫時切到 English；在暫時 English 再向下滑一次，回到原本的倉頡或注音。\\n長按倉頡字根或注音符號：直接輸出鍵面文字，不進入組字。\\n候選列左右滑：翻頁；右側箭頭可展開更多候選。\\nEmoji／顏文字頁上下滑：瀏覽更多。\\n倉頡輸入第 2 個字根起，會先依個人使用頻率，再依內建常用字順位排列完整碼與前綴候選；注音候選也使用同一套常用順位。\\n沒有輸入字根／注音時，會先顯示情境接續與個人常用內容，再以內建常用字補足。\\n倉頡碼若疑似按錯相鄰字根，只在沒有正常候選時提供修正候選，不會自動改碼。\\n個人學習與最近使用資料只保存在這支手機內。");'
m2,n = re.subn(r'note\.setText\(".*?"\);', lambda _: note_java, m, count=1, flags=re.S)
if n!=1:
    raise SystemExit(f'v0.9.5 patch failed: settings note matched {n}')
main.write_text(m2,encoding='utf-8')

# ---------------------------------------------------------------------------
# 5) Selected green 倉 app icon with transparent outer background.
# ---------------------------------------------------------------------------
icon_b64 = 'iVBORw0KGgoAAAANSUhEUgAAAJAAAACQCAYAAADnRuK4AABnPUlEQVR42uX9ebxkZ1Uujj9rvXtX1Rm6T3cn6aQzAUkgJIEwhZmYgIJcQPACjSKIggoyOADKpNJpQRCQCMggCPEigpJmDqN4CeFCmGS4QDqGMWQeu/uMNez9rvX945131UkAUbm/3+HTpE+fOlV7WHuNz/Msws/uF0EBQIFzziEAwGn76afyzpfcQDhtp/6XncmP+3mXnJpee85e3ezK/GzcpP/uz9+zh3DafsIlpwYjUezeJz9LF+ln5l7t3s04NbtOl5yq2LtX3VP2/y8GtGcPA59mnLZT8bh9dtPXvfmp9WFmaWDWDsxz1R+AxvMkgzk2tidAzS1qYulp3TPGttRWBmiECBAwqVHhFmAwuYsrrKSiCrCpmKCiJKwwoiAmFSFYgIhVWQgArLDWtRFtLFtSATOpVaoAtBVQqXtvK+J8pQoTqRAZUQUDQAVAmCooK7F7HZRUWKwVFVJryRrRCmMBGqjVqsG4pWqlrdvJvDWjK07ZuYoH7m03vVbn7zb+ARTs3Sv/v2dAqoRzzjbA2VMnuOs1Tz2eBrg9hO9ASicS020VeiQBR6jookLniKiGog/mHhH5ewAQMYizM1H1f6H4d2JK36p7DTP7MycQAaoWqu7HJAARxbci4/6u/gUKjc4xHEuIKQoFqYIIAHHpFyj/K0EhECuAhkNXqIr7u6hV6FCBlplHpHpIrR4A4waArjKGv2MF322byaXXH7V8RfEgqhLOOcf8VxkT/ad7m9P2U36Cu17z1OPRwwMYeJCCz2DCCWSqLVxXABEgCqhCrUBF3fdQVYICLIRwI6EwpMEoAIBYFUoEpWRORLkh+xvPAJM/eXfjggGFm+7sKxgfASrxBUoAGMpCpOQ/RBRKUIgSMTk7C5eXVOP7EgAlAqkCAgiRO1whZ1oASKGqDDATh4eAAOMMnoihrYWM2w0l+aGI/hsYn2ps+9kbn/W273Y8k/5nGhL9VxjOEa9/xlE91kcS4ZcVcn/u1VuZjTOSxiqBWpCKsxIiKEAKAqm/uAjGFY9X/b9x9n38IVFmDeT/yd1DUgI4N6rkVTS8g2onY9Xs39UbFjlPJeJdVZ6J5B4qmimgimi25P2WhrfV9DZMClV/GP6omRSkRFCBkHsraE0VEyoGoLDjyZCBz4P4A2LxgWue+aYrU9oA/GcY0k/XgBSEfbs5GM7Ov3nqnevKPJ2Ax3K/OoKUYCctoDQhhoKIISBidf5ejMZbqSFdcE8dQC6M5EaRYlfHiqgMaQSAhEAKEhNsFOSe+vS2wfZEoapZFPIGA0BJvFmwAkoKhSopQKDcgLz3ohDeROIHkGp8P+cBXQij7NhVFeSNO3hSIm/M0eDc/wUHrQzDdWWUGXY0OkiCD7WMN17/9Dd/KXqk3ecLiPRnz4DO322C4RzzpmfdBbDPA/TR3O8NMG6ggol/IlnJRRGo9x/sbgqEVd19UArPCiu5m0oK6eQSwTuFy0FCqqQpMcquE4mPKwSA3Q0nSwCgwppfDHX3heCT5PwGFpdOAbAljW6EvGHlhgdA/HuGEFYcu7pQTV3PWT6ZiuxBitauCgFFO2IIBAqIKrTHvZpk3AqIP9SovOKGZ/ztF7r36mfBgAjnO6+z9Ne/sW2h138xMT3D1HVfx9YqoYX66O2fXQ33L7j23JuEC2vdUw323kNc9QISgrJSeFIRMoeQIrGqhqdfkbIWUkDI/Y3dx/qUyr1f54uFVOC9S0q4kRkG+fdwCXbmdbztxYRf3cMRw5QPgSHxUtU8gHZMZ/pmZZEtXi/ncCX7oaoSLCkZ6lWVbSYWivMmI9lz03Pecq03IvmPtgD+YwakGq6q7nr90x5FTK82g+pE2WgtAMtEhphDHuw/keNJK1tCfPioDEH+vsQ0Qgkg62xFvFtCum/BywCsPnuO1U3yGJoZbKrOaKpKSp5IO7VTrN6C0ZKGpMZ9Ekl6FpQA5RCMkZLk7FNU4+eEPC1PuYrLklsPlQccA6JKCocpsltAmPtVJePmWmnaP772D976zpgf/Qdyo5/cgMIH795tdv3c9leYfvVcUkBbGQNkvNMHiFzV4lyKv/khCVYin9CSZiGKlNR7ieKpd2WPNxBNf88vpH+6tTDa7nOWvbEiL48y4/E3N4s71LlqRIzgRVW8R4Al+DAWXGRwNkV1lwWnqSpA8+JSp8M15Z2LTr7lEzpihEScYlhWaZmorxXBtvZvzXcOPOeqv943/I+ENPqP5DvHvvq3dmi/+icz6D1ERpMJyLgCN3O26nsxCiFSnx9oylhTTqDkEkr2ESc7OBYi71lIsxwgWEb+bUw2XTJMNOU102eXfgGhKaPBqeRJO3UvFvlHRLOiTf3vUJmVd9Oxrj1Tlu+TEsSnz74Sy5Pr6ZJzRtCL56bRw7lTc50uhSjP9XoynHyORhu/ctVz/+Hqn9SI6Cc1nqP/6jePw3zvw1WvPl027IiIajD5e8/+qcySw3CjQlOOAWKOFQURZfag2U31BkQhrNGUF9GsiZM8ULfZl9+pvApCUb4TZ03HzhXq5iMprPicB1lWp90G52b5TZ5b+SdEWAEtPFDXgEKnS7veV4G82aG+3ZTOMvbRGu7RoJ1MvkcjPOyaP3rrt38SI/rxDGj3boN9++wxf/mEY7Fl4V9NXZ8s43YMMlXwJhrCE1yFMHWR8v4MczqpLK/oXm7yiTOm+jzh7TS7xiHsuBtLebEffy+V1DEfUe3cb40tBJ0xbaJbu4LZzZ9KW7IWBWKFV/aDovEUVpteHz1L+AATig1ODaXwEHfyutgiUdtSzX1r9SpdbR9y3QvedumPa0T8Y+U8+/bZI1/1xJ26ZeGjXFcny8SOQVy56KJlH81f/XgLfTAm6rp/RcpNderpJtIQ3NL1jBdAXYeYdEaIUYrWECygYwmUV3AzbIH8289+Dx9iJJyDxqoqeFHNwsesyorKtCedl4T3oc2Kl+6ZutcH44EWuVORdxlS10glgE2lFuPKmGN5gT921Kuechs8bp+Njccf4cv8yA3CT59NRz7iuHkzN/+xqt+7hzTOeIjLvr97mriM21R6IQJiNzh0a8kPMP2Qq2wW+oFBvFJxHqaxgssrLopXlbL8uJOT3JI7zqNnZmZEmxXaVBZWyZqJimBcdrHz8I6sVps+Kuq6ZH8NxTkTpRmnRh3PRYBBfFopXnpmiDRUV4cp4+e3/fxd373ywnOHABgXXaQ/HQ90zh6DvXuF6rk3m/nBfWTcjhhceUMozlzzB7b7yHXyCspCh4t9IQClp101lWzoVESxylOU7lyLxzpr6mmR0868OrFdRb5wVyQXWd5SygJv13WR65a6p52o42q6NjFt3DpVp1Hy3AQExABl7505wJiAF701AVE06fR+IK60lXE16N/Z9vgd7mef5h8lxbl1D3T+boNnvdHuevVTfqda7P+JDpsxgepwcKF3oRKeCypbXgQ/4aRiej3r+SdKT7l2E82icditpzvdaeqGQZq6QbRJaTwzYw71S3YzYgXXOSWd2SXPDISo03eidJOjF6I4Aimvjc+bwjhFjWZOrSzvKOsPURHvqNMICN+xNrbhuf4pc//ntNH6Cz/wGZy/22Dffv3Jk2jX69Ht5/72qXM9+iKB+2H8XBx5bIRxx4+6eY2qq067LrbMD6nzkGphRBSS4lis5X0bgsKqN7RoaYqsoUZa3mVC56rnOVanuahpnKH5UDWGE03no1p0mrU0t1Q0xJtdeiftJt9Flz7zpKykQppwKNPpUdlzSudH+fuzKASpnBXXSWknzVk3PPu8z4fC6ScLYQ5Cqn2W13FdL8CGdplmIYt8zKdyLBFOTCgzHv/aThc278bq1HgjFhTlTS5a/+IfW3WN38wRalZkxWvLmjIOUve9ZkPKfNZE/gcs07MG8hASUlUSN1INlZ5oFq38IeV3V7M+Ux7zY1fZD2e1+5z7ayA01Q4rX6mYnVVplmr4c+b8kqoym5pAr8OePYxTT9WfLAfy5dyu1/zWr1WDwYMwbidEMGnu4ycZPg+LYabTqAteSjsfpdmAIM7IPKgqeFrN43fKgtwNIU2WFVv/RBAQVKBq0f1USvlWijyhT5J3ffNEzvWsfJqa8CQcZmFWCdbPtzmWT/HBoAIKgqneVNG0CGbNnWunM8o40QxA120waufBmy4K0v3iNDgkBoiNNO3EzPXOOGLblU/C3r2C83ebHzeEEVRx7LnPGbRm5Zumrk8gixahR0/Bag0VDcAZlQ51hn/JYLS8rjHRy/A1+RhJghPLcoi8Ca3+ccj7JxTQhVmLP59FkR/9d+5ADEVwxuNtwgPFCoRaOmfj+waqUHEYkTTBz9BG5BEk6rP/HKqSjSWCyeZZUXRjUtoGZZ4rb+ASOrlfQAyEtkhC2AIex6BCUBFBpcZae9VknU8/+Pw3rxTT6Vv1QHv2GBBpWy8/uZrvnYhWGiWw5pNkJn+DtcwRc5ccQpRodik0w7SU8J1UkYm7gRYUn/Dkr7KoFdOsNB1PscVZuRYxA6F1FI6r6OFEY5UU8ywI1l86ASlE1R+fQhXsEy0hUgE5x5eAYpqnYMHArFL6vKxXFHIsyY2zc8+EqPhXTdczVV4oQn0ZlgOYreyMxPMhqyAwrDbVoH98PZBngkhxzh7zo3sgBeEtT612De3Xq7p3ioq26o2NwnDUP5keX0MRxgCBkqqKs3PyT3nIR1SLmiYVBkUekPV1/L8xc3z41SP7iDoXsegdcYmnCVDZ6GO4U7koQOwPQ7JkvVNukcOu+i4pwrSKPBxXg0UrUbf2J+Tjh3AMNDtbUZQ9tNxPqqbzy72+D7cOnOePCVok8fkMMBqoh0ZqDLkKFSgRVW3TXC1V/5Qbn/XG9Vn1Kc/MfQh61Kh5FPd7p6rVBkTMxBGMTiBAXANIJXvw4+iZiPKeC6EIC3nFFTyOZq15CpCI4LHC2F3SY62ZUcTRhqYBZMinVAXiPY1oMiKF+17Ewa9EFWIF4lrP6WkO7yUCiEAtkbp82eN8InDav6+oKwio07F2GO94CqIRepGOO12DGODJH4gg/XwKqpIl/6Lkuybu/fOyxAc+yYoXF72IoEzgrP/BSoJ2YuZ7x3I73A1Asecsc+shzJPaCOZpxC57VC0n3TEJFKIwTkgHm1cOmWeRWNm4RLmLqtRIT/A2YgNuw3EYRCHB0MT9ESis+JsDFDdBVCDi/qiIh+q5i2dF3c9UIGrde1t17Ayx7ne8V9PMuMVVuVACJHudiMBa/54WJK2SWOs/X9MxBFvIQml4XVHaB2/pQ6KKTiXbIYwG48gbqZpXtUrJ+/prR3kzM6EhMs8dRlBM5JhQz8Du3QbnXGRv2YA8xmfX6556RzL0czppLflmY8LH+ADESsRKeXOtnOfklUIHKmqU1ONFc8xxPkfKqw2xKdV16Ym7Ie54BFbcH3cDBdZaiG3JaksSPJBYstqSVUsqAmkFtm1JpCXnHax/ncP2iCqsFVhRuN9rSUSg1kKsdZ9nBVbC9y1EGhK0JAgGrNGInZFZf2zhZ+59VNwDks/QIFLO0bJZW2Y7iCA2Sr216MXCPaNOZ95HB8pmS9Kd2ymIlI1OxJrK3GPn/bbcCwTtVmTVDIMSEB7F/X5PR80YoCqnOAVH4ZoulJkBdbsMsaNa5BIeqlBOPzuzD0u+SBFvieGz2Xeh/Mm2HsYapvhuHOKJEhZK6jIdl7NqGMuSwgHWBOp6VI2rQaRggCAh3dTBXtXh0TWGWIc4BALe2ifCav3AwKhmPTH1mX8XX+3gPx5s7yuymU3Xbjkfxi1Kmt2YCJIMJSblD6cfs+SzJ3dcBeLNIYNbJQCWe3VFrX00gM9HBvEmBiT+MX8YbOjjpvpYYzJMMWzE38rhGll3VCnLZTSHF/hEkDW2jVUSIwH5PIetPy13opKiJkhsbMwQCGr9K8U1CUPogsS819mJWlJhhQgRux+pUJzQukpaI3Lbde6SJ4hjBdiUAxWoQPicK2IWC+wTMaNAZhI73HzAe286VxFvJJzCV3DNWd8owqUdEaiY52kMEVRWiC7I+8ZnPC3W1gKkD8ebn/oiPK1kyFbd8HX0G37zOG307jJpNY3Vw41PiJ1yWKoFDhGdQap0S+XYkhCo+EdXQosDRfmvEB8KARs6fgqwHwtaG5LH+JRSaDdDhSTHzeRYGoeHoTi5T7gf4rz976sdjZ5QfVVJRUuIZszTVITUJ+XuuvlGlbIaBZjJg/e9VyMGQYmV3VmwEqnRvPnoHwYQSRxrRIPPcqVijho9lWbkyASAE0Q6EwVqbWw1MrFatcTmDkdsyGk3Al/PcdSZB/o0A5C2obOqQbWIsU6I2IRmm4S5UnCRpDHGUjZ7yjsBkHTxEN1zp9kXgVuUqh0NXXWfBJMUT+6wGWF9PIKqoDIVeqZyQE0JrwHUeotiQk6lykcgKeRoQSGL7QpR59nisaUqMgRpZlN0ujXkHJJPbMS5PXbHw8w0FEUznkDVwvRqLA7mUKOGiB/FeKKIM1OjRec6n+9pJ0XgHDPVYc7lVZzGoVRKTbwLVUo8Nd+cbHlQ92V9dBaAr8dUZ0YIA4PuA2YoWY9dT+5YAjlOU+Yf4DxQoCKjVJTUEp8GK9Z5ogxlqORJDEqKyv2T2qxFTy75VQIMM9YmI4wmIxy/dSceddI96QHHn6wnbN+F7YMFVME4ixJXAwGt6BN22whdD6U6DWCO/94Fr3f7TZr/TtZlzofGBGzYBtcuH8D+G6/E//nhJfji1d/BTasr2DK3Ff1eRSLiSndHSPBUoXzwTLPxQ/4WGTaxHcW+8ev8uUJsi9jD8309zy0jAin7wOPuL0evzobvD+C1OG2/TjcSnSPRo17725/lXnV/nWgDQwwlWLEY9PuY7/dhEtAwS/QYVgSHVpbRtFYNm6wtYaGktGV+UXumgqgl5zZd5cTeewlCxWVdIqyWGrGwrUXTWiwPV3HHw4/D79/7YXjMKfelnVu2zQRe/JhQ3p8ZCZlLrr0cb/nyv+Ad37wIKzrG1t4ciAi1MTAcenDk/qsMdjWFD+SszCCQUSKlpm2wNtwAEYO50mDk1mO+ty3OU89UAaeXQojvHa2PxljbGKJio+Q8uFDFtW2by67v853xtLc06AxcCATd+ne/tWN+HZcRm8OVqCViam1Dx+08EkfvOAIqVlXF5XwEJZgQzcDMGDcNLr3q+1gbDtWQgajCsNLJx9wOSwuLaHzPQyG+RLax3xJKXGstFIKmtYAVbLRj3Ly6it+92y/i+fd7FJbmFwGAJm0Lsa06g0s9qC6FRqc5EJ0p3WZI7BkIxCxME9Et2Oat22VEbrKBMRUq40Lht669HH/0r/+AC3/4Ldzx6GNBRDDEqKoaxhsTiF00ZAM2zriIGOwZMIYZaxsb+PerL3eRU41aEQx6fZx6m9vQoO65aw9RAhER+y50iIqqNywv0w9vuNZFNDc9MaJ2yGpOufYP33JFyIMqjzgkYK/219rjierDIGqZmRo7wa4dh+H4w3bq+mhEAiGxLk9x03DOBpKKflXjxCOPxTeu+A6pFW3F4sSjjsXWwTxW1tcck1xd/8M18ZzHsVkn2IqglRakhJWNNWzvLeB9j/1jnH27O0Nsi0nTgAkqzRhibWyaEZF6lgeRageHPo1L7oCRZ5uAdkhdHZfdIQTNQDnF7HQada3OC5O4QVvjr8eddt0WH/3VF+BVX/gwXvuVD2O+6qHX64PbBkwMw+QNyRkPtQBzBebKIyNcaFicX8CJu47DpVf/ADUIIoITjzoSNTFWhxuuUyEWFMYp4kZQ5MoTOnL7Nm1sQ1Rd3TRSRZ1h0SrsAAAAAAAAAAAAAElFTkSuQmCC'
icon_path = Path('imeapp/app/src/main/res/drawable-nodpi/ic_launcher_ajo.png')
icon_path.parent.mkdir(parents=True,exist_ok=True)
icon_path.write_bytes(base64.b64decode(icon_b64))

manifest = Path('imeapp/app/src/main/AndroidManifest.xml')
ma = manifest.read_text(encoding='utf-8')
app_anchor = '        android:allowBackup="true"\n        android:label="阿喬輸入法"\n'
if app_anchor not in ma:
    raise SystemExit('v0.9.5 patch failed: manifest application anchor not found')
ma = ma.replace(app_anchor,
                '        android:allowBackup="true"\n        android:icon="@drawable/ic_launcher_ajo"\n        android:roundIcon="@drawable/ic_launcher_ajo"\n        android:label="阿喬輸入法"\n',1)
manifest.write_text(ma,encoding='utf-8')

assert 'COMMON_CHARS' in s and 'rankCommon' in s
assert 'dy>0&&ady>vth' in s and 'dy<0&&mode!=Mode.ENGLISH' not in s
assert 'emojiActivity' in s and 'emojiFlags' in s and 'getWidth()/10f' in s
assert 'drawInputKey(c,18,518,145,647,left,22' in s
assert 'drawInputKey(c,18,518,145,647,s,s.length()>3?16:20' in s
assert 'versionCode 15' in g and "versionName '0.9.5'" in g
assert icon_path.exists() and icon_path.stat().st_size>20000
assert 'android:icon="@drawable/ic_launcher_ajo"' in ma
print('v0.9.5 common ranking, down-toggle English, expanded emoji/kaomoji and selected app icon applied')
