[English](README.md) · **Français**

# Treelune

**Chaque app de suivi fait une chose, à sa façon. Treelune te donne les pièces, et tu construis celle qu'il te faut.**

Un suivi, un carnet, une liste, des fiches, un objectif, un graphique, des flashcards, un agenda… Chaque pièce se règle à ton idée, et toutes s'emboîtent. Ce que tu notes une fois sert partout : ça se compte, se compare, se dessine, se révise.

- La liste de vocabulaire devient un paquet de flashcards.
- Les repas puisent dans les fiches d'aliments, et les calories du jour se comptent seules.
- Les heures de travail se répartissent par client, prêtes pour la facture.
- Chaque sortie de course fait avancer l'objectif de la semaine, et une courbe montre le chemin parcouru.
- L'humeur de chaque soir colore un calendrier, mois après mois.

Une application Android libre, sans compte ni publicité. Les données restent sur le téléphone.

Le site, avec l'app en action : [treelune.app](https://treelune.app/fr/)

Treelune est encore jeune, et n'a été essayée que sur quelques téléphones. Pour qu'un problème soit corrigé, signale-le depuis Réglages › Signaler un bug.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="200" alt="">
</p>

## Les pièces

### Zones et outils

Chaque pan de ta vie devient une **zone** : le sport, un projet, un potager, une langue, et tout ce que tu voudras y ajouter. Dans chaque zone, tu poses les **outils** dont tu as besoin. Ce qu'on note dans un outil, une pesée, un repas, une page de journal, en est une **entrée**. Les types d'outils :

<!-- tools -->
- **Suivi** — Des chiffres, des notes sur une échelle, des choix ou des durées chronométrées, notés au fil des jours, comme le poids, le sommeil, l'humeur ou les verres d'eau
- **Notes** — Des notes, chacune avec son titre, rangées là où elles servent
- **Journal** — Un carnet daté, pour raconter ses journées
- **Messages** — Des rappels et des notifications, une fois ou selon un planning
- **Liste** — Ce qui reste à faire, coché au fur et à mesure, avec des échéances si tu veux
- **Fiches** — Les champs que tu choisis, chaque fiche retrouvée par son nom, pour des aliments, des livres, des plantes ou autre chose
- **Objectif** — Un objectif jugé chaque jour, semaine ou mois sur les critères que tu fixes, lus dans tes entrées
- **Questionnaire** — Des questions posées une par écran, quand tu veux ou à heures prévues
- **Graphique** — Tes entrées et tes chiffres dessinés en courbes, barres, camemberts ou calendriers
- **Séance** — Des étapes préparées à l'avance, suivies à l'écran avec un minuteur et un signal à chaque changement, comme un entraînement ou une recette
- **Agenda** — Les rendez-vous et les événements à heure, qui passent d'eux-mêmes, avec leurs rappels
- **Flashcards** — Des cartes à réviser tirées d'un autre outil, chacune revenant juste avant d'être oubliée
<!-- /tools -->

Chaque outil se règle : ses champs, ses unités, ses rappels, son affichage sur la zone.

### Ce qui les relie

Une **variable** est un chiffre calculé à partir des entrées, comme les calories du jour ou les kilomètres de la semaine. Un Objectif la compare à sa cible, un Graphique la dessine, l'IA la lit. Une entrée peut aussi **pointer** vers une autre, un repas vers la fiche de l'aliment, une récolte vers sa plante.

### Ce qui part tout seul

Une **Tâche** est une suite d'étapes que l'app exécute elle-même, sans IA : appeler un service du web (une **API**, comme une prévision météo), ajouter ou modifier une entrée, lancer une routine. Elle part à la main, sur un planning, ou quand quelque chose arrive : une entrée écrite, un chiffre qui passe un seuil, un **événement** comme un rappel ou une séance prête.

### Avec une IA, si tu veux

Une IA connaît les mêmes pièces que toi. Demande-lui « monte-moi de quoi préparer un semi-marathon », et elle crée la zone, les outils et leurs liens. Elle lit ensuite les outils, y ajoute des entrées, en crée de nouveaux et les règle. Tout reste possible sans IA.

- Tu lui parles dans une conversation, en lui joignant d'un geste un outil ou une zone, sur la période qui t'intéresse.
- Avant toute modification, tu peux exiger qu'elle montre ce qu'elle va faire et attende ton accord, pour toute l'app, une zone, un outil ou une conversation.
- Quand il lui manque une information, elle te pose la question sous forme de champ à remplir.
- Elle peut parler sous une **identité**, avec sa voix et son caractère, créée dans l'app ou importée d'un fichier, choisie pour une zone, une routine ou une conversation.
- Avec une **routine**, elle travaille seule, sur un planning ou quand quelque chose arrive : « chaque dimanche soir, fais-moi le bilan de la semaine ». Elle sait aussi en créer.
- Une IA qui tourne ailleurs, comme Claude, ChatGPT ou un modèle libre sur ton ordinateur avec LM Studio, peut aussi se brancher à l'app comme connecteur, et travailler sur les outils depuis là-bas.

