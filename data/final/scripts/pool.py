import pymorphy3, itertools, collections, random, re, time
t = time.time()
m = pymorphy3.MorphAnalyzer()

# all known word forms: danakt (1.5M) + AOT
known = set(l.strip().lower() for l in open('gh/russian_utf8.txt', encoding='utf-8'))
for l in open('russian-spellpack/russian-aot.dic', encoding='utf-8').read().split('\n')[1:]:
    known.add(l.split('/')[0].lower())

# stress marks in the PDF scan get OCR'd as other letters
ALT = {'б': ['о'], 'й': ['и', 'у'], 'ё': ['е', 'а', 'я']}

def fix(w):
    if w in known:
        return w
    pos = [i for i, c in enumerate(w) if c in 'бйё'][:4]
    opts = [[w[i]] + ALT[w[i]] for i in pos]
    for combo in itertools.product(*opts):
        t_ = list(w)
        for ch, i in zip(combo, pos):
            t_[i] = ch
        v = ''.join(t_)
        if v in known:
            return v
    return None

BAD = ('Abbr', 'Name', 'Surn', 'Patr', 'Geox', 'Orgn', 'Trad')

def classify(w):
    tags = set()
    for p in m.parse(w):
        if (p.normal_form == w and p.tag.POS in ('NOUN', 'ADJF', 'INFN')
                and p.is_known and not any(b in p.tag for b in BAD)):
            tags.add(p.tag.POS)
    return tags

cands = set(known)
pdf = set()
for f in ('ros_in_aot.txt', 'ros_not_in_aot.txt'):
    for l in open(f, encoding='utf-8'):
        w = l.strip()
        if w:
            v = fix(w)
            if v:
                pdf.add(v)
print('pdf words fixed', len(pdf), round(time.time() - t), flush=True)

pool = collections.defaultdict(set)
pdf_only = collections.Counter()
for w in cands | pdf:
    if len(w) < 3 or re.search(r'[^а-яё\-]', w) or w.startswith('-') or w.endswith('-'):
        continue
    tg = classify(w)
    for x in tg:
        pool[x].add(w)
        if w in pdf and w not in cands:
            pdf_only[x] += 1
print({k: len(v) for k, v in pool.items()}, 'pdf_only', dict(pdf_only), round(time.time() - t), flush=True)

for k, v in pool.items():
    open(f'/home/lagrunge/proj/wtfay/data/{k.lower()}.txt', 'w', encoding='utf-8').write('\n'.join(sorted(v)) + '\n')
random.seed(4)
for k, v in pool.items():
    print(k, ' '.join(random.sample(sorted(v), 25)))
