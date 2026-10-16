# Les incarnations — conception

Conçu le 2026-09-27, repassé le 2026-10-16 contre le format d'export de personae, figé depuis. Une incarnation, c'est l'IA de l'app qui parle en tant que quelqu'un : Jung pour l'analyse des rêves, par exemple. Une identité dit **qui** parle ; l'app décide **où** et **comment**.

## Le format lu

Le projet personae (`/mnt/data/OUTILS/personae/docs/reference.md`, « Fichiers ») publie un fichier JSON par identité, `exports/<id>.json` :

- `format`, un entier, 1 au 2026-10-16 : il monte à chaque changement de structure qu'un lecteur doit connaître, et ne bouge pas pour un champ ou un morceau facultatif ajouté ;
- `id` (stable, jamais changé après la première publication), `nom`, `distribuable`, `publie_le` (une date au jour, `2026-10-16`), et `langues.<langue>`, une section par langue, qui se suffit ;
- dans une section : `voix`, `esprit`, `portrait`, chacun `{adresse, contenu}` ; `bagage`, indexé par une clé (`1/lexique@Stan Prokopenko`), chaque ressource `{adresse, titre, description, contenu}` ;
- une ressource absente dans une langue est omise de cette langue : le bagage peut différer d'une langue à l'autre.

Le texte est du Markdown : la voix française de Stan Prokopenko a des titres `#` et `##`, du gras, de l'italique, des listes.

Le premier export, `stan-prokopenko`, est publié le 2026-10-16 (`format` 1, `en` et `fr`, les quatre ressources du bagage dans les deux). Tailles mesurées le même jour (`./run status` de personae) : voix, esprit et portrait font ~30 000 caractères en `en`, ~32 000 en `fr` ; une ressource du bagage, de 12 209 à 21 671.

`distribuable` ne change rien ici : personae ne demande de refuser une identité non marquée qu'au projet qui la distribue, et l'app n'en livre aucune — c'est l'utilisateur qui l'apporte.

## L'identité, un objet de l'app

- Une identité est un objet de l'app, que l'import remplit : un `id`, un nom, une icône et sa couleur, une langue, et pour chaque langue la voix, l'esprit, le portrait et le bagage. L'app garde l'export entier, toutes ses langues.
- **Tout s'édite à la main**, dans l'écran de l'identité, et une identité peut naître sans export. Un import garde l'`id` de personae ; une identité écrite à la main reçoit le sien.
- Une identité écrite à la main demande un nom et une langue ; voix, esprit et portrait sont facultatifs, et un morceau vide ne part pas dans le prompt.
- Chaque morceau importé garde son adresse personae comme origine, montrée sur l'écran de l'identité ; une retouche l'efface.
- **Édition en texte brut seul, lecture mise en forme.** Le lecteur du Guide (`GuideMarkup`) lit aussi les titres `#` à `###` et les listes `* `, et devient le seul lecteur de l'app (Guide, usage d'un outil, identités). Le gras et l'italique sont lus et leurs marques retirées : les thèmes ne dessinent qu'un texte simple (`ThemeContract.Text`), les dessiner demande de l'étendre dans les trois thèmes.
- **L'icône** se prend dans le vocabulaire Lucide, avec une couleur (`icon_color`), comme une zone ou un outil ; par défaut, `user-round`.
- L'écran de l'identité montre la taille de chaque ressource du bagage.

## L'import

- Dans les réglages de l'app, et nulle part ailleurs.
- Un fichier retrouve l'identité déjà là par son `id`. Avant de remplacer, l'app montre les deux `publie_le`, les langues gagnées ou perdues, et nomme les morceaux retouchés à la main qui seront perdus ; l'utilisateur confirme. Un fichier plus ancien est signalé, pas refusé : c'est le retour en arrière.
- **Une réimportation remplace tout**, sans fusion morceau par morceau.
- Un `format` que l'app ne connaît pas est refusé, en le disant. Au `format` connu, un champ attendu absent ou d'un autre type est refusé, le champ en cause nommé ; un champ en plus est ignoré, puisque personae en ajoute sans monter `format`. Jamais d'import partiel.

