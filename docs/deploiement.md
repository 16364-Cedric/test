# Déployer DATI dans une entreprise

Ce guide s'adresse à la personne qui met en place DATI pour une équipe (service informatique,
responsable sécurité). Avant tout déploiement, lire aussi le [cadre juridique](cadre-juridique.md).

## Vue d'ensemble

```
Téléphone du salarié ──(SMS)──────────────────────────► Contacts d'urgence (optionnel)
        │
        └──(HTTPS : battements de cœur, alertes)──► Serveur de supervision ──► Console web
                                                            │                    (« Je prends en charge »)
                                                            └──► ntfy / Teams / Slack (référents)
```

- Le **serveur** reçoit un battement de cœur toutes les 5 minutes (30 s en alarme). Sans nouvelles
  pendant 15 minutes d'un téléphone en surveillance, il lève une alerte **« perte de contact »** :
  c'est ce qui couvre la batterie vide, le téléphone cassé ou l'absence de réseau.
- Une alerte non prise en charge au bout de 5 minutes est **escaladée** vers un second canal.
- Quand un référent clique sur **« Je prends en charge »**, son nom s'affiche sur le téléphone du
  salarié en alarme.
- Les SMS aux contacts restent possibles en complément (ou seuls, sans serveur).

## 1. Installer le serveur

Le serveur est un programme Node.js sans dépendance, fourni en image Docker. Il faut une machine
joignable en **HTTPS** depuis Internet (les téléphones l'appellent depuis le réseau mobile).

```sh
docker run -d --name dati --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  -v dati-data:/data \
  -e ADMIN_PASSWORD='un-mot-de-passe-long-et-unique' \
  -e PUBLIC_URL='https://dati.mon-entreprise.fr' \
  -e NTFY_URL='https://ntfy.sh/dati-alertes-7f3k2q9x' \
  -e ESCALATION_NTFY_URL='https://ntfy.sh/dati-escalade-p4m8z1' \
  ghcr.io/16364-cedric/dati-supervision:latest
```

L'image est publiée par la CI à chaque modification. Si `docker pull` est refusé, rendre le
paquet public (GitHub → profil → *Packages* → `dati-supervision` → *Package settings*) ou
construire l'image soi-même : `docker build -t dati-supervision server`.

Puis placer un proxy HTTPS devant, par exemple [Caddy](https://caddyserver.com) qui obtient le
certificat tout seul (`/etc/caddy/Caddyfile`) :

```
dati.mon-entreprise.fr {
    reverse_proxy 127.0.0.1:8080
}
```

### Variables d'environnement

| Variable | Rôle | Défaut |
|---|---|---|
| `ADMIN_PASSWORD` | Mot de passe de la console (obligatoire) | – |
| `PUBLIC_URL` | Adresse publique HTTPS, utilisée dans les QR codes | `http://localhost:8080` |
| `NTFY_URL` | Sujet [ntfy](https://ntfy.sh) recevant les alertes | – |
| `ESCALATION_NTFY_URL` | Sujet ntfy des escalades (sinon `NTFY_URL`) | – |
| `WEBHOOK_URL` | Webhook recevant `{"text": "…"}` (Teams, Slack, Mattermost…) | – |
| `HEARTBEAT_TIMEOUT_MIN` | Silence avant « perte de contact » | `15` |
| `ESCALATE_AFTER_MIN` | Délai avant escalade d'une alerte non prise en charge | `5` |
| `RETENTION_DAYS` | Conservation des alertes clôturées | `30` |
| `DATA_FILE` | Fichier de données | `/data/dati.json` |

**Notifications des référents** : installer l'application **ntfy** sur leurs téléphones et
s'abonner au sujet choisi. Choisir un nom de sujet long et imprévisible (il fait office de mot de
passe), ou héberger sa propre instance ntfy. Les alertes urgentes arrivent en priorité maximale,
avec un lien vers la position.

**Sauvegarde** : le volume `/data` contient les appareils enrôlés et l'historique des alertes.

## 2. Distribuer l'application

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

## 3. Enrôler un salarié

1. Console → **Équiper un salarié** : nom, réglages, éventuels numéros SMS de secours.
2. Le salarié installe l'application, puis **scanne le QR code** avec l'appareil photo et touche
   **« Ouvrir dans DATI »**. L'application affiche la configuration et demande confirmation.
3. Si le QR code n'ouvre pas l'application : copier le lien affiché sous le QR code dans
   DATI → Réglages → **Lien de configuration**.
4. Le salarié démarre la protection, accepte les autorisations, puis **lance un essai** : une
   alerte marquée ESSAI doit apparaître dans la console.

Le QR code contient la clé secrète de l'appareil et n'est affiché qu'une fois. Pour un
téléphone perdu ou remplacé : supprimer l'appareil dans la console et en créer un nouveau.

### Configuration par MDM

L'application déclare une configuration gérée. Clés disponibles (identiques au lien de
configuration) :

| Clé | Type | Exemple |
|---|---|---|
| `server` | texte | `https://dati.mon-entreprise.fr` |
| `token` | texte | clé de l'appareil (créée dans la console) |
| `name` | texte | `Alice Martin` |
| `contacts` | texte | `+33612345678,+33698765432` |
| `checkin` | entier (min, 0 = désactivé) | `30` |
| `prealarm` | entier (s, ≥ 10) | `30` |
| `immobility` | entier (s, 0 = désactivé) | `120` |
| `fall` | booléen | `true` |
| `sensitivity` | `LOW` / `NORMAL` / `HIGH` | `NORMAL` |
| `call` | booléen | `false` |
| `powersos` | booléen | `true` |

Une valeur invalide (serveur non HTTPS, numéro mal formé…) fait rejeter toute la configuration ;
l'erreur est notée dans le journal de l'application.

## 4. Fiabilité selon les téléphones

Certains fabricants arrêtent les applications en arrière-plan malgré le service au premier plan.
Pour chaque modèle utilisé dans l'entreprise, **tester une nuit complète** écran éteint et
vérifier qu'aucune « perte de contact » n'apparaît dans la console.

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
3. Relever chaque fausse alerte (journal de l'application + console) et chaque situation qui
   aurait dû déclencher une alerte.
4. Ajuster la sensibilité de chute, la durée d'immobilité et l'intervalle de confirmation.
5. Valider la procédure d'intervention ([modèle](modeles/procedure-intervention.md)) avec un
   exercice réel : alerte déclenchée, prise en charge, arrivée sur place, délai mesuré.
