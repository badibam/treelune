# Progression — brouillon de conception

Consignation du brainstorm du 2026-11-23. Rien n'est figé : ce qui ressemble à des décisions est potentiellement à revoir. Ce fichier est le matériau de base de la prochaine session, qui reprend à la section « Reprise ». Rien de codé.

## Le besoin

- Tenir une progression (programme d'entraînement, plan de mise en œuvre, parcours) = **se situer** : où j'en suis sur un chemin.
- Aujourd'hui, les notes tiennent le récit, l'Objectif tient la mesure, rien ne tient la position. Le récit n'est pas un fait que la machine lit ; la mesure ne dit pas où on en est.
- Envie complémentaire, pas encore traitée : **voir le chemin parcouru** (arbre de compétence, grille) — dimension d'affichage.

## La notion traversée

- **Deux couches** : le **plan** (le paysage — des pas ordonnés, sa logique propre, éventuellement ses dates) et la **position** (le marcheur posé dessus, en retard ou en avance).
- Un **pas** porte un titre et un texte, autonome. Rien de codé en dur : les outils ne se connaissent pas mutuellement.
- La **position** est toujours un fait écrit — par l'utilisateur ou par l'IA, c'est le principe du dispatcher — jamais un calcul : seule une chose écrite peut être en désaccord avec le plan. Le temps et les données ne font que proposer.
- Les **mouvements** de la position : avancer, reculer, sauter, recommencer. Chaque mouvement est une écriture : la trace (le chemin parcouru) vient de l'historique des écritures que l'app tient déjà, sans rien inventer.
- Une **règle d'avancement** par pas : une condition de la brique commune (seuils, variables, comptes d'entrées — ce que font déjà les filtres et les « Seulement si » des Tâches), en trois modes : **auto** (la règle écrit la position), **suggéré** (elle propose), **manuel** (pas de règle). En auto, la règle se déclenche sur l'événement « la condition vient de devenir vraie », jamais sur l'état : une correction manuelle ne se fait pas refaire avancer tant que la condition n'est pas retombée puis repassée.
- La condition peut lire **l'état interne de la progression**, un vocabulaire fermé comme `state.running` du Suivi ou les résultats d'étapes d'une Tâche : position courante, date d'arrivée sur un pas, nombre de passages, redémarrages, manière dont un pas a été passé (à la main, par la règle, sauté), temps passé sur le pas, décalage plan/position.
- **Le temps** : le plan porte une date de départ (facultative) et chaque pas une durée (facultative) ; les plages se dérivent (aujourd'hui moins le départ, en descendant les durées). Sans date = pas de temps, juste l'ordre (un chemin de compétences). Des dates écrites à la main pas par pas sont refusées : c'est le travail de l'Agenda, et refaire un programme obligerait à tout réécrire — une seule date à changer, la forme reste. Deux séries de temps coexistent : le temps du plan (dérivé) et le temps vécu (la trace) ; le décalage est leur écart.
- **Frontière avec l'Objectif** : l'Objectif tient la cible et le verdict ; la progression tient l'ordre et la position. Dry January est un Objectif (une cible, rien d'ordonné) ; l'année santé est une progression dont la règle du pas de janvier lit le verdict de l'Objectif de janvier — la progression relie les Objectifs selon une logique, linéaire pour commencer. Critère de partage : l'ordre compte-t-il, y a-t-il une position à tenir ?

## Cas d'usage traversés

- Suites ordonnées : programme d'entraînement, parcours d'hypnose (dans TODO.md), protocole de santé, cours, plan de mise en œuvre — la forme de TODO.md lui-même.
- La position vient des données : courir 5 km, soulever 60 kg, poids par paliers — l'Objectif avec des étapes enchaînées, qu'il ne sait pas faire aujourd'hui.
- Le temps fait le plan : calendrier de l'Avent (progression), 30 jours de méditation (un Suivi, rien à ordonner), une habitude par semaine.
- Sans ordre ni fin : arbre de compétences (la suite possible de la notion), collection à compléter (une Liste).
- Déjà dans l'app, pour mesurer le trou : le Guide (chapitres + progression, écrite à la main dans son code), la Séance (position bornée à la session), les Flashcards (progression des révisions).
- Les bords : refaire un programme avec la trace des deux passages, importer un programme trouvé en ligne, un plan écrit puis tenu par l'IA.

## Ouvert, à revoir

- **La pertinence — la question d'origine, non tranchée** : type d'outil à part entière, montage de briques existantes, ou « modèle d'outil prédéfini » (idée déjà dans TODO.md) ? Avant ce fork : relire `ai_design_guide` et `docs/TOOLS.md` pour chiffrer le coût de chaque branche.
- La **forme** : la ligne d'abord ; l'arbre (prérequis, fronts parallèles) comme suite de la notion — la position y devient un ensemble, « avancer » devient « débloquer ».
- **L'affichage** du chemin parcouru (arbre, grille), la tuile, l'écran : dimension pas encore abordée.
- Le **nom** de la notion (plan, programme, progression).
- Un pas porte-t-il autre chose que titre et texte (média, durée vécue) : pas discuté.
- **L'import** d'un programme, **les commandes de l'IA** (lire le plan, avancer, l'écrire) : pas discutés.
- Le modèle de temps (départ + durées) : pas confronté aux programmes réels.

## Reprise

Prochaine session, dans cet ordre : le fork outil vs montage (avec la lecture du guide de conception et de `docs/TOOLS.md`) — c'est la question de pertinence posée au départ, tout le reste en dépend ; le modèle de temps confronté aux programmes réels ; la forme (ligne, puis l'arbre) ; l'affichage du chemin parcouru ; les commandes de l'IA et l'import.
