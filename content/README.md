# Relanti content

Every question in Relanti is written by us from Spanish traffic law, cites the
BOE article it comes from, and is compiled into one JSON pack the apps load.
Licences and what we may reuse are in [SOURCES.md](SOURCES.md).

```
content/
  src/
    sources.yaml      the 4 laws (RGC, LSV, RGCond, RGV) and DGT reading references
    etapas.yaml       12 etapas and their tramos, in road order
    facts/*.yaml      numeric values table: every speed, rate, distance and point value
    templates/*.yaml  question templates over facts; each row is one item with a fixed id
    items/*.yaml      hand-written items, one file per etapa
    trucos/*.yaml     Turbo's tips (reading, memory, novedad), max two lines
    ids.lock          every item id ever built; written by the build
  boe/
    <BOE id>/index.json, blocks/<block>.json   snapshot of the consolidated laws
    reviewed.json     fingerprint + text of each cited article when we last checked it
  packs/relanti-b.json   compiled pack (committed; CI fails if stale)
  pipeline/           Python: boe.py (API client), compile.py, watch.py
  tests/
```

## Commands

Run from `content/` with Python 3.11+ and `pip install -r requirements.txt`.

| Command | What it does |
|---|---|
| `python -m pipeline build` | Validate everything and write `packs/relanti-b.json` and `src/ids.lock` |
| `python -m pipeline check` | Same validation, fails if the committed pack is stale (CI) |
| `python -m pipeline sync` | Refresh `boe/` from the BOE API (only blocks whose date changed; `--full` for all) |
| `python -m pipeline watch --report r.md` | Compare the live BOE with `reviewed.json` and write a report |
| `python -m pipeline accept RGC#a48` | Record the current snapshot of an article as reviewed (`--all` for every cited one) |
| `python -m unittest discover -s tests -v` | Pipeline tests |

`boe.es` may be blocked in some sandboxes. The `Content · BOE snapshot sync`
workflow runs `sync` in GitHub Actions and commits the result; touch
`boe/.sync-request` to trigger it on a branch.

## Writing an item

```yaml
- id: VE-103-08                 # tramo + two digits; never renumber or reuse
  question: Fuera de poblado, un camión de más de 3.500 kg ... ¿Qué separación mínima debe dejar?
  correct: "{fact:distancia.pesados}"
  wrong: ["25 m", "100 m"]      # exactly two; plausible, our own
  explanation: Los de más de 3.500 kg o 10 m dejan {fact:distancia.pesados} ...   # max 220 chars
  source: {law: RGC, block: a54, ref: art. 54.2 RGC}
  truco: T-VE-103               # optional
```

- Numbers come from facts with `{fact:id}`, never typed into the text, so a
  rule change is one cell.
- A fact needs a `quote:` that appears word for word in the cited article and
  contains its value. The build checks both against `boe/`.
- An item's `source.quote` is optional; use it when the answer hinges on one
  phrase of the article.
- The answer position (A, B or C) is chosen from a hash of the id, so it is
  stable across builds and spread evenly. `keep_order: true` keeps `correct`
  first.
- Write in our own words. Don't copy DGT questions, their distractors or their
  photos (see SOURCES.md).

## Pack format (schemaVersion 1)

Agreed with the app; the KMP core reads it.

```json
{ "format": "relanti-pack", "schemaVersion": 1, "contentVersion": 2, "contentHash": "…",
  "licence": "B", "placeholder": false,
  "attribution": { "boe": "Basado en datos de la Agencia Estatal Boletín Oficial del Estado",
                   "boeUrl": "https://www.boe.es", "consolidatedNotice": "…" },
  "sources": { "RGC": { "boe": "BOE-A-2003-23514", "title": "…", "url": "…" } },
  "etapas": [{ "code": "SE", "order": 1, "name": "Villaseñal", "topic": "Señales", "landscape": "ciudad", "free": true }],
  "tramos": [{ "code": "SE-101", "etapa": "SE", "order": 1, "title": "…", "truco": "T-SE-101" }],
  "trucos": [{ "id": "T-SE-101", "kind": "memory", "title": "…", "body": "…", "facts": null, "source": { … } }],
  "facts":  [{ "id": "vmax.turismo.autopista", "value": 120, "unit": "km/h", "label": "…", "group": "vmax-fuera", "source": { … } }],
  "items":  [{ "id": "SE-101-01", "tramo": "SE-101", "question": "…", "options": ["…", "…", "…"], "answer": 0,
               "explanation": "…", "image": null, "novedad": false, "truco": null,
               "template": null, "facts": null, "rev": "3f9c…",
               "source": { "law": "RGC", "boe": "BOE-A-2003-23514", "block": "a133", "ref": "art. 133.1 RGC",
                           "url": "https://www.boe.es/buscar/act.php?id=BOE-A-2003-23514#a133",
                           "fingerprint": "sha256:…", "checkedOn": "2026-11-04", "note": null } }] }
```

- `answer` is a 0-based index into `options`.
- `rev` changes whenever the question, options or answer change; the app can
  treat a new `rev` as a new item for the Retrovisor while keeping earned
  tickets.
- Only tramos with at least 8 items are in `tramos`. All 12 etapas are always
  listed.
- `contentVersion` goes up by one on every build whose content differs from
  the committed pack.
- The app must show `attribution` somewhere (Yo › Fuentes) to meet the BOE's
  reuse conditions.

## When the law changes

1. Every Monday `Content · weekly BOE change check` compares each cited
   article with `reviewed.json`. If one changed, or an amendment is published
   with a later entry-into-force date, it opens (or comments on) an issue
   labelled `boe-change` with a diff and the affected item, fact and truco ids.
2. In a PR: run `sync`, update the facts and items (for a changed number this
   is usually one fact and its quote), then `accept LAW#block` and `build`.
   Until you accept, the build fails for content that cites the changed
   article, so nothing ships against a law we haven't read.
3. CI validates the PR; a person approves it.
