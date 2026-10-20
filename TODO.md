# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque. Les deux premières sections suivent l'ordre de travail : la release, puis ce qui la suit ; les autres suivent l'état de l'item : les bugs, ce qu'attend le téléphone, ce qu'attend du code, ce qu'attend une décision, ce qu'attend un déclencheur. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La fiche fastlane (`fastlane/metadata/android/`, textes écrits en `en-US` et `fr-FR`) : `changelogs/<versionCode>.txt` de la 0.9.0 ; les captures d'écran, à faire sur le téléphone, servent aussi au README (`fastlane/metadata/android/en-US/images/phoneScreenshots/`, leur place marquée dans `README.md` et `README.fr.md`), l'icône de l'app en tête.
- La version soumise, la 0.9.0, taguée sur un arbre propre, ses notes de version écrites et validées avant de publier ; la mise à jour depuis la dernière version publiée passée sur le téléphone avec ses données (une base neuve ne passe aucune migration, une mise à jour les passe toutes, nouvelles comprises).
- La recette sur `v0.8.1`, avec `Binaries` et `AllowedAPKSigningKeys`, essayée dans le conteneur (`docs/design/fdroid-compliance.md`), son APK comparé par `apksigcopier compare` à celui de la release GitHub : podman à installer d'abord, absent de cette machine.

## Juste après la release

- Une réponse coupée par la limite de longueur (`OpenAICompatibleExtensions.kt`, `finish_reason` « length ») garde son texte et ses tokens, aujourd'hui jetés, ce qui montre gratuit un appel facturé ; à trancher : le texte montré dans la session avec l'erreur, ou seulement gardé en base.
- L'export des entrées d'un outil (`docs/design/export.md`) : le dossier choisi dans les réglages, l'opération `exports.csv`, « Exporter… » sur l'écran d'un outil, l'action `EXPORT_DATA` de l'IA.
- Le titre de l'écran de config d'un outil (`ToolConfigScreen`) dit « Configurer » et le type de l'outil, jamais son nom.
- La phrase de date et d'heure envoyée à l'IA (`ai_prompt_current_datetime`) ne dit pas qu'elle est écrite par le système, et non par l'utilisateur.

## Bugs


- Des boutons à libellé posés dans une simple rangée, qui se perdent quand la place manque : à passer dans `ButtonRow` (`VariableScreen.kt:175`, `JournalEntryScreen.kt:376`, `DuplicateSelector.kt:234`, `StructuredScreen.kt:260` et `:299`), et la rangée d'actions de `InteractionCard` (la validation dans le chat), que chaque thème pose avec deux moitiés de largeur fixe (`InteractionActions`).

## À passer sur le téléphone

Codé, jamais vu sur l'appareil.

