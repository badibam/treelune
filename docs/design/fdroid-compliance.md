# Conformité F-Droid

Ce qui reste à faire pour publier sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, abonnée en plus de `android`). Repris le 2026-10-11. À élaguer une fois la première version publiée : le code, la recette et les commits deviennent le registre.

## Déjà en place

- Licence GPL-3.0-or-later (`LICENSE`, dite dans les deux README).
- Aucune dépendance Google ; aucune clé d'API dans l'app (l'utilisateur apporte la sienne).
- Les médias ont leur licence : Lucide (ISC), Baloo 2 et Cartouche (OFL, dans `assets/fonts/`), le marimba et les sons de Kenney (CC0, la provenance de ces derniers dans `themes/retro/sounds.json`).
- Build : wrapper Gradle versionné avec l'empreinte de sa distribution, NDK épinglé, `isCrunchPngs = false`, `vcsInfo.include = false`, signature hors du dépôt. La bibliothèque Go (Tailscale) se compile depuis `vendor/` seul, réseau coupé, sans chemins ni identifiant de build (`GoLibraryTask`, `app/build.gradle.kts`).
- Tags `vX.Y.Z` sur chaque version, `versionCode` monotone (28 pour `v0.6.0`).

## La recette, essayée chez nous avant de la proposer

`scripts/fdroid/app.treelune.yml` est la recette qu'on proposera à `fdroiddata` (`metadata/app.treelune.yml`). F-Droid n'admet pas un Go tout prêt hors de Debian : comme celle de Tailscale, elle installe le Go de Debian, compile avec lui Go 1.27.2 depuis la source `go` de `fdroiddata`, et le donne au build par `go.dir` dans `local.properties`.

`scripts/fdroid/build_in_container.sh` refait, pour la seule app, le job de build de la CI de `fdroiddata` : lint de la recette, scanner, build depuis le tag.

Une fois pour toutes :

```
sudo apt install podman
podman pull registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie
```

Les images vont dans la home (`~/.local/share/containers`, ext4) : `/mnt/data` est un disque NTFS, qui refuse les `:` dans les noms de fichiers que l'image contient, et ne garde pas les propriétaires que le build change.

1. Cloner `fdroiddata`, sur un disque ext4 lui aussi :

   ```
   mkdir -p ~/fdroid-test
   git clone --depth 1 https://gitlab.com/fdroid/fdroiddata.git ~/fdroid-test/fdroiddata
   ```

2. Lancer le build (il télécharge le NDK, les sources de Go, les dépendances Gradle) :

   ```
   podman run --rm -it \
     -v ~/fdroid-test/fdroiddata:/fdroiddata \
     -v /mnt/data/OUTILS/treelune/scripts/fdroid:/recipe:ro \
     registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
     bash /recipe/build_in_container.sh 2>&1 | tee /mnt/data/OUTILS/treelune/tmp/fdroid-build.log
   ```

Inconnues que ce build tranchera :

- le Go de Debian trixie assez récent pour compiler Go 1.27 — sinon `trixie-backports`, comme Tailscale ;
- le NDK `30.0.16248370` connu de fdroidserver ;
- le scanner devant `vendor/github.com/tailscale/web-client-prebuilt` (du JavaScript compilé) : il n'entre pas dans la bibliothèque Android (`go list -deps` pour android/arm64 ne le nomme pas), la recette peut donc le supprimer avant le build (`scandelete`).

## Ce qui reste

- **Reproductibilité** : construire la release deux fois, depuis deux clones du tag à deux chemins différents, et comparer les APK — un chemin qui fuit dans la bibliothèque Go se voit là.
- **Fiche fastlane**, `fastlane/metadata/android/en-US/` (l'anglais est la langue de repli obligatoire) : `title.txt` (≤ 50 caractères), `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000), `changelogs/<versionCode>.txt` (≤ 500, nommé du `versionCode`), `images/icon.png`, `images/featureGraphic.png` (paysage), `images/phoneScreenshots/` — les captures que le README attend déjà là. Le français (`fr-FR/`) ensuite, en traduction.
- **Anti-features**, la grille entière repassée juste avant la soumission (`Ads`, `DisabledAlgorithm`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `TetheredNet`, `Tracking`). Déjà connu : `NonFreeNet` pour Anthropic, OpenAI, DeepSeek et le serveur de connexion de Tailscale ; à trancher pour la liste de prix LiteLLM que `ModelPriceManager` lit sur GitHub.
- **La version soumise** : taguée sur un arbre propre, construite depuis le tag ; ses notes de version écrites et validées avant de publier ; la recette mise à jour sur ce tag.
- **La signature** : par défaut F-Droid signe l'app de sa propre clé, et Android refuse alors toute mise à jour d'un APK installé depuis GitHub, signé de la nôtre. Un build reproductible lui fait publier notre APK signé (`Binaries` et `AllowedAPKSigningKeys` dans la recette) : une seule signature partout.
