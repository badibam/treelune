# Le dépôt GitHub de l'utilisateur : fournisseur et recettes

Conception du 2026-11-08. Un dépôt GitHub privé de l'utilisateur, où Treelune fait tourner des jobs GitHub Actions, pour deux usages qui partagent le dépôt, sa mise en place et son canal :

- le fournisseur d'IA « Recette sur GitHub » : un fournisseur générique, qui dépose chaque tour sur une branche et lit la réponse d'une recette de session du dépôt ; l'app ne sait rien du programme qui répond. Pour l'usage personnel visé, cette recette, dans le dépôt de l'utilisateur et jamais fournie par l'app, lance `claude` sur son propre abonnement ; routines d'abord, conversations aussi ;
- l'API « Recette sur GitHub » : un travail long fait sur la machine de GitHub (une séance d'hypnose fabriquée par hypnogen), appelé par une Tâche comme une API, sans hébergement à soi.

## Ouvert : à vérifier avant de coder

Un dépôt d'essai, des workflows lancés à la main, avant toute ligne de Kotlin. Ce qui n'est pas marqué mesuré ou réglé ne l'est pas.

1. Le démarrage d'un job, de l'appel qui le lance au fichier « prêt ». Mesuré une fois le 2026-11-08 (essai de la recette) : la machine tourne 9 s après l'appel, Claude Code s'installe en 20 s ; mesuré le 2026-11-22 (run 45072763822) : 4 s de l'appel au job, 15 s d'installation, 1,7 s pour le premier tour. Reste la file d'attente de GitHub à d'autres heures.
2. `claude -p --input-format stream-json --output-format stream-json --verbose --tools "" --system-prompt …` : mesuré le 2026-11-22, sur le PC puis sur GitHub avec le jeton `setup-token`, trois tours dans un seul processus resté vivant, 20 s puis 90 s d'attente, aucun outil. Reste : une image dans un tour, une attente plus longue.
3. Le cache : mesuré le 2026-11-22 sur GitHub (Haiku 5.5, prompt système d'environ 37 500 jetons) : tour 1, 37 526 écrits, 0 lu ; tour 2 après 20 s, 37 160 lus, 406 écrits ; tour 3 après 90 s, 37 541 lus, 65 écrits. Non mesuré : au-delà de 90 s d'attente, donc la durée de vie du cache.
4. Une requête conditionnelle (`If-None-Match`) qui répond 304 ne compte pas dans les limites de l'API GitHub, ni pour le jeton de l'utilisateur ni pour le `GITHUB_TOKEN` du job (environ 1 000 appels par heure et par dépôt).
5. Les minutes d'un dépôt privé sur un compte gratuit (2 000 par mois, arrondies à la minute par job), la durée maximale d'un job (6 h). Mesuré le 2026-11-08 : une séance d'hypnose de 21 min 36 s, un job de 10 min 48 s, 11 minutes décomptées. Les limites de l'abonnement Claude, elles, ne sont pas mesurées.
6. Réglé le 2026-11-08 : l'appel qui lance un workflow (`POST …/dispatches`) avec `return_run_details: true` répond 200 avec `workflow_run_id`.
7. Ce que `claude` dit d'un jeton expiré ou révoqué, d'une limite d'abonnement atteinte (l'heure de reprise y est-elle ?), d'une option qu'il ne connaît plus : de quoi les reconnaître sans lire une phrase.
8. Le délai avant que GitHub efface les commits d'une branche supprimée.
9. Réglé le 2026-11-08 sur `badibam/treelune-worker` : hypnogen fait en recette par `app/src/main/assets/github/treelune-recipe.yml`, `claude -p` avec ses outils sur le jeton de l'abonnement (Opus 5.5, 17 tours, 9 min 51 s), la clé Inworld lue dans l'environnement du job, ffmpeg installé par apt (absent de la machine) ; une séance jugée bonne à l'écoute, sur le contexte de test.
10. Les noms des secrets d'un dépôt lus par le jeton GitHub (`GET /actions/secrets`, la permission « Secrets » en lecture), sans leurs valeurs.
11. Un audio de quelques dizaines de Mo sur une branche, lu par l'app (l'API des contenus de GitHub, ou l'adresse brute du fichier). Mesuré le 2026-11-08 : 6,1 Mo d'Opus pour 21 min 36 s, écrit par le `git push` du job et relu par `git fetch` ; l'API des contenus pas essayée.