- La recette, `docs/design/device-checks.md` : chaque section dit quoi faire et quoi voir, une ligne vue en sort ; une section vidée, la spec qu'elle nomme s'élague, ses garanties devenant des tests.
- Les specs que la recette ne nomme pas, à élaguer une fois leurs lignes passées : `missing-tools.md` (Champs RÉFÉRENCE, Variables, Données structurées, Objectif, Questionnaire, Graphique), `icon-colors.md`, `settings-pages.md` (Réglages et affichage), `message-images.md` (Chat IA, la ligne « Images »), `sequence-tool.md` (Séance), `entry-start-state.md`, `always-send.md`, `bug-report.md` (Mise à jour et démarrage), `validation.md` (Validation), `mcp-server.md` (Accès externe), `funnel-poc.md` avec `funnel-access.md`, `custom-fields-display.md` (Outils et saisie) ; `unified-fields.md` (Lecture seule pour l'IA), dont la section part dans `docs/DATA.md` ; `image-field.md`, sa propre section « À passer sur le téléphone », la migration 82 sur la release d'abord.
- Un modèle libre chez soi : un Ollama sur l'ordinateur, servi en https par `tailscale serve`, branché comme Autre serveur depuis le téléphone avec un modèle de la classe 30B, prix à 0 ; mesurer le temps d'une réponse (la lecture d'un prompt de ~29k tokens sur la machine). Passé, une page du Guide « Brancher un modèle libre » d'après ce qui a été fait.
- Les passes sur le téléphone de `docs/THEMES.md`, puis ce que la spec de chaque thème garde dans « Ce qui reste ouvert » :
  - le rétro (`retro-theme.md`), vu le 2026-10-01 (écran d'une zone, palette pâle, marges reprises) : écran par écran, les graduations en pointillé de ses graphiques et le damier du mode d'édition de la grille compris ;
  - le cosy (`cosy-theme.md`, maquettes liées dedans) : le banc des palettes d'abord ;
  - l'encre et lavis (`ink-theme.md`) : les icônes au pinceau d'abord (leur coût à l'ouverture d'une page, celles qui se bouchent) ; ce qui a changé le 2026-10-17 après la première passe : le lavis d'une tuile qui déborde et s'éteint à son bord, sa texture estompée avec lui, la carte en lavis au bord fondu (des marches dans le fondu ?), la gouache sous les icônes et les tags, les ronds peints à la main (boutons, case, sceaux), le coût d'ouverture d'une zone aux tuiles texturées ;
  - le métro (`metro-theme.md`) ;
  - le papier découpé (`paper-theme.md`) : les icônes découpées d'abord (leur coût, celles qui se bouchent) ;
  - le plan d'architecte (`blueprint-theme.md`) : le débord des icônes d'abord ;
  - la liseuse (`eink-theme.md`) : les trames d'abord (deux couleurs au pixel près, la discrète des textures), la bande d'encre et ses boutons d'abord dans « Ce qui reste ouvert ».

## Conçu, à coder

Une spec existe dans `docs/design/`.


## À concevoir

Une idée sans spec : elle demande une décision avant du code.

### IA et automations

- Le connecteur MCP : sans session, rien n'oblige le client à lire le schéma d'un outil avant d'y écrire (la consigne seule, `ai_chunk_validation_strategy`) ; l'imposer demande de retenir, par connexion, les schémas envoyés.
- Les lectures du connecteur MCP se tiennent au seuil de la conversation (`chat_max_data_chars`, `McpReads`) : un seuil propre au connecteur, réglé par client à côté de son masque (migration 71), la limite de réponse de chaque client étant la sienne — à trancher.
- Une question posée par l'IA dans une routine (`communication_module`) est ignorée sans le lui dire (`AIStateMachine`) : la refuser comme une erreur de format, que l'IA corrige, ou la garder pour l'utilisateur dans le compte rendu de l'exécution — à trancher. La relecture de la campagne 1 en a vu chez gemma et gpt-oss (`docs/design/bench-campaign-1.md`).
- Le prochain envoi se calcule de deux façons : à la volée pour une automation (`AutomationScheduler`), stocké en base et remis à jour à chaque envoi pour Messages — en garder une.

### Outils et champs

- Des modèles d'outils prédéfinis et paramétrables (« Série de questions » suivie d'un « Avis de l'IA »…).
- Un type de Suivi « étiquette », qui ne note qu'une étiquette.
- Un outil changé dont dépend une condition ailleurs (le Graphique filtre sur « moment = déjeuner », l'option « déjeuner » est retirée du Repas, ou le champ « moment » renommé) : refuser le changement en nommant les outils concernés, ou reporter un renommage dans les conditions, que `ListOrigins` sait distinguer d'une suppression — à trancher ; il faut savoir quelles configs et quelles variables visent un outil.

### Interface

- Lucide redessiné à leur manière pour le cosy et le suisse, comme le pinceau d'Encre et lavis (`docs/design/ink-theme.md`, ligne 9), un trait à chacun : une fois la lecture des tracés d'une icône (`IconStrokes`) tenue sur le téléphone avec Encre.

### Events du cœur

- Un système d'events du cœur, intégré au planificateur, avec une section par zone : un lien entre outils qui passe par le cœur (par une automation, par exemple). Déclencheurs d'une exécution : `SCHEDULE`, `MANUAL`, `EVENT` — revoir ceux des automations (ce qui est un déclencheur y est traité de façon inégale). Un event peut naître du planning d'un outil, et nomme l'outil qui l'a déclenché pour qu'on y aille en un geste ; des events système, sur le même modèle. Les constantes sont à penser avec. L'Agenda en donne une première source, « un événement commence » (`docs/design/agenda-tool.md`). L'Alerte et l'automation directe le croisent.

### Documentation

- Traduire la doc en anglais.

## Dette de code

Ce qui marche, mais contredit l'architecture ou une règle du projet.

- `~/.config/treelune/signing.env`, lu par `./run` pour signer une release, garde ses mots de passe en clair : les passer au trousseau (KWallet, via `secret-tool`, comme la clé OpenRouter du banc depuis le 2026-10-19), le `release.keystore` restant un fichier.
- `CommandExecutor` lit la base directement (`AppDatabase.getDatabase`, les messages d'une session), hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` est appelé depuis l'écran (`AutomationCard`, `AutomationScreen`), sans passer par le service.
- `CommandExecutor` remplace un nom ou un `schema_id` manquant par `"unknown"` : un repli que la règle du projet interdit.
- `ToolConfigActions` (`core/ui/components`), utilisé par l'IA, porte un nom qui ne dit pas ce qu'il fait.
- Des conversions JSON → valeurs refaites sur place au lieu de `JsonUtils` : au moins `toValueMap` (`MessagesScreen`), `jsonToValue` (`StateSavers`), `toFieldConfigValue` (`FieldDefinition`) — compter le reste avant de les ramener.
- `shared.xml` (~1 900 lignes) à découper en plusieurs fichiers.
- `RetroPaletteTest` et `CosyPaletteTest` ne tiennent les contrastes qu'au décalage de teinte 0 : les parcourir par pas de 15°, comme `SwissPaletteTest`, et reprendre les nombres d'un décalage qui tombe sous le seuil.
- `TextType.SUBTITLE` n'a pas de rang dans le contrat des thèmes (`UITypes.kt`) : plus grand que `HEADING` en défaut et en cosy, plus petit en rétro, pour 71 usages. Lui donner un rang, aligner les thèmes, puis passer ses usages écran par écran dans les trois thèmes.

- `FlashcardsServiceTest` (« a deck's reviews are written by its own operation alone ») a échoué à 00:30 le 2026-10-20 (`flashcards.due` vide), passé quelques heures plus tôt : il dépend sans doute de l'heure, la démo et les cartes dues se calculant depuis l'instant présent et le début de la journée (4 h) ; non vérifié. Lui donner une horloge fixe.
- Une apostrophe nue dans un texte qui en a déjà une échappée (`\'`) passe tous les contrôles de `./run test` et casse la compilation des ressources (« Invalid unicode escape sequence »), le générateur n'échappant que les textes qui n'en ont aucune : la refuser dans un contrôle des sources de textes, ou faire échapper toutes les apostrophes au générateur.

## Bugs du téléphone

Vus le 2026-10-20, laissés pour plus tard :

- Un titre trop long d'une entrée de Journal ou d'une note est refusé avec « Nom: … » quand le formulaire dit « Titre » : le champ du nom du cœur porte le même libellé dans tous les outils (`CoreFields.name`).
- Flashcards : la confirmation au retrait d'un sens dit « N entrée(s) supprimée(s) faute de valeur obligatoire », sans dire que ce sont les révisions de ce sens.
- La config d'un paquet Flashcards : « Source » et « Sens » écrits deux fois (le titre du cadre, puis le libellé du champ) ; « Rétention voulue » écrite « 0.9 » en français ; la poignée d'un champ d'un sens collée à sa liste déroulante.
- Le choix d'une texture : toucher le libellé sous une vignette ne choisit rien, seule la vignette le fait.
- La fenêtre de suppression d'un outil dit « cet élément » sans nommer l'outil.
- Au cran de taille 3, le point d'attente et le chrono recouvrent presque toute l'icône d'une zone sur l'accueil (Travail).
- La tuile de l'Agenda dit encore « 3 images » en texte (champ Image).

## En attente d'un déclencheur

- L'import d'une incarnation depuis personae (la dernière section de la recette, l'identité de Stan Prokopenko) : quand personae exportera au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.
- Des niveaux de prompt (`docs/design/local-models.md`, « Si un modèle plus petit est voulu ») : décidé le 2026-10-17, un seul prompt, la classe 30B le suivant — quand un modèle plus petit, ou un modèle dans l'app sur le téléphone, sera voulu.
- Un filtre qui compare un champ à une variable ou à une lecture (« kcal > objectif_calorique ») : `EntryFilters.parse` le refuse, `ConditionPicker` n'offre qu'une valeur écrite — quand un pointeur ou une variable en a besoin ; le terme se lit alors une fois à la référence du contexte (`TermReader`).


- D'autres sources de l'attente (`docs/BRICKS.md`) : un message de l'IA arrivé dans une session pendant qu'on était ailleurs, signalé par exemple sur le bouton de conversation — à concevoir.
- Choisir une entrée précise comme cible du pointeur dans son sélecteur (conçu dans `docs/design/pointer.md` : étiquette par type d'outil, liste, relecture de l'entrée) — si le besoin apparaît : un pointeur peut désigner une entrée (le Questionnaire en pose un), mais seule l'app le fait. La cible APP attend que l'IA sache lire les réglages de l'app.
- Lire un long texte du connecteur MCP par section (le sommaire de ses titres `##`, puis une section par son titre), en plus des tranches — si l'IA lit mal un bagage d'identité en tranches.
- Le point d'attente et la tuile d'un paquet de Flashcards ne se relisent qu'à un changement d'entrée ou à l'ouverture de la zone (`rememberWaiting`, `rememberFlashcardsTile`), jamais d'eux-mêmes au début de la journée, quand des cartes deviennent dues : l'app restée ouverte sur la zone les montre en retard — si ça se voit sur le téléphone.
- Le nombre d'entrées en attente sur la tuile, au lieu du point (`WaitingMark`, décidé « pour le moment » le 2026-09-29) — si le point ne suffit pas.
- Les valeurs des champs de l'utilisateur dans une série d'Agenda (« Kiné, Lieu : Cabinet Dupont »), recopiées dans chaque occurrence : une brique du cœur, des valeurs pour les champs de l'utilisateur d'un outil, déclarée dans une config (schéma, saisie dans le formulaire, conversion pour l'IA des types qu'elle ne connaît pas d'avance) — quand remplir chaque occurrence pèsera.
- Outil jamais livré : l'Alerte, laissée pour plus tard (outil ou event du cœur, sans doute une lecture et une condition).
- L'automation directe : des commandes que l'app exécute elle-même, sans IA. Premier usage connu, le relevé (une écriture qui accepte une valeur lue à l'exécution : un Suivi qui garde un chiffre de Calcul, `docs/design/missing-tools.md`) ; à concevoir avec les events du cœur (« À concevoir › Events du cœur ») et l'Alerte, qu'elle croise, et à trancher : la date de l'entrée écrite, le doublon d'une exécution relancée.
- Des outils sur l'accueil : une tuile de zone qui montre les résumés de certains de ses outils, ou des raccourcis vers eux selon la place qu'elle a — à discuter : lesquels, dans quel ordre, un outil montré à deux endroits.

------

- Un appel à l'IA relancé sans raison : les effets d'une phase (`AIEventProcessor.handleStateChange`, l'appel en `CALLING_AI`) se rejouent à chaque nouvel état, pas seulement à chaque changement de phase ; un événement qui ne change qu'un champ pendant l'appel en relance un, la requête déjà partie pouvant être facturée. Deux réponses identiques à une même demande le 2026-10-09 ; les gardes de phase (`AIStateMachine.expectedPhases`) en limitent l'effet — à mesurer dans le journal (« ignored: it belongs to ») au prochain doublon.
- Faire monter dans le module de sagesse `android` le pilotage du téléphone par adb pour la recette (captures réduites, toucher par le texte de l'écran, frappe mot par mot ; `scripts/phone_ui.sh`) — au prochain `/update push android` fait pour autre chose.
- Le correctif de la route `cert/` sous Android est proposé à Tailscale (ticket #21719, PR #21720) : fusionné, monter à la version qui le porte, retirer la correction de `scripts/vendor_go.py` et son contrôle de `./run test` ; refusé, la garder et noter pourquoi dans `docs/design/funnel-poc.md`.
- Des écrans encore ouverts à l'intérieur d'un lieu, hors de la pile des lieux (`Place`, `docs/UI.md`) : la config d'un fournisseur d'IA, les pages d'un formulaire de config (`SettingsForm`), les écrans internes d'un outil (fiche, historique), l'historique des conversations — à passer en lieux quand un tutoriel, une notification ou un pointeur doit les viser.
- Revenir à une zone la relit (ses outils, ses variables) : ce que l'écran garde par `remember` ne survit pas à son passage sous un autre lieu — si le rechargement se voit.
- Un pointeur touché dans un message (l'étiquette d'une zone ou d'un outil, aujourd'hui un simple texte) ouvre sa cible, empilée au-dessus de la conversation pour que le retour y ramène ; de même pour une cible nommée par l'IA dans sa réponse — la pile des lieux sait l'ouvrir (`Navigator.open`, `docs/UI.md`) — quand on le voudra.
- Le sens « out » : l'app qui confie une tâche lourde à une IA extérieure (Claude Code, par exemple) et en reprend le résultat, comme une commande différée — une fois le serveur MCP en place, par lequel la tâche lira et écrira, et vérifié ce que chaque service permet pour lancer une tâche par une requête.
- Le graphique Calories de Panorama (74 lignes) se lit en 0,75 s à l'ouverture de l'outil, 0,9 à 1,2 s à celle de la zone (2026-10-01, version debug, une fiche lue une fois par calcul) ; sa mise en page prend 15 à 19 ms. Restent 181 appels aux services : par jour, deux lectures des repas (60) et la lecture de l'outil qui donne leurs champs (`ToolFields.filterable`, `tools.get`) à chacune — si ça gêne encore.
- L'ouverture d'une zone bloque encore l'écran ~1 s la première fois après le démarrage (version debug, 2026-10-01 : 6 graphiques, 18 tuiles) — à décomposer si ça gêne.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Joindre un fichier ou une image au message de départ d'une automation : son composeur n'a pas toujours de session, à laquelle un fichier joint et une image appartiennent.
- La brique de planning ne sait pas dire « tous les N jours / N semaines à partir d'une date », ni plusieurs jours d'un mois (« le 1er et le 15 ») — au premier rythme qu'on ne peut pas saisir.
- Une chose répétée dont on veut savoir qu'elle est faite (un médicament chaque jour) demande deux gestes : lire le message, cocher le suivi — un bouton « Fait » sur le message qui écrit dans un suivi (avec les automations directes), ou un type Rappels avec un champ planning par entrée ; quand ce double geste pèse.
- Un calcul lourd en trois temps (lire, calculer longtemps sans bloquer les autres opérations, écrire), le résultat rendu à l'écran qui l'a lancé et arrêté si cet écran se ferme — au premier calcul qui fige l'app. Les écritures longues ont leur place unique (`LongOperation`) ; un calcul n'a rien à y faire, son résultat ne servant qu'à son écran.
- Arrêter une opération longue en cours (un bouton dans la bande de `LongOperation`) — quand un import lancé par erreur gênera ; l'import ne regarde aujourd'hui sa demande d'arrêt qu'au début.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.
- Le journal de l'app ne garde que ses ~12 000 dernières lignes, 24 minutes d'usage le 2026-10-01 (VERBOSE 7 562 dont 3 782 « Service result » du coordinateur, DEBUG 2 439) : les lignes utiles, migrations comprises, en sortent avant d'être lues. Réduire ce que VERBOSE et DEBUG écrivent, ou ne garder qu'eux en rotation courte.
- La mémoire d'un import grandit avec son fichier : le texte entier, toutes ses cellules (`CsvReader.read`), le plan de toutes les lignes et leurs fiches sont tenus ensemble — l'écriture va au service par tranches de 500 lignes, mais toutes les fiches sont construites avant ; 64 659 lignes (5 Mo) ont atteint 276 Mo de tas Java pour une limite de 256 Mo le 2026-10-01, en 3 min 19 s. À lire au fil de l'eau et construire chaque tranche au moment de l'écrire, dans la même transaction (le contrôle des doublons sur les seules clés), puis l'écran d'une table qui charge toutes ses fiches — au premier import qui plante, ou à une limite de mémoire plus basse.
