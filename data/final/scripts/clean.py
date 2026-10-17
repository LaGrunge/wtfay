"""Build the final word lists for the 3-word address scheme.

  address = {common, common} (unordered pair) x argo
  need C(P,2) * A >= Earth area / 9 m^2

Outputs go to ~/proj/wtfay/data/final/.
"""
import json, re, collections, os, math
import pymorphy3

OUT = '/home/lagrunge/proj/wtfay/data/final'
os.makedirs(OUT, exist_ok=True)
m = pymorphy3.MorphAnalyzer()

known = set(l.strip().lower() for l in open('gh/russian_utf8.txt', encoding='utf-8'))
for l in open('russian-spellpack/russian-aot.dic', encoding='utf-8').read().split('\n')[1:]:
    known.add(l.split('/')[0].lower())

def key(w):
    """Lookup key: users will type е for ё."""
    return w.replace('ё', 'е')

V = 'аеёиоуыэюя'

# ---------------------------------------------------------------- argo
heads = json.load(open('eli_heads.json', encoding='utf-8'))
entries, refs, gram = heads['entries'], heads['refs'], heads['gram']

def fix_stress(w):
    """Elistratov marks stress with an accent that OCR reads as ё / doubled vowel."""
    w = w.replace('её', 'е').replace('ёё', 'ё').replace('ёе', 'е')
    # stress on и after a consonant comes out as й (ильйч); no native word has that
    w = re.sub(r'(?<=[бвгджзклмнпрстфхцчшщ])й', 'и', w)
    # there is no verb ending -ёть, it is a stressed -еть
    w = re.sub(r'ёть(ся)?$', lambda mm: 'еть' + (mm.group(1) or ''), w)
    # OCR reads the final ь of an infinitive as ы: думаты, киксануты
    if w not in known and re.search(r'[аеиуяо]ты(сь|ся)?$', w) and len(w) >= 6:
        w = re.sub(r'ты(сь|ся)?$', lambda mm: 'ть' + (mm.group(1) or ''), w)
    if 'ё' in w:
        plain = w.replace('ё', 'е')
        if plain in known and w not in known:
            return plain
    return w

FUNC = {'PREP', 'CONJ', 'PRCL', 'NPRO', 'NUMR', 'PRED', 'INTJ'}
PLAIN_POS = {'NOUN', 'ADJF', 'INFN', 'ADVB'}

def own_lemma(w):
    """True if w is a dictionary form (or a nominative plural noun - many slang heads are plural)."""
    for cand in {w, w.replace('е', 'ё')} if 'е' in w else {w}:
        for p in m.parse(cand):
            if p.normal_form == cand:
                return True
            if p.tag.POS == 'NOUN' and 'plur' in p.tag and 'nomn' in p.tag:
                return True
            # participles used as slang adjectives: упёртый, запиленный
            if p.tag.POS == 'PRTF' and {'masc', 'sing', 'nomn'} <= set(p.tag.grammemes):
                return True
    return False

def glued_phrase(w):
    """Two words of a phrase glued by OCR: литьводу, кидатьпонты, масонскиезамуты."""
    if w in known or len(w) < 8:
        return False
    for i in range(3, len(w) - 2):
        left, right = w[:i], w[i:]
        if (re.search(r'(ть|ться|ые|ие|ый|ий|ая|ой)$', left) or left in ('десять', 'не', 'да')) \
                and left in known and right in known and len(right) >= 3:
            return True
    return False

def garbage(w):
    if re.match(r'^[ьъыйё]', w) and not w.startswith('ё'): return 'starts with impossible letter'
    if re.search(r'(.)\1\1', w): return 'triple letter'
    if re.search(r'[ьъ][ьъ]|[аеёиоуыэюя][ьъ]|й[ьъ]', w): return 'impossible soft/hard sign'
    if not re.search('[%s]' % V, w): return 'no vowels'
    return None

