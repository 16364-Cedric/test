# Déployer DATI dans une entreprise

Ce guide s'adresse à la personne qui met en place DATI pour une équipe (service informatique,
responsable sécurité). Avant tout déploiement, lire aussi le [cadre juridique](cadre-juridique.md).

## Vue d'ensemble

```
Téléphone du salarié ──(SMS + position, appel en option)──► Contacts d'urgence (référents)
```

- Le téléphone surveille seul le salarié ; en cas d'alarme, il envoie un **SMS** avec un lien
  vers sa position à chaque contact, puis un rappel toutes les 5 minutes ; le premier contact
  peut être appelé automatiquement.
- Il n'y a **pas de serveur central** : aucune alerte « perte de contact » si le téléphone
  s'éteint ou casse. La confirmation périodique « Je vais bien », les alertes batterie et
  l'alerte de perte de réseau en tiennent lieu en partie ; la procédure d'intervention doit
  prévoir un contrôle humain (appel de fin de poste, par exemple).

## 1. Distribuer l'application

### Signature

L'APK publiée par défaut est signée avec une clé de debug **publique** (présente dans le dépôt) :
pratique pour les essais, pas pour la production. Pour une clé privée :

```sh
keytool -genkeypair -keystore dati-release.keystore -alias dati \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Mon Entreprise"
base64 -w0 dati-release.keystore   # contenu du secret DATI_KEYSTORE_BASE64
```

Dans GitHub → *Settings* → *Secrets and variables* → *Actions*, créer `DATI_KEYSTORE_BASE64`,
`DATI_KEYSTORE_PASSWORD`, `DATI_KEY_ALIAS` (`dati`) et `DATI_KEY_PASSWORD`. La CI publie alors
une APK signée avec cette clé. **Conserver la clé en lieu sûr** : sans elle, plus aucune mise à
jour n'est possible sans désinstaller l'application sur tous les téléphones.

Changer de clé impose de désinstaller une fois l'ancienne version sur chaque téléphone.

### Installation sur les téléphones

- **Avec un MDM** (Intune, Workspace ONE, Google Endpoint Management…) : publier l'APK comme
  application privée (*Managed Google Play*). Play Protect ne bloque plus l'installation et la
  configuration peut être poussée à distance (voir ci-dessous).
- **Sans MDM** : lien direct vers `dati.apk` (release `dati-latest`). Play Protect avertit que
  le développeur est inconnu ; il faut accepter « Installer quand même », voire désactiver
  temporairement l'analyse Play Protect.

### Mode administrateur

Un **mot de passe administrateur** empêche le salarié de modifier les réglages. Il peut être
défini sur le téléphone (Réglages → Mode administrateur), dans le lien de configuration
(QR code) ou par le MDM (clé `admin`).

| Protégé par le mot de passe | Toujours accessible au salarié |
|---|---|
| Réglages, lien de configuration | Démarrer / arrêter la protection |
| Application d'un nouveau QR code de configuration | « Je vais bien », SOS, essai |
| Création et suppression des zones sur le téléphone | Carte (consultation) |
| Effacement du journal | Journal (consultation), À propos |

Arrêter la protection reste libre : le salarié doit pouvoir la couper en fin de poste et pendant
ses pauses (voir le [cadre juridique](cadre-juridique.md)).

- Le mot de passe n'est jamais stocké en clair (empreinte PBKDF2 salée).
- Après 5 erreurs, la saisie est bloquée 1 minute, puis 2, 4… à chaque nouvelle série.
- Le déverrouillage dure 5 minutes, et se referme dès qu'on quitte les réglages.
- **Mot de passe oublié** : pousser un nouveau mot de passe par le MDM, ou désinstaller et
  réinstaller l'application (tous les réglages sont alors perdus).
- **Limite** : sur un téléphone non géré, l'utilisateur peut toujours effacer les données de
  l'application dans les paramètres Android, ce qui la réinitialise. Seul un téléphone géré par
  un MDM (Android Enterprise) permet de l'empêcher.

## 2. Équiper un salarié

1. Le salarié installe l'application.
2. **Réglages** : nom, numéros des contacts d'urgence, durées ; ou ouvrir un **lien de
   configuration** (QR code, MDM) préparé par l'administrateur, que l'application affiche et
   fait confirmer avant de l'appliquer.
3. Le salarié démarre la protection, accepte les autorisations, puis **lance un essai** : chaque
   contact doit recevoir un SMS marqué ESSAI.

### Configuration par MDM

