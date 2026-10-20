# Les sons de tous les thèmes

Conçu le 2026-10-18. Le registre sonore de chaque thème, codé ou à venir, posé côte à côte pour que deux thèmes ne sonnent pas pareil, et pour savoir quelles sources ajouter avant d'écouter. Le flux d'un choix (planche d'écoute, `theme_sounds.py`) est dans `docs/THEMES.md`, « The sounds » ; la ligne 12 de la spec d'un thème, « Le registre sonore », reprend la sienne d'ici. Un thème qui se construit part de sa ligne et la précise dans sa spec, la carte suivant ce qu'elle tranche ; un registre qui n'est pas sur la carte s'y ajoute d'abord, à côté des autres, avant sa spec.

## Ce qui distingue deux registres

- **La matière** : papier, bois, métal, plastique, verre, électronique, ou un vrai son dégradé.
- **La note** : un son qui a une hauteur (un carillon, un bip) ou un bruit sans note (un clic, un froissement).
- **L'attaque et la longueur** : sec et court, ou rond et qui résonne.

Deux thèmes peuvent partager une matière s'ils diffèrent sur un autre de ces trois points.

## Ce que chaque signal demande, quel que soit le thème

- Le cran (`step`) revient en rafale (un curseur qu'on glisse) : le plus court et le plus discret du thème.
- Le bout de liste (`scroll_end`) arrive sans qu'on l'ait voulu : doux, jamais un reproche.
- Le refus (`refuse`) est le seul son négatif : il se reconnaît sans regarder l'écran.
- Entrer et revenir, ouvrir et fermer vont par paires : le même son, ou deux sons parents, qui montent puis descendent.

## Ce qui tient un thème ensemble

Deux sons sonnent de la même famille par leur prise : la pièce et sa réverbération, la distance du micro, le bruit de fond, la couleur, la longueur et la façon de s'éteindre, le volume. Kenney et BigSoundBank tiennent chacun ensemble parce qu'un seul auteur a tout pris et traité ; c'est en mêlant les sources qu'un thème se défait.

- **Une source principale par thème** (la colonne de la carte). Une seconde ne vient que pour un trou, nommé sur la ligne du thème ; Freesound n'est jamais la source principale. Le terminal en a deux par nature : le bip, synthétique, et le clavier, enregistré.
- **Une finition par thème**, écrite dans son `sounds-choice.json` et appliquée par `theme_sounds.py make` à chacun de ses sons, quelle que soit sa source : un canal et une fréquence d'échantillonnage communs, les graves coupés sous un même seuil (la pièce, les manipulations), le silence de tête coupé, une longueur maximale et un même fondu de fin, une même couleur (graves et aigus), une même petite réverbération ou aucune (le suisse reste sec), et le niveau ramené à un même volume mesuré avant le décalage du signal, là où le gain d'aujourd'hui s'applique au son brut. Chaque étape est nommée dans le fichier ; le rétro et le cosy n'ont que leur gain d'aujourd'hui, et leurs fichiers ne changent pas.
- **Le thème écouté d'une traite** sur la planche, ses signaux enchaînés : un son venu d'ailleurs s'entend aussitôt. `make` écrit à côté, pour chaque son, son bruit de fond, sa longueur, sa couleur et son volume : une mesure qui montre l'écart, l'oreille tranchant.

## La carte

| Thème | Matière | Note | Attaque | Sa signature | Source principale |
|---|---|---|---|---|---|
| Défaut | aucune | | | muet | — |
| Rétro (fait) | plastique, interrupteurs | non | sec | les clics de Kenney | Kenney |
| Cosy (fait) | bois creux, gouttes | oui | rond | des sons ronds qui rebondissent | Kenney |
| Suisse | métal de bureau | non | très sec | l'agrafeuse, l'interrupteur, la perforatrice | BigSoundBank |
| Encre et lavis | pierre, bois, eau | peu | feutré, lent | le sceau qu'on frappe, une goutte, le pinceau | BigSoundBank |
| Signalétique de métro | carillon, métal | oui | net, résonne | le carillon de deux ou trois notes douces, le clac du portillon | synthèse, et BigSoundBank pour le clac |
| Papier découpé | papier épais | non | net | les ciseaux, une feuille posée sur le kraft | BigSoundBank |
| Plan d'architecte | outils de dessin | non | sec, fin | le porte-mine qu'on clique, la règle posée | BigSoundBank |
| Carnet | papier, stylo | non | doux | le stylo qui coche, la page qu'on tourne | BigSoundBank |
| Monochrome / e-ink | presque rien | non | très doux | le thème qui répond le moins : un souffle, quelques signaux seulement | synthèse |
| Terminal | haut-parleur de PC, clavier | oui (une seule) | sec | le bip carré à une seule hauteur, les touches d'un clavier mécanique | synthèse et BigSoundBank |
| Instruments | métal lourd, relais | non, sauf l'alarme | lourd, mécanique | l'interrupteur à levier, le relais qui claque, le buzzer | BigSoundBank |
| Menu de JRPG | électronique | oui, mélodique | court | le curseur bip-bip, la confirmation qui monte, l'annulation qui descend | synthèse |
| Mac 1-bit | vrais sons dégradés | non | sec | des sons d'objets réduits à 8 bits et 11 kHz, granuleux | BigSoundBank, dégradé |
| HUD de science-fiction | électronique fine | oui, aiguë | léger | des bips fins, des balayages, un scan | Kenney (Sci-fi Sounds) |
| Risographie | machine à imprimer | non | mécanique | le tambour qui tourne, la feuille qui sort | BigSoundBank |
| Arcade néon | synthé gros et brillant | oui | fort, éclatant | la pièce qu'on glisse, un arpège, un laser | Kenney (Digital Audio) |
| Cartes à jouer | carton, jetons | non | net | la carte posée, glissée, le jeton qu'on empile | Kenney (Casino Audio) |

## Thème par thème

- **Défaut** : muet (décidé le 2026-10-18).
- **Rétro** : fait (`retro/sounds-choice.json`). Ses clics sont ceux d'un objet en plastique, pas d'une console : les bips 8 bits vont au JRPG, à l'arcade et au Mac, et le rétro les garde. À réécouter à côté du suisse.
- **Cosy** : fait (`cosy/sounds-choice.json`), rien à changer.
- **Suisse** : la spec disait des clics et des tics de Kenney, ceux-là mêmes que le rétro a pris ; il s'en éloigne par la matière : du métal de bureau, plus mat et plus court que le rétro. BigSoundBank : l'agrafeuse de bureau (n° 9) et sur bois (n° 1580), les interrupteurs (n° 26, 27, 36, 52), le stylo à poussoir (n° 613, 810), le classeur métallique (n° 773).
- **Encre et lavis** : la spec disait « une page qu'on tourne » ; la page va au carnet, et l'encre garde ce qui n'appartient qu'à elle : le sceau frappé (confirmer), une goutte dans l'eau (ouvrir, fermer), un bois léger (cocher), le pinceau qui glisse (entrer, revenir). BigSoundBank : les gouttes (n° 1384 à 1387), le bois (tiroir, clé posée) ; ni pinceau ni sceau, deux trous pour Freesound.
- **Signalétique de métro** (codé, muet ; sa spec, `docs/design/metro-theme.md`, ligne 12) : le seul thème à vraies notes claires ; un carillon de deux ou trois notes douces pour confirmer et entrer, le clac sec d'un portillon pour cocher et le cran, un bip bref pour le refus. Le carillon et le bip par une recette `synth` en sinus, une banque d'instruments si le sinus ne suffit pas ; le clac, un métal de BigSoundBank, plus lourd que l'interrupteur du suisse (aucun portillon dans la banque) ; jamais l'enregistrement d'un vrai réseau (BigSoundBank a le jingle de la SNCF, écarté).
- **Papier découpé** : du papier épais et sec, coupé et posé ; les ciseaux (confirmer), une feuille posée (entrer), soulevée (revenir). BigSoundBank : les ciseaux (n° 8), le papier déchiré ; peu de prises, Freesound pour un second ciseau.
- **Plan d'architecte** : des outils de dessin fins et secs ; le porte-mine qu'on clique (cocher, cran), la règle posée (confirmer), le tampon de révision (refuser). BigSoundBank : le crayon (n° 221, 3233 à 3238), le taille-crayon, le stylo à poussoir ; la règle, un trou pour Freesound.
- **Carnet** : un cahier et un stylo ; le stylo qui coche (confirmer, cocher), la page qu'on tourne (entrer, revenir), la spirale, la gomme (refuser). BigSoundBank : la page tournée (n° 164, 362, 363, 493), le crayon, le capuchon, le feutre.
- **Monochrome / e-ink** : une liseuse ne fait aucun bruit ; le thème répond à peu de signaux, un souffle très bas pour confirmer, cocher et refuser, le reste muet. Une recette : un bruit filtré, très court.
- **Terminal** : le bip du haut-parleur d'un PC, une seule hauteur, longueurs différentes (refuser = deux bips), et les touches d'un clavier mécanique pour le cran et cocher. Une recette `synth` en onde carrée ; BigSoundBank : les claviers (n° 229, 1724 à 1728…) et les machines à écrire.
- **Instruments** : un panneau de commande lourd ; l'interrupteur à levier (cocher), le relais (confirmer, cran), le buzzer d'alarme (refuser). BigSoundBank : les interrupteurs, les buzzers (n° 35, 89, 173), le classeur métallique ; peu de relais (un thermostat, un clignotant), un trou pour Freesound.
- **Menu de JRPG** : la convention des menus de jeu de rôle, mélodique et courte : le curseur (cran), une note qui monte (confirmer, entrer), qui descend (revenir, fermer), un buzzer grave (refuser). Une recette `synth`.
- **Mac 1-bit** : pas des bips de synthé mais de vrais sons d'objets (un clic, un bouton, un tiroir) passés à 8 bits et 11 kHz, granuleux ; jamais une imitation des sons d'Apple. Une recette qui dégrade un son de BigSoundBank.
- **HUD de science-fiction** : de l'électronique fine et aiguë, aérée ; des bips brefs, un balayage qui monte (ouvrir), qui descend (fermer), un scan (entrer). Où chercher : Sci-fi Sounds et Digital Audio de Kenney.
- **Risographie** : la machine qui imprime ; le tambour qui tourne (entrer), la feuille qui sort (confirmer), le bac qu'on pousse (fermer). BigSoundBank : les imprimantes jet d'encre et laser (n° 169, 394, 395), la Dymo (n° 1620, 1621), le ticket d'une vieille calculatrice (n° 3264…).
- **Arcade néon** : le plus fort et le plus brillant de tous ; la pièce glissée (confirmer), un arpège qui monte (entrer), un laser (ouvrir). Où chercher : Digital Audio (`laser`, `powerUp`), Music Jingles pour un bout d'arpège.
- **Cartes à jouer** : la carte posée (confirmer, cocher), glissée (entrer, revenir), le paquet battu (ouvrir), le jeton qu'on empile (cran). Casino Audio de Kenney ; les jetons ont aussi BigSoundBank (n° 942 à 945), qui reste hors du thème.

## Les sources

Ajoutées le 2026-10-18 (`sound-sources.json`) : Casino Audio et Sci-fi Sounds de Kenney ; 84 enregistrements de BigSoundBank, choisis par mots-clés dans le catalogue de Saylune, en dehors des voix ; 26 sons de Freesound pour les trous (pinceau, sceau, règle, relais, ciseaux), pris par leur extrait Ogg public, à remplacer par l'original, téléchargé à la main, si l'un d'eux sonne abîmé sur le téléphone. Un son qui manque à un thème s'ajoute à la liste de sa source. Le métro attend une recette `synth` en sinus, et une banque d'instruments si elle ne suffit pas.

## Ce qui reste ouvert

- L'e-ink : quels signaux il garde.
