"""Stage 2: pick the final common.txt and argo.txt.

  address = (common, common) ordered pair of distinct words x argo
  need P * (P - 1) * A >= Earth area / 9 m^2

argo:   words labelled K in argo_review.tsv, plus J words with a fix (the fixed spelling).
common: common_pool.txt plus argo words labelled O (ordinary words, once removed from the pool
        because Elistratov lists them), ranked from most to least usable; keep the top P,
        the smallest round number that covers the planet with a 1 % margin.

Ranking:
  tier 1: words seen in freq_ipm.tsv, by max ipm over the three sources;
  tier 2: unseen words, minus a few useless classes, by a character 4-gram model trained
          on tier 1 (how ordinary a word looks), with a penalty for length.

Inputs and outputs are in ~/proj/wtfay/data/final/.
"""
import collections, json, math, re
import pymorphy3

D = '/home/lagrunge/proj/wtfay/data/final/'
CELLS = 510_072_000e6 / 9
MARGIN = 1.01
def key(w): return w.lower().replace('ё', 'е')
def read_tsv(name):
    lines = [l.rstrip('\n') for l in open(D + name, encoding='utf8') if not l.startswith('#')]
    head = lines[0].split('\t')
    return [dict(zip(head, (l.split('\t') + [''] * len(head))[:len(head)])) for l in lines[1:]]

morph = pymorphy3.MorphAnalyzer()
BAD = {'Name', 'Surn', 'Patr', 'Geox', 'Orgn', 'Trad', 'Abbr'}

def is_lemma(w):
    """A dictionary form of a noun, infinitive, adjective or adverb, not a proper name."""
    return any(key(p.normal_form) == key(w) and p.tag.POS in ('NOUN', 'INFN', 'ADJF', 'ADVB')
               and not (BAD & p.tag.grammemes) for p in morph.parse(w))

# ---------------------------------------------------------------- argo
review = read_tsv('argo_review.tsv')
argo = {}
for r in review:
    w = r['fix'] or r['word']
    if r['label'] == 'K' or (r['label'] == 'J' and r['fix']):
        argo.setdefault(key(w), w)
labels = collections.Counter(r['label'] for r in review)

# ---------------------------------------------------------------- common pool
pool = {}
for w in open(D + 'common_pool.txt', encoding='utf8').read().split():
    pool.setdefault(key(w), w)
moved = 0
for r in review:
    if r['label'] == 'O' and key(r['word']) not in pool and len(r['word']) <= 15 and is_lemma(r['word']):
        pool[key(r['word'])] = r['word']
        moved += 1
for k in argo:
    pool.pop(k, None)

freq = {r['key']: max(float(r['subs']), float(r['rnc']), float(r['wordfreq'])) for r in read_tsv('freq_ipm.tsv')}

# ---------------------------------------------------------------- tier 2 helpers
def geox_stem(w):
    """кизлярский, бейрутец, ферганка: derived from a place name."""
    if not re.search(r'(ский|цкий|ец|анин|янин|анка|янка|чанка|инка)$', w):
        return None
    for cut in range(2, 7):
        for suf in ('', 'а', 'ь', 'о', 'и', 'ы', 'ск', 'я', 'е', 'ан'):
            s = w[:-cut] + suf
            if len(s) >= 3 and morph.word_is_known(s) and \
                    any(p.normal_form == s and 'Geox' in p.tag for p in morph.parse(s)):
                return s
    return None

def flag(w):
    if re.search(r'(щ|вш|ш)ийся$', w):
        return 'reflexive participle'
    if w.startswith('не') and key(w[2:]) in pool and not w.endswith(('ть', 'ться')):
        return 'не + existing word'
    g = geox_stem(w)
    return f'from place name {g}' if g else ''

N = 4
grams, ctx, alphabet = collections.Counter(), collections.Counter(), set()
for k in pool:
    p = freq.get(k, 0)
    if p > 0:
        s = '^' * (N - 1) + k + '$'
        wt = 1 + math.log10(1 + p * 100)
        alphabet.update(s)
        for i in range(N - 1, len(s)):
            grams[s[i - N + 1:i + 1]] += wt
            ctx[s[i - N + 1:i]] += wt

def plausibility(k):
    s = '^' * (N - 1) + k + '$'
    lp = sum(math.log((grams[s[i - N + 1:i + 1]] + 0.1) / (ctx[s[i - N + 1:i]] + 0.1 * len(alphabet)))
             for i in range(N - 1, len(s)))
    return lp / (len(s) - N + 1) - 0.08 * max(0, len(k) - 8)

ranked = []
for k, w in pool.items():
    p = freq.get(k, 0)
    ranked.append((w, 1, p, '') if p > 0 else (w, 2, plausibility(k), flag(w)))
ranked.sort(key=lambda r: (r[1], r[3] != '', -r[2], key(r[0])))

# ---------------------------------------------------------------- size
A = len(argo)
need = math.ceil((1 + math.sqrt(1 + 4 * CELLS * MARGIN / A)) / 2)
P = -(-need // 1000) * 1000
assert P <= sum(1 for r in ranked if not r[3]), 'not enough unflagged common words'
common = [r[0] for r in ranked[:P]]

# ---------------------------------------------------------------- write
def dump(name, words):
    open(D + name, 'w', encoding='utf8').write('\n'.join(sorted(words, key=key)) + '\n')
dump('common.txt', common)
dump('argo.txt', argo.values())
with open(D + 'common_cut.tsv', 'w', encoding='utf8') as o:
    o.write('# слова пула, не вошедшие в common.txt, от лучших к худшим; tier 2 = нет ни в одном частотном списке\n')
    o.write('word\ttier\tscore\tflag\n')
    for w, t, s, fl in ranked[P:]:
        o.write(f'{w}\t{t}\t{s:.4g}\t{fl}\n')
tiers = collections.Counter(r[1] for r in ranked[:P])
stats = {
    'common_words': P, 'argo_words': A,
    'common_with_frequency': tiers[1], 'common_without_frequency': tiers[2],
    'common_pool': len(pool), 'ordinary_words_moved_from_argo': moved,
    'argo_labels': dict(labels),
    'cells_3x3_earth': f'{CELLS:.4g}', 'combinations': f'{P * (P - 1) * A:.4g}',
    'coverage_ratio': round(P * (P - 1) * A / CELLS, 3),
}
json.dump(stats, open(D + 'stats.json', 'w'), ensure_ascii=False, indent=1)
print(json.dumps(stats, ensure_ascii=False, indent=1))
