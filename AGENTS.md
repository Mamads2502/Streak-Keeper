# Instructions permanentes

- Répondre en français.
- À la fin de chaque modification, proposer un message de commit résumant le travail effectué. L’utilisateur se charge du commit et du push ; ne pas les exécuter sans demande explicite.
- Avant les travaux importants, consulter `ETAT_DU_PROJET.md`, puis les sources concernées et le `README.md` si nécessaire.
- Respecter l’architecture existante et les conventions du projet. Ne pas introduire de refonte, de nouvelle couche ou de dépendance sans nécessité liée à la demande.
- Vérifier les modifications avec des contrôles adaptés à leur portée : relecture et contrôle du diff pour la documentation ; compilation et tests pertinents pour le code. Indiquer les vérifications effectuées et celles qui n’ont pas pu l’être, sans présenter un comportement non testé comme validé.
- Après chaque modification importante, mettre à jour `ETAT_DU_PROJET.md` pour refléter les fonctionnalités, l’architecture, les décisions techniques, les problèmes connus et les prochaines étapes. Conserver les informations utiles, supprimer ou corriger les éléments devenus obsolètes et éviter les doublons.

Le `README.md` contient les instructions d’installation et d’utilisation ; `ETAT_DU_PROJET.md` décrit l’état technique courant et le travail restant.

## Règles Git et validation

- Pour chaque nouvelle fonctionnalité importante, vérifier l’état Git, puis créer et sélectionner une branche `feature/<nom-descriptif>`.
- Travailler dans le dossier actuel, sans worktree.
- Garder les corrections liées à une fonctionnalité sur sa branche.
- Compiler et vérifier l’APK debug après les modifications importantes.
- Attendre les tests de l’utilisateur sur Waydroid avant de proposer une fusion dans `main`.
- Ne jamais effectuer de merge, de push ou de suppression de branche sans l’accord de l’utilisateur.