## Ce qui est permis

Lu le 2026-11-08 sur `code.claude.com/docs/en/legal-and-compliance` et `code.claude.com/docs/en/authentication` :

- Interdit : *« Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through … plan credentials on behalf of their users »* ; *« developers may not collect, store, or intermediate Claude.ai credentials or session tokens »*. Les utilisateurs de l'Agent SDK sont rangés parmi ces développeurs.
- Permis : *« Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription »*. Le jeton de `claude setup-token` (un an) est fait *« for CI pipelines and scripts where browser login isn't available »*.
- Les limites des abonnements *« assume ordinary, individual usage of Claude Code and the Agent SDK »*.
- Le nom : on peut dire en texte simple qu'un produit « runs Claude Code », pas mettre « Claude Code » ni « Anthropic » dans le nom d'une fonction.

D'où la règle de fondation : **le jeton Claude ne passe jamais par Treelune.** L'utilisateur le crée lui-même (`claude setup-token`) et le range lui-même en secret de son dépôt ; Treelune ne tient qu'un jeton GitHub. Le programme `claude` tourne tel qu'Anthropic le publie, jamais patché.

Relu le 2026-11-22, mêmes textes. D'où une seconde règle : **l'app publiée ne propose ni « Abonnement Claude » ni workflow qui lance `claude`.** Le texte interdit à un développeur de *« route requests through Free, Pro, Max, or Ultra plan credentials on behalf of their users »*. L'usage visé est celui de l'utilisateur sur son propre compte, comme ses recettes : sa recette de session reste dans son dépôt, Treelune ne la distribue pas. Une recette partagée qui lance `claude -p` sur l'abonnement d'un autre se regarde à la lumière de ce même texte.

## Le dépôt et son canal

- **Le lieu : GitHub Actions.** Toujours disponible, le même pour tous. Termux est écarté : depuis la 2.1.113, Claude Code ne tourne sur Android que patché, ce qui le sort de *« unmodified »*, ou figé à la 2.1.112, qui vieillit. Un ordinateur de l'utilisateur n'est pas toujours allumé.
- **Réglages › GitHub** : le dépôt et le jeton GitHub (`secret`), un seul dépôt. Le fournisseur et les recettes lisent ce réglage, sans copie à eux ; créer le premier d'entre eux renvoie à ce réglage s'il est vide.
- **La mise en place.**
  1. Un dépôt privé vide.
  2. `claude setup-token` sur un ordinateur (une fois par an : le jeton dure un an), le jeton rangé en secret du dépôt, `CLAUDE_CODE_OAUTH_TOKEN`. Piste pour qui n'a qu'un téléphone : un Codespace, si la connexion y passe.
  3. Un jeton GitHub limité à ce dépôt (Actions, Contents, Workflows, Secrets en lecture), saisi dans Réglages › GitHub.
  4. Les secrets que demande une recette (la clé Inworld pour hypnogen), rangés par l'utilisateur dans le même dépôt.
