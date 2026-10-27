# Streak Presets — Android (prototype personnel)

Application Android **non officielle**, développée en Java natif pour gérer des profils et des listes d'amis, puis préremplir le formulaire d'assistance Snapchat dans un WebView.

## Ce qui est implémenté

- Plusieurs presets : pseudo, e-mail de contact, e-mail du compte, téléphone, description facultative.
- Listes d'amis séparées pour chaque profil.
- Ajout, suppression, importation de tous les amis d'un autre profil sans doublons.
- Données conservées **localement** dans les préférences privées de l'application.
- Un seul ami à la fois : chargement du formulaire officiel, tentative de préremplissage de ses champs HTML, puis affichage de la vraie page pour la vérification et l'envoi **manuels**.
- Conservation ordinaire des cookies du WebView. Le reCAPTCHA reste sous le contrôle du site.

## Installation / essai

1. Installer Android Studio et son SDK Android 35.
2. Ouvrir le dossier `streak-presets-android` dans Android Studio comme projet Gradle.
3. Laisser Gradle synchroniser et télécharger ses dépendances.
4. Brancher un téléphone Android avec le débogage USB activé (ou lancer un émulateur).
5. Cliquer sur **Run**. Aucune connexion à un compte Snapchat n'est demandée.

Le projet contient un Gradle Wrapper 8.9 et le plugin Android Gradle 8.7.3. Java 17 est nécessaire.

## À propos du formulaire

Le préremplissage utilise les identifiants HTML observés sur le formulaire officiel du 27 octobre 2026. Ces identifiants peuvent changer. Si un champ ne se remplit pas, le site demeure accessible pour corriger les valeurs à la main.

L'application **n'appuie jamais sur « Envoyer »**, n'imite pas de jeton reCAPTCHA et n'effectue aucune requête directe à l'API de Zendesk. La personne doit vérifier les informations, résoudre toute vérification demandée, puis envoyer elle-même le formulaire depuis la page affichée.

Un CAPTCHA n'est pas nécessairement redemandé à chaque visite, mais aucune session ou mise en cache ne garantit son absence. Les jetons de réponse reCAPTCHA sont à usage unique.

Snapchat peut bloquer le formulaire dans les WebViews, modifier ses champs ou refuser des demandes tierces. Ses conditions encadrent aussi les applications tierces. Il ne faut pas considérer ce prototype comme une intégration approuvée par Snapchat.

## Limites de cette version

- Seulement les conversations **privées** ; le choix « groupe » n'est pas traité.
- Importation de tous les amis (pas de sous-sélection lors de l'import).
- Pas de récupération automatique de photo de profil ou de contacts depuis Snapchat.
- Pas de soumission automatique, d'envoi groupé ni de contournement reCAPTCHA.
- Le code source a été préparé ici, mais **n'a pas pu être compilé ni testé sur un appareil Android** dans cet environnement (SDK Android non installé).

## Confidentialité

Les e-mails et téléphones sont enregistrés dans le stockage privé de l'application, mais ne sont **pas chiffrés séparément**. N'utilise que des profils dont tu es autorisé à gérer les informations. Aucun mot de passe Snapchat n'est requis.
