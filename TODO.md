# TODO

Travail ouvert, une ligne par item ; un item fait sort, le commit en est le registre. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La fiche fastlane : le changelog de la 0.9.0, les captures d'écran faites sur le téléphone (elles servent aussi aux README, place marquée).
- La 0.9.0 taguée sur un arbre propre, ses notes de version validées, la mise à jour depuis la dernière version publiée passée sur le téléphone avec ses données.
- La recette F-Droid sur `v0.8.1` essayée dans le conteneur (`docs/design/fdroid-compliance.md`), son APK comparé à celui de GitHub (`apksigcopier compare`) — podman à installer.

## Juste après la release

- L'export des entrées d'un outil (`docs/design/export.md`).
- Partager une partie de l'app, et reprendre une partie d'une sauvegarde (`docs/design/share.md`).

## Bugs

- Des boutons à libellé posés dans une simple rangée, qui se perdent quand la place manque : à passer dans `ButtonRow` (`VariableScreen.kt:175`, `JournalEntryScreen.kt:376`, `DuplicateSelector.kt:234`, `StructuredScreen.kt:260` et `:299`), et la rangée d'actions de `InteractionCard` (la validation dans le chat), que chaque thème pose avec deux moitiés de largeur fixe (`InteractionActions`).
- Les libellés du sélecteur de période comptent un écart en millisecondes divisées par 24 h ou 7 jours (`generateDayLabel`, `generateWeekLabel`) : le lendemain du passage à l'heure d'été, la veille, de 23 h, se dirait « Aujourd'hui » — déduit du code ; compter les jours entre deux dates de l'app.
- La durée d'un Objectif est stockée en millisecondes (7 jours = 604 800 000) : une tentative qui traverse un changement d'heure finit une heure avant ou après l'heure de son début — déduit du code.

## Bugs du téléphone

Vus le 2026-10-20, laissés pour plus tard :

- Un titre trop long d'une entrée de Journal ou d'une note est refusé avec « Nom: … » quand le formulaire dit « Titre » : le champ du nom du cœur porte le même libellé dans tous les outils (`CoreFields.name`).
- Flashcards : la confirmation au retrait d'un sens dit « N entrée(s) supprimée(s) faute de valeur obligatoire », sans dire que ce sont les révisions de ce sens.
- La config d'un paquet Flashcards : « Source » et « Sens » écrits deux fois (le titre du cadre, puis le libellé du champ) ; « Rétention voulue » écrite « 0.9 » en français ; la poignée d'un champ d'un sens collée à sa liste déroulante.
- Le choix d'une texture : toucher le libellé sous une vignette ne choisit rien, seule la vignette le fait.
- La fenêtre de suppression d'un outil dit « cet élément » sans nommer l'outil.
- Au cran de taille 3, le point d'attente et le chrono recouvrent presque toute l'icône d'une zone sur l'accueil (Travail).
- La tuile de l'Agenda dit encore « 3 images » en texte (champ Image).

## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- La recette, `docs/design/device-checks.md` : chaque section dit quoi faire et quoi voir, une ligne vue en sort ; une section vidée, la spec qu'elle nomme s'élague, ses garanties devenant des tests.
- Les specs que la recette ne nomme pas, à élaguer une fois leurs lignes passées : `missing-tools.md` (Champs RÉFÉRENCE, Variables, Données structurées, Objectif, Questionnaire, Graphique), `icon-colors.md`, `settings-pages.md` (Réglages et affichage), `message-images.md` (Chat IA, la ligne « Images »), `sequence-tool.md` (Séance), `entry-start-state.md`, `always-send.md`, `bug-report.md` (Mise à jour et démarrage), `validation.md` (Validation), `mcp-server.md` (Accès externe), `funnel-poc.md` avec `funnel-access.md`, `custom-fields-display.md` (Outils et saisie) ; `unified-fields.md` (Lecture seule pour l'IA), dont la section part dans `docs/DATA.md` ; `image-field.md`, sa propre section « À passer sur le téléphone », la migration 82 sur la release d'abord.
- Un modèle libre chez soi : un Ollama sur l'ordinateur, servi en https par `tailscale serve`, branché comme Autre serveur depuis le téléphone avec un modèle de la classe 30B, prix à 0 ; mesurer le temps d'une réponse (la lecture d'un prompt de ~29k tokens sur la machine). Passé, une page du Guide « Brancher un modèle libre » d'après ce qui a été fait.
- Les passes sur le téléphone de `docs/THEMES.md`, puis ce que la spec de chaque thème garde dans « Ce qui reste ouvert » :
  - le rétro (`retro-theme.md`), vu le 2026-10-01 (écran d'une zone, palette pâle, marges reprises) : écran par écran, les graduations en pointillé de ses graphiques et le damier du mode d'édition de la grille compris ;
  - le cosy (`cosy-theme.md`, maquettes liées dedans) : le banc des palettes d'abord, les icônes retracées (leur coût à l'ouverture d'une page, celles qui se bouchent à 24 dp) ;
  - l'encre et lavis (`ink-theme.md`) : ce qui a changé le 2026-10-17 après la première passe : le lavis d'une tuile qui déborde et s'éteint à son bord, sa texture estompée avec lui, la carte en lavis au bord fondu (des marches dans le fondu ?), la gouache sous les icônes et les tags, les ronds peints à la main (boutons, case, sceaux), le coût d'ouverture d'une zone aux tuiles texturées ;
  - le métro (`metro-theme.md`) ;
  - le papier découpé (`paper-theme.md`) : les icônes découpées d'abord (leur coût, celles qui se bouchent) ;
  - le plan d'architecte (`blueprint-theme.md`) : le débord des icônes d'abord ;
  - la liseuse (`eink-theme.md`) : les trames d'abord (deux couleurs au pixel près, la discrète des textures), la bande d'encre et ses boutons d'abord dans « Ce qui reste ouvert ».

## Décidé, à coder

- Un filtre qui compare un champ à une variable ou une lecture (« kcal > objectif_calorique ») : le terme lu une fois à la référence du contexte (`TermReader`, `docs/BRICKS.md`), puis le filtre SQL comme aujourd'hui ; `EntryFilters.parse` et `ConditionPicker` le prennent.

## À concevoir

- Les events du cœur : quel premier usage — un événement de l'Agenda qui commence, une entrée écrite, une condition devenue vraie (l'Alerte) ? L'automation directe (le relevé, `docs/design/missing-tools.md`) attend la même brique.
- Des outils montrés sur la tuile d'une zone de l'accueil (le poids du jour sur « Santé ») : leur résumé ou un raccourci selon la place de la tuile — lesquels, dans quel ordre, un outil montré à deux endroits.
- Des rythmes que la planification (`ScheduleConfig`, commune à tous les outils et aux routines) ne sait pas dire : « tous les N jours à partir d'une date », « une semaine sur deux », plusieurs jours d'un mois (« le 1er et le 15 »).
- Une réponse coupée par la limite de longueur (`max_tokens` chez Claude et DeepSeek, un `status` incomplet chez OpenAI, `finish_reason` « length » chez un autre serveur) : refusée, son texte jeté et ses tokens mis à 0, le coût affiché la compte gratuite alors qu'elle est facturée — que garder du texte, et où le montrer.

## Dette de code

- Les mots de passe de `~/.config/treelune/signing.env` en clair : au trousseau (`secret-tool`).
- `CommandExecutor` lit la base directement, hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` appelé depuis l'écran, sans le service.
- Des conversions JSON refaites sur place au lieu de `JsonUtils` (`toValueMap`, `jsonToValue`, `toFieldConfigValue`…).
- `shared.xml` (~1 900 lignes) à découper.
- `RetroPaletteTest` et `CosyPaletteTest` à parcourir par pas de 15° de teinte, comme `SwissPaletteTest`.
- `TextType.SUBTITLE` sans rang dans le contrat des thèmes : lui en donner un, aligner les thèmes et ses 71 usages.
- Le journal ne garde que ~24 min d'usage (12 000 lignes le 2026-10-01, dont 3 782 « Service result » en VERBOSE) : les lignes utiles à la recette en sortent avant d'être lues ; réduire VERBOSE et DEBUG, « Service result » d'abord.
- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config, les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux, au plus tard quand un tutoriel, une notification ou un pointeur doit les viser.

## Sagesse

Ce qui doit monter dans un module de sagesse (`/update push`).

- Le pilotage du téléphone par adb pour la recette (`scripts/phone_ui.sh` : captures réduites, toucher par le texte de l'écran, frappe mot par mot), dans `android`.

## Bloqué par autre chose

- Le correctif Tailscale de la route `cert/` (PR #21720) : fusionné, monter de version et retirer la correction de `scripts/vendor_go.py` ; refusé, noter pourquoi dans `funnel-poc.md`.
- L'import d'une incarnation depuis personae (la dernière section de la recette, l'identité de Stan Prokopenko) : quand personae exportera au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.

## À surveiller

- Une zone lente à s'afficher, à l'ouverture comme au retour, qui relit tout (~1 s en debug le 2026-10-01, le graphique Calories de Panorama de 0,75 à 1,2 s).
- Un `DATA_REFUSED` légitime dans une routine : un seuil par routine.
- Un calcul lourd qui fige l'app : lire, calculer sans bloquer, écrire.
- Un import lancé par erreur qu'on ne peut pas arrêter (`LongOperation`).
- Des réponses de l'IA perdues pendant la génération : streaming et keep-alive TCP.
- La mémoire d'un import grandit avec son fichier (276 Mo pour 5 Mo) : lire et écrire par tranches.
- Un événement toute la journée de l'Agenda qui se montre sur le mauvais jour : il est un instant, le début du jour de l'app à sa date (migration 85), qu'un changement de l'heure où commence le jour ou du fuseau (un voyage, le fuseau suivant le téléphone) fait glisser d'un jour, rien ne le replaçant ; le replacer à chaque changement du calendrier, ou stocker une date.

## Idée sans besoin

- Des modèles d'outils prédéfinis.
- Lire un long texte du connecteur MCP par section (le sommaire des titres `##`, puis une section par son titre), en plus des tranches.
- Des niveaux de prompt pour un modèle plus petit (`docs/design/local-models.md`).
- Une entrée précise comme cible d'un pointeur (`docs/design/pointer.md`).
- Des valeurs de champs de l'utilisateur recopiées dans chaque occurrence d'une série d'Agenda.
- Un pointeur touché dans un message ouvre sa cible.
- Le sens « out » : l'app confie une tâche lourde à une IA extérieure et en reprend le résultat.

## Écarté

- Obliger le client MCP à lire le schéma avant d'écrire : chaque écriture est validée, un refus dit quoi corriger.
- Un seul calcul du prochain envoi : Messages stocke des envois que l'utilisateur voit, une routine n'a qu'une heure.
- Un Suivi « étiquette » : le type « choix » le couvre.
- Le nombre d'entrées en attente sur la tuile, au lieu du point : le résumé des tuiles donne déjà ce compte.
- Un fichier joint au message de départ d'une routine : un pointeur vers un outil qui garde le contenu fait mieux, relu à chaque exécution.
