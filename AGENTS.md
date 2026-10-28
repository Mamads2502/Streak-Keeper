# Consignes

- Répondre en français.
- Avant tout travail important, lire `ETAT_DU_PROJET.md` (état technique, limites, prochaines étapes), puis les sources utiles. Consulter `README.md` pour l’installation et l’utilisation si nécessaire.
- Respecter l’architecture et les conventions existantes ; éviter les refontes et dépendances inutiles.
- Après toute modification importante, actualiser `ETAT_DU_PROJET.md` : fonctionnalités, architecture, décisions, problèmes et prochaines étapes. Préserver les informations utiles, corriger l’obsolète, éviter les doublons.

## Git

- Nouvelle fonctionnalité importante : vérifier l’état Git, créer et sélectionner `feature/<nom-descriptif>`. Garder les corrections associées sur cette branche.
- Travailler dans le dossier actuel, sans worktree.
- Attendre les tests de l’utilisateur sur Waydroid avant de proposer une fusion dans `main`.
- Aucun commit, merge, push ou suppression de branche sans accord explicite. L’utilisateur gère normalement commit et push.

## Validation et livraison

- Documentation : relire et vérifier le diff. Code : compiler et effectuer les tests pertinents ; après les modifications importantes, vérifier aussi l’APK debug.
- Indiquer les vérifications réalisées et leurs limites ; ne pas présenter un comportement non testé comme validé.
- Terminer chaque modification par une proposition de message de commit.