- **L'app écrit les workflows.** Elle pose dans le dépôt, par l'API, `.github/workflows/treelune-session.yml` (le fournisseur, qui lance la recette de session sans rien savoir de ce qu'elle exécute) et `.github/workflows/treelune-recipe.yml` (les recettes), et les réécrit quand sa version en porte d'autres. Leur texte vit dans le code de Treelune : le protocole entre eux est toujours celui de l'app installée.
- **Une recette qui demande Claude Code en installe la dernière version.** Une version trop vieille qui ne s'authentifierait plus bloquerait l'utilisateur jusqu'à la version suivante de Treelune ; une option qui change se voit et se dit.
- **Une seule route, des fichiers sur une branche.** Une branche par session du fournisseur, une par appel d'une recette. L'app et le job y déposent leurs fichiers et les interrogent en requêtes conditionnelles. La branche est supprimée à la fin de la session ; celle d'un appel de recette un jour après son départ, à l'appel suivant, ses fichiers étant lus par les étapes qui suivent dans la Tâche (l'heure de départ est dans son nom, `treelune/call-<millisecondes>-<16 hex>`).
- **Les échecs** : la machine qui ne démarre pas en 5 minutes (fixes, à revoir sur les temps mesurés) est de l'app ; le reste (jeton expiré, limite d'abonnement, programme qui a changé) est dit par la recette, dans le champ `failure` de sa réponse, avec son message.
- **Les données chez GitHub.** Pas de chiffrement : le job déchiffrerait sur la machine de GitHub, et celui qui choisit ce dépôt confie déjà ses données à Anthropic. Le job n'écrit aucun contenu dans ses journaux, seulement des états, et l'app règle leur conservation au minimum du dépôt (1 jour).

## Le fournisseur « Recette sur GitHub »

Récrit le 2026-11-22, pas encore codé. L'app ne connaît ni Claude Code, ni son jeton, ni ses modèles.

- **Un job éveillé par session.** Le démarrage se paie une fois : le job tient le processus de la recette et le nourrit tour après tour, jusqu'à la fin de la session ou à un délai sans message. Une routine lance son job à son lancement, une conversation dès qu'elle s'ouvre.
- **Chaque minute d'éveil est décomptée** des 2 000 minutes gratuites par mois : avec 10 minutes d'attente, une conversation en coûte une dizaine au moins. Le délai est à choisir.
- **La langue du contrat : le format de message de l'API Messages, tour par tour**, que l'app sait déjà produire (texte et images). Le prompt système part une seule fois, dans le premier fichier.

```json
// 0003-app.json
{"message": {"role": "user", "content": [{"type": "text", "text": "…"}]}}

// 0004-reply.json
{"text": "…", "model": "…", "usage": {"input": 0, "output": 0, "cache_read": 0, "cache_write": 0}}
```

- **Une erreur** : `{"failure": "CONFIG", "message": "…"}`, dans les catégories d'`AIFailure`.
- **Le programme de la recette tient l'historique et le cache** : l'app n'envoie que le nouveau tour et suit ce qu'elle a déjà envoyé. Un job mort en cours de session : un nouveau job, à qui elle renvoie tout l'historique d'un coup.
- **L2 relu à chaque appel** : quand il diffère de celui déjà envoyé, il part dans le tour suivant, précédé de la phrase qui dit qu'il remplace le précédent (`SessionReads`, `ai_read_replaces`). L3 ne bouge pas pendant une session. À surveiller : une IA qui suivrait le L2 du prompt système plutôt que le plus récent.
- **Un fichier par tour sur la branche de la session**, un fichier « prêt » au démarrage. L'interrogation toutes les 2 s, l'app seulement quand elle attend une réponse.
- **Un fournisseur comme les autres** : toute la mécanique (commandes, budget, masque, accords, historique) reste dans l'app. Le coût compté est 0 $, le budget du jour ne le bloque pas.
- **Dans la recette de session de l'utilisateur, pas dans l'app** : le modèle, le jeton et sa date d'expiration, la traduction vers `claude --input-format stream-json`, les erreurs propres à Claude.

### À décider à la reprise

1. Comment le workflow parle à la commande de la recette (un message par ligne sur son entrée, une réponse par ligne sur sa sortie ?), et comment `recipe.json` déclare une recette de session.
2. Le délai d'attente du job, à choisir entre le coût en minutes et la reprise sans redémarrage.
3. Comment le job surveille la branche sans dépasser les limites de l'API GitHub (`git fetch` plutôt que l'API, point 4 de « Ouvert »).

## L'API « Recette sur GitHub »

