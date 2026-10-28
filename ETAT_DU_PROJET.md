# État du projet — Streak keeper

Dernière mise à jour : 28 octobre 2026.

## Objectif

Application Android expérimentale et non officielle permettant de gérer plusieurs profils Snapchat et leurs listes d’amis, puis de préremplir le formulaire officiel d’assistance pour un ami à la fois. L’utilisateur vérifie les informations, réalise les éventuelles vérifications reCAPTCHA et envoie lui-même le formulaire.

## Architecture

- Un seul module Android : `app`, avec le package et l’identifiant d’application `fr.streakpresets.app`.
- `app/src/main/java/fr/streakpresets/app/MainActivity.java` regroupe l’interface native construite en Java, la logique des profils et amis, la persistance et le WebView. Les modèles `Profile` et `Friend` sont des classes internes.
- Les profils, leurs amis et l’identifiant du profil actif sont sérialisés en JSON dans les préférences privées de l’activité (`SharedPreferences`), sous la clé `streak_presets_v1`.
- Le WebView charge le formulaire hébergé sur `help.snapchat.com`. Le préremplissage utilise `evaluateJavascript` et les identifiants HTML des champs, avec des tentatives différées si les champs ne sont pas encore disponibles.
- `app/src/main/AndroidManifest.xml` déclare l’activité de lancement et la permission Internet. Les fichiers Gradle à la racine et dans `app/` définissent la compilation ; le wrapper se trouve dans `gradle/wrapper/`.

## Fonctionnalités existantes

Fonctionnalités présentes dans le code, dont le fonctionnement sur appareil reste à valider :

- Création, sélection, modification et suppression de profils : nom du preset sans exemple nominatif, pseudo, e-mail unique, téléphone et description facultative. Une case à cocher affiche ou masque la description ; son état est enregistré et contrôle le préremplissage.
- Menu latéral gauche animé : barre supérieure fixe avec ☰, ouverture par balayage depuis le bord gauche, profils défilants avec sélection active et ajout fixé en bas. Fermeture par balayage à gauche, toucher extérieur, × ou Retour Android. La sélection et la création conservent leur persistance existante.
- Actions de profil regroupées dans le bouton ⋮ de chaque ligne du tiroir : modification et suppression avec confirmation nominative. Aucun bouton de gestion sur l’accueil ; modifier ou supprimer un profil inactif conserve le profil actif. Créer un profil le sélectionne ; supprimer le profil actif sélectionne le premier restant, ou aucun si la liste est vide.
- Liste d’amis propre à chaque profil : ajout et suppression avec confirmation ; utilisation du pseudo comme nom affiché si aucun nom n’est saisi.
- Copie de tous les amis d’un autre profil, sans doublons de pseudo (comparaison insensible à la casse, également utilisée lors de l’ajout).
- Sauvegarde locale et restauration du profil actif au démarrage.
- Bouton « Préparer » pour ouvrir le formulaire avec les données d’un ami ; bouton « Re-remplir », état du préremplissage et retour aux presets.
- Gestion d’une erreur de chargement de la page principale et message de repli lorsque le préremplissage échoue.

## Décisions techniques actuelles

- Android natif en Java 17, interface construite en Java. AndroidX `drawerlayout:1.2.0` gère les gestes, l’animation, le voile et l’accessibilité du tiroir ; l’activité et le stockage existants sont conservés.
- Le tiroir mesure au maximum 320 dp en gardant une zone extérieure visible. Son ouverture est restaurée après recréation de l’activité. Il est verrouillé pendant le formulaire WebView puis déverrouillé au retour à l’accueil.
- Android Gradle Plugin 8.7.3 et Gradle 8.9 ; `compileSdk 35`, `targetSdk 34`, `minSdk 26` (Android 8.0 minimum).
- Migration des profils existants : le champ JSON `email` remplace `contactEmail` et `accountEmail`, avec priorité à l’ancien e-mail de contact puis repli sur celui du compte. La même adresse alimente les deux identifiants HTML e-mail déjà ciblés, si présents.
- Le champ JSON `descriptionEnabled` mémorise l’activation de la description ; pour les anciens profils, une description non vide est activée par défaut. Le texte est conservé quand l’option est décochée, mais n’est pas injecté dans le formulaire.
- Nom de l’application : `Streak keeper` (libellé Android, accueil et projet Gradle). L’identifiant/package `fr.streakpresets.app` et la clé `streak_presets_v1` sont conservés pour la compatibilité des mises à jour et des données. Le dépôt GitHub garde son nom actuel.
- Version déclarée : `0.1.0`, code de version `1`.
- Stockage local privé, sans chiffrement supplémentaire des e-mails et téléphones ; sauvegarde Android désactivée dans le manifeste. Aucun mot de passe Snapchat n’est demandé.
- JavaScript, stockage DOM et cookies, y compris tiers, activés pour le formulaire. Accès WebView aux fichiers et contenus locaux désactivé, contenu mixte et trafic HTTP en clair interdits.
- Pas de soumission automatique, d’envoi groupé, de requête directe à l’API Zendesk ni de contournement reCAPTCHA. Le formulaire reste visible et l’envoi manuel.

## Problèmes connus et limites

- Validation du menu latéral et des options ⋮ par profil le 28 octobre 2026 : `./gradlew assembleDebug lintDebug --offline` réussit. APK généré dans `app/build/outputs/apk/debug/app-debug.apk`, intégrité ZIP et signature (`apksigner verify`) vérifiées. Les gestes et le rendu restent à tester sur Waydroid.
- Le fonctionnement sur appareil et le préremplissage réel du formulaire n’ont pas été testés lors de cette modification.
- Lint termine sans erreur, avec 16 avertissements : API cible ancienne, activation de JavaScript, règles d’extraction des données, icône absente, chaînes d’interface écrites directement en Java et alignements à gauche explicites (tiroir demandé à gauche).
- Aucun test automatisé n’est présent dans les sources examinées.
- Le préremplissage dépend des identifiants HTML du site et de son chargement dynamique. Le README indique des identifiants observés le 27 octobre 2026 ; leur validité actuelle n’a pas été vérifiée.
- Le site peut bloquer les WebViews ou modifier son formulaire. La conservation des cookies ne garantit pas l’absence de CAPTCHA.
- Seules les conversations privées sont visées ; le choix de groupe n’est pas traité.
- La copie des amis est globale, sans sélection partielle. Aucune récupération automatique des contacts ou photos depuis Snapchat n’est implémentée.
- La validation des saisies se limite principalement aux champs obligatoires et aux doublons d’amis ; les formats d’e-mail et de téléphone ne sont pas vérifiés.

## Prochaines étapes

1. Tester le menu latéral sur Waydroid selon la liste du README : gestes, fermetures, création/sélection persistante, liste longue, petits écrans, rotation et retour depuis le formulaire. Attendre le retour de l’utilisateur avant de proposer une fusion dans `main`.
2. Tester sur émulateur ou appareil les opérations sur les profils et amis, la copie sans doublons, la persistance après redémarrage et la navigation. Vérifier aussi la migration des anciens e-mails et descriptions, ainsi que le masquage et la désactivation du préremplissage de la description via la case à cocher.
3. Vérifier le chargement du formulaire, le préremplissage, le bouton « Re-remplir » et les erreurs réseau, sans envoyer de véritable demande lors du premier test, conformément au README.
4. Corriger les problèmes constatés et ajouter les vérifications adaptées aux changements ; actualiser le statut de validation dans la documentation.

Les procédures détaillées d’installation, de compilation et de test manuel sont dans `README.md`.
