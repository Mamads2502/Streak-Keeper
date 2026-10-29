# État du projet — Streak keeper

Dernière mise à jour : 29 octobre 2026.

## Objectif

Application Android expérimentale et non officielle permettant de gérer plusieurs profils Snapchat et leurs listes d’amis, puis de préremplir le formulaire officiel d’assistance pour un ami à la fois. L’utilisateur vérifie les informations, réalise les éventuelles vérifications reCAPTCHA et envoie lui-même le formulaire.

## Architecture

- Un seul module Android : `app`, avec le package/namespace Java `fr.streakkeeper.app` et l’identifiant d’installation historique `fr.streakpresets.app`.
- `app/src/main/java/fr/streakkeeper/app/MainActivity.java` regroupe l’interface native construite en Java, la logique des profils et amis, la persistance et le WebView. Les modèles `Profile` et `Friend` sont des classes internes.
- `AppTheme.java` centralise les composants et les styles CSS du formulaire. `res/values/attrs.xml` définit les rôles de couleur, `colors.xml` la palette claire et l’accent unique `sk_accent`, `styles.xml` le thème Android et celui des dialogues.
- Les profils, leurs amis et l’identifiant du profil actif sont sérialisés en JSON dans le fichier privé `MainActivity` (`SharedPreferences`), sous la clé `streak_keeper_v1`. La lecture accepte l’ancienne clé `streak_presets_v1` si la nouvelle est absente ; la prochaine sauvegarde écrit la nouvelle clé et supprime l’ancienne dans la même opération.
- Le WebView charge le formulaire hébergé sur `help.snapchat.com`. Le préremplissage utilise `evaluateJavascript` et les identifiants HTML des champs, avec des tentatives différées si les champs ne sont pas encore disponibles.
- `app/src/main/AndroidManifest.xml` déclare l’activité de lancement et la permission Internet. Les fichiers Gradle à la racine et dans `app/` définissent la compilation ; le wrapper se trouve dans `gradle/wrapper/`.

## Fonctionnalités existantes

Fonctionnalités présentes dans le code, dont le fonctionnement sur appareil reste à valider :

- Création, sélection, modification et suppression de profils : nom du preset sans exemple nominatif, pseudo, e-mail unique, téléphone et description facultative. Une case à cocher affiche ou masque la description ; son état est enregistré et contrôle le préremplissage.
- Thème clair par défaut : cartes blanches, fond neutre, accent indigo unique et composants partagés pour l’accueil, le menu, les dialogues, champs et écran du formulaire. Guide en trois étapes accessible par le lien grisé et souligné « Comment ça marche ? », y compris sans profil ; sa zone cliquable est limitée à la largeur du texte.
- Menu latéral gauche animé : barre supérieure fixe avec icône de menu aux barres arrondies, ouverture par balayage depuis le bord gauche, profils défilants avec sélection active et ajout fixé en bas. Fermeture par balayage à gauche, toucher extérieur, × ou Retour Android. La sélection et la création conservent leur persistance existante.
- Actions de profil regroupées dans le bouton ⋮ de chaque ligne du tiroir : modification et suppression avec confirmation nominative. Aucun bouton de gestion sur l’accueil ; modifier ou supprimer un profil inactif conserve le profil actif. Créer un profil le sélectionne ; supprimer le profil actif sélectionne le premier restant, ou aucun si la liste est vide.
- Liste d’amis compacte : surnom et pseudo à gauche, flamme ronde de préparation et menu ⋮ à droite (cibles de 48 dp, libellés accessibles, infobulles). Menu : modifier, copier vers un autre profil, supprimer avec confirmation. Le nom affiché vide reprend le pseudo ; une modification conserve `addedAt` et refuse les doublons sans fermer le dialogue.
- Barre adaptative `FriendToolsLayout` : loupe à gauche, entonnoir de tri à droite et espace central libre lorsque la recherche est fermée. Le champ apparaît au centre si la largeur le permet, sinon sous les icônes ; le seuil tient compte de la taille de police. Icônes vectorielles, cibles tactiles de 48 dp, descriptions accessibles et champ conservé lors du changement de disposition.
- Recherche repliable et dynamique sur surnom et pseudo Snapchat : casse/accents ignorés, `@` facultatif, correspondances partielles et mots combinés. Compteur filtré, état vide, effacement et fermeture par Retour. Le filtre est réinitialisé au changement de profil et conservé au retour du formulaire ou après rotation.
- Tri mémorisé globalement : nom A–Z (collation française), plus récents, plus anciens (défaut historique). La recherche conserve le tri choisi.
- Copie individuelle vers un autre profil : nouveau contact indépendant avec surnom/pseudo conservés et nouvelle date d’ajout ; aucun écrasement ni changement du profil actif. Les profils où l’ami existe déjà sont signalés. `FriendList.copyTo` centralise la copie sans doublons pour les opérations individuelles et globales.
- Import depuis un autre profil : icône à côté du bouton d’ajout, choix du profil par badges, sélection individuelle ou globale des amis et compteur. Les pseudos déjà présents sont signalés et désactivés ; la copie vérifie à nouveau les doublons sans tenir compte de la casse.
- Sauvegarde locale et restauration du profil actif au démarrage.
- Bouton rond à icône de flamme pour ouvrir le formulaire avec les données d’un ami ; bouton « Re-remplir », état du préremplissage et retour aux presets.
- Après le préremplissage, centrage instantané sans animation du bouton d’envoi ou priorité à une erreur, un champ invalide ou une vérification visible reconnue. Attente de position stable (3 mesures), délai borné à 3 secondes, annulation dès interaction et lors du retour/navigation. « Re-remplir » autorise une nouvelle tentative. Le formulaire reste entièrement accessible et l’envoi manuel.
- Gestion d’une erreur de chargement de la page principale et message de repli lorsque le préremplissage échoue.

