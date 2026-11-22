# Les parcours d'hypnose

Conception du 2026-11-22, à coder. Plusieurs parcours d'hypnose vivent en même temps, autant qu'il en faut, chacun lancé depuis la zone où il sert : un parcours suivi, une thématique à la demande, un type de séance. Le seul point commun est l'API hypnogen.

## Un parcours

- **Un montage complet et indépendant** : ses fiches, son lecteur, ses Tâches ; aucun outil partagé avec un autre parcours.
- **Le squelette fixe** : un questionnaire → la Tâche de fabrication → les fiches → le lecteur (une Séance « Son ou vidéo »). Le questionnaire est le seul lancement : sans horaire, un bouton ; sans questions, un « Fabriquer » ; une réponse ne fait qu'une fiche (`existing` sur la réponse, `then: stop`).
- **Ce qui varie, par parcours** : le contenu et l'horaire du questionnaire, ce qui part à hypnogen, la narratrice et sa voix, ce qu'il retient après, s'il retient quelque chose. Aucune notion du montage actuel (directions, état du jour, Atlas, Vie d'Hélène) n'est un socle.
- **Contre les redites, par défaut** : toutes les séances déjà faites du parcours, une ligne chacune (titre et une ligne), jointes à chaque fabrication.
- **La recette d'un parcours** (une note de Saisons › Organisation, comme « Recette : une bibliothèque jouée ») s'écrit à partir du deuxième parcours monté, pas avant : avec une seule instance, le squelette ne se distingue pas de ce qui lui est propre.

## La mémoire, tenue par la session de fabrication

Plus de routine de mémoire. La session hypnogen qui fabrique la séance N+1 met aussi la mémoire à jour, selon des consignes ; la Tâche range ce qu'elle rend.

- **Le décalage d'une séance** : les retours de la séance N arrivent après sa fabrication ; la session N+1 intègre les retours des séances écoutées depuis, et ce qu'elle crée pour N+1 (ancres posées, faits de la narratrice).
- **En entrée** (le contexte, assemblé) : les consignes du parcours (ce qu'il y a à faire, les règles de chaque outil : « mes mots restent mes mots », une entrée par lieu…), et les matériaux, des pointeurs Lisibles avec la configuration et les entrées identifiées, de n'importe quel type d'outil.
- **En retour** : `retour` (`docs/design/api-return.md`), une clé par outil cible, choisie par le montage ; chacune deux listes, à modifier (avec l'identifiant de l'entrée) et à créer. Une clé `seance` réservée par le contrat de hypnogen.
- **La Tâche range** : une étape par clé, répétée sur sa liste ; « modifier une entrée » prend son entrée dans l'élément (vérifié : `TaskSteps.kt:587-590`, `TaskRunner.kt:485`, `idOf`) ; un identifiant faux fait échouer la Tâche et l'étape répétée défait ce qu'elle avait écrit. Jamais de rapprochement par le nom, qui créerait en silence un double.
- **Les séances à intégrer** : un champ « intégrée » sur les séances du lecteur, le pointeur ne joignant que celles où il est absent, coché par la Tâche après ; il remplace « versée à la mémoire » de la fiche, et chaque réécoute est une séance avec ses retours.
- **Des retours corrigés** après la fabrication suivante ne sont plus repris : accepté.

## Le contrat de hypnogen

- **Valeurs déclarées**, ce que toute séance a par nature : `audio`, `fiche`, `titre`, `duree`.
- **Disparaissent** : `voix` (Treelune l'a envoyée), `memoire` ; `intention`, `metaphore` et la ligne anti-redite passent dans `retour`, demandées par les consignes.
- **Une passe de plus**, après l'écriture de la séance : appliquer les consignes aux matériaux, rendre `retour`. Le temps qu'elle ajoute n'est pas mesuré (10 à 11 min de machine mesurées le 2026-11-08, `timeout_minutes` 45).
- **Une consigne contre les redites** dans sa passe de conception : ne refaire ni un lieu ni une métaphore déjà faits que le contexte nomme, sauf s'il le demande.
- Le changement casse les deux montages qui lisent `intention`, `metaphore`, `voix`, `memoire` (Racines › Hypnose, la zone Hypnose v2) : passés au nouveau contrat avec hypnogen, et `hypnogen/docs/treelune-contract.md` réécrit.

## Ouvert

- Les Portraits : la routine Mémoire en crée depuis les présences rencontrées en hypnose, Préparer joint ceux « à rencontrer en hypnose », l'étape 5 du Rituel dit « jamais les figures des séances d'hypnose » ; à trancher avant de remonter ce parcours.
- Les notes de mémoire actuelles (Atlas, Vie d'Hélène, Ce qui porte, Ce qui ne prend pas, Ancres) : reprises telles quelles comme matériaux du parcours actuel.
