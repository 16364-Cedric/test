# DATI – Dispositif d'Alarme pour Travailleur Isolé

Application Android qui transforme un smartphone en **DATI / PTI** : elle surveille une personne
qui travaille seule et prévient automatiquement ses contacts d'urgence par SMS (avec sa position GPS)
si elle ne donne plus signe de vie.

## Fonctionnement

| Déclencheur | Détection |
|---|---|
| **Chute** | chute libre suivie d'un choc (accéléromètre) |
| **Immobilité** | ni secousse, ni changement d'orientation (accéléromètre), ni rotation (gyroscope) pendant la durée réglée (2 min par défaut) |
| **Confirmation manquée** | l'utilisateur doit appuyer sur « Je vais bien » à intervalle régulier (30 min par défaut) |
| **SOS** | appui long sur le bouton SOS : alarme immédiate |

1. Un événement déclenche une **pré-alarme** : le téléphone sonne et vibre, et un écran plein
   s'affiche même téléphone verrouillé, avec un compte à rebours (30 s par défaut).
2. Si l'utilisateur n'appuie pas sur **« Je vais bien »** à temps, l'**alarme** part :
   - un SMS est envoyé à chaque contact avec la cause, l'heure et un lien Google Maps vers la position ;
   - un second SMS suit dès qu'une position GPS fraîche est disponible ;
   - en option, le premier contact est appelé automatiquement ;
   - la sonnerie continue pour alerter les personnes à proximité.
3. Quand l'utilisateur arrête l'alarme, les contacts reçoivent un SMS « tout va bien ».

La surveillance tourne dans un service au premier plan (notification permanente) et continue écran éteint.
Le GPS est suivi en continu pendant la surveillance (toutes les 30 s ou 10 m) pour que la position
envoyée dans l'alerte soit récente. Il ne sert volontairement **pas** à détecter le mouvement : à
l'intérieur, la position GPS dérive de plusieurs dizaines de mètres, ce qui ferait croire qu'une
personne inconsciente bouge encore.

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
core/   Logique pure Kotlin, testée sans téléphone
        DatiStateMachine  Idle → Monitoring → PreAlarm → Alarm
        FallDetector      chute libre + choc
        ImmobilityDetector
app/    Application Android
        DatiService       service au premier plan : capteurs, minuterie, alertes
        AlertSender       SMS + position GPS
        AlarmSignal       sonnerie et vibration
        PreAlarmActivity  écran plein « Je vais bien »
        MainActivity / SettingsActivity
```

Tests : `./gradlew :core:test`.

## Limites

- **Ce n'est pas un DATI certifié.** Un smartphone n'offre pas les garanties d'un équipement dédié
  (autonomie, robustesse, réseau). L'application ne remplace ni l'évaluation des risques ni
  l'organisation des secours, qui restent à la charge de l'employeur.
- L'alerte dépend du **réseau mobile** (SMS) : pas de couverture, pas d'alerte.
- La détection de chute repose sur des seuils simples ; elle peut produire des fausses alertes
  (téléphone qui tombe) ou rater une chute lente. La pré-alarme sert à annuler les premières, le
  contrôle d'immobilité et la confirmation périodique couvrent en partie les secondes.
- iOS n'est pas supporté : Apple n'autorise pas l'envoi automatique de SMS ni une surveillance
  continue des capteurs en arrière-plan.
- L'autorisation `SEND_SMS` est restreinte sur le Google Play Store : l'application est prévue pour
  être installée directement (APK).
