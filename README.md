# Streak keeper 🔥 — application Android expérimentale

Application Android **non officielle**, développée en Java natif pour gérer des profils et des listes d'amis, puis préremplir le formulaire d'assistance Snapchat dans un WebView.

## Ce qui est implémenté

- Menu latéral gauche : ouverture via ☰ ou balayage depuis le bord gauche, sélection du profil actif et bouton « Ajouter un profil » fixé en bas. Fermeture par balayage à gauche, toucher extérieur, × ou Retour Android.
- Chaque profil du menu possède un bouton ⋮ : « Modifier » ou « Supprimer… ». La suppression exige une confirmation ; gérer un autre profil ne change pas le profil actif.
- Recherche repliable dans la liste des amis : surnom et pseudo Snapchat, sans distinction de casse ou d’accents, avec compteur de résultats et effacement rapide. Tri mémorisé : nom A–Z, plus récents ou plus anciens.
- Plusieurs presets : pseudo, e-mail unique, téléphone et description facultative activable par une case à cocher.
- Listes d'amis séparées pour chaque profil.
- Ajout, suppression, importation de tous les amis d'un autre profil sans doublons.
- Données conservées **localement** dans les préférences privées de l'application.
- Un seul ami à la fois : chargement du formulaire officiel, tentative de préremplissage de ses champs HTML, puis affichage de la vraie page pour la vérification et l'envoi **manuels**.
- Conservation ordinaire des cookies du WebView. Le reCAPTCHA reste sous le contrôle du site.

> **État actuel : prototype non testé sur un appareil Android.** La compilation debug et l’analyse lint ont réussi le 28 octobre 2026 ; l’APK local est disponible dans `app/build/outputs/apk/debug/app-debug.apk`. Le préremplissage dépend du site Snapchat, qui peut changer ou bloquer les navigateurs intégrés.

Les anciens profils reprennent leur e-mail de contact (ou celui du compte si le premier est vide). L’adresse unique sert au préremplissage des champs e-mail connus du formulaire. Les descriptions existantes restent activées ; décocher leur option conserve le texte mais désactive son préremplissage.

## Identité visuelle

Le thème par défaut est clair : fond gris très léger, cartes blanches, typographie sans-serif et accent indigo. Accueil, menu latéral, dialogues, champs et écran d’assistance partagent les mêmes composants. Le lien grisé et souligné « Comment ça marche ? » ouvre un guide en trois étapes.

Pour changer l’accent, modifier uniquement `sk_accent` dans `app/src/main/res/values/colors.xml`, puis recompiler. Les teintes secondaires, boutons, contrôles Android et styles du formulaire en découlent ; le texte des boutons pleins choisit automatiquement le noir ou le blanc selon le contraste.

Les rôles de couleur sont déclarés dans `attrs.xml`, associés au thème dans `styles.xml`, et consommés par `AppTheme.java` (composants et CSS du formulaire). Un futur thème pourra fournir une autre palette sans modifier la logique métier. Aucun mode sombre ni sélecteur de thème n’est implémenté.

Le formulaire officiel reçoit uniquement des styles CSS sur sa page HTTPS connue : couleurs, typographie, espacements et champs. Aucun champ, message de validation, CAPTCHA ou bouton d’envoi n’est masqué. Les pages externes et le contenu des iframes restent gérés par leurs sites ; des changements du site Snapchat peuvent limiter l’harmonisation.

## Prérequis

Streak keeper est une application **Android native en Java** : elle ne s'exécute pas directement comme une application de bureau Ubuntu. Sous Linux, on peut la compiler et la lancer dans un émulateur Android ou sur un vrai téléphone.

| Outil | Version / condition |
|---|---|
| Ubuntu | Linux 64 bits |
| Android Studio | Version récente compatible avec le projet |
| Java (JDK) | **17** |
| Android SDK Platform | **API 35** (`compileSdk 35`) |
| Android Gradle Plugin | **8.7.3** |
| Gradle | **8.9**, fourni via le wrapper |
| Téléphone Android | Android 8.0 (API 26) minimum |

Les téléchargements du SDK et des dépendances nécessitent une connexion Internet.

## 1. Installer les outils sur Ubuntu

### Java et utilitaires

```bash
sudo apt update
sudo apt install git unzip openjdk-17-jdk
```

