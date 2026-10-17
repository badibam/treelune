# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque. Les deux premières sections suivent l'ordre de travail : la release, puis ce qui la suit ; les autres suivent l'état de l'item : les bugs, ce qu'attend le téléphone, ce qu'attend du code, ce qu'attend une décision, ce qu'attend un déclencheur. Les pistes brutes arrivent dans `NOTES.md` et se trient ici.

## Release F-Droid

- La recette F-Droid (`scripts/fdroid/app.treelune.yml`) essayée dans l'image de build de F-Droid : la démarche, les deux commandes et les inconnues sont dans `docs/design/fdroid-compliance.md`, « La recette, essayée chez nous ».
- La reproductibilité : construire la release deux fois depuis deux clones du tag, à deux chemins différents, et comparer les APK.
- La fiche fastlane (`docs/design/fdroid-compliance.md`) : en-US `title`, `short_description`, `full_description`, `changelogs/<versionCode>.txt`, `images/icon.png`, `images/featureGraphic.png` ; les captures d'écran, à faire sur le téléphone, servent aussi au README (`fastlane/metadata/android/en-US/images/phoneScreenshots/`, leur place marquée dans `README.md` et `README.fr.md`), l'icône de l'app en tête.
- Les anti-features : `NonFreeNet` (Anthropic, OpenAI, DeepSeek, le serveur de connexion de Tailscale) ; à trancher pour la liste de prix LiteLLM que `ModelPriceManager` lit sur GitHub.
- La version soumise, taguée sur un arbre propre, ses notes de version écrites et validées avant de publier ; la mise à jour depuis la 0.7.0 installée passée sur le téléphone avec ses données (une base neuve ne passe aucune migration, une mise à jour les passe toutes, nouvelles comprises).
- La signature : si F-Droid signe l'app de sa propre clé, un APK installé depuis GitHub ne peut plus se mettre à jour par F-Droid ; un build reproductible lui fait publier l'APK signé de la nôtre (`Binaries`, `AllowedAPKSigningKeys` dans la recette).

## Juste après la release

- Une réponse coupée par la limite de longueur (`OpenAICompatibleExtensions.kt`, `finish_reason` « length ») garde son texte et ses tokens, aujourd'hui jetés, ce qui montre gratuit un appel facturé ; à trancher : le texte montré dans la session avec l'erreur, ou seulement gardé en base. Puis rejouer les scénarios coupés du banc en supprimant leur `verdict.json`.
- L'export des entrées d'un outil (`docs/design/export.md`) : le dossier choisi dans les réglages, l'opération `exports.csv`, « Exporter… » sur l'écran d'un outil, l'action `EXPORT_DATA` de l'IA.
- Les boutons du haut d'un outil changent de hauteur selon le nombre de lignes de son titre.
- Le titre de l'écran de config d'un outil, écrit par chaque écran : le rendre commun, le nom de l'outil et son type.
- La phrase de date et d'heure envoyée à l'IA (`ai_prompt_current_datetime`) ne dit pas qu'elle est écrite par le système, et non par l'utilisateur.

## Bugs

- Réglages › Interface : un thème choisi, vu, enregistré, et l'ancien revient ; une seconde fois, ça tient (2026-10-09, pas reproduit, peut-être dans la release). Au prochain cas, lire dans le journal les lignes « Theme applied » : laquelle a remis l'ancien (STORED, PREVIEW, LEFT_UNSAVED).

## À passer sur le téléphone

Codé, jamais vu sur l'appareil. Chaque item renvoie à ses lignes de `docs/design/device-checks.md` (la recette, classée par écran) ; passé, sa spec s'élague, ses garanties devenant des tests.

