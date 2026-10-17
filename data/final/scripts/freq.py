"""Lemma frequencies for every word of common_pool.txt and argo_ocr.txt -> freq_ipm.tsv.

subs:     OpenSubtitles 2018 word forms, ru_full.txt from hermitdave/FrequencyWords
rnc:      Lyashevskaya & Sharov 2011, freqrnc2011.csv from dict.ruslang.ru (lemmas already)
wordfreq: `wordfreq` package, Russian 'large' list (wiki, subtitles, news, books, web, twitter, reddit)

Word forms are lemmatised with pymorphy3; a form's count is split evenly between
the distinct lemmas it may belong to. Verb forms and participles also count
towards their infinitive. Values are instances per million.
"""
import collections, csv
import pymorphy3
from wordfreq import get_frequency_dict

FINAL = '/home/lagrunge/proj/wtfay/data/final/'
def key(w): return w.lower().replace('ё', 'е')

wanted = {key(w) for f in ('common_pool.txt', 'argo_ocr.txt')
          for w in open(FINAL + f, encoding='utf8').read().split()}
morph = pymorphy3.MorphAnalyzer()

def lemmas(form):
    parses = morph.parse(form)
    out = {key(p.normal_form) for p in parses}
    for p in parses:
        if p.tag.POS in ('PRTF', 'PRTS', 'GRND', 'VERB'):
            inf = p.inflect({'INFN'})
            if inf:
                out.add(key(inf.word))
    out &= wanted
    if key(form) in wanted:
        out.add(key(form))
    return out

def by_lemma(forms):
    """forms: iterable of (form, ipm)."""
    acc = collections.Counter()
    for form, ipm in forms:
        if not form.isalpha():
            continue
        ls = lemmas(form)
        for l in ls:
            acc[l] += ipm / len(ls)
    return acc

pairs = [l.split() for l in open('ru_full.txt', encoding='utf8')]
total = sum(int(n) for _, n in pairs)
subs = by_lemma((f, int(n) * 1e6 / total) for f, n in pairs if int(n) >= 2)

rnc = collections.Counter()
with open('freqrnc2011.csv', encoding='utf8') as f:
    for row in csv.DictReader(f, delimiter='\t'):
        rnc[key(row['Lemma'])] += float(row['Freq(ipm)'])

wf = by_lemma((f, p * 1e6) for f, p in get_frequency_dict('ru', 'large').items())

with open(FINAL + 'freq_ipm.tsv', 'w', encoding='utf8') as o:
    o.write('# lemma frequency, instances per million: OpenSubtitles 2018, НКРЯ (Ляшевская–Шаров 2011), wordfreq large; key: ё → е\n')
    o.write('key\tsubs\trnc\twordfreq\n')
    for w in sorted(wanted):
        s, r, x = round(subs[w], 4), round(rnc[w], 2), round(wf[w], 4)
        if s or r or x:
            o.write(f'{w}\t{s:g}\t{r:g}\t{x:g}\n')
