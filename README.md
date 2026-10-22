# DATI – Dispositif d'Alarme pour Travailleur Isolé

Application Android qui transforme un smartphone en **DATI / PTI** : elle surveille une personne
qui travaille seule et prévient automatiquement ses contacts d'urgence par SMS (avec sa position GPS)
si elle ne donne plus signe de vie.

## Fonctionnement

| Déclencheur | Détection |
|---|---|
| **Chute** | chute libre suivie d'un choc (accéléromètre), sensibilité réglable |
| **Immobilité** | ni secousse, ni changement d'orientation (accéléromètre), ni rotation (gyroscope) pendant la durée réglée (2 min par défaut) |
| **Perte de verticalité** *(option)* | le téléphone, porté debout (poche de poitrine, étui de ceinture), reste couché pendant la durée réglée |
| **Zone à risque (ATEX…)** *(avec serveur)* | présence prolongée au-delà de la durée maximale fixée pour une zone tracée sur la carte de la console (avertissement 1 min avant) |
| **Confirmation manquée** | l'utilisateur doit appuyer sur « Je vais bien » à intervalle régulier (30 min par défaut) |
| **SOS** | appui long sur le bouton SOS, ou **4 appuis rapides sur le bouton marche/arrêt** sans regarder l'écran : alarme immédiate |
| **Perte de contact** *(avec serveur)* | le téléphone ne donne plus de nouvelles pendant 15 min (batterie vide, casse, pas de réseau) |

1. Un événement déclenche une **pré-alarme** : le téléphone sonne et vibre, et un écran plein
   s'affiche même téléphone verrouillé, avec un compte à rebours (30 s par défaut).
2. Si l'utilisateur n'appuie pas sur **« Je vais bien »** à temps, l'**alarme** part :
   - vers le **serveur de supervision** de l'entreprise : alerte dans la console et notification
     des référents (ntfy, Teams, Slack…), escalade si personne ne la prend en charge ;
   - et/ou par **SMS** aux contacts, avec un lien Google Maps vers la position, puis un rappel
     toutes les 5 minutes ;
   - en option, le premier contact est appelé automatiquement ;
   - la sonnerie continue pour alerter les personnes à proximité.
3. Quand un référent clique sur **« Je prends en charge »**, son nom s'affiche sur le téléphone.
   La console montre l'alerte sur une **carte**, avec les zones à risque.
4. Quand l'utilisateur arrête l'alarme, les contacts et la console en sont informés.

Également : **auto-contrôle** sur l'écran principal (autorisations, batterie, capteurs, réseau,
serveur), **essai guidé** de la chaîne d'alerte, alerte **batterie faible** (sous 20 % par défaut)
et **extinction**, **journal** des événements, configuration à distance par **QR code** ou **MDM**.

**Perte de réseau** : sans réseau, aucune alerte ne peut partir du téléphone. Après 1 minute sans
réseau mobile ni Internet, le téléphone vibre et prévient le salarié pour qu'il rejoigne une zone
couverte. Au retour du réseau, une alarme déclenchée pendant la coupure est renvoyée, les
événements en attente sont transmis au serveur, et les contacts sont informés si la coupure a
duré plus de 15 minutes. Côté serveur, un téléphone muet depuis 15 minutes déclenche l'alerte
« perte de contact ».

La surveillance tourne dans un service au premier plan (notification permanente) et continue écran éteint.
Le GPS est suivi en continu pendant la surveillance (toutes les 30 s ou 10 m) pour que la position
envoyée dans l'alerte soit récente. Il ne sert volontairement **pas** à détecter le mouvement : à
l'intérieur, la position GPS dérive de plusieurs dizaines de mètres, ce qui ferait croire qu'une
personne inconsciente bouge encore.

## Déploiement en entreprise

- **[Guide de déploiement](docs/deploiement.md)** : serveur de supervision (Docker), signature,
  installation, enrôlement par QR code, MDM, réglages par fabricant, phase pilote.
- **[Cadre juridique](docs/cadre-juridique.md)** : sécurité au travail, CSE, RGPD/CNIL, check-list.
- Modèles : [notice d'information des salariés](docs/modeles/notice-information-salaries.md),
  [procédure d'intervention](docs/modeles/procedure-intervention.md),
  [fiche registre des traitements](docs/modeles/fiche-registre.md).

## Installer l'application

Chaque push compile l'APK via GitHub Actions et la publie à une adresse fixe :

**<https://github.com/16364-Cedric/test/releases/download/dati-latest/dati.apk>**

Ouvrir ce lien dans Chrome sur le téléphone, puis ouvrir `dati.apk` (autoriser l'installation
d'applications inconnues ; si Google Play Protect bloque, désactiver temporairement l'analyse
Play Protect le temps de l'installation).

Android 8.0 (API 26) minimum.

Ou en local, avec le SDK Android installé : `./gradlew :app:assembleDebug`.

## Première utilisation

1. **Réglages** : saisir votre nom et au moins un numéro de contact (format `+33…` recommandé).
2. **Démarrer la protection** et accepter les autorisations : localisation, SMS, notifications
   (et téléphone si l'appel automatique est activé).
3. Accepter l'**alarme plein écran** (Android 14+) et la **désactivation de l'optimisation de batterie** :
   sans cela, le système peut suspendre la surveillance.
4. **Faire un test** avec vos contacts (laisser expirer une pré-alarme) avant toute utilisation réelle.

> Certains fabricants (Xiaomi, Huawei, Samsung, OnePlus…) tuent agressivement les applications en
> arrière-plan. Voir <https://dontkillmyapp.com> pour les réglages propres à chaque modèle.

## Structure du code

```
core/    Logique pure Kotlin, testée sans téléphone
         DatiStateMachine     Idle → Monitoring → PreAlarm → Alarm
         FallDetector         chute libre + choc
         ImmobilityDetector   accéléromètre + gyroscope
         PressSequenceDetector  SOS par le bouton marche/arrêt
         DeploymentConfig     lien de configuration / MDM
server/  Serveur de supervision (Node.js, sans dépendance) et console web
app/     Application Android
        DatiService       service au premier plan : capteurs, minuterie, alertes
        AlertSender       SMS + position GPS
        ServerClient      liaison avec le serveur de supervision
        SelfCheck         vérifications affichées sur l'écran principal
        AlarmSignal       sonnerie et vibration
        PreAlarmActivity  écran plein « Je vais bien »
        MainActivity / SettingsActivity
```

Tests : `./gradlew :core:test` et `cd server && npm test`.

## Limites

- **Ce n'est pas un DATI certifié.** Un smartphone n'offre pas les garanties d'un équipement dédié
  (autonomie, robustesse, réseau). L'application ne remplace ni l'évaluation des risques ni
  l'organisation des secours, qui restent à la charge de l'employeur.
- L'alerte dépend du **réseau mobile** : pas de couverture, pas d'alerte depuis le téléphone.
  Avec le serveur de supervision, l'absence de nouvelles déclenche toutefois une alerte
  « perte de contact » au bout de 15 minutes.
- La détection de chute repose sur des seuils simples ; elle peut produire des fausses alertes
  (téléphone qui tombe) ou rater une chute lente. La pré-alarme sert à annuler les premières, le
  contrôle d'immobilité et la confirmation périodique couvrent en partie les secondes.
- iOS n'est pas supporté : Apple n'autorise pas l'envoi automatique de SMS ni une surveillance
  continue des capteurs en arrière-plan.
- L'autorisation `SEND_SMS` est restreinte sur le Google Play Store : l'application est prévue pour
  être installée directement (APK).
