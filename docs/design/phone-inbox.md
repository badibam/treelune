# Les points arrivés du téléphone (2026-11-26)

Les items de la section « En provenance du tel » de `TODO.md`, tranchés un à un, pour implémentation ultérieure.

## Refaire la démo et le Guide

L'idée : une fois l'outil Parcours fait, reprendre la démo et le Guide pour montrer ce que l'app sait faire aujourd'hui (Giulia pourrait y introduire l'identité) ; le site ne change pas d'apparence, il gagne seulement une mention que l'app a évolué depuis.

- Le site cesse de lire la démo vivante. Aujourd'hui `scripts/make_site_data.py` tire la zone Italien de la démo dans `site/private/generated.json` (ses outils, sa routine, son identité, leurs icônes), et relève une empreinte des fichiers de la démo que `--check`, lancé par `./run release-tag`, compare : une démo changée bloquerait la release jusqu'à une régénération, qui réécrirait la zone du site depuis la nouvelle démo. La partie zone de `generated.json` reste donc telle qu'elle est, l'empreinte disparaît ; le script ne garde de l'app que ce qui ne casse pas l'apparence du site (types d'outils, thèmes, Android minimal, icônes). Les captures et vidéos actuelles font référence.
- Les captures françaises et les cartes du site encore en attente (`TODO.md`, « Site treelune.app ») se prennent avant de toucher la démo, sinon elles partiraient de la nouvelle.
- La démo se refait sur l'histoire, pas sur le contenu : d'abord le parcours qu'on fait suivre à qui découvre l'app, dans l'ordre où les notions se comprennent (une page, des outils, une variable, une Tâche, une routine, une identité, un Parcours) ; puis pour chaque étape, la zone existante qui la montre déjà ou une nouvelle. Compléter sans ce plan ferait de la démo un catalogue. Les chapitres du Guide qui citent la démo se réécrivent avec elle (`scripts/check_guide.py` les tient d'accord).
- Le plan du parcours de la démo s'écrit une fois l'outil Parcours fini (sa liste « Reste », `docs/design/progression.md`) : écrit avant, il raconterait un outil qui change encore.