- La place à l'écran (2026-10-14) : marge de bord unique, rangées de boutons, grille de choix, clavier, poignées, dates des tuiles, point d'attente du rétro, page du message d'une routine (Réglages et affichage, Automations).
- Les outils manquants (sélection du cœur, RÉFÉRENCE, lecture du cœur, variables, Données structurées et import, Objectif, Questionnaire, Graphique) : puis élaguer de `docs/design/missing-tools.md` ce qui est codé.
- Le thème rétro (`docs/design/retro-theme.md`), vu le 2026-10-01 (écran d'une zone, palette pâle, marges reprises) : le parcourir écran par écran, les graduations en pointillé de ses graphiques et le damier du mode d'édition de la grille compris, puis ce que la spec garde dans « Ce qui reste ouvert ».
- Le thème cosy (`docs/design/cosy-theme.md`, maquettes liées dedans) : l'essayer, puis ce que la spec garde dans « Ce qui reste ouvert », le banc des palettes d'abord.
- Ce qui n'est pas passé le 2026-10-02 : la conversion du réglage d'apparence en v58 (une famille devient une teinte), les titres de section du thème par défaut, la tuile sans icône centrée.
- Les couleurs des icônes (`docs/design/icon-colors.md`).
- Les images dans un message (`docs/design/message-images.md`) : la ligne « Images ».
- L'outil Séance (`docs/design/sequence-tool.md`) : sa section.
- Le formulaire de config en pages (`docs/design/settings-pages.md`) : les boutons sur la page racine seule, les marques d'un problème, la phrase d'une condition (Réglages et affichage).
- L'état de départ d'une entrée (`docs/design/entry-start-state.md`) : migration 65, un message demandé à l'IA, une tentative refusée.
- Les outils toujours envoyés (`docs/design/always-send.md`) : migration 64, Réglages.
- Le rapport de bug (`docs/design/bug-report.md`) : démarrage, Réglages.
- La validation et les accès de l'IA (`docs/design/validation.md`) : les trois niveaux et les cases de session (migration 69), `UPDATE_APP_CONFIG`, le masque des automations (migration 70), la validation du connecteur par notification et son masque par client (migration 71) — section Validation.
- Le serveur MCP (`docs/design/mcp-server.md`, `core/mcp`, migration 60) ; le relais (projet `relay`) est en ligne sur `https://relay.badibam.fr` depuis le 2026-10-05, Claude s'y est branché comme connecteur depuis le téléphone ; l'hébergement tient une requête 120 s, `RelayLoop.WAIT_SECONDS` = 20 reste en dessous : la section « Accès externe ».
- L'accès externe par Tailscale Funnel (`docs/design/funnel-access.md`) : sa section (migration 68), puis élaguer aussi `funnel-poc.md`.
- Ce qui a changé le 2026-10-11, vu en debug seulement ou pas du tout : la migration 73 (le compteur devient numérique) sur la release et ses données, l'avertissement d'arrière-plan sur une installation neuve, les automations et leurs accès, une fenêtre flottante longue dans le cosy (Mise à jour et démarrage, Automations, Réglages et affichage).
- Ce qui a changé le 2026-10-13 (branche ai-runtime) : une routine lancée app fermée et sa notification (Automations) ; la réflexion d'une conversation, la migration 74, ses cases de validation gardées, une écriture dans un outil qui n'existe pas, un champ sans nom affiché (Chat IA).
- Ce qui a changé le 2026-10-14 (branche data-text) : copier et sélectionner, le compteur des champs et les nouvelles limites, les fuseaux et les langues, les colonnes écartées de l'import, la sauvegarde automatique et sa marque ; la durée d'une copie, à lire dans le journal (« Auto backup: … written in N ms ») : au-delà de 50 s app fermée, la faire tourner sous le service au premier plan des routines (dernière section de la recette).
- Ce qui a changé le 2026-10-14 sur develop : « routine » à l'écran dans les deux langues ; la page du message d'une routine, ses blocs à leur hauteur entière, la page défilant d'un bloc (un bloc déplacé par sa poignée au-delà de l'écran compris) ; la config d'un champ référence (Cible, Peut désigner, la liste d'outils seulement avec Entrée) ; le Guide, les chapitres Les routines (six étapes, celle du message attend l'enregistrement) et Parler à l'IA (l'étape des cases d'accord), « Les outils en un coup d'œil », la partie Les outils et ses dix chapitres (textes mis en forme, tuiles de titre de partie, dans les trois thèmes) ; le Guide réécrit dans sa langue, les tutoriels nouveaux de Prise en main (Organiser, Relier, Apparence : chaque étape se valide-t-elle par le bon geste, et seulement par lui), les chapitres lus (Tes données, Accès externe, Quand ça coince) et la partie Approfondissement.
- Les champs de l'utilisateur dans le Suivi et Messages (`docs/design/custom-fields-display.md`, branche custom-fields) : l'historique du Suivi en cartes, les champs sous le texte d'un message (Outils et saisie) ; passé, élaguer la spec.
- L'IA et les routines (branche ai-routines) : leur création par l'écran et la démo, celle de l'IA en conversation, une routine qui en crée une sous son masque, la lecture d'une exécution ratée, le connecteur (section « L'IA et les routines »).
- La barre d'un texte sélectionné dessinée par le thème (branche theme-recipe) : sa ligne de la section « Copier, limites, fuseau… » de la recette.
- L'outil Agenda (`docs/design/agenda-tool.md`, branche agenda) : l'Instant étendu dans un formulaire, les séries, les rappels app fermée et le réveil précis (section « Agenda ») ; passé, élaguer la spec.
- Les bugs du téléphone du 2026-10-16 et le changement de config (`docs/design/config-change.md`) : la dernière section de la recette ; passé, élaguer la spec.
- Les sources de l'attente (`docs/design/waiting-sources.md`, branche waiting-sources) : migration 76, la conversation marquée sur le bouton flottant, le résumé d'une routine, sa notification et ses points, le réglage « Me signaler la fin » (section du même nom) ; passé, élaguer la spec.
- Les réglages de réflexion de chaque fournisseur, en envoyant une requête : Claude (Sonnet 5.5 sans réflexion par `between_tools`, Opus 5.5 sans « Sans réflexion », Haiku 4.5 sans effort), OpenAI (un modèle à `none`, un modèle qu'aucun fait ne couvre), DeepSeek (effort obligatoire, sans réflexion) ; le changement de modèle qui vide les deux réglages, l'enregistrement d'une combinaison refusée.
- La lecture seule pour l'IA (`docs/design/unified-fields.md`, branche user-only) : un champ et un critère saisi fermés à l'IA, la validation d'un Objectif fermée à l'IA : la section « Lecture seule pour l'IA » de la recette ; passé, la section de la spec part dans `docs/DATA.md`.
- Les lectures du connecteur entières, refusées ou en tranches (`McpReads`, branche mcp-reads) : sa ligne « Seuil de données à 5 000 » de la section « Accès externe (serveur MCP) ».
- Les textures d'outil et de zone (`docs/design/tool-texture.md`, branche tool-texture) : les tons réglés d'abord au banc des textures (`./run themes`), dans les trois thèmes et les deux modes, puis la section « Les textures » de la recette ; passé, élaguer la spec.
- La texture sur tout le fond de la page et le choix de la couleur et de la texture sur une page du formulaire (branche texture-background, 2026-10-17) : la vue « page » du banc des textures (`./run themes`) d'abord, puis les lignes de la section « Les textures » de la recette qui parlent du fond de page et des pages de choix.
- Les lectures envoyées une fois par session (`SessionReads`, branche read-once) : la section du même nom de la recette.
- Le thème suisse (`docs/design/swiss-theme.md`, branche swiss-theme) : la passe sur le téléphone de `docs/THEMES.md`, puis ce que la spec garde dans « Ce qui reste ouvert ».
- Le thème encre et lavis (`docs/design/ink-theme.md`, branche theme-ink) : la passe sur le téléphone de `docs/THEMES.md`, les icônes au pinceau d'abord (leur coût à l'ouverture d'une page, celles qui se bouchent), puis ce que la spec garde dans « Ce qui reste ouvert ».
- Les incarnations (`docs/design/incarnations.md`, branche incarnations) : la dernière section de la recette, l'identité de Stan Prokopenko importée et le coût d'un tour mesuré pour personae ; passé, élaguer la spec. L'import attend que personae exporte au format 2 du fichier d'identité (`docs/IDENTITY_FILE.md`), le format 1 n'étant plus lu.
- Les bugs du téléphone du 2026-10-17 : la section du même nom de la recette.
- La répétition d'un Objectif, « Une fois » ou « Récurrent » (branche goal-repetition) : migration 79, sa ligne de la section « Objectif » de la recette.
- Les conditions typées à l'écriture (`docs/design/typed-conditions.md`, branche typed-conditions) : la section du même nom de la recette ; passé, élaguer la spec.
- Le gras et l'italique d'un texte lu (branche text-emphasis) : la section du même nom de la recette.
- La démo enrichie (branche demo-guide), jamais installée par les services : « Demo installed » dans le journal, sinon la raison du refus ; puis l'Agenda de Travail (la série du lundi à venir donnée par l'outil, les passées, l'annulée, la retirée, le salon sur deux jours), « Cap sur le semi » (sa tentative ouverte seule, du début à l'échéance), Giulia sur la zone Italien et sa routine, le camembert des heures et sa légende, les étapes gardées d'une séance de fractionné, les étiquettes du carnet et le classement du questionnaire, les textures.

## Conçu, à coder

Une spec existe dans `docs/design/`.

- L'historique des écritures et leur annulation (`docs/design/undo-history.md`) : un pas par opération demandée de l'extérieur, la ligne entière avant et après, écrite par une seule classe que garde un script, 30 jours, le service `history` ouvert à l'IA, son écran.
- Modèles libres (`docs/design/local-models.md`) : le banc est en place et rodé ; la campagne 1 a tourné le 2026-10-04 (190 scénarios en 5 h 15, 1,35 $ comptés hors réponses coupées, tableau dans `bench-results/2026-10-04_1326/summary.md`), ses échecs relus dans `docs/design/bench-campaign-1.md` ; les corrections de l'app qu'elle a trouvées sont faites (2026-10-13) : les niveaux de prompt se décident après une campagne rejouée.

## À concevoir

Une idée sans spec : elle demande une décision avant du code.

### IA et automations

- Le connecteur MCP : sans session, rien n'oblige le client à lire le schéma d'un outil avant d'y écrire (la consigne seule, `ai_chunk_validation_strategy`) ; l'imposer demande de retenir, par connexion, les schémas envoyés.
- Les lectures du connecteur MCP se tiennent au seuil de la conversation (`chat_max_data_chars`, `McpReads`) : un seuil propre au connecteur, réglé par client à côté de son masque (migration 71), la limite de réponse de chaque client étant la sienne — à trancher.
- Le prochain envoi se calcule de deux façons : à la volée pour une automation (`AutomationScheduler`), stocké en base et remis à jour à chaque envoi pour Messages — en garder une.

### Outils et champs

- Des modèles d'outils prédéfinis et paramétrables (« Série de questions » suivie d'un « Avis de l'IA »…).
- Un type de Suivi « étiquette », qui ne note qu'une étiquette.
- Un outil changé dont dépend une condition ailleurs (le Graphique filtre sur « moment = déjeuner », l'option « déjeuner » est retirée du Repas, ou le champ « moment » renommé) : refuser le changement en nommant les outils concernés, ou reporter un renommage dans les conditions, que `ListOrigins` sait distinguer d'une suppression — à trancher ; il faut savoir quelles configs et quelles variables visent un outil.
- Trier Notes, Liste, Journal et Données structurées par un champ (un Tri = un Champ et un sens, brique à faire de celui de Données structurées) : ranger une fois, l'ordre manuel réécrit après confirmation, ou un réglage de l'outil, manuel ou trié — à trancher.

### Interface

- Le thème Signalétique de métro (le suivant du classement de `docs/theme-tracks.json`, Suisse et Encre et lavis faits) : la recette de `docs/THEMES.md`, de l'étape 0.
- Lucide redessiné à leur manière pour le cosy et le suisse, comme le pinceau d'Encre et lavis (`docs/design/ink-theme.md`, ligne 9), un trait à chacun : une fois la brique du cœur qui lit les tracés d'une icône codée et tenue sur le téléphone avec Encre.

### Events du cœur

- Un système d'events du cœur, intégré au planificateur, avec une section par zone : un lien entre outils qui passe par le cœur (par une automation, par exemple). Déclencheurs d'une exécution : `SCHEDULE`, `MANUAL`, `EVENT` — revoir ceux des automations (ce qui est un déclencheur y est traité de façon inégale). Un event peut naître du planning d'un outil, et nomme l'outil qui l'a déclenché pour qu'on y aille en un geste ; des events système, sur le même modèle. Les constantes sont à penser avec. L'Agenda en donne une première source, « un événement commence » (`docs/design/agenda-tool.md`). L'Alerte et l'automation directe le croisent.

### Documentation

- Traduire la doc en anglais.

## Dette de code

Ce qui marche, mais contredit l'architecture ou une règle du projet.

- `CommandExecutor` lit la base directement (`AppDatabase.getDatabase`, les messages d'une session), hors du coordinateur.
- `AutomationScheduler.getNextExecutionForAutomation()` est appelé depuis l'écran (`AutomationCard`, `AutomationScreen`), sans passer par le service.
- `CommandExecutor` remplace un nom ou un `schema_id` manquant par `"unknown"` : un repli que la règle du projet interdit.
- `ToolConfigActions` (`core/ui/components`), utilisé par l'IA, porte un nom qui ne dit pas ce qu'il fait.
- Des conversions JSON → valeurs refaites sur place au lieu de `JsonUtils` : au moins `toValueMap` (`MessagesScreen`), `jsonToValue` (`StateSavers`), `toFieldConfigValue` (`FieldDefinition`) — compter le reste avant de les ramener.
- `SessionCostDisplay` formate coûts et tokens par ses propres fonctions, à côté de `AIFormatUtils`, qui fait la même chose pour les exécutions.
- `shared.xml` (~1 900 lignes) à découper en plusieurs fichiers.
- `RetroPaletteTest` et `CosyPaletteTest` ne tiennent les contrastes qu'au décalage de teinte 0 : les parcourir par pas de 15°, comme `SwissPaletteTest`, et reprendre les nombres d'un décalage qui tombe sous le seuil.
- `TextType.SUBTITLE` n'a pas de rang dans le contrat des thèmes (`UITypes.kt`) : plus grand que `HEADING` en défaut et en cosy, plus petit en rétro, pour 71 usages. Lui donner un rang, aligner les thèmes, puis passer ses usages écran par écran dans les trois thèmes.

- Une apostrophe nue dans un texte qui en a déjà une échappée (`\'`) passe tous les contrôles de `./run test` et casse la compilation des ressources (« Invalid unicode escape sequence »), le générateur n'échappant que les textes qui n'en ont aucune : la refuser dans un contrôle des sources de textes, ou faire échapper toutes les apostrophes au générateur.

## Bugs du téléphone

## En attente d'un déclencheur

- Un filtre qui compare un champ à une variable ou à une lecture (« kcal > objectif_calorique ») : `EntryFilters.parse` le refuse, `ConditionPicker` n'offre qu'une valeur écrite — quand un pointeur ou une variable en a besoin ; le terme se lit alors une fois à la référence du contexte (`TermReader`).


- D'autres sources de l'attente (`docs/BRICKS.md`) : un message de l'IA arrivé dans une session pendant qu'on était ailleurs, signalé par exemple sur le bouton de conversation — à concevoir.
- Choisir une entrée précise comme cible du pointeur dans son sélecteur (conçu dans `docs/design/pointer.md` : étiquette par type d'outil, liste, relecture de l'entrée) — si le besoin apparaît : un pointeur peut désigner une entrée (le Questionnaire en pose un), mais seule l'app le fait. La cible APP attend que l'IA sache lire les réglages de l'app.
- Lire un long texte du connecteur MCP par section (le sommaire de ses titres `##`, puis une section par son titre), en plus des tranches — si l'IA lit mal un bagage d'identité en tranches.
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
