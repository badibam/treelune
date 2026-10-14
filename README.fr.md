[English](README.md) · **Français**

# Treelune

**Suis ce qui compte — sport, repas, tâches, humeur — avec des outils que tu assembles toi-même, et une IA qui peut les lire, les remplir et les régler avec toi.**

Par exemple, tu notes tes repas, et l'app en tire les calories du jour, les compare à une cible et les dessine en graphique. Chaque outil peut lire les autres.

Une application Android libre, sans compte ni publicité. Les données restent sur le téléphone.

<!-- Captures d'écran : l'accueil, une zone de la démo, un graphique, une conversation avec l'IA, rangées dans fastlane/metadata/android/en-US/images/phoneScreenshots/ -->

## Ce que tu peux faire

### Noter

Tu ranges ta vie en **zones** (Santé, Travail, Cuisine…), et dans chaque zone tu poses les **outils** dont tu as besoin. Ce qu'on note dans un outil, une pesée, un repas, une page de journal, en est une **entrée**. Les types d'outils :

<!-- tools -->
- **Suivi** — Des chiffres, des notes sur une échelle, des choix ou des durées chronométrées, notés au fil des jours : poids, sommeil, humeur, verres d'eau
- **Notes** — Des notes, chacune avec son titre, rangées là où elles servent
- **Journal** — Un carnet daté, pour raconter ses journées
- **Messages** — Des rappels et des notifications, une fois ou selon un planning
- **Liste** — Ce qui reste à faire, coché au fur et à mesure, avec des échéances si tu veux
- **Données structurées** — Des fiches faites de tes champs, retrouvées par leur nom : aliments, livres, plantes
- **Objectif** — Un objectif jugé chaque jour, semaine ou mois sur les critères que tu fixes, lus dans tes entrées
- **Questionnaire** — Des questions posées une par écran, quand tu veux ou à heures prévues
- **Graphique** — Tes entrées et tes chiffres dessinés en courbes, barres, camemberts ou calendriers
- **Séance** — Des étapes préparées à l'avance, suivies à l'écran avec un minuteur et un signal à chaque changement : un entraînement, une recette
<!-- /tools -->

Chaque outil se règle : ses champs, ses unités, ses rappels, son affichage sur la zone.

### Relier

Une **variable** est un chiffre calculé à partir des entrées, comme les calories du jour ou les kilomètres de la semaine. Un Objectif la compare à sa cible, un Graphique la dessine, l'IA la lit. Une entrée peut aussi **pointer** vers une autre, un repas vers la fiche de l'aliment, une récolte vers sa plante.

### Avec une IA

L'IA dispose des mêmes gestes que toi : elle lit les outils, y ajoute des entrées, en crée de nouveaux et les règle.

- Tu lui parles dans une conversation, en lui joignant d'un geste un outil ou une zone, sur la période qui t'intéresse.
- Avant toute modification, tu peux exiger qu'elle montre ce qu'elle va faire et attende ton accord, pour toute l'app, une zone, un outil ou une conversation.
- Quand il lui manque une information, elle te pose la question sous forme de champ à remplir.
- Avec une **routine**, elle travaille seule, sur un planning : « chaque dimanche soir, fais-moi le bilan de la semaine ». Elle sait aussi en créer.
- Claude peut aussi se brancher à l'app depuis l'extérieur, comme connecteur, et travailler sur les outils depuis claude.ai.

Tu apportes ta propre clé d'API, chez l'un de ces fournisseurs :

<!-- providers -->
**Claude**, **OpenAI**, **DeepSeek**
<!-- /providers -->

ou chez tout service qui parle comme l'API d'OpenAI (Ollama, LM Studio, OpenRouter…). L'app affiche ce que coûte chaque échange.

### À ton goût

Tu disposes les tuiles de chaque zone comme tu veux, tu choisis l'icône et la couleur de chaque outil, et l'apparence de toute l'app :

<!-- themes -->
- **Par défaut** — Clair et sobre, dans la teinte de ton choix
- **Rétro** — Du pixel art : des pixels entiers, une police dessinée, des icônes en pixels, ses propres sons
- **Cosy** — Comme un téléphone dans un jeu doux : rond et dodu, tout rebondit, ses propres sons
<!-- /themes -->

## Commencer

Télécharge l'APK de la dernière version sur la [page des releases](https://github.com/badibam/treelune/releases), ou construis l'app depuis les sources (voir plus bas). Elle n'est pas encore sur F-Droid.

Au premier lancement, une **démo** t'attend : douze semaines de la vie de Camille, qui prépare un semi-marathon, travaille en indépendante, cuisine et jardine sur son balcon. On y voit à quoi ressemble l'app une fois construite. Ensuite, le **Guide** (le livre en haut de l'accueil) se suit pas à pas : la démo, une première zone, un premier outil, puis l'IA et les routines.

## Tes données

- Tout est enregistré sur le téléphone. Aucun compte, aucune télémétrie, aucune publicité.
- Rien ne part vers une IA sans que tu l'aies demandé : quand tu lui écris, ou quand une routine programmée tourne. Ce qui part alors va au seul fournisseur choisi, avec ta clé, gardée chiffrée sur le téléphone.
- Pour afficher le coût des échanges, l'app télécharge une liste publique des prix des modèles (LiteLLM, sur GitHub). Elle n'envoie rien.
- Une fois le connecteur activé, ses échanges passent par un relais HTTPS dont tu donnes l'adresse, ou par Tailscale Funnel avec ton propre compte Tailscale.
- Tout se sauvegarde et se restaure en un fichier, que l'app recopie après chaque changement dans un dossier choisi.

## État du projet

L'app est en développement actif. Elle sert tous les jours, mais tout peut encore changer, la forme des données comprise. Garde des sauvegardes.

<!-- status -->
- Version 0.7.0
- Android 8.0 ou plus récent
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