L'application déclare une configuration gérée. Clés disponibles (identiques au lien de
configuration) :

| Clé | Type | Exemple |
|---|---|---|
| `name` | texte | `Alice Martin` |
| `contacts` | texte | `+33612345678,+33698765432` |
| `checkin` | entier (min, 0 = désactivé) | `30` |
| `prealarm` | entier (s, ≥ 10) | `30` |
| `immobility` | entier (s, 0 = désactivé) | `120` |
| `verticality` | entier (s, 0 = désactivé) – téléphone porté debout uniquement | `30` |
| `battery` | entier (%, 0 = désactivé) | `20` |
| `batterycritical` | entier (%, 0 = désactivé) – second palier, alerte urgente | `10` |
| `fall` | booléen | `true` |
| `sensitivity` | `LOW` / `NORMAL` / `HIGH` | `NORMAL` |
| `call` | booléen | `false` |
| `powersos` | booléen | `true` |
| `admin` | texte (4 caractères min.) – mot de passe administrateur, stocké sur le téléphone sous forme d'empreinte | – |

Une valeur invalide (numéro mal formé, durée hors bornes…) fait rejeter toute la configuration ;
l'erreur est notée dans le journal de l'application.

## 3. Zones à risque (ATEX…)

Sur le téléphone, écran **Carte et zones à risque** : **appui long** sur la carte à l'endroit de
la zone, puis nommer la zone, fixer son rayon et la durée maximale de présence. Les zones sont
conservées sur le téléphone et fonctionnent sans réseau. Le décompte se fait **sur le téléphone** :

1. à l'entrée dans la zone, le salarié est informé de la durée autorisée ;
2. une minute avant la limite, le téléphone vibre et l'invite à sortir ;
3. à la limite, une **pré-alarme** se déclenche ; sans réponse, l'**alarme** part avec le nom de
   la zone. « Je vais bien » relance le décompte pour une nouvelle période.

**Limites de la localisation par GPS** :
- La précision est de 5 à 15 m à l'extérieur, et peut se dégrader à 30 m ou plus près des
  bâtiments ou à l'intérieur ; les positions moins précises que 50 m sont ignorées.
- Dessiner les zones **plus larges que la zone réelle** (marge de 10 à 20 m) pour ne pas sous-estimer le
  temps de présence.
- Dans un bâtiment ou un local en sous-sol, le GPS peut ne pas fonctionner du tout : utiliser
  alors la confirmation périodique (ci-dessous).
- La localisation précise doit être autorisée sur le téléphone ; l'écran principal le signale
  sinon.

### Zones intérieures

Les zones créées sur le téléphone reposent sur le GPS seul. L'application sait détecter des
**balises Bluetooth**, mais elles ne se déclarent que dans des zones transmises par un serveur
de supervision, qui n'est plus fourni : en intérieur, préférer la confirmation périodique et
l'immobilité.

## 4. Fiabilité selon les téléphones

Certains fabricants arrêtent les applications en arrière-plan malgré le service au premier plan.
Pour chaque modèle utilisé dans l'entreprise, **tester une nuit complète** écran éteint et
vérifier au journal de l'application que la surveillance n'a pas été interrompue.

| Fabricant | Réglages à faire |
|---|---|
| Tous | Infos sur l'appli → Batterie → **Sans restriction** |
| Samsung | Batterie → Limites d'utilisation en arrière-plan → retirer DATI des applis en veille |
| Xiaomi / Redmi / POCO | Démarrage automatique **activé**, Économiseur de batterie → **Aucune restriction** |
| Huawei / Honor | Lancement d'applications → DATI → gestion **manuelle**, tout activer |
| OnePlus / OPPO / realme | Batterie → Optimisation → **Ne pas optimiser** ; verrouiller l'appli dans le multitâche |
| Sony | Ne pas utiliser le **mode STAMINA** pendant la protection |

Référence détaillée : <https://dontkillmyapp.com>.

## 5. Phase pilote recommandée

1. 3 à 5 volontaires, 2 à 3 semaines, sur les vrais postes de travail.
2. Un essai guidé par personne et par semaine.
3. Relever chaque fausse alerte (journal de l'application) et chaque situation qui
   aurait dû déclencher une alerte.
4. Ajuster la sensibilité de chute, la durée d'immobilité et l'intervalle de confirmation.
5. Valider la procédure d'intervention ([modèle](modeles/procedure-intervention.md)) avec un
   exercice réel : alerte déclenchée, prise en charge, arrivée sur place, délai mesuré.
