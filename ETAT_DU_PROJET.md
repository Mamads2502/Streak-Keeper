# État du projet — Streak Presets

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

- Création, sélection, modification et suppression de profils : nom du preset, pseudo, e-mail de contact, e-mail du compte, téléphone et description facultative.
- Liste d’amis propre à chaque profil : ajout et suppression avec confirmation ; utilisation du pseudo comme nom affiché si aucun nom n’est saisi.
- Copie de tous les amis d’un autre profil, sans doublons de pseudo (comparaison insensible à la casse, également utilisée lors de l’ajout).
- Sauvegarde locale et restauration du profil actif au démarrage.
- Bouton « Préparer » pour ouvrir le formulaire avec les données d’un ami ; bouton « Re-remplir », état du préremplissage et retour aux presets.
- Gestion d’une erreur de chargement de la page principale et message de repli lorsque le préremplissage échoue.

## Décisions techniques actuelles

- Android natif en Java 17, avec les composants du framework Android ; aucune dépendance applicative externe déclarée.
- Android Gradle Plugin 8.7.3 et Gradle 8.9 ; `compileSdk 35`, `targetSdk 34`, `minSdk 26` (Android 8.0 minimum).
- Version déclarée : `0.1.0`, code de version `1`.
- Stockage local privé, sans chiffrement supplémentaire des e-mails et téléphones ; sauvegarde Android désactivée dans le manifeste. Aucun mot de passe Snapchat n’est demandé.
- JavaScript, stockage DOM et cookies, y compris tiers, activés pour le formulaire. Accès WebView aux fichiers et contenus locaux désactivé, contenu mixte et trafic HTTP en clair interdits.
- Pas de soumission automatique, d’envoi groupé, de requête directe à l’API Zendesk ni de contournement reCAPTCHA. Le formulaire reste visible et l’envoi manuel.

## Problèmes connus et limites

- Le README décrit un prototype non compilé et non testé sur appareil dans son environnement de rédaction, faute de SDK Android. Cette mention est historique : la disponibilité actuelle du SDK et la réussite d’une compilation restent à vérifier. Aucun nouveau résultat de compilation ou de test sur appareil n’a été établi lors de cette mise à jour documentaire.
- Un APK local existe dans `app/build/outputs/apk/debug/app-debug.apk`, accompagné de `output-metadata.json`. Sa présence nuance le README, mais ne prouve pas qu’il correspond aux sources actuelles ni qu’il a été testé ; sa validation reste à effectuer.
- Aucun test automatisé n’est présent dans les sources examinées.
- Le préremplissage dépend des identifiants HTML du site et de son chargement dynamique. Le README indique des identifiants observés le 27 octobre 2026 ; leur validité actuelle n’a pas été vérifiée.
- Le site peut bloquer les WebViews ou modifier son formulaire. La conservation des cookies ne garantit pas l’absence de CAPTCHA.
- Seules les conversations privées sont visées ; le choix de groupe n’est pas traité.
- La copie des amis est globale, sans sélection partielle. Aucune récupération automatique des contacts ou photos depuis Snapchat n’est implémentée.
- La validation des saisies se limite principalement aux champs obligatoires et aux doublons d’amis ; les formats d’e-mail et de téléphone ne sont pas vérifiés.

## Prochaines étapes

1. Vérifier l’environnement Java 17 / SDK Android 35 et exécuter `./gradlew assembleDebug` ; consigner le résultat réel et les éventuels blocages.
2. Tester sur émulateur ou appareil les opérations sur les profils et amis, la copie sans doublons, la persistance après redémarrage et la navigation.
3. Vérifier le chargement du formulaire, le préremplissage, le bouton « Re-remplir » et les erreurs réseau, sans envoyer de véritable demande lors du premier test, conformément au README.
4. Corriger les problèmes constatés et ajouter les vérifications adaptées aux changements ; actualiser le statut de validation dans la documentation.

Les procédures détaillées d’installation, de compilation et de test manuel sont dans `README.md`.