- **Une recette, c'est un dossier du dépôt**, `recipes/<nom>/`, et une API de Treelune en désigne une. Son `recipe.json` déclare la commande à lancer, la durée maximale, ce que la machine doit installer (`claude` par exemple), les secrets qu'elle demande, et les entrées et valeurs de l'API, que l'API reprend : l'utilisateur n'en règle aucune.
- **Le job ne sait rien des IA.** Il lance la commande de la recette ; une recette qui veut Claude lance elle-même `claude -p`, avec ses outils, ou un autre programme, ou aucun.
- **Un appel, un job.**
  1. L'app crée la branche de l'appel, y dépose `input.json` (toutes les entrées nommées de l'API en un objet), et lance le workflow avec le nom de la branche et de la recette. Le contexte passe par le fichier : aucune limite de taille des paramètres d'un workflow.
  2. Le job installe ce que la recette demande, ne lui donne que les secrets qu'elle déclare, lance sa commande dans la durée maximale (celle de la recette, plafonnée par le workflow), et dépose sur la branche ce qu'elle a écrit.
  3. L'app interroge la branche jusqu'à `result.json`, dont les étapes suivantes de la Tâche téléchargent les fichiers avec le jeton.
- **`result.json`** : `status` (`done` ou `failed`), `message` (une phrase, à l'échec), `values` (les valeurs de l'API ; une valeur de type fichier est le nom d'un fichier posé à côté). Une commande qui sort sans l'écrire, ou qui dépasse sa durée : le job l'écrit `failed`, avec la raison.
- **Pas d'annulation** : la durée maximale borne un job qui ne finit pas.
- **Mettre au point une recette** se fait sur l'ordinateur, dans son propre dépôt (hypnogen et son `./run`) ; la publier, c'est pousser son dossier dans `recipes/<nom>/` du dépôt de l'utilisateur (`./run publish` de la recette).
- **La partager, c'est l'exporter.** Une partie exportée qui contient l'API emporte les fichiers de la recette, lus dans le dépôt au moment de l'export ; l'import les écrit dans `recipes/<nom>/` du dépôt de qui importe : une API remplacée remplace sa recette, une API nouvelle n'écrase jamais une recette différente du même nom (l'import est refusé avant toute écriture, en le disant) et laisse une recette identique telle quelle. Une mise à jour est un nouvel export.
- **Les secrets manquants** : la carte de l'API dit « Secret `INWORLD_KEY` à poser », d'après la liste du `recipe.json` comparée aux noms des secrets du dépôt.
- **Première recette : hypnogen.** Son entrée `context` (le texte qu'envoie la Tâche aujourd'hui) ; ses valeurs, la fiche et l'audio en Opus. Elle reprend sa chaîne actuelle, sa livraison écrivant `result.json` au lieu d'envoyer à l'hébergement OVH, qui reste en place jusqu'à la recette vue sur une séance réelle.

## Écarté

- Termux, et la machine virtuelle d'Android tant qu'une autre app ne peut pas y lancer une commande.
- Un job par échange avec le fournisseur : une minute de démarrage à chaque aller-retour.
- Un agent dehors, `claude` qui tient toute la routine et agit par le connecteur MCP : l'app garde sa mécanique. Il reste possible à l'utilisateur, hors de ce fournisseur.
- Un type de fournisseur « Abonnement Claude » dans l'app publiée (« Ce qui est permis »).
- Des commentaires d'issue comme boîte aux lettres : trop petits pour le premier tour, sans images ; une seconde route pour la même chose.
- Un dépôt modèle copié par l'utilisateur, même public pour les recettes : sa copie ne suit pas l'app.
- Une version de Claude Code figée dans le workflow.
- Un état du fournisseur sur la tuile (« Démarrage de la machine… ») : un canal commun pour un seul fournisseur, à rouvrir quand un second fournisseur lent le demandera.
- La tâche cloud de Claude pour les recettes : elle ne rend rien qu'on puisse lire sans une boîte aux lettres à côté (l'hébergement OVH aujourd'hui).
- Le résultat d'une recette en fichier joint à l'exécution : GitHub le livre en zip, et ce serait une seconde route à côté de la branche.
- Un dépôt par recette lu par une clé de déploiement, et la liste des recettes dans un fichier du dépôt : la recette voyage dans l'export de Treelune, et une API la désigne.
- Le type d'API générique pour une recette : il n'envoie pas d'objet imbriqué, n'a pas d'identifiant à lire au lancement, et n'ouvre aucun fichier.