Pour vérifier :

```bash
java -version
git --version
```

Si plusieurs Java sont installés, choisis **Gradle JDK 17** dans les paramètres Gradle d'Android Studio.

### Android Studio

1. Télécharger [Android Studio pour Linux](https://developer.android.com/studio) depuis Google.
2. Décompresser le fichier `.tar.gz` dans un répertoire de ton choix, par exemple `~/Applications`.
3. Lancer le programme `android-studio/bin/studio` (ou `studio.sh` sur certaines versions).
4. Suivre l'assistant de configuration.
5. Dans **SDK Manager**, installer **Android SDK Platform 35**, **Android SDK Platform-Tools** et les outils de compilation proposés. Si tu veux un émulateur, installer aussi **Android Emulator**.

Guide officiel : [Installer Android Studio sur Linux](https://developer.android.com/studio/install).

## 2. Récupérer le projet

L’URL du dépôt GitHub reste inchangée ; la commande ci-dessous utilise le dossier local `StreakKeeper`. Si SSH fonctionne :

```bash
git clone git@github.com:Mamads2502/StreakPresets.git StreakKeeper
cd StreakKeeper
```

**Si tu as déjà le dossier local**, inutile de refaire le `git clone`. Ouvre simplement le dossier contenant `settings.gradle`, `gradlew` et `app/`.

Dans Android Studio : **Open** → sélectionner le dossier du projet → attendre la fin de **Gradle Sync** et accepter le téléchargement des dépendances nécessaires.

## 3. Compiler un APK de test sous Ubuntu

Ouvre un terminal à la racine du projet :

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Si la compilation réussit, le fichier généré est :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Il s'agit d'un **APK de débogage**, automatiquement signé pour les tests, et non d'une version destinée à Google Play. Tu peux relancer `./gradlew assembleDebug` après une modification du code.

## 4. Tester l'application sur Ubuntu (émulateur Android)

1. Ouvrir Android Studio, puis **Device Manager**.
2. Cliquer sur **Create Device** et choisir un modèle (par exemple Pixel).
3. Télécharger une image système Android et terminer la création.
4. Démarrer le téléphone virtuel.
5. Le sélectionner en haut d'Android Studio, puis cliquer sur **Run ▶**.

Si l'émulateur est déjà démarré, tu peux aussi installer l'APK depuis le terminal :

```bash
./gradlew installDebug
```

L'émulateur peut être gourmand en mémoire et nécessite une virtualisation correctement configurée ; un téléphone physique est souvent plus simple pour tester le formulaire web.

## 5. Installer sur un vrai téléphone Android — après compilation réussie

**L’APK debug a été compilé, mais reste à tester sur appareil.** Pour installer une version correspondant à tes sources, utilise le résultat d’une compilation `assembleDebug` réussie.

### Méthode recommandée : Android Studio

1. Sur ton téléphone, activer les **Options pour les développeurs** (si nécessaire, appuyer plusieurs fois sur *Numéro de version* dans les paramètres).
2. Activer **Débogage USB**.
3. Brancher le téléphone au PC et accepter la demande d'autorisation de débogage.
4. Sélectionner le téléphone dans Android Studio, puis cliquer sur **Run ▶**.

### Méthode terminal : ADB

Si `adb` est accessible (via **Android SDK Platform-Tools**) :

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb devices` doit afficher le téléphone avec l'état `device`. Si son état est `unauthorized`, accepte la demande de débogage sur l'écran du téléphone.

Autre possibilité : avec le téléphone connecté et reconnu, exécuter directement :

```bash
./gradlew installDebug
```

Une fois l'APK créé, tu peux aussi le transférer sur le téléphone et l'ouvrir avec son gestionnaire de fichiers (en autorisant l'installation depuis cette source). Le débogage USB n'est pas requis pour cette dernière méthode.

## 6. Premier test de l'application

1. Ouvrir le menu ☰, puis « Ajouter un profil » en bas du menu. Renseigner le pseudo Snapchat, l’e-mail et le téléphone. Pour saisir une description, cocher « Ajouter une description à la demande ».
2. Ajouter un ami avec son nom affiché et son pseudo Snapchat.
3. Cliquer sur **Préparer** à côté de cet ami.
4. Attendre l'ouverture du formulaire dans le `WebView`.
5. Vérifier les valeurs préremplies ; utiliser **Re-remplir** si nécessaire.
6. Vérifier la description et toute demande de reCAPTCHA.

**Pour le tout premier test, n'envoie aucun véritable formulaire : vérifie seulement l'affichage et le préremplissage.** Lors d'une utilisation réelle, l'envoi reste manuel et ne concerne qu'un ami à la fois.

### Vérifier le thème clair sur Waydroid

- Vérifier l’accueil vide et rempli, les noms longs, le menu et ses options ⋮, les dialogues de création/modification/suppression et les cases à cocher.
- Toucher le lien grisé « Comment ça marche ? » : les explications doivent apparaître uniquement après le clic et pouvoir être refermées.
- Vérifier que le libellé de chaque champ reste lisible pendant la saisie ; décocher la description doit masquer son champ et son libellé.
- Tester une police agrandie, le paysage et le clavier ouvert ; les formulaires et le guide doivent rester consultables.
- Ouvrir l’assistance, tester Retour et Re-remplir, et vérifier la présence des champs, messages de validation et CAPTCHA sans envoyer de demande réelle. Vérifier également les erreurs réseau.

### Vérifier le menu sur Waydroid

- Ouvrir via ☰ puis depuis le bord gauche ; fermer par balayage à gauche, toucher extérieur, × et Retour Android.
- Créer un premier profil, puis un second. Passer de l’un à l’autre : vérifier la coche du profil actif, sa liste d’amis et sa sélection après redémarrage.
- Avec plusieurs profils, faire défiler la liste et vérifier que « Ajouter un profil » reste en bas. Vérifier aussi les noms longs, le mode paysage et une taille de police agrandie.
- Via ⋮, modifier ou supprimer un profil actif puis inactif : vérifier le profil ciblé, l’actualisation du menu et la conservation de la sélection pour un profil inactif. Annuler une modification et une suppression pour vérifier qu’aucune donnée ne change. Tester aussi la suppression du dernier profil et l’annulation d’une création.
- Ouvrir « Préparer » puis revenir aux presets : le menu doit redevenir accessible. Il reste verrouillé pendant l’affichage du formulaire pour éviter un changement de profil en cours de préparation.

### Centrage automatique du formulaire

Après le préremplissage, l’application attend brièvement une position stable puis centre instantanément le vrai bouton d’envoi, sans animation de défilement. Les erreurs, champs invalides et vérifications visibles reconnus sont prioritaires. Le centrage s’annule dès que tu manipules la page ; « Re-remplir » permet une nouvelle tentative. Tu peux toujours remonter et vérifier les informations, et l’envoi reste manuel.

La détection dépend du site : si un élément n’est pas reconnu, le défilement manuel reste disponible. Tester sur Waydroid le formulaire complet, les champs manquants, le CAPTCHA et le retour rapide vers un autre ami. Aucun envoi réel n’est nécessaire pour vérifier le centrage.

Tests de logique (DOM simulé, QuickJS `qjs` requis) : `python3 tests/test_form_navigation.py`.

### Recherche et tri des amis

La loupe à gauche ouvre une saisie entre les icônes ; si la largeur disponible est insuffisante (notamment avec de grands caractères), elle apparaît en dessous. L’entonnoir à droite ouvre le menu de tri. La recherche est dynamique sur les surnoms et les pseudos Snapchat. Les fragments, accents et majuscules sont pris en charge ; `@` est facultatif. Plusieurs mots doivent tous correspondre, éventuellement répartis entre surnom et pseudo. Le tri choisi reste appliqué aux résultats et est mémorisé entre les lancements.

La croix efface le texte ; toucher à nouveau la loupe ou utiliser Retour (après fermeture du clavier) referme la recherche et réaffiche la liste complète. Changer de profil réinitialise la recherche ; revenir du formulaire conserve le filtre.

Les nouveaux amis enregistrent leur date d’ajout au profil, y compris lors d’une copie. Pour les anciens amis sans date, l’ordre de la liste sauvegardée fait référence ; ils précèdent les nouveaux dans « Plus anciens ». Aucune date historique n’est inventée.

À tester sur Waydroid : filtre par surnom/pseudo, accents et majuscules, aucun résultat, effacement, changement de profil, retour du formulaire, rotation, tri après redémarrage et copie d’amis.

Tests Java sans dépendance Android :

```bash
mkdir -p /tmp/streak-friend-tests
javac -d /tmp/streak-friend-tests app/src/main/java/fr/streakkeeper/app/FriendList.java tests/FriendListTest.java
java -cp /tmp/streak-friend-tests fr.streakkeeper.app.FriendListTest
```

## 7. Dépannage rapide

| Problème | Piste de résolution |
|---|---|
| `Permission denied: ./gradlew` | Exécuter `chmod +x gradlew`. |
| `SDK location not found` | Installer/configurer le SDK dans Android Studio et relancer Gradle Sync. |
| Erreur de version Java | Sélectionner le JDK 17 comme **Gradle JDK**. |
| Échec de téléchargement Gradle | Vérifier Internet et les paramètres de proxy. |
| Aucun téléphone détecté | Vérifier le câble USB, le débogage et l'autorisation sur le téléphone. |
| `adb: command not found` | Installer **Android SDK Platform-Tools** et ajouter son répertoire au `PATH`, ou utiliser Android Studio. |
| Formulaire vide ou mal rempli | Patienter, cliquer sur **Re-remplir**, puis corriger manuellement. |
| reCAPTCHA ou page bloquée | Le site Snapchat contrôle cette étape ; l'application ne contourne pas sa sécurité. |

## 8. Structure du projet

```text
app/build.gradle                       # Configuration Android du module
app/src/main/AndroidManifest.xml       # Déclaration de l'application
app/src/main/java/fr/streakkeeper/app/
    MainActivity.java                  # Interface, presets, contacts et WebView
gradlew                                # Compilation via Gradle sous Linux
gradle/wrapper/                        # Version du wrapper Gradle
```

Les sources utilisent le package et le namespace `fr.streakkeeper.app`. L’identifiant Android installé reste `fr.streakpresets.app` pour conserver la compatibilité des mises à jour. Le fichier de préférences historique `MainActivity` est ouvert explicitement ; les anciennes données sont lues puis migrées vers la clé `streak_keeper_v1` lors de la prochaine sauvegarde.

## 9. À propos du formulaire

Le préremplissage utilise les identifiants HTML observés sur le formulaire officiel du 27 octobre 2026. Ces identifiants peuvent changer. Si un champ ne se remplit pas, le site demeure accessible pour corriger les valeurs à la main.

L'application **n'appuie jamais sur « Envoyer »**, n'imite pas de jeton reCAPTCHA et n'effectue aucune requête directe à l'API de Zendesk. La personne doit vérifier les informations, résoudre toute vérification demandée, puis envoyer elle-même le formulaire depuis la page affichée.

Un CAPTCHA n'est pas nécessairement redemandé à chaque visite, mais aucune session ou mise en cache ne garantit son absence. Les jetons de réponse reCAPTCHA sont à usage unique.

Snapchat peut bloquer le formulaire dans les WebViews, modifier ses champs ou refuser des demandes tierces. Ses conditions encadrent aussi les applications tierces. Il ne faut pas considérer ce prototype comme une intégration approuvée par Snapchat.

## 10. Limites de cette version

- Seulement les conversations **privées** ; le choix « groupe » n'est pas traité.
- Importation de tous les amis (pas de sous-sélection lors de l'import).
- Pas de récupération automatique de photo de profil ou de contacts depuis Snapchat.
- Pas de soumission automatique, d'envoi groupé ni de contournement reCAPTCHA.
- La compilation et l’analyse lint réussissent, mais **le fonctionnement sur appareil Android reste à valider**.

## 11. Confidentialité

Les e-mails et téléphones sont enregistrés dans le stockage privé de l'application, mais ne sont **pas chiffrés séparément**. N'utilise que des profils dont tu es autorisé à gérer les informations. Aucun mot de passe Snapchat n'est requis.

## Documentation Android officielle

- [Installer Android Studio](https://developer.android.com/studio/install)
- [Compiler un APK en ligne de commande](https://developer.android.com/build/building-cmdline)
- [Tester sur un téléphone Android](https://developer.android.com/studio/run/device)
- [Créer un téléphone virtuel](https://developer.android.com/studio/run/managing-avds)
