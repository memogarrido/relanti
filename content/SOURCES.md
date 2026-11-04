# Sources for Relanti's permiso B content

Checked 4 November 2026. This is a summary of what each source offers, what we
may reuse, and how we use it. It is not legal advice; have a lawyer confirm the
reuse position before launch.

## The short answer

- **BOE laws are the source of every question.** Legal texts have no copyright
  in Spain (Ley de Propiedad Intelectual, art. 13), and the BOE's consolidated
  versions are free to reuse under its reuse conditions, including
  commercially. They come through an open API, one block per article, with
  every version dated.
- **DGT material is reading only.** The DGT's legal notice reserves all rights
  in its website content and allows reproduction only "para uso personal y
  privado". So we do not copy text, images or questions from the DGT manual,
  the study booklets or the sample exams. We read them to know what the exam
  covers, and we write our own questions from the law.
- **Facts are free; wording is not.** A speed limit or an alcohol rate is a
  fact from the law and anyone can teach it. A particular DGT question's
  wording, its distractors and its photos are someone's expression. Our items
  are written from scratch, in our own words, with our own distractors.

## BOE consolidated legislation

| Law | Short key | BOE id | Etapas it feeds |
|---|---|---|---|
| Reglamento General de Circulación (RD 1428/2003) | RGC | BOE-A-2003-23514 | SE PR VE AD PA AL FH PS |
| Ley sobre Tráfico (RDL 6/2015) | LSV | BOE-A-2015-11722 | PP VH FH |
| Reglamento General de Conductores (RD 818/2009) | RGCond | BOE-A-2009-9481 | PP EX |
| Reglamento General de Vehículos (RD 2822/1998) | RGV | BOE-A-1999-1826 | VH |

**API** (`https://www.boe.es/datosabiertos/api/legislacion-consolidada`):
`/id/{id}/metadatos` (last update), `/id/{id}/texto/indice` (one entry per
block with its own last-update date) and `/id/{id}/texto/bloque/{block}` (XML,
one `<version>` per amendment with publication and entry-into-force dates and
the amending norm). Block ids are the article anchors: `a48` is article 48,
`ai` / `aii` style ids are annexes. Documentation:
<https://www.boe.es/datosabiertos/documentos/APIconsolidada.pdf>.

**Reuse conditions** (<https://www.boe.es/informacion/aviso_legal/index.php#reutilizacion>):

1. Cite the source. For derived products like ours: "Basado en datos de la
   Agencia Estatal Boletín Oficial del Estado", with a link to
   <https://www.boe.es>.
2. Do not suggest the reuse is official.
3. For consolidated legislation, say expressly that it is a consolidated text
   for information only.

The pack carries all three in its `attribution` block, and the app should show
them in Yo › Fuentes and next to any article text it quotes.

**Timing.** Consolidated versions appear one to three days after an amendment is
published. When an amendment is published with a later entry-into-force date,
the API already lists the future version, and our fetcher records it as
`upcoming` so we can write a Novedad truco before the date.

## DGT material

| Material | What it is | Use in Relanti |
|---|---|---|
| Manual básico permiso B, lectura fácil (Dec 2023) | Simplified study book by topic | Topic order and coverage for the etapas. Not copied. |
| Cuadernillo de formación DGT B | Autoescuela training booklet | Coverage check. Not copied. |
| Sample exams on sede.dgt.gob.es | Official practice tests | Style reference only: 3 options, one correct, 30 questions, pass with 3 mistakes or fewer. Not copied. |
| Revista DGT | News and campaigns | Watch for Novedad trucos (rule changes in practice or still in parliament). Not copied; we link. |

DGT legal notice: <https://www.dgt.es/contenido/aviso-legal/>. It says the
portal's intellectual property belongs to the DGT unless stated otherwise, and
that unauthorised reproduction, distribution, commercialisation or
transformation, except for personal and private use, infringes those rights.
No open licence (such as CC BY or the general public-sector reuse licence) is
stated for the manual or the test questions. If we ever want to use DGT
images, such as photos from the exam, we have to ask the DGT for written
permission; until then, images are ours or come from the BOE (for example the
sign catalogue in the RGC's annex, which is part of the law).

Downloads for reading (not stored in the repo):

- Manual lectura fácil press note and PDF link:
  <https://www.dgt.es/comunicacion/notas-de-prensa/la-dgt-publica-el-primer-manual-basico-para-obtener-el-permiso-b-en-base-a-la-norma-de-lectura-facil>
- Cuadernillo DGT B:
  <https://www.dgt.es/export/sites/web-DGT/.galleries/downloads/nuestros_servicios/para-colaboradores-y-empresas/autoescuelas/CUADERNILLO-FORMACION-DGT-B.pdf>

## How a question stays tied to its source

1. Every item, fact and truco names its law and block (`RGC#a48`) and a
   readable reference (`art. 48.1.a) RGC`).
2. Facts can carry `quote:` lines. The build fails unless each quote appears
   word for word in the current BOE text of that article, so a number in our
   fact table is proven against the law on every build.
3. `content/boe/reviewed.json` stores the fingerprint and text of each cited
   article as it was when a person last checked the content against it. The
   build fails if the snapshot has moved on since, and the weekly watch opens
   an issue with the old and new text and the ids that cite it.
