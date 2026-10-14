# Écrire pour l'utilisateur

Tout texte que lit l'utilisateur suit ces règles : les textes de l'app (`shared`, outils, thèmes, Guide, notifications), la démo (`texts-*.json`), les README, les notes de version et la fiche F-Droid. Le lecteur découvre l'app, il ne connaît pas son code : rien du registre de la doc interne et des commits.

## La phrase

- Une phrase, une idée, avec un vrai verbe. Jamais une chose qui agit à la place de quelqu'un : ni « la routine garde un message », ni « le message ouvre une conversation », mais « un message se prépare une fois », « touche la bulle pour commencer une conversation ».
- Pas d'avalanche de tu, ton, ta, tes (ou de vous, votre, vos) : un article suffit quand on sait de qui il s'agit (« une première zone », « la liste »), et « on » ou une tournure passive allègent.
- Le pourquoi avant le comment : à quoi ça sert, puis où toucher. Nommer un bouton se fait dans une phrase qui dit pourquoi le toucher.
- Les deux-points annoncent une liste ou un exemple, jamais une moitié de phrase ; pas de point-virgule.
- Un mot de l'app (entrée, zone, pointeur, tentative) s'explique à sa première apparition, puis reste le même : « configuration », pas « config ».
- Pas de précision contre un malentendu que le lecteur n'a pas encore.
- Pas de formule répétée (« Puis Suivant. ») : le bouton se voit.
- Les guillemets de chaque langue : « » en français, “ ” en anglais.

## À qui on parle

- En français, on tutoie l'utilisateur partout : les textes de l'app (les `*-fr.xml`), la démo (`texts-fr.json`) et `README.fr.md`, qui montre les phrases de présentation des outils et des thèmes parmi les siennes. `scripts/check_french_address.py` refuse vous, votre et vos dans ces textes à chaque `./run test`. L'anglais dit you.
- Le français s'écrit d'abord, l'anglais en est la traduction.
