from pathlib import Path
import re, subprocess

p=Path('/tmp/ajo/ajo_build_min/app/src/main/assets/index.html')
s=p.read_text()
m=re.search(r'<script>(.*)</script>',s,re.S)
if not m:
    raise SystemExit('inline script not found')
js=m.group(1)
start=js.index('function receiptCleanStoreValue')
end=js.index('function receiptOcrResult',start)

tests=r"""
const cases=[
 {name:'cube',lines:['CubeRefund Slip for Tax Refund','판매자 Retailer','상호 Name 롯데(백) 부산점','Payment Total KRW 123,200','V.A.T KRW 11,200','Net Refund value KRW 6,000','Passport No. 367126844'],amount:123200,store:'롯데',cat:'購物'},
 {name:'medi',lines:['메디스퀘어약국','날짜 2026-09-30 12:04:26','소계 273,000','부가세 과세 물품가액 246,364','부가세 24,636','택스프리 즉시환급액 -15,000','청구금액 256,000','신용카드 256,000','승인번호 666659'],amount:256000,store:'메디스퀘어',cat:'購物'},
 {name:'artbox',lines:['ARTBOX','2026-09-30 17:59:07','합계 14,500','받을금액 14,500','현금 14,500','P97270125260930130250'],amount:14500,store:'ARTBOX',cat:'購物'},
 {name:'olive1',lines:['CJ올리브영(주) 서면의시장','상품코드 8809891185375','판매계 103,500','택스리펀드 7,000','신용카드 96,500','결제금액 103,500','승인금액 96,500'],amount:96500,store:'올리브영',cat:'購物'},
 {name:'olive2',lines:['CJ올리브영(주) 니모 타운','상품코드 8809555960379','판매계 45,350','택스리펀드 2,000','신용카드 43,350','결제금액 45,350','승인금액 43,350'],amount:43350,store:'올리브영',cat:'購物'},
 {name:'blanket',lines:['매장명: 이불168','날짜 2026-09-30 12:32:46','Total Amount 346,000','Tax Refund 21,000','Cash 25,000','Credit Card 300,000','Receive Amount 325,000','카드번호 5520-0366-****-****'],amount:325000,store:'이불168',cat:'購物'}
];
for(const t of cases){
 const c=receiptAmountCandidates(t.lines,'KRW');
 if(!c.length||c[0].amount!==t.amount) throw new Error(t.name+' amount '+JSON.stringify(c));
 const st=guessReceiptStore(t.lines);
 if(!st.replace(/\\s+/g,'').includes(t.store.replace(/\\s+/g,''))) throw new Error(t.name+' store '+st);
 const cat=guessReceiptCategory(t.lines.join('\\n'));
 if(cat!==t.cat) throw new Error(t.name+' category '+cat);
}
console.log('receipt parser tests passed');
"""
out=Path('/tmp/receipt-parser-v118-test.js')
out.write_text(js[start:end]+'\n'+tests)
Path('/tmp/travel-index-v118.js').write_text(js)
subprocess.run(['node','--check','/tmp/travel-index-v118.js'],check=True)
subprocess.run(['node',str(out)],check=True)
