# The identity file

An identity is a character Treelune's AI can speak as: its voice (how it speaks), its mind (how it thinks), its portrait (who it is), and its resources, texts it reads when it needs them (a vocabulary, a list of references). The AI still does the work in the app; the identity gives its answers their voice.

An identity file holds one identity, in one or several languages. Anyone can write one, by hand or with a tool of their own: the app imports it in Settings › Identities › Import, whoever made it. An AI working in the app through its MCP connector imports it too, with the tool `import_identity` (the file's text, the language it speaks, and `replace: true` to replace an identity already there), under the access and the protections the person set for that connector. This document is the whole contract. Its JSON Schema is [`schemas/identity-file.schema.json`](schemas/identity-file.schema.json), to check a file before sharing it.

## An example

```json
{
  "format": 2,
  "id": "drawing-teacher",
  "name": "Drawing teacher",
  "published_at": "2026-10-16",
  "languages": {
    "en": {
      "voice": { "content": "Short sentences. Encouraging, never vague: every remark points at a place in the drawing." },
      "mind": { "content": "Looks at proportions first, then values, then line. One correction at a time." },
      "portrait": { "content": "Has taught life drawing for twenty years." },
      "resources": [
        { "key": "vocabulary", "title": "Vocabulary", "description": "When a term of drawing needs defining", "content": "Value: how light or dark…" }
      ]
    },
    "fr": {
      "voice": { "content": "Des phrases courtes. Encourageant, jamais vague : chaque remarque désigne un endroit du dessin." }
    }
  }
}
```

## The fields

The file is a JSON object, in UTF-8.

| Field | Required | What it is |
|---|---|---|
| `format` | yes | `2`, the version of this contract. A file of another format is refused, naming it. |
| `id` | yes | What the identity is known by: lower-case ASCII letters, digits and hyphens (`drawing-teacher`). Importing a file whose `id` is already in the app replaces that identity whole. |
| `name` | yes | Its name, as shown; 100 characters at most. |
| `published_at` | no | The day this version was published, `YYYY-MM-DD`. The app shows it, and warns when a file is older than the one in place. |
| `languages` | yes | At least one language, by its code: two or three lower-case letters, and a region if needed (`fr`, `en`, `pt-BR`). |

A language holds any of these, and at least one of its three texts:

| Field | What it is |
|---|---|
| `voice` | `{ "content": "…" }`: how it speaks |
| `mind` | `{ "content": "…" }`: how it thinks |
| `portrait` | `{ "content": "…" }`: who it is |
| `resources` | A list, in the order shown: each `{ "key", "title", "description", "content" }`. `key` names the resource, 120 characters at most, unique in its language; `description` says what it is and when to read it, which the AI is told; `content` is the text read. |

Every text is non-empty. The texts are written as Markdown is: the app lays out headings, lists and emphasis.

## How the app reads it

- **Whole or nothing.** A field required and missing, or a field of the wrong type, refuses the file, naming the field by its path (`languages.fr.resources.2.title`). Nothing is ever imported in part.
- **A field more is passed over.** A producer may add its own (where a text was taken from, a licence) without raising the format: the app keeps none of it.
- **Replacing.** Before importing a file whose identity is already there, the app says what replacing loses: the languages gone, and the texts retouched in the app since the last import.
- **The language.** The person importing chooses the language the identity speaks, among the file's; every language is kept.
- **After the import**, the identity belongs to the app: its texts are changed there, by hand or by the AI. The file is not read again until it is imported again.
- **Exported**, from the identity's screen, it is this same file again, every language holding a text: The language it speaks holds a text, or the export is refused. `published_at` stays its author's while nothing of the identity changed since its import, and becomes the day of the export once anything did (a text removed included), or for an identity written in the app.
