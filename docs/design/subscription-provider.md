# Le fournisseur « Abonnement Claude »

Conception du 2026-11-08. Un fournisseur d'IA qui fait tourner les sessions de Treelune sur l'abonnement Claude de l'utilisateur (Pro, Max), sans clé d'API : Treelune lance le programme `claude` (Claude Code) sur une machine GitHub Actions d'un dépôt privé de l'utilisateur, et lui parle par des fichiers sur ce dépôt. Pour tout utilisateur, routines d'abord, conversations aussi.

## Ouvert : à vérifier avant de coder

Rien de ce qui suit n'est mesuré. Un dépôt d'essai, un workflow à la main, avant toute ligne de Kotlin.

1. Le démarrage d'un job, de l'appel qui le lance au fichier « prêt » : la file d'attente de GitHub, l'installation de Claude Code (un cache des Actions la raccourcit-il ?).
2. `claude -p --input-format stream-json --output-format stream-json --tools "" --system-prompt …` : plusieurs tours dans un seul processus, une image dans un tour, aucun outil de Claude Code utilisable.
3. Le cache : les jetons lus en cache d'un tour à l'autre, dans la réponse JSON de `claude`.
4. Une requête conditionnelle (`If-None-Match`) qui répond 304 ne compte pas dans les limites de l'API GitHub, ni pour le jeton de l'utilisateur ni pour le `GITHUB_TOKEN` du job (environ 1 000 appels par heure et par dépôt).
5. Les minutes d'un dépôt privé sur un compte gratuit (2 000 par mois, arrondies à la minute par job), la durée maximale d'un job (6 h).
6. L'identifiant de l'exécution rendu par l'appel qui lance un workflow (`workflow_dispatch`) ; sinon, l'exécution retrouvée par l'identifiant de session passé en entrée.
7. Ce que `claude` dit d'un jeton expiré ou révoqué, d'une limite d'abonnement atteinte (l'heure de reprise y est-elle ?), d'une option qu'il ne connaît plus : de quoi les reconnaître sans lire une phrase.
8. Le délai avant que GitHub efface les commits d'une branche supprimée.

## Ce qui est permis

Lu le 2026-11-08 sur `code.claude.com/docs/en/legal-and-compliance` :

- Interdit : *« Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through … plan credentials on behalf of their users »* ; *« developers may not collect, store, or intermediate Claude.ai credentials or session tokens »*. Les utilisateurs de l'Agent SDK sont rangés parmi ces développeurs.
- Permis : *« Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription »*.
- Le nom : on peut dire en texte simple qu'un produit « runs Claude Code », pas mettre « Claude Code » ni « Anthropic » dans le nom d'une fonction.

D'où la règle de fondation : **le jeton Claude ne passe jamais par Treelune.** L'utilisateur le crée lui-même (`claude setup-token`) et le range lui-même en secret de son dépôt ; Treelune ne tient qu'un jeton GitHub. Le programme `claude` tourne tel qu'Anthropic le publie, jamais patché.

## Décisions

- **Le lieu : GitHub Actions.** Toujours disponible, le même pour tous. Termux est écarté : depuis la 2.1.113, Claude Code ne tourne sur Android que patché, ce qui le sort de *« unmodified »*, ou figé à la 2.1.112, qui vieillit. Un ordinateur de l'utilisateur n'est pas toujours allumé.
- **Un job éveillé par session.** Le démarrage (environ une minute, non mesuré) se paie une fois : le job tient un seul processus `claude` en flux (`stream-json`), le nourrit tour après tour, et s'arrête après 10 minutes sans message ou à la fin de la session.
  - Une routine lance son job à son lancement.
  - Une conversation lance le sien dès qu'elle s'ouvre, pour que la minute passe pendant qu'on tape.