## Décisions techniques actuelles

- `FriendList.java` isole le filtrage et le tri sans dépendance Android et sans modifier l’ordre des données sauvegardées. Le JSON des amis contient `addedAt` (millisecondes depuis epoch). Les anciens amis sans date gardent la valeur 0 et leur ordre sauvegardé ; les copies prennent la date de leur ajout au profil destinataire. Le tri est stocké sous `friendSort`, la recherche reste temporaire.
- Android natif en Java 17, interface construite en Java. AndroidX `drawerlayout:1.2.0` gère les gestes, l’animation, le voile et l’accessibilité du tiroir ; l’activité et le stockage existants sont conservés.
- L’accent se modifie dans `res/values/colors.xml` (`sk_accent`) ; teintes secondaires dérivées et contraste noir/blanc automatique pour les boutons pleins. Les rôles sémantiques préparent de futurs thèmes ; seul le thème clair est implémenté. Sur Android 8.0, la barre de navigation reste foncée pour préserver la lisibilité des icônes système.
- Le WebView reçoit une feuille CSS idempotente uniquement sur le formulaire HTTPS `help.snapchat.com/hc/fr-fr/requests/new`. Aucun élément ni validation n’est masqué ; les iframes et les autres pages restent inchangées. Les callbacks de préremplissage devenus obsolètes après navigation sont ignorés.
- Le tiroir mesure au maximum 320 dp en gardant une zone extérieure visible. Son ouverture est restaurée après recréation de l’activité. Il est verrouillé pendant le formulaire WebView puis déverrouillé au retour à l’accueil.
- Android Gradle Plugin 8.7.3 et Gradle 8.9 ; `compileSdk 35`, `targetSdk 34`, `minSdk 26` (Android 8.0 minimum).
- Migration des profils existants : le champ JSON `email` remplace `contactEmail` et `accountEmail`, avec priorité à l’ancien e-mail de contact puis repli sur celui du compte. La même adresse alimente les deux identifiants HTML e-mail déjà ciblés, si présents.
- Le champ JSON `descriptionEnabled` mémorise l’activation de la description ; pour les anciens profils, une description non vide est activée par défaut. Le texte est conservé quand l’option est décochée, mais n’est pas injecté dans le formulaire.
- Nom de l’application : `Streak keeper` (libellé Android, accueil et projet Gradle). Les sources sont dans `fr/streakkeeper/app` et le manifeste référence explicitement `fr.streakkeeper.app.MainActivity`. L’identifiant d’installation historique est conservé ; le nom du fichier de préférences est fixé explicitement afin que le changement de package ne perde pas les profils. L’URL du dépôt GitHub reste inchangée.
- Version déclarée : `0.1.0`, code de version `1`.
- Stockage local privé, sans chiffrement supplémentaire des e-mails et téléphones ; sauvegarde Android désactivée dans le manifeste. Aucun mot de passe Snapchat n’est demandé.
- JavaScript, stockage DOM et cookies, y compris tiers, activés pour le formulaire. Accès WebView aux fichiers et contenus locaux désactivé, contenu mixte et trafic HTTP en clair interdits.
- Pas de soumission automatique, d’envoi groupé, de requête directe à l’API Zendesk ni de contournement reCAPTCHA. Le formulaire reste visible et l’envoi manuel.

