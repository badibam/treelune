# Fournisseurs d'IA créés par l'utilisateur — conception

Conçu le 2026-10-17. Aujourd'hui, huit fournisseurs sont écrits en dur (`AIProviderRegistry`) : quatre sortes (Claude, OpenAI, DeepSeek, Autre serveur), chacune en « standard » et « économique », au même code, l'identifiant seul différant. La liste les montre tous, réglés ou non ; un seul est actif. Le but : créer les fournisseurs qu'on veut, et ne voir que ceux-là.

## Ce que l'utilisateur crée

- **Un fournisseur = une sorte + un nom + sa config** (clé, modèle, réglages), comme une config d'aujourd'hui. Deux modèles chez le même fournisseur sont deux fournisseurs, chacun sa copie de la clé. Un compte séparé des modèles qu'on y règle a été écarté : un niveau de plus que l'écran, les routines et la sauvegarde connaîtraient, pour ne pas recopier une clé.
- **La sorte** est le code qui parle à une API : les quatre d'aujourd'hui, sans variantes. Les variantes disparaissent.
- **Le nom** est obligatoire, prérempli avec celui de la sorte (texte traduit), unique : l'app refuse un doublon en le disant, sans renommer. Le modèle s'affiche à côté (« Claude · claude-sonnet-5-5 ») sans faire partie du nom.
- **Dupliquer** copie la config, clé comprise, sous « <nom> (copie) », à changer avant d'enregistrer.

## Qui sert quel fournisseur

- **Un fournisseur par défaut**, en place de l'actif. Une conversation nouvelle le prend et le garde jusqu'au bout (`provider_id` de la session, aujourd'hui noté mais pas lu : chaque appel prend l'actif du moment). Changer le défaut ne touche pas aux conversations ouvertes.
- **Une conversation change le sien** dans ses paramètres, au-dessus de sa réflexion ; le changer remet sa réflexion à celle du nouveau fournisseur.
- **Une routine** garde le sien. Il est facultatif : sans lui, elle existe mais ne s'active ni ne se lance, refus à l'appui, et sa tuile le montre. Le formulaire d'une routine nouvelle est prérempli avec le défaut ; la démo pose ses routines avec le défaut s'il y en a un, sans fournisseur sinon ; une routine que l'IA crée sans fournisseur reste sans, et le résultat de sa commande le dit.
- **Les replis disparaissent** : `"claude"` noté par `AIOrchestrator.createNewChatSession` sans fournisseur actif, un identifiant qui n'existe pas ; l'actif pris par `AIEventProcessor` quand la session d'une routine ne se lit pas. Sans fournisseur, une conversation se refuse en disant d'en régler un ; une routine dont la session ne se lit pas échoue.

## Supprimer

- **Refusée tant qu'une routine le nomme**, le refus listant ces routines ; on les change dans leurs réglages.
- **Le défaut supprimé** : l'app demande lequel le devient ; le dernier supprimé, il n'y a plus de défaut.
- **Une conversation passée** garde historique et coûts, et affiche « fournisseur supprimé » ; ouverte, elle se refuse au message suivant et dit d'en choisir un autre dans ses paramètres.

## Les prix

- Claude, OpenAI et DeepSeek restent sur la liste LiteLLM (`ModelPriceManager`), paliers compris : leurs prix publiés, tenus à jour.
- **Autre serveur** a un choix « Prix » : liste LiteLLM (prérempli), ou réglés à la main, qui montre trois cases par million de tokens : entrée, entrée en cache, sortie (la réflexion est de la sortie ; l'écriture du cache n'existe pas en Chat Completions). Une case vide quand l'appel a des tokens de sa sorte rend son coût inconnu ; 0 dit « gratuit », par l'utilisateur. Pas de paliers.

## L'écran

- **Réglages › Fournisseurs d'IA** (`AIProvidersScreen`) : une carte par fournisseur créé (nom, modèle, sorte, marque du défaut) ; la toucher ouvre sa config, en bas de laquelle « Dupliquer », « Supprimer », « Par défaut ».
- **« Ajouter un fournisseur »** ouvre le choix des quatre sortes, une tuile chacune avec ce qu'elle est (« Autre serveur : Ollama, LM Studio, OpenRouter, tout serveur qui parle l'API Chat Completions »), puis le formulaire. Liste vide : une phrase sur ce qu'est un fournisseur et le bouton.
- **Le Guide**, chapitre « Brancher une IA » : « Ajoute un fournisseur », « Choisis sa sorte et colle ta clé » ; la clé dite facultative pour un serveur à soi.

## Ce qui change ailleurs

- **La base** : `ai_provider_configs` passe d'une ligne par identifiant fixe à une ligne par fournisseur créé : un identifiant tiré au hasard, la sorte, le nom, la marque du défaut, la config. Migration : chaque config réglée devient un fournisseur sous son nom d'aujourd'hui (« Claude », « Claude (économique) »…), clé et réglages gardés, l'actif devenant le défaut ; les sortes jamais réglées disparaissent. Les routines et les sessions gardent leur lien. Les configs ne sont pas dans la sauvegarde.
- **Les noms à l'écran** : ceux des sortes passent par les textes traduits ; « (économique) », écrit en dur en français dans chaque fournisseur, disparaît avec les variantes.
- **Le choix d'une routine** (`AutomationSettings`) offre les fournisseurs créés, non plus les huit.
- **Le banc** (`BenchScenario`) crée son fournisseur Autre serveur au lieu de régler `compatible_standard`.