- **Un fournisseur comme les autres.** `claude` ne fait que répondre du texte (`--tools ""`) : toute la mécanique de l'app (commandes, budget, masque, accords, historique) reste dans l'app. Le connecteur MCP n'est pas utilisé.
- **Claude Code tient l'historique et le cache.** L1 et L2 partent au démarrage, en `--system-prompt` ; l'app n'envoie ensuite que le nouveau tour (les messages depuis la dernière réponse, le message daté). L3 ne bouge pas pendant une session (photographié au premier message, `PromptManager`). L1 est reconstruit à chaque appel mais ne dépend que du type de session et de l'identité. L2 est relu à chaque appel : quand il diffère de celui déjà envoyé, il part dans le tour suivant, précédé de la phrase qui dit qu'il remplace le précédent, comme une lecture renvoyée (`SessionReads`, `ai_read_replaces`). À surveiller : une IA qui suivrait le L2 du prompt système plutôt que le plus récent.
- **Une seule route, des fichiers sur une branche.** Une branche par session ; un fichier par tour (`0003-app.json`, `0004-claude.json`), les images à côté ; un fichier « prêt » déposé par le job au démarrage. L'app et le job interrogent la branche toutes les 2 s, en requêtes conditionnelles ; l'app seulement quand elle attend une réponse. La branche est supprimée à la fin de la session.
- **L'app écrit le workflow.** L'utilisateur crée un dépôt privé vide ; l'app y pose `.github/workflows/treelune.yml` par l'API et le réécrit quand sa version en porte un autre. Le texte du workflow vit dans le code de Treelune, le protocole entre eux est donc toujours celui de l'app installée.
- **Le job installe la dernière version de Claude Code.** Une version trop vieille qui ne s'authentifierait plus bloquerait l'utilisateur jusqu'à la version suivante de Treelune ; une option qui change se voit et se dit.
- **La mise en place.**
  1. Un dépôt privé vide.
  2. `claude setup-token` sur un ordinateur (une fois par an : le jeton dure un an), le jeton rangé en secret du dépôt, `CLAUDE_CODE_OAUTH_TOKEN`. Piste pour qui n'a qu'un téléphone : un Codespace, si la connexion y passe.
  3. Un jeton GitHub limité à ce dépôt (Actions, Contents, Workflows), saisi dans la config du fournisseur.
- **La config.** Le dépôt, le jeton GitHub (`secret`), le modèle, la date de création du jeton Claude (aujourd'hui par défaut) : l'app prévient deux semaines avant ses un an.
- **Le modèle : les alias** `opus`, `sonnet`, `haiku`, en liste fixe. L'app ne peut pas lister les modèles (elle n'a pas le jeton), et un alias suit de lui-même le dernier modèle de sa famille. Le modèle servi se lit dans la réponse de `claude`.
- **Les échecs, dans les catégories qui existent** (`AIFailure`), chacun avec son message :
  - le jeton expiré ou révoqué : `CONFIG`, « relance `claude setup-token` sur ton ordinateur et remplace le secret du dépôt » ;
  - la limite de l'abonnement atteinte : `REFUSED`, avec l'heure de reprise si `claude` la donne ;
  - la machine qui ne démarre pas en 5 minutes (fixes, à revoir sur les temps mesurés) : `REFUSED` ;
  - Claude Code qui a changé : `CONFIG`, « mets Treelune à jour ».
- **Rien dans le code commun.**
  - Le coût compté est 0 $ : le budget du jour ne bloque jamais ce fournisseur, et l'équivalent API rendu par `claude` (`total_cost_usd`) n'est pas affiché.
  - La tuile d'une routine dit « en cours », comme avec les autres fournisseurs.
- **Le nom du type : « Abonnement Claude ».** Sa description : « Fait tourner Claude Code sur un dépôt GitHub à toi, avec ton abonnement. Tes conversations passent par ce dépôt privé. »
- **Les données chez GitHub.** Pas de chiffrement : le job déchiffrerait sur la machine de GitHub, et celui qui choisit ce fournisseur confie déjà ses données à Anthropic. Le job n'écrit aucun contenu dans ses journaux, seulement des états, et l'app règle leur conservation au minimum du dépôt (1 jour).

## Écarté

- Termux, et la machine virtuelle d'Android tant qu'une autre app ne peut pas y lancer une commande.
- Un job par échange : une minute de démarrage à chaque aller-retour.
- Un agent dehors, `claude` qui tient toute la routine et agit par le connecteur MCP.
- Des commentaires d'issue comme boîte aux lettres : trop petits pour le premier tour, sans images ; une seconde route pour la même chose.
- Un dépôt modèle copié par l'utilisateur : sa copie ne suit pas l'app.
- Une version de Claude Code figée dans le workflow.
- Un état du fournisseur sur la tuile (« Démarrage de la machine… ») : un canal commun pour un seul fournisseur, à rouvrir quand un second fournisseur lent le demandera.
