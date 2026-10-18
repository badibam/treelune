# Conformité F-Droid

Ce qui reste à faire pour publier sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, abonnée en plus de `android`). Repris le 2026-10-11. À élaguer une fois la première version publiée : le code, la recette et les commits deviennent le registre.

## Déjà en place

- Licence GPL-3.0-or-later (`LICENSE`, dite dans les deux README).
- Aucune dépendance Google ; aucune clé d'API dans l'app (l'utilisateur apporte la sienne).
- Les médias ont leur licence : Lucide (ISC), Baloo 2 et Cartouche (OFL, dans `assets/fonts/`), le marimba et les sons de Kenney (CC0, la provenance de ces derniers dans `themes/retro/sounds.json`).
- Build : wrapper Gradle versionné avec l'empreinte de sa distribution, NDK épinglé, `isCrunchPngs = false`, `vcsInfo.include = false`, signature hors du dépôt. La bibliothèque Go (Tailscale) se compile depuis `vendor/` seul, réseau coupé, sans chemins ni identifiant de build (`GoLibraryTask`, `app/build.gradle.kts`).
- Tags `vX.Y.Z` sur chaque version, `versionCode` monotone (28 pour `v0.6.0`).

## La recette, essayée chez nous avant de la proposer

`scripts/fdroid/app.treelune.yml` est la recette qu'on proposera à `fdroiddata` (`metadata/app.treelune.yml`). F-Droid n'admet pas un Go tout prêt hors de Debian : comme celle de Tailscale, elle installe le Go de `trixie-backports` (celui de trixie, 1.24.4, est trop vieux pour compiler Go 1.27), compile avec lui Go 1.27.2 depuis la source `go` de `fdroiddata`, et le donne au build par `go.dir` dans `local.properties`. Elle fait supprimer avant le build ce que le scanner refuse et que l'app n'utilise pas (`scandelete`) : le `package.json` de la copie de Lucide, et l'interface de débogage de Tailscale (`eventbus/assets`, du JavaScript compressé que Go n'embarque pas sous Android).

`scripts/fdroid/build_in_container.sh` refait, pour la seule app, le job « fdroid build » de la CI de `fdroiddata` : fdroidserver cloné au commit que `fdroiddata` épingle, lint de la recette, scanner, build depuis le tag. Le 2026-10-18, il a construit `v0.8.0` sans erreur (`tmp/app.treelune_30.apk` dans `fdroiddata`, ~25 min dont 13 de Gradle).

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
   podman run --rm \
     -v ~/fdroid-test/fdroiddata:/fdroiddata \
     -v /mnt/data/OUTILS/treelune/scripts/fdroid:/recipe:ro \
     registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
     bash /recipe/build_in_container.sh > /mnt/data/OUTILS/treelune/tmp/fdroid-build.log 2>&1
   ```

Le build construit le tag nommé dans la recette, cloné de GitHub, jamais l'arbre de travail : un correctif du build ne s'essaie qu'une fois tagué et poussé, ou en faisant pointer la recette, le temps de l'essai, sur un clone local (`Repo:`, `commit:`).

## Ce qui reste

- **Reproductibilité** : tenue sur `v0.8.0` le 2026-10-18. L'APK que construit `./run release-tag v0.8.0` (un clone propre du tag, hors de l'arbre de travail) passe `apksigcopier compare` contre l'APK non signé de la recette : notre signature tient sur leur build, à deux chemins différents. L'APK de la v0.8.0 publié sur GitHub, construit dans l'arbre de travail, diffère par `classes.dex` : seule une version publiée par `release-tag` peut l'être par F-Droid sous notre signature.
- **Fiche fastlane**, `fastlane/metadata/android/en-US/` (l'anglais est la langue de repli obligatoire) : `title.txt` (≤ 50 caractères), `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000), `changelogs/<versionCode>.txt` (≤ 500, nommé du `versionCode`), `images/icon.png`, `images/featureGraphic.png` (paysage), `images/phoneScreenshots/` — les captures que le README attend déjà là. Le français (`fr-FR/`) ensuite, en traduction.
- **Anti-features**, la grille entière repassée juste avant la soumission (`Ads`, `DisabledAlgorithm`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `TetheredNet`, `Tracking`). Déjà connu : `NonFreeNet` pour Anthropic, OpenAI, DeepSeek et le serveur de connexion de Tailscale ; à trancher pour la liste de prix LiteLLM que `ModelPriceManager` lit sur GitHub.
- **La version soumise** : taguée sur un arbre propre, construite depuis le tag ; ses notes de version écrites et validées avant de publier ; la recette mise à jour sur ce tag.
- **La signature** : par défaut F-Droid signe l'app de sa propre clé, et Android refuse alors toute mise à jour d'un APK installé depuis GitHub, signé de la nôtre. Un build reproductible lui fait publier notre APK signé (`Binaries` et `AllowedAPKSigningKeys` dans la recette) : une seule signature partout.
