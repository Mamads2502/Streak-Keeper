# Consignes

- Répondre en français.
- Avant tout travail important, lire `ETAT_DU_PROJET.md` (état technique, limites, prochaines étapes), puis les sources utiles. Consulter `README.md` pour l’installation et l’utilisation si nécessaire.
- Respecter l’architecture et les conventions existantes ; éviter les refontes et dépendances inutiles.
- Après toute modification importante, actualiser `ETAT_DU_PROJET.md` : fonctionnalités, architecture, décisions, problèmes et prochaines étapes. Préserver les informations utiles, corriger l’obsolète, éviter les doublons.

## Git

- Nouvelle fonctionnalité importante : vérifier l’état Git, créer et sélectionner `feature/<nom-descriptif>`. Garder les corrections associées sur cette branche.
- Travailler dans le dossier actuel, sans worktree.
- Attendre les tests de l’utilisateur sur Waydroid avant de proposer une fusion dans `main`.
- Aucun commit, merge ou push sans accord explicite. L’utilisateur gère normalement commit et push.
- Une fonctionnalité terminée et intégrée à `main` : vérifier que sa branche locale est entièrement fusionnée, puis la supprimer avec `git branch -d`, sans redemander confirmation. Toute autre suppression de branche nécessite un accord explicite.

## Validation et livraison

- Documentation : relire et vérifier le diff. Code : compiler et effectuer les tests pertinents ; après les modifications importantes, vérifier aussi l’APK debug.
- Indiquer les vérifications réalisées et leurs limites ; ne pas présenter un comportement non testé comme validé.
- Terminer chaque modification sans demande explicite de commit par une proposition de message courte couvrant toutes les modifications non commitées. Résumer le résultat final lorsque le même élément a changé plusieurs fois et séparer les changements distincts par « + ».
