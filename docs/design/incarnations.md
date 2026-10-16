# Les incarnations — conception

Conçu le 2026-09-27, repassé le 2026-10-16 contre le format d'export de personae, figé depuis. Une incarnation, c'est l'IA de l'app qui parle en tant que quelqu'un : Jung pour l'analyse des rêves, par exemple. Une identité dit **qui** parle ; l'app décide **où** et **comment**.

## Le format lu

Le projet personae (`/mnt/data/OUTILS/personae/docs/reference.md`, « Fichiers ») publie un fichier JSON par identité, `exports/<id>.json` :

- `id` (stable, jamais changé après la première publication), `nom`, `distribuable`, `publie_le` (une date au jour, `2026-10-16`), et `langues.<langue>`, une section par langue, qui se suffit ;
- dans une section : `voix`, `esprit`, `portrait`, chacun `{adresse, contenu}` ; `bagage`, indexé par une clé (`1/lexique@Stan Prokopenko`), chaque ressource `{adresse, titre, description, contenu}` ;
- une ressource absente dans une langue est omise de cette langue : le bagage peut différer d'une langue à l'autre.

Le texte est du Markdown : la voix française de Stan Prokopenko a des titres `#` et `##`, du gras, de l'italique, des listes.

Tailles mesurées le 2026-10-16 (`./run status` de personae, stan-prokopenko, rien de publié) : voix, esprit et portrait font ~30 000 caractères en `en`, ~32 000 en `fr` ; une ressource du bagage, de 12 209 à 21 671.

`distribuable` ne change rien ici : personae ne demande de refuser une identité non marquée qu'au projet qui la distribue, et l'app n'en livre aucune — c'est l'utilisateur qui l'apporte.

## L'identité, un objet de l'app

- Une identité est un objet de l'app, que l'import remplit : un `id`, un nom, une icône et sa couleur, une langue, et pour chaque langue la voix, l'esprit, le portrait et le bagage. L'app garde l'export entier, toutes ses langues.
- **Tout s'édite à la main**, dans l'écran de l'identité, et une identité peut naître sans export. Un import garde l'`id` de personae ; une identité écrite à la main reçoit le sien.
- **Édition en texte brut seul, lecture mise en forme.** Le lecteur du Guide (`GuideMarkup` : `##`, `- `, `{icône}`) ne connaît ni `#`, ni le gras, ni l'italique : il s'étend, et devient le seul lecteur de l'app (Guide, usage d'un outil, identités).
- **L'icône** se prend dans le vocabulaire Lucide, avec une couleur (`icon_color`), comme une zone ou un outil ; par défaut, `user-round`.
- L'écran de l'identité montre la taille de chaque ressource du bagage.

## L'import

- Dans les réglages de l'app, et nulle part ailleurs.
- Un fichier retrouve l'identité déjà là par son `id`. Avant de remplacer, l'app montre les deux `publie_le`, les langues gagnées ou perdues, et nomme les morceaux retouchés à la main qui seront perdus ; l'utilisateur confirme. Un fichier plus ancien est signalé, pas refusé : c'est le retour en arrière.
- **Une réimportation remplace tout**, sans fusion morceau par morceau.
- Un fichier dont la structure ne correspond pas est refusé, le champ en cause nommé ; jamais d'import partiel. Le `format` de l'export (« Ce qui attend personae ») dit la structure attendue ; un `format` inconnu est refusé.

## La langue

- Un réglage par identité, parmi les langues de l'export : choisi à l'import, la langue de l'app présélectionnée si l'export l'a, l'utilisateur confirmant. Un export d'une seule langue la donne, et l'écran le dit.
- Une réimportation qui perd la langue choisie le dit dans sa confirmation et en fait choisir une autre ; jamais de changement en silence.
- Le bagage de la session est celui de la langue choisie : une ressource absente dans cette langue n'existe pas pour elle.

## Où elle intervient

- **Associée à une zone**, dans les réglages de la zone : une identité au plus par zone ; une identité peut servir dans plusieurs zones.
- **La tuile d'identité**, en haut de la zone : l'icône de l'identité, son nom, et un bouton qui ouvre une nouvelle conversation avec elle. C'est le geste qui ouvre déjà une conversation depuis une zone (`ChatRequests.open`), avec ses règles : une conversation active est arrêtée et reste dans l'historique ; une routine qui tourne fait demander s'il faut l'interrompre. L'ouverture ne joint pas les données de la zone : un pointeur s'ajoute d'un geste.
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
- À mesurer sur la première session avec une identité : les tokens d'un tour, en cache ou non — la mesure que personae attend pour décider de versions compactes.

## Le bagage

- Une commande de lecture par clé, ouverte à la seule session qui a une identité, sur cette identité et sa langue.
- **Sans limite de taille** : la limite d'une conversation (`chat_max_data_chars`, 15 000 par défaut) a été posée pour les données des outils, dont la taille ne se connaît pas d'avance ; celle d'une ressource se lit sur l'écran de l'identité, et une ressource trop longue se coupe ou se retire à la main.
- **Une lecture par session et par clé**, comme un schéma (`CommandExecutor`, résultat `CACHED`) : une seconde demande répond « déjà dans cette session ». Une ressource modifiée en cours de session garde dans celle-ci le texte déjà lu.

## Suppression

- Un seul contrôle, partagé avec les fournisseurs d'IA (`AIProviderConfigService.deleteProviderConfig`, qui ne regarde rien aujourd'hui) : la suppression est **refusée tant qu'une zone ou une routine la nomme**, en les listant. Chacun déclare ses champs ; la zone ne compte que pour l'identité. Pas de suppression en cascade.
- **Une session passée** dont l'identité ou le fournisseur n'existe plus reste lisible ; sa barre du chat est remplacée par le message qui dit pourquoi, et on n'y écrit plus. Jamais de reprise sans voix.

## Ce qui attend personae

- **Un champ `format` à la racine de l'export**, demandé le 2026-10-16 : un entier, 1 aujourd'hui, monté à chaque changement de structure qu'un lecteur doit connaître (les versions compactes de « Poids », par exemple). L'import l'attend.
- **Un premier export publié** (aucun au 2026-10-16 : pas de dossier `exports/`, une seule fiche, stan-prokopenko) : pour essayer l'import sur un vrai fichier, et la mesure d'un tour.

Le reste se code sans export : une identité écrite à la main suffit.