## La langue

- Un réglage par identité, parmi les langues de l'export : choisi à l'import, la langue de l'app présélectionnée si l'export l'a, l'utilisateur confirmant. Un export d'une seule langue la donne, et l'écran le dit.
- Une réimportation qui perd la langue choisie le dit dans sa confirmation et en fait choisir une autre ; jamais de changement en silence.
- Le bagage de la session est celui de la langue choisie : une ressource absente dans cette langue n'existe pas pour elle.

## Où elle intervient

- **Associée à une zone**, dans les réglages de la zone : une identité au plus par zone ; une identité peut servir dans plusieurs zones. `identity_id` est dans la config de la zone : l'IA l'écrit comme le reste de cette config, au niveau de validation de l'app, qui garde les réglages d'une zone.
- **La tuile d'identité**, en haut de la zone : l'icône de l'identité, son nom, et un bouton qui ouvre une nouvelle conversation avec elle. C'est le geste qui ouvre déjà une conversation depuis une zone (`ChatRequests.open`), avec ses règles (`AIOrchestrator.startNewChatSession`) : une conversation active est arrêtée et reste dans l'historique ; une routine qui tourne est suspendue, et reprend quand la place se libère. L'ouverture ne joint pas les données de la zone : un pointeur s'ajoute d'un geste.
- **Le bouton de conversation flottant ne change pas** : il ouvre la session active, ou propose d'en démarrer une.
- **La puce de la barre du chat**, à droite des enrichissements, montre l'identité de la session. Avant le premier message, elle propose toutes les identités existantes et l'IA ordinaire ; une conversation ouverte par la tuile arrive avec la sienne. Après, elle se voit mais ne change plus, et la toucher dit qu'une autre conversation change d'interlocuteur.
- **L'identité se fixe au premier message** d'une session. Ce qui se fixe, c'est qui parle, pas une version : une identité modifiée ou réimportée vaut dès le tour suivant, dans les sessions en cours aussi.

## Les routines

- Une routine porte `identity_id` à côté de son fournisseur (`Automation.providerId`), vide pour l'IA ordinaire ; chaque exécution le reçoit. Elle garde l'`id` : toute modification de l'identité vaut à l'exécution suivante.
- **À l'écran**, une routine créée dans une zone part avec l'identité de la zone, présélectionnée ; elle ne suit pas le choix de la zone ensuite.
- **L'IA** donne `identity_id` à la création d'une routine (un `id`, ou `null` pour l'IA ordinaire) : absent, la création est refusée, le champ nommé. À la modification, absent ne change rien. Pour choisir, elle voit l'identité d'une zone en lisant la zone, et la liste des identités existantes.

## Ce qu'elle peut faire

- Les mêmes capacités que l'IA ordinaire, sur **toute l'app** : lire, créer, modifier, avec les validations habituelles. L'association à une zone ne limite rien.
- **Sa mémoire, ce sont les données** : ce qu'elle écrit (une interprétation de rêve dans une note) elle le relit aux sessions suivantes. Pas de mémoire d'identité à part.

## Deux rôles dans une réponse

- **L'IA de l'app opère** : elle produit le JSON, choisit les commandes, demande les validations, construit les formulaires.
- **L'identité est l'auteur de ce que l'utilisateur lit** : `pre_text`, `post_text`, les questions d'un module de communication, et le texte des entrées qu'elle écrit.
- **La structure de l'app reste neutre** : les noms et descriptions des zones, des outils et des champs, et le message d'une routine — une consigne à l'IA, l'identité parlant à l'exécution.

## Ce que porte le prompt

- La voix, l'esprit et le portrait, toujours, dans la langue choisie, au L1, juste après le rôle de l'IA, avec la consigne des deux rôles. Le L1 ne change pas au cours d'une session : chez Claude, ils prennent son point de cache (l'app pose déjà les quatre permis, `ClaudeExtensions`).
- La liste du bagage : clé, titre et description de chaque ressource.
- Le prompt étant reconstruit de la base à chaque appel, chaque morceau y est une fois, remplacé quand il change.
- À mesurer sur la première session avec une identité : les tokens d'un tour, en cache ou non (les statistiques de la session ; la taille du L1 dans la ligne « Prompt data built » du journal) — la mesure que personae attend pour décider de versions compactes.

