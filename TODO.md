# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La fiche fastlane : le changelog de la 0.9.0, les captures d'écran faites sur le téléphone (elles servent aussi aux README, place marquée).
- La 0.9.0 taguée sur un arbre propre, ses notes de version validées, la mise à jour depuis la dernière version publiée passée sur le téléphone avec ses données.
- La recette F-Droid sur `v0.8.1` essayée dans le conteneur (`docs/design/fdroid-compliance.md`), son APK comparé à celui de GitHub (`apksigcopier compare`) — podman à installer.

## Juste après la release

- Une réponse coupée par la limite de longueur (`finish_reason` « length ») garde son texte et ses tokens, aujourd'hui jetés — à trancher : montré avec l'erreur, ou gardé en base seulement.
- L'export des entrées d'un outil (`docs/design/export.md`).
- Le titre de `ToolConfigScreen` donne le nom de l'outil, pas seulement son type.
- `ai_prompt_current_datetime` dit qu'elle est écrite par le système.

## Bugs

- Supprimer une zone laisse ses routines (`automations.zone_id` sans clé étrangère) — avec l'historique des écritures.
- Des rangées de boutons à libellé à passer dans `ButtonRow` (`VariableScreen.kt:175`, `JournalEntryScreen.kt:376`, `DuplicateSelector.kt:234`, `StructuredScreen.kt:260` et `:299`, `InteractionActions`).

## À passer sur le téléphone

Codé, jamais vu sur l'appareil ; le détail est dans la recette, `docs/design/device-checks.md`. Passé, la spec s'élague et ses garanties deviennent des tests.

- Un Ollama sur l'ordinateur servi par `tailscale serve`, modèle de classe 30B, en Autre serveur : le temps d'une réponse ; puis une page du Guide « Brancher un modèle libre ».
- La place à l'écran (2026-10-14) : Réglages et affichage, Automations.
- Les outils manquants : puis élaguer `docs/design/missing-tools.md`.
- Thème rétro (`docs/design/retro-theme.md`) : écran par écran, puis « Ce qui reste ouvert ».
- Thème cosy (`docs/design/cosy-theme.md`) : le banc des palettes, puis « Ce qui reste ouvert ».
- Le 2026-10-02 : la conversion d'apparence en v58, les titres de section du thème par défaut, la tuile sans icône.
- Couleurs des icônes (`docs/design/icon-colors.md`).
- Images dans un message (`docs/design/message-images.md`).
- Outil Séance (`docs/design/sequence-tool.md`).
- Formulaire de config en pages (`docs/design/settings-pages.md`).
- État de départ d'une entrée (`docs/design/entry-start-state.md`).
- Outils toujours envoyés (`docs/design/always-send.md`).
- Rapport de bug (`docs/design/bug-report.md`).
- Validation et accès de l'IA (`docs/design/validation.md`) : section Validation.
- Serveur MCP (`docs/design/mcp-server.md`) : section « Accès externe ».
- Accès par Tailscale Funnel (`docs/design/funnel-access.md`) : « Adresse prête » qui arrive enfin ; puis élaguer aussi `funnel-poc.md`.
- Le 2026-10-11 : migration 73 sur la release, l'avertissement d'arrière-plan, les automations, une fenêtre flottante longue du cosy.
- Le 2026-10-13 (ai-runtime) : une routine app fermée, la réflexion d'une conversation, migration 74.
- Le 2026-10-14 (data-text) : section « Copier, limites, fuseau… » ; la durée d'une sauvegarde automatique dans le journal, au-delà de 50 s la passer sous le service au premier plan.
- Le 2026-10-14 sur develop : « routine » à l'écran, la page du message d'une routine, la config d'un champ référence, le Guide (chapitres et tutoriels nouveaux, dans les trois thèmes).
- Champs de l'utilisateur dans Suivi et Messages (`docs/design/custom-fields-display.md`).
- L'IA et les routines : section du même nom.
- La barre d'un texte sélectionné dessinée par le thème.
- Outil Agenda (`docs/design/agenda-tool.md`).
- Bugs du 2026-10-16 et changement de config (`docs/design/config-change.md`).
- Sources de l'attente (`docs/design/waiting-sources.md`).
- La réflexion de chaque fournisseur, requête envoyée : Claude (Sonnet 5.5 par `between_tools`, Opus 5.5, Haiku 4.5), OpenAI (`none`, un modèle sans fait), DeepSeek ; le changement de modèle qui vide les réglages, une combinaison refusée.
- Lecture seule pour l'IA (`docs/design/unified-fields.md`) : passé, la section part dans `docs/DATA.md`.
- Lectures du connecteur en tranches, et son propre seuil (migration 83) : section « Accès externe (serveur MCP) ».
- Textures (`docs/design/tool-texture.md`) : le banc d'abord (`./run themes`), vue « page » comprise.
- Lectures envoyées une fois par session.
- Thème encre et lavis (`docs/design/ink-theme.md`) : la passe de `docs/THEMES.md`, ce qui a changé le 2026-10-17, puis « Ce qui reste ouvert ».
- Incarnations (`docs/design/incarnations.md`) : l'import attend le format 2 du fichier d'identité côté personae.
- Bugs du 2026-10-17, du 2026-10-19.
- Répétition d'un Objectif (migration 79).
- Conditions typées (`docs/design/typed-conditions.md`).
- Gras et italique d'un texte lu.
- La démo enrichie : tout sauf les textures, déjà vues.
- Fournisseurs créés (`docs/design/ai-providers.md`) : migration 80 sur la release d'abord.
- Écrans du chat, des identités et des fournisseurs (2026-10-17).
- Thème métro (`docs/design/metro-theme.md`).
- Thème papier découpé (`docs/design/paper-theme.md`) : les icônes découpées d'abord.
- Mise en forme des textes longs (`docs/design/text-formatting.md`).
- La SQLite de l'app : la mise à jour depuis la release, puis la démo sur l'OPPO A5 2020.
- Thème plan d'architecte (`docs/design/blueprint-theme.md`) : le débord des icônes d'abord.
- Flashcards (`docs/design/flashcards.md`) : la démo, puis le lexique réel.
- Une seule activité (singleTask) : une notification qui ouvre un lieu, app ouverte et fermée ; le retour du sélecteur de photos ; le lanceur.
- Champ Image (`docs/design/image-field.md`) : migration 82 d'abord ; la tuile de l'Agenda dit « 3 images » en texte.
- Thème liseuse (`docs/design/eink-theme.md`) : les trames d'abord.
- Une question de l'IA dans une routine refusée, l'IA continuant seule (gemma, gpt-oss au banc).