Tu apportes ta propre clé d'API, chez l'un de ces fournisseurs :

<!-- providers -->
**Claude**, **OpenAI**, **DeepSeek**
<!-- /providers -->

ou chez tout service qui parle comme l'API d'OpenAI (Ollama, LM Studio, OpenRouter…). L'app affiche ce que coûte chaque échange.

### À ton goût

Tu disposes les tuiles de chaque zone comme tu veux, tu choisis l'icône et la couleur de chaque outil, et l'apparence de toute l'app :

<!-- themes -->
- **Material** — Clair et sobre, dans la teinte de ton choix
- **Rétro** — Du pixel art : des pixels entiers, une police dessinée, des icônes en pixels, ses propres sons
- **Cosy** — Comme un téléphone dans un jeu doux : rond et dodu, tout rebondit, ses propres sons
- **Suisse** — Comme une affiche suisse : une grille stricte, noir et blanc et un seul rouge, les chiffres en grand, ses propres sons
- **Encre et lavis** — Comme un lavis à l'encre : un papier crème, l'encre noire et un seul rouge de sceau, beaucoup de vide, ses propres sons
- **Métro** — Comme la signalétique d'un métro : chaque outil est une ligne de sa couleur, chaque zone une station, ses propres sons
- **Papier découpé** — Des feuilles de couleur découpées aux ciseaux, posées de travers sur du kraft, ses propres sons
- **Plan d'architecte** — Comme un plan d'architecte : des traits fins sur un papier quadrillé, des cotes et des hachures, ses propres sons
- **Liseuse** — Comme une liseuse : noir et blanc pur, des trames à la place des gris, rien qui bouge
<!-- /themes -->

## Commencer

Télécharge l'APK de la dernière version sur la [page des releases](https://github.com/badibam/treelune/releases), ou construis l'app depuis les sources (voir plus bas). Elle n'est pas encore sur F-Droid.

Au premier lancement, une **démo** t'attend : douze semaines de la vie de Camille, qui prépare un semi-marathon, travaille en indépendante, cuisine, jardine sur son balcon et apprend l'italien avant un voyage à Rome. On y voit à quoi ressemble l'app une fois construite. Ensuite, le **Guide** (le livre en haut de l'accueil) se suit pas à pas : la démo, une première zone, un premier outil, puis l'IA et les routines.

## Tes données

- Tout est enregistré sur le téléphone. Aucun compte, aucune télémétrie, aucune publicité.
- Rien ne part vers une IA sans que tu l'aies demandé : quand tu lui écris, ou quand une routine programmée tourne. Ce qui part alors va au seul fournisseur choisi, avec ta clé, gardée chiffrée sur le téléphone.
- Une API n'est appelée qu'à l'adresse que tu as déclarée, avec ce que la Tâche lui passe.
- Pour afficher le coût des échanges, l'app télécharge une liste publique des prix des modèles (LiteLLM, sur GitHub). Elle n'envoie rien.
- Une fois le connecteur activé, ses échanges passent par un relais HTTPS dont tu donnes l'adresse, ou par Tailscale Funnel avec ton propre compte Tailscale.
- Tout se sauvegarde et se restaure en un fichier, que l'app recopie après chaque changement dans un dossier choisi.

## État du projet

L'app est en développement actif. Elle sert tous les jours, mais tout peut encore changer, la forme des données comprise. Garde des sauvegardes.

<!-- status -->
- Version 0.9.3
- Android 8.0 ou plus récent
- Un téléphone 64 bits (arm64)
- Langues : anglais, français
<!-- /status -->

## Pour les développeurs

Kotlin, Jetpack Compose et Room. Tout ce qui agit sur les données — l'interface, l'IA, le planificateur — passe par un même point d'entrée, ce qui donne à l'IA exactement les gestes de l'utilisateur. Un type d'outil s'ajoute sans toucher au cœur de l'app.

```bash
git clone https://github.com/badibam/treelune.git
cd treelune
./run            # le menu : construire, installer, tester…
./run build      # l'APK de debug
```

La documentation technique commence à [`docs/reference.md`](docs/reference.md).

## Licence

[GPL-3.0-or-later](LICENSE), c'est-à-dire la version 3 de la GNU General Public License, ou toute version ultérieure.
