"""Extract headwords from the OCR of Elistratov's "Словарь русского арго".

Entry lines start with one or more ALL-CAPS headwords separated by grammar
(endings like -а, -ая; labels м., ж., сов. ...), then either a definition or
"см. <TARGET>" for a reference. Hyphenated line breaks are joined first, which
is what produced fragments like "ально" / "тиль" in the first pass.
"""
import re, glob, collections, json

pages = sorted(glob.glob('ocr/p*.txt'), key=lambda f: int(re.findall(r'p(\d+)', f)[0]))
FRONT_MATTER_LAST_PAGE = 4   # preface pages before the dictionary body

raw = []
for f in pages:
    if int(re.findall(r'p(\d+)', f)[0]) <= FRONT_MATTER_LAST_PAGE:
        continue
    raw.extend(l.rstrip() for l in open(f, encoding='utf-8'))

# join hyphenated line breaks: "МОНОПЕНИСУ-" + "АЛЬНО, нареч." -> one line
lines = []
for l in raw:
    if lines and re.search(r'[А-Яа-яЁё][-¬]$', lines[-1]) and l and re.match(r'[А-Яа-яЁё]', l):
        lines[-1] = lines[-1][:-1] + l
    else:
        lines.append(l)

CAP = r'[А-ЯЁ][А-ЯЁ\-]*[А-ЯЁ]'
GRAM_LABEL = r'(?:м|ж|ср|мн|ед|ч|сов|несов|нареч|межд|предл|союз|част|прил|вводн|нескл|безл|числ|мест|л|и)\.?'
# a token in the headword zone: CAPS word (+ optional homonym digit/garbage), grammar ending, label, punctuation
tok_re = re.compile(r'\s*(?:(?P<cap>%s)[\d\*\^!?°]*|(?P<end>[-—–][а-яёА-ЯЁ\d]{0,8}(?:\([а-яё]+\))?)|(?P<lab>%s(?=[\s,;.]|$))|(?P<p>[,;]))' % (CAP, GRAM_LABEL))

entries = collections.Counter()   # headword -> count (entry heads)
refs = collections.Counter()      # headwords of "см." references
multi = collections.Counter()
gram_heads = collections.Counter()  # heads directly followed by an inflection ending: true dictionary forms
for l in lines:
    s = l.strip()
    if not re.match(r'^[^\wА-Яа-яЁё]{0,2}[А-ЯЁ]', s):
        continue
    s = re.sub(r'^[^\wА-Яа-яЁё]{1,2}', '', s)
    heads, pos, saw_gram, phrase = [], 0, False, []
    last_head = None   # single-word head waiting for its inflection ending ("ЛИМИТА, -ы")
    while pos < len(s):
        m = tok_re.match(s, pos)
        if not m or m.end() == pos:
            break
        if m.group('cap'):
            phrase.append(m.group('cap'))
            last_head = None
        else:
            if phrase:
                heads.append(' '.join(phrase))
                last_head = phrase[0] if len(phrase) == 1 else None
                phrase = []
            if m.group('end') or m.group('lab'):
                saw_gram = True
                if m.group('end') and last_head:
                    gram_heads[last_head.strip('-').lower()] += 1
                last_head = None
        pos = m.end()
    if phrase:
        heads.append(' '.join(phrase))
    rest = s[pos:].lstrip()
    is_ref = rest.startswith('см.') or rest.startswith('см ')
    # running page headers are a lone 3-4 letter CAPS token with nothing after
    if not heads or (len(heads) == 1 and not rest and not saw_gram):
        continue
    # require some evidence this is a dictionary entry: grammar, reference, or a definition starting with a capital
    if not (saw_gram or is_ref or re.match(r'^[А-ЯЁ][а-яё]', rest)):
        continue
    for h in heads:
        h = h.strip('-').lower()
        if len(h) < 2:
            continue
        if ' ' in h:
            multi[h] += 1
        elif is_ref:
            refs[h] += 1
        else:
            entries[h] += 1

single = set(entries) | set(refs)
print('entry heads', len(entries), 'ref-only', len(set(refs) - set(entries)),
      'single total', len(single), 'multi', len(multi))
json.dump({'entries': entries, 'refs': refs, 'multi': multi, 'gram': gram_heads},
          open('eli_heads.json', 'w', encoding='utf-8'), ensure_ascii=False)