argo, rejected = {}, []
all_heads = collections.OrderedDict()
for w in sorted(set(entries) | set(refs)):
    all_heads[w] = 'entry' if w in entries else 'ref'

for raw, kind in all_heads.items():
    w = fix_stress(raw)
    reason = None
    if not re.fullmatch(r'[а-яё]+(?:-[а-яё]+)?', w): reason = 'not a single cyrillic word'
    elif len(w) < 3: reason = 'too short'
    elif len(w) > 20: reason = 'too long'
    else:
        reason = garbage(w) or ('glued phrase' if glued_phrase(w) else None)
    if not reason and w in known:
        if raw not in gram and not own_lemma(w):
            reason = 'inflected form'
        elif (lambda ps: ps and ps <= FUNC)({p.tag.POS for p in m.parse(w) if p.normal_form == w}):
            reason = 'function word'
        elif kind == 'ref':
            # only mentioned inside a phrase ("БЫТЬ см. ..."): an ordinary word, not slang
            reason = 'ordinary word, only inside a phrase'
    if reason:
        rejected.append((raw, w, reason))
        continue
    k = key(w)
    if k in argo:
        rejected.append((raw, w, 'duplicate of ' + argo[k]))
        continue
    argo[k] = w

# ---------------------------------------------------------------- common
common_src = {}
for pos in ('noun', 'infn', 'adjf', 'advb'):
    for l in open(f'/home/lagrunge/proj/wtfay/data/{pos}.txt', encoding='utf-8'):
        w = l.strip()
        if w:
            common_src.setdefault(w, pos)

# obscene roots stay out of the "literary" words, the joke belongs to slot 3
OBSCENE = re.compile(r'х[уy][йеёяию]|пизд|п[ие]зд|(?:^|[аоеи]|[зсвдтп]ъ?|раз|под|от|до|на|за|у|вы|при|про|пере|недо)[её]б(?:а|у|ы|л|н|ё|и[тн]|ну|ок)|бля[дт]|муд[аои]|жоп|говн|срат|ссат|дроч|пидор|пидар|залуп|манд[ао]|гандон|гондон')

common, crej = {}, collections.Counter()
for w, pos in sorted(common_src.items()):
    if not re.fullmatch(r'[а-яё]+', w): crej['hyphen / non-letters'] += 1; continue
    if len(w) < 3: crej['too short'] += 1; continue
    if len(w) > 15: crej['too long (>15)'] += 1; continue
    if OBSCENE.search(w): crej['obscene root'] += 1; continue
    k = key(w)
    if k in common: crej['ё/е duplicate'] += 1; continue
    if k in argo: crej['also in argo'] += 1; continue
    common[k] = w

# ---------------------------------------------------------------- write + stats
P, A = len(common), len(argo)
cells = 510_072_000e6 / 9
combos = P * (P - 1) // 2 * A
def dump(name, words):
    open(f'{OUT}/{name}', 'w', encoding='utf-8').write('\n'.join(sorted(words, key=key)) + '\n')
dump('common.txt', common.values())
dump('argo.txt', argo.values())
with open(f'{OUT}/argo_rejected.tsv', 'w', encoding='utf-8') as f:
    f.write('ocr\tnormalized\treason\n')
    for r in rejected:
        f.write('\t'.join(r) + '\n')
stats = {
    'common_words': P, 'argo_words': A,
    'common_by_pos': dict(collections.Counter(common_src[w] for w in common.values())),
    'common_rejected': dict(crej),
    'argo_rejected': dict(collections.Counter(r[2] if not r[2].startswith('duplicate') else 'duplicate' for r in rejected)),
    'cells_3x3_earth': f'{cells:.4g}', 'combinations': f'{combos:.4g}',
    'coverage_ratio': round(combos / cells, 3),
    'min_argo_needed': math.ceil(cells / (P * (P - 1) // 2)),
}
json.dump(stats, open(f'{OUT}/stats.json', 'w'), ensure_ascii=False, indent=1)
print(json.dumps(stats, ensure_ascii=False, indent=1))