## Problèmes connus et limites

- Import sélectif compilé et analysé ; badges, sélection, listes longues et doublons restent à tester sur appareil ou Waydroid.

- Validation de la liste compacte et des actions par ami le 29 octobre 2026 : `./gradlew assembleDebug lintDebug --offline` réussit ; APK debug vérifié (intégrité ZIP et signature). Tests Java : 18 scénarios passent (tri, données historiques, recherche, conservation de la liste source et préférence invalide, copie indépendante, absence d’écrasement et collisions lors de la modification). La barre à icônes et sa disposition adaptative sont également compilées et analysées ; le rendu, le clavier et les interactions restent à tester sur Waydroid.
- Contrastes du thème clair déjà calculés : texte principal 15,11:1, texte secondaire sur fond 5,01:1, bouton principal 6,29:1.
- Le fonctionnement sur appareil et le préremplissage réel du formulaire n’ont pas été testés lors de cette modification.
- Lint termine sans erreur, avec 12 avertissements : API cible ancienne, activation de JavaScript, règles d’extraction des données, icône absente, chaînes d’interface écrites directement en Java et alignements à gauche explicites (tiroir demandé à gauche).
- `python3 tests/test_form_navigation.py` (QuickJS `qjs` requis) : 11 scénarios de navigation passent sur un DOM simulé. Ils couvrent les priorités, la temporisation bornée, l’annulation et le défilement instantané ; ils ne remplacent pas un test WebView réel.
- Le préremplissage dépend des identifiants HTML du site et de son chargement dynamique. Le README indique des identifiants observés le 27 octobre 2026 ; leur validité actuelle n’a pas été vérifiée.
- Le centrage reconnaît les contrôles HTML invalides, certains bandeaux de consentement et les cadres reCAPTCHA visibles ; il ne lit pas le contenu des iframes externes. Un obstacle non reconnu peut nécessiter un défilement manuel. Un espace vide en bas est ajouté pour permettre de centrer le bouton sans le déplacer.
- Le site peut bloquer les WebViews ou modifier son formulaire. La conservation des cookies ne garantit pas l’absence de CAPTCHA.
- Seules les conversations privées sont visées ; le choix de groupe n’est pas traité.
- Aucune récupération automatique des contacts ou photos depuis Snapchat n’est implémentée.
- La validation des saisies se limite principalement aux champs obligatoires et aux doublons d’amis ; les formats d’e-mail et de téléphone ne sont pas vérifiés.

## Prochaines étapes

1. Tester la liste compacte et les actions par ami sur Waydroid : flamme, options, modification/annulation, doublons, suppression confirmée, copie entre profils et indépendance des copies après redémarrage. Tester la recherche et les tris sur Waydroid : saisie continue, accents, surnom/pseudo, résultats vides, effacement, changement de profil, rotation, retour du formulaire, redémarrage et copie d’amis. Tester le centrage après « Préparer » et « Re-remplir », la priorité aux champs incomplets/CAPTCHA, l’annulation au toucher, le retour rapide et le changement d’ami, sans envoyer de vraie demande. Tester le thème clair et le menu sur Waydroid selon les listes du README : accueil, guide au clic, dialogues, saisie, gestes, sélection persistante, liste longue, grands caractères, rotation et formulaire officiel. Attendre le retour de l’utilisateur avant de proposer une fusion dans `main`.
2. Tester la mise à jour de l’ancienne APK vers la nouvelle sur Waydroid sans désinstallation : profils, amis et profil actif conservés, puis redémarrage après sauvegarde pour vérifier la migration de clé. Tester sur émulateur ou appareil les opérations sur les profils et amis, la copie sans doublons, la persistance après redémarrage et la navigation. Vérifier aussi la migration des anciens e-mails et descriptions, ainsi que le masquage et la désactivation du préremplissage de la description via la case à cocher.
3. Vérifier le chargement du formulaire, le préremplissage, le bouton « Re-remplir » et les erreurs réseau, sans envoyer de véritable demande lors du premier test, conformément au README.
4. Corriger les problèmes constatés et ajouter les vérifications adaptées aux changements ; actualiser le statut de validation dans la documentation.

Les procédures détaillées d’installation, de compilation et de test manuel sont dans `README.md`.
