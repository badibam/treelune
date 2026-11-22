# Le fond sonore d'une Séance

Conçu le 2026-11-22. Le besoin : une piste de musique sous des vidéos, des sons binauraux sous une séance d'hypnose, sans passer par une autre app. Une Séance ne jouait qu'un son ou une vidéo à la fois.

## Décisions

- **Config, toutes sortes de Séance** : une case « Proposer un fond sonore », éteinte par défaut ; éteinte, rien ne change. Allumée, elle montre un champ Fichier audio en mode lié, plusieurs fichiers sans limite, facultatif (le fond par défaut), et les valeurs par défaut d'« Aléatoire » (décoché), « En boucle » (coché) et du volume.
- **Au lancement** : la fenêtre d'avant le départ montre le même champ, prérempli avec le fond de la config, et juste dessous, sans menu à ouvrir, les trois réglages préremplis. Ces choix valent pour cette séance seulement. Une Séance « Étapes » gagne cette fenêtre quand la case est allumée. Un fichier absent du téléphone est dit dans le champ, jamais remplacé, et n'empêche pas de lancer. Valider après coup une séance faite sans l'app ne montre pas le fond.
- **Un seul mode, lié** : un fichier de l'app n'a qu'un propriétaire, et chaque séance recevrait une copie complète du fond. Le mode copié attend qu'une séance puisse utiliser un fichier sans le posséder (`TODO.md`, « Retenir un fichier sans le posséder »).
- **Données** : l'entrée de la séance possède ses liens de fond dans un champ Fichier déclaré, et garde ses trois réglages. Elle garde les fichiers choisis, pas ceux réellement joués, qui demanderaient une écriture à chaque piste. Aucune colonne, aucune migration.
- **Lecture** (`SequenceBackground`, à côté du son des étapes, suivie par le même service) : un `MediaPlayer`, les fichiers l'un après l'autre. En boucle, un fichier seul reboucle sur lui-même, plusieurs recommencent une fois tous joués ; sans boucle, le fond s'arrête après le dernier et la séance continue. Aléatoire : un nouvel ordre à chaque tour, sans rejouer à la jointure le fichier qui vient de finir. Un court silence à la jointure est accepté : un fond qui ne doit pas s'interrompre est un fichier assez long.
- **Sa position est la sienne** : il traverse les changements d'étape, se met en pause avec la séance, continue écran éteint, démarre et finit par un fondu de 3 secondes. Un fichier illisible est sauté ; si aucun ne se lit, la séance continue sans fond.
- **Volume** : une part du volume média du téléphone, réglable aussi sur l'écran de la séance.
- **Vidéo** : elle ne demande plus la priorité audio, pour ne pas couper le fond.
- **Après une interruption** : le fond repart du début de sa liste, un nouvel ordre tiré en aléatoire. Rien n'est écrit pendant la lecture.

## À vérifier sur le téléphone

- La stéréo d'un son binaural intacte.
- Le fond sous une vidéo, sans que l'un coupe l'autre.
- Les 3 secondes de fondu, à l'écoute.
