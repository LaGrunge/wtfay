import re, glob, itertools, collections
# known forms from AOT, for repairing stress-mark OCR errors
aot=set()
for l in open('russian-spellpack/russian-aot.dic',encoding='utf-8').read().split('\n')[1:]:
    aot.add(l.split('/')[0].lower())
for l in open('gh/russian_utf8.txt',encoding='utf-8'):
    aot.add(l.strip().lower())
files=sorted(glob.glob('ros2/o*.txt'))
GRAM=r'(?:р\.|мн\.|ед\.|тв\.|кр\.|нескл|сов|несов|нареч|межд|предл|союз|част|прил|вводн|сниж|разг|спец|проф|уст|прош|буд|пов|вин|дат|род|пр\.|им\.|жен|муж|ср\.|м\.|ж\.|с\.)'
cands=[]  # (page,raw_token)
for f in files:
    page=int(re.findall(r'o(\d+)',f)[0])
    prev_open=False
    for line in open(f,encoding='utf-8'):
        s=line.strip()
        if not s: prev_open=False; continue
        # continuation lines of a wrapped entry start with grammar / punctuation / dash / parenthesis
        if re.match(r'^[-–—·(\[«"“,;.]',s) or re.match(r'^'+GRAM,s,re.I) or s[0].isdigit():
            continue
        # drop running headers (ALL CAPS) and page numbers
        letters=re.sub(r'[^А-Яа-яЁё]','',s)
        if letters and letters.upper()==letters and len(letters)>3: continue
        m=re.match(r'^([А-Яа-яЁё][А-Яа-яЁё\-]*)(?:\(([А-Яа-яЁё]+)\))?',s)
        if not m: continue
        head=m.group(1)
        rest=s[m.end():]
        # the entry is complete only when followed by , - ; ( end or ' и ' etc; require next char be punctuation/space/end
        cands.append((page,head.strip('-'),m.group(2),rest))
print('raw candidates',len(cands))
# stress-mark OCR confusions: stressed vowel read as another char
CONF={'б':'о','й':'и','ё':'е','ё':'ё','а':'а'}
ALT={'б':['о'],'й':['и','у'],'ё':['е','а','я']}
def variants(w):
    # positions of confusable chars
    pos=[i for i,c in enumerate(w) if c in 'бйё']
    if len(pos)>3: pos=pos[:3]
    opts=[[w[i]]+ALT[w[i]] for i in pos]
    seen=[]
    for combo in itertools.product(*opts):
        t=list(w)
        for ch,i in zip(combo,pos): t[i]=ch
        seen.append(''.join(t))
    return seen
fixed=collections.OrderedDict(); unknown=collections.OrderedDict()
for page,head,par,rest in cands:
    base=head.lower()
    forms=[base]
    if par:  # "натруживать(ся)" -> both
        forms=[base, base+par.lower()]
    for w in forms:
        if w in aot: fixed.setdefault(w,page); continue
        ok=None
        for v in variants(w):
            if v in aot: ok=v;break
        if ok: fixed.setdefault(ok,page)
        else: unknown.setdefault(w,page)
print('in AOT (after repair)',len(fixed),'not in AOT',len(unknown))
open('ros_in_aot.txt','w').write('\n'.join(fixed)+'\n')
open('ros_not_in_aot.txt','w').write('\n'.join(unknown)+'\n')
