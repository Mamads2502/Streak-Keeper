# Streak keeper 🔥

Une application Android pour préparer plus facilement des demandes de restauration de flammes Snapchat, sans recopier les mêmes informations à chaque fois.

Streak keeper permet de conserver des profils et des listes d’amis, puis de préremplir le formulaire officiel d’assistance Snapchat. Pratique quand plusieurs demandes sont à préparer. Les flammes ne vont pas se récupérer toutes seules, mais les doigts peuvent souffler un peu.

> **Projet indépendant et expérimental.** Streak keeper n’est pas affilié à Snapchat. Il a été créé par un étudiant en « vibe coding », avec l’aide d’outils d’intelligence artificielle. Le code et les fonctionnalités peuvent contenir des bugs : les retours sont les bienvenus.

## Ce que fait l’application

- Gérer plusieurs profils, chacun avec sa propre liste d’amis.
- Ajouter, modifier, rechercher, trier et marquer des amis en favoris.
- Importer des amis depuis un autre profil, un export Snapchat ou un fichier Streak keeper.
- Préparer une demande pour un ami, ou une série de demandes pour plusieurs amis.
- Personnaliser l’apparence avec des thèmes clair, sombre et OLED, des couleurs d’accent et plusieurs icônes.

**L’envoi de chaque formulaire reste manuel.** Le site Snapchat peut demander une vérification CAPTCHA et décide lui-même du traitement des demandes. L’application ne garantit pas la restauration des flammes.

## Installer l’application

**Android 8.0 ou plus récent** et une connexion Internet sont nécessaires pour accéder à l’assistance Snapchat.