## Décidé, à coder

- L'historique des écritures et leur annulation (`docs/design/undo-history.md`).
- L'écran de confirmation d'un changement de config nomme les outils dont un filtre vise une option retirée ou renommée.
- `docs/` en anglais ; `docs/design/` et `TODO.md` restent en français.

## À concevoir

- Les icônes du cosy redessinées depuis les tracés de Lucide (`IconStrokes`), comme Encre et lavis : leur trait.
- Les events du cœur : quel premier usage — un événement de l'Agenda qui commence, une entrée écrite, une condition devenue vraie ?

## Dette de code

- Les mots de passe de `~/.config/treelune/signing.env` en clair : au trousseau (`secret-tool`).
- `CommandExecutor` lit la base directement, hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` appelé depuis l'écran, sans le service.
- `CommandExecutor` remplace un nom ou un `schema_id` manquant par `"unknown"`.
- `ToolConfigActions` porte un nom qui ne dit pas ce qu'il fait.
- Des conversions JSON refaites sur place au lieu de `JsonUtils` (`toValueMap`, `jsonToValue`, `toFieldConfigValue`…).
- `shared.xml` (~1 900 lignes) à découper.
- `RetroPaletteTest` et `CosyPaletteTest` à parcourir par pas de 15° de teinte, comme `SwissPaletteTest`.
- `TextType.SUBTITLE` sans rang dans le contrat des thèmes : lui en donner un, aligner les thèmes et ses 71 usages.
- `FlashcardsServiceTest` dépend sans doute de l'heure : lui donner une horloge fixe.
- Une apostrophe nue à côté d'une échappée casse la compilation des ressources sans qu'aucun contrôle la voie.

## Bloqué par autre chose

- L'Alerte et l'automation directe (le relevé, `docs/design/missing-tools.md`) : les events du cœur.
- Le pilotage du téléphone par adb (`scripts/phone_ui.sh`) dans le module de sagesse `android` : au prochain `/update push android`.
- Le correctif Tailscale de la route `cert/` (PR #21720) : fusionné, monter de version et retirer la correction de `scripts/vendor_go.py` ; refusé, noter pourquoi dans `funnel-poc.md`.

## À surveiller

- Un appel à l'IA relancé par un changement d'état qui ne change pas de phase (`AIEventProcessor.handleStateChange`) : au prochain doublon, lire « ignored: it belongs to » dans le journal.
- Le point d'attente et la tuile de Flashcards pas relus au début de la journée.
- Revenir à une zone la relit entièrement.
- Le graphique Calories de Panorama (0,75 à 1,2 s) et l'ouverture d'une zone (~1 s).
- Défilement saccadé à la réouverture d'une longue conversation.
- Un `DATA_REFUSED` légitime dans une routine : un seuil par routine.
- Un calcul lourd qui fige l'app : lire, calculer sans bloquer, écrire.
- Un import lancé par erreur qu'on ne peut pas arrêter (`LongOperation`).
- Des réponses de l'IA perdues pendant la génération : streaming et keep-alive TCP.
- Le journal ne garde que ~24 min d'usage : réduire VERBOSE et DEBUG.
- La mémoire d'un import grandit avec son fichier (276 Mo pour 5 Mo) : lire et écrire par tranches.
- Une IA qui lit mal un long texte en tranches par le connecteur : le lire par section `##`.

## Idée sans besoin

- Des modèles d'outils prédéfinis.
- Des niveaux de prompt pour un modèle plus petit (`docs/design/local-models.md`).
- Un filtre qui compare un champ à une variable ou une lecture (`EntryFilters.parse`, `TermReader`).
- D'autres sources de l'attente : un message de l'IA arrivé pendant qu'on était ailleurs.
- Une entrée précise comme cible d'un pointeur (`docs/design/pointer.md`).
- Le nombre d'entrées en attente sur la tuile, au lieu du point.
- Des valeurs de champs de l'utilisateur recopiées dans chaque occurrence d'une série d'Agenda.
- Des outils montrés sur la tuile d'une zone de l'accueil.
- Des écrans internes passés en lieux (`Place`) : quand un tutoriel, une notification ou un pointeur doit les viser.
- Un pointeur touché dans un message ouvre sa cible.
- Le sens « out » : l'app confie une tâche lourde à une IA extérieure et en reprend le résultat.
- Un fichier joint au message de départ d'une routine.
- « Tous les N jours à partir d'une date », plusieurs jours d'un mois, dans la brique de planning.
- Un bouton « Fait » sur un message qui écrit dans un Suivi.

## Écarté

- Obliger le client MCP à lire le schéma avant d'écrire : chaque écriture est validée, un refus dit quoi corriger.
- Un seul calcul du prochain envoi : Messages stocke des envois que l'utilisateur voit, une routine n'a qu'une heure.
- Un Suivi « étiquette » : le type « choix » le couvre.
