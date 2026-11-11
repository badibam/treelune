# L'étape « Assembler » d'une Tâche

Conception du 2026-11-11, à implémenter puis élaguer. Elle se sert du tri et de la limite de la sélection (`docs/BRICKS.md`) pour les 3 dernières Escales.

## Le besoin

« Préparer la séance » est une routine dont l'IA ne fait que recopier : une dizaine de sources (le Programme, les notes de Mémoire du parcours, les Escales, les retours de la dernière séance, le sommeil, les portraits, la réponse du jour) mises bout à bout dans le contexte d'une fiche de Séances d'hypnose, qu'une Tâche envoie à hypnogen. Elle paie deux fois tout le texte, à la lecture et à la réécriture, pour un travail sans jugement. Une Tâche ne sait pas faire ce travail : une entrée d'étape est une valeur seule, jamais une valeur faite de plusieurs morceaux.

hypnogen reste générique : c'est une recette qu'une partie exportée emporte, chez quelqu'un dont le montage n'est pas le même. Ce qui dépend du montage (quelles notes, quels portraits) reste dans l'app.

## L'étape

Une étape d'un nouveau genre, qui assemble un **objet à clés** et le rend aux étapes suivantes (`{"result": {"step": n}}`). Elle n'écrit rien. Son exécution montre l'objet assemblé, même quand une étape suivante échoue.

Une clé, nommée par l'utilisateur, reçoit une seule valeur, de l'une de ces sortes :

1. **un texte écrit** ;
2. **un pointeur** : une sélection d'entrées (tri et limite compris), rendue en liste d'entrées, ou en une entrée pour une entrée désignée ;
3. **une valeur de la Tâche** : une entrée d'étape telle qu'elle existe (ce que le déclencheur a donné, une variable, une lecture, le résultat d'une étape précédente) ; une entrée y est rendue comme par un pointeur ;
4. **un objet** : d'autres clés, la même règle à chaque niveau, sans limite de profondeur.

Plusieurs choses sous une clé s'écrivent par un sous-objet. Pas de liste écrite à la main : un pointeur donne une liste, des clés nommées font le reste. Dans l'éditeur, un objet s'ouvre sur sa page, sous le fil de ses clés.

## Le rendu d'un pointeur

Chaque pointeur choisit son rendu, et garde les deux cases qu'il a déjà dans le message d'une routine (`attach: {config, entries}`).

- **Lisible**, par défaut dans cette étape : chaque champ gardé sous son libellé ; une échelle avec sa légende (« 2 sur 5 (Trop lent) ») ; un choix par son option ; une référence par le nom de ce qu'elle désigne ; une date lisible ; les champs vides absents ; **l'identifiant de chaque entrée, toujours**, et rien d'autre de technique. La config cochée, l'outil se réduit à son nom, sa description, et pour chaque champ gardé sa description ou ce que disent ses valeurs (les bornes d'une échelle, les options d'un choix) :
  ```json
  {
    "Outil": "Hypnose",
    "Description": "…",
    "Champs": { "Rythme": "de 1 (Trop lent) à 5 (Trop rapide)" },
    "Entrées": [ { "id": "…", "Date": "09/11/2026 21:40", "Rythme": "2 sur 5 (Trop lent)" } ]
  }
  ```
  Les mots de l'enveloppe (« Outil », « Description », « Champs », « Entrées ») dans la langue de l'app. Le chemin d'un champ n'y est pas : qui veut écrire lit le schéma de l'outil.
- **Technique** : ce que reçoit une entrée d'API de type liste aujourd'hui, chaque champ sous la dernière partie de son chemin (`extra.element` en `element`), sa valeur stockée ; la config cochée, la config entière.

Deux champs gardés d'un même pointeur Lisible au même libellé : l'enregistrement de la Tâche est refusé en les nommant (« Escales : deux champs s'appellent « Contenu » ; renomme l'un, ou n'en garde qu'un »), jamais une clé qui en écrase une autre ni un libellé changé par l'app.

## Le JSON lu

- **Un champ texte « JSON »** : une option du réglage de longueur d'un champ TEXT, à côté de « mis en forme ». Il s'écrit en texte brut ; un contenu qui n'est pas du JSON est refusé à l'enregistrement. Déclaré, jamais deviné à l'allure du texte.
- **Un lecteur de JSON**, un composant partagé, en lecture seule : les clés en gras, les listes et les objets repliables, un texte long avec ses retours à la ligne. Là où un JSON se lit : un champ « JSON » d'une entrée, le résultat d'une étape dans l'exécution d'une Tâche, ce qu'une API a reçu et répondu dans « Essayer ».

## Vers l'API

- **Un type d'entrée d'API « objet »** : l'objet part tel quel dans ce que reçoit l'API (`input.json` d'une recette, le corps d'un service web), pas écrit en chaîne. Une entrée de type texte qui reçoit un objet est refusée à l'enregistrement de la Tâche, en le disant.
- L'objet donné à un champ texte « JSON » (`create_entry`, `update_entry`) s'y écrit en JSON.

## « Arrêter la Tâche »

Une troisième issue au garde-fou de « Créer une entrée » (`existing.then`), à côté de « La laisser telle quelle » et « La modifier avec ces valeurs » : une entrée a déjà la même valeur dans ce champ, l'exécution s'arrête là, réussie, et dit pourquoi (« une fiche avait déjà cette Réponse »). Ce qu'une étape ne doit faire qu'une fois par entrée reçue.

## La doc, avec le code

Écrite en même temps que le code, pas après : `docs/CORE.md` (« Tasks » : l'étape, l'issue « Arrêter la Tâche », l'entrée d'API « objet »), `docs/BRICKS.md` (le pointeur et ses deux rendus, le champ texte « JSON »), le schéma de l'étape que l'IA lit (`task_config`), et le guide de conception (`ai_design_guide`) : le montage « assembler, puis appeler une API », quand le préférer à une routine.

## Le montage, une fois vu sur le téléphone

- Une Tâche « Préparer la séance », lancée par « Avant la séance » rempli : assembler (le Programme et les notes de Mémoire du parcours avec leur config, les 3 dernières Escales en entier, toutes en titre, date et « En une phrase », la dernière séance Hypnose, le sommeil de la nuit, les portraits à rencontrer, la réponse du jour) ; créer la fiche de Séances d'hypnose, son contexte l'objet, « Arrêter la Tâche » sur Réponse ; appeler hypnogen ; remplir la fiche ; prévoir la séance. Elle remplace la routine et « Fabriquer la séance », supprimées.
- Le champ contexte de Séances d'hypnose passé en texte « JSON » ; plus de limite de 65 000 caractères, l'entrée d'une recette voyageant par un fichier de sa branche (`RecipeCall`).
- Dans `badibam/treelune-worker`, `context` d'hypnogen déclaré « objet » dans `recipe.json`, et son prompt qui le lit ainsi.

## Ouvert

- Le nom de l'étape à l'écran (« Assembler », « Composer un objet »).