1. Ouvrir la page [Releases](https://github.com/Mamads2502/StreakPresets/releases).
2. Télécharger l’APK de la version souhaitée, puis l’ouvrir sur le téléphone.
3. Si Android le demande, autoriser l’installation depuis le navigateur ou le gestionnaire de fichiers utilisé.

Les fichiers « Source code » présents sur GitHub contiennent le code du projet : ce ne sont pas les applications à installer. Si aucune release n’est disponible, l’APK peut aussi être compilé depuis les sources, voir [la section technique](#pour-les-bidouilleurs).

## Premiers pas

1. Ouvrir le menu ☰ et ajouter un profil avec les informations nécessaires au formulaire.
2. Ajouter des amis manuellement ou utiliser le bouton **Importer**.
3. Appuyer sur la flamme à côté d’un ami, ou sélectionner plusieurs amis par un appui long et choisir **Préparer**.
4. Vérifier chaque formulaire, effectuer les éventuelles vérifications demandées par Snapchat et envoyer la demande.

Pour une série, l’application présente les formulaires successivement. Le passage au suivant peut être automatique après détection d’une réponse, ou manuel selon le réglage choisi. Dans tous les cas, chaque envoi demande une action manuelle.

## Importer des amis

Le bouton **Importer** propose deux sources :

- **Depuis un autre profil** : copie des amis déjà enregistrés dans l’application.
- **Depuis un fichier JSON ou ZIP** : importe un fichier JSON exporté par Streak keeper ou un ZIP de données Snapchat.

Les amis à importer sont sélectionnables avant validation. Les pseudos déjà présents dans le profil actif ne sont pas écrasés.

### Depuis Snapchat

1. Sur [accounts.snapchat.com](https://accounts.snapchat.com/), ouvrir **Mes données**.
2. Sélectionner **Données utilisateur** et **Exporter des fichiers JSON**, puis demander l’export.
3. Une fois l’export prêt, télécharger son ZIP et le choisir dans Streak keeper.

L’application recherche la liste des amis actuels dans `json/friends.json`. Les anciens amis, les comptes bloqués et les demandes ne sont pas importés. Le format de l’export dépend de Snapchat : un fichier absent, une liste vide ou un changement de structure peut empêcher l’import.

## Données et sauvegarde

Les profils et les amis sont enregistrés localement dans le stockage privé de l’application. Aucun mot de passe Snapchat n’est demandé. L’analyse des fichiers importés se fait sur l’appareil ; les informations d’une demande sont transmises au site d’assistance lorsque le formulaire est utilisé.

Dans les paramètres, **Exporter les amis du profil actif** enregistre la liste dans un fichier JSON. Cet export conserve les noms, pseudos, favoris et dates enregistrées pour les amis. Il **ne sauvegarde pas les coordonnées, la description ou les réglages du profil**. Pour transférer plusieurs profils, il faut exporter chaque liste, puis recréer les profils avant de les réimporter.

La sauvegarde Android automatique est désactivée. **Désinstaller l’application efface ses données locales.** Il est donc préférable d’exporter les listes d’amis et de conserver séparément les informations des profils avant une désinstallation. Les données locales ne disposent pas d’un chiffrement supplémentaire par l’application : les exports sont à conserver et partager avec précaution.

## Limites à connaître

- Le formulaire est affiché dans une WebView et son préremplissage dépend de la structure du site Snapchat. Une modification du site ou un blocage des navigateurs intégrés peut perturber le fonctionnement.
- Le formulaire et ses données doivent être vérifiés avant chaque envoi. Une progression manuelle ou un indicateur de demande récente ne garantit pas que Snapchat a reçu la demande ou restauré une flamme.
- Une série en cours est abandonnée si l’activité est recréée ou interrompue. Les conversations de groupe ne sont pas (encore) prises en charge.
- Le projet est expérimental : certains parcours et appareils restent à tester.

## Pour les bidouilleurs

### Stack et prérequis

| Élément | Version |
| --- | --- |
| Langage | Java 17, interface Android native |
| Android minimum | 8.0, API 26 |
| Compilation | Android SDK Platform 35, Android Gradle Plugin 8.7.3 |
| Gradle | 8.9, fourni par le wrapper |

Android Studio est facultatif. Il faut un JDK 17 et un SDK Android configuré ; le premier lancement de Gradle peut télécharger des dépendances.

### Compiler et installer un APK debug

```bash
git clone https://github.com/Mamads2502/StreakPresets.git
cd StreakPresets
./gradlew assembleDebug
```

L’APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Pour l’installer sur un appareil connecté avec ADB :

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Un APK debug et un APK release signé avec une autre clé ne peuvent généralement pas se remplacer directement. Une désinstallation peut être nécessaire et efface les données locales : prévoir une sauvegarde avant de changer de signature.

Pour exécuter la compilation et l’analyse statique :

```bash
./gradlew assembleDebug lintDebug
```

### Organisation du code

- `MainActivity.java` : interface, profils, stockage et navigation.
- `AppTheme.java` et `res/values/` : composants visuels, palettes et thèmes.
- `FriendList.java` : recherche, tri et gestion des doublons.
- `SnapchatImport.java` et `FriendsTransfer.java` : lecture des exports et transfert JSON.
- `FormNavigation.java` et `SubmissionPage.java` : logique d’interaction avec le formulaire WebView.
- `tests/` : tests de logique Java et tests de scripts sur DOM simulé.

L’identifiant Android installé est `fr.streakpresets.app` ; le package Java est `fr.streakkeeper.app`. Les préférences utilisent explicitement le fichier historique `MainActivity` afin de préserver les données lors des mises à jour compatibles.

### Format JSON des amis

L’export Streak keeper est un objet versionné. Exemple avec des données fictives :

```json
{
  "format": "streak-keeper-friends",
  "version": 1,
  "friends": [
    {
      "username": "ami_exemple",
      "name": "Un ami",
      "addedAt": 0,
      "favorite": false,
      "lastRequestAt": 0
    }
  ]
}
```

Les dates sont exprimées en millisecondes Unix ; `0` signifie qu’elles sont absentes ou inconnues. Le pseudo est conservé séparément du nom affiché. Les doublons sont ignorés lors de l’import et les entrées existantes ne sont pas remplacées.

### Tests

Les tests Java indépendants d’Android peuvent être lancés avec le JDK :

```bash
mkdir -p /tmp/streak-tests
javac -d /tmp/streak-tests \
  app/src/main/java/fr/streakkeeper/app/FriendList.java \
  app/src/main/java/fr/streakkeeper/app/RecentRequest.java \
  app/src/main/java/fr/streakkeeper/app/TestProfiles.java \
  tests/FriendListTest.java tests/RecentRequestTest.java tests/TestProfilesTest.java
java -cp /tmp/streak-tests fr.streakkeeper.app.FriendListTest
java -cp /tmp/streak-tests fr.streakkeeper.app.RecentRequestTest
java -cp /tmp/streak-tests fr.streakkeeper.app.TestProfilesTest
```

Les tests `test_form_navigation.py` et `test_submission_page.py` nécessitent Python, le JDK et QuickJS (`qjs`). Ils utilisent un DOM simulé et ne remplacent pas un essai sur un appareil ou sur le site réel. `FriendsTransferTest.java` est un test autonome destiné à être exécuté sur Android.

### Builds release

Une release distribuée doit être signée avec une clé privée conservée en lieu sûr. Pour permettre les mises à jour, garder le même identifiant d’application et signer les versions suivantes avec la même clé. Ne jamais publier la clé, son mot de passe ou des exports personnels dans le dépôt.