## Le bagage

Il se lit par les commandes de l'identité (« L'identité pour l'IA »), sans réserve à la session de l'identité ; la liste du prompt donne les clés.

## L'identité pour l'IA

Un service `identities`, ouvert à toute IA : l'IA intégrée, en session ou en routine, l'identité incarnée ou non, et une IA extérieure par le connecteur MCP (un outil par commande, comme les autres).

- **Lire** : la liste des identités ; une identité (voix, esprit, portrait, liste du bagage) ; une ressource du bagage par sa clé. La langue se donne en paramètre ; absente, c'est celle de l'identité, et le résultat dit laquelle il a lue.
- **Modifier** : créer une identité ; remplacer un morceau entier (une voix, une ressource) ; ajouter ou retirer une ressource ; changer le nom, l'icône, la langue. Jamais un bout de texte : l'IA renvoie le morceau entier.
- **Validation** : au niveau de l'app (`validate_app`, avec les zones et les groupes de l'accueil). Par le connecteur, une écriture gardée attend l'accord par notification (`McpApprovals`). Sous un masque d'accès (une routine, un client du connecteur), la lecture reste libre, pour qu'une routine incarnée lise son bagage, et l'écriture est refusée, comme celle de l'app (`AccessRules`).
- **Sans limite de taille en conversation** : la limite a été posée pour les données des outils, dont la taille ne se connaît pas d'avance ; celle d'une identité se lit sur son écran, et un morceau trop long se coupe ou se retire à la main. Par le connecteur, une lecture d'identité suit la règle commune des lectures (`McpReads`, `docs/AI.md`, « Seuil de taille des données ») : au-delà du seuil, elle part en tranches.
- **Un même texte n'est envoyé qu'une fois par session**, comme un schéma (`CommandExecutor`, résultat `CACHED`) : une seconde demande sur une clé dont le texte n'a pas changé répond « déjà dans cette session » ; un texte changé depuis (par l'IA ou à l'écran) est renvoyé en entier, le résultat disant qu'il remplace la lecture précédente. Un changement fait à l'écran n'est pas signalé de lui-même : l'IA relit quand on le lui dit. Le connecteur, sans session, n'a pas ce garde.
- Une modification par l'IA compte comme une retouche à la main : une réimportation la nomme parmi les pertes.
- `app_context`, le contexte de l'app que lit le connecteur, porte la liste des identités et l'identité de chaque zone, comme l'instantané de l'app que lit l'IA intégrée.
- Par le connecteur, l'IA extérieure peut incarner une identité qu'elle lit : la consigne des deux rôles part avec la lecture, mais l'app ne peut pas l'y tenir.

## Suppression

- Un seul contrôle, partagé avec les fournisseurs d'IA (`AIProviderConfigService.deleteProviderConfig`, qui ne regarde rien aujourd'hui) : la suppression est **refusée tant qu'une zone ou une routine la nomme**, en les listant. Chacun déclare ses champs ; la zone ne compte que pour l'identité. Pas de suppression en cascade.
- **Une session passée** dont l'identité ou le fournisseur n'existe plus reste lisible ; sa barre du chat est remplacée par le message qui dit pourquoi, et on n'y écrit plus. Jamais de reprise sans voix.

## Ce qui reste à mesurer

- Le coût d'un tour avec une identité, en cache ou non, sur la première session : personae n'en publiera de versions compactes qu'après, et n'a pas tranché qui les produirait (mediatheque ou personae). Elles monteront `format`.
- Le reste se vérifie sur le téléphone : la recette (`docs/design/device-checks.md`) prend une section des incarnations, la mesure du coût d'un tour comprise ; un chapitre du Guide les présente.
