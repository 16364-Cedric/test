# Cadre juridique et organisationnel

> **Avertissement** : ce document est une aide à la préparation, pas un avis juridique. Il doit
> être validé par le délégué à la protection des données (DPO), le service juridique ou RH et le
> service de prévention de l'entreprise avant tout déploiement.

## 1. Ce que l'outil est, et ce qu'il n'est pas

- C'est une **mesure de prévention complémentaire** pour des salariés travaillant seuls.
- Ce n'est **pas un DATI certifié** : un smartphone dépend de sa batterie, du réseau mobile et du
  système Android. L'outil ne remplace ni l'évaluation des risques, ni la réduction du travail
  isolé quand elle est possible, ni l'organisation des secours.
- Si l'entreprise l'adopte, **c'est elle qui en porte la responsabilité** au titre de son
  obligation de sécurité.

## 2. Santé et sécurité au travail

| Étape | Références | À faire |
|---|---|---|
| Obligation de sécurité | Code du travail, art. L4121-1 à L4121-3 | Évaluer le risque « travail isolé » pour chaque poste concerné |
| Document unique | Art. R4121-1 et suivants | Inscrire le risque, la mesure DATI et ses limites dans le DUERP |
| Organisation des secours | – | Rédiger une **procédure d'intervention** ([modèle](modeles/procedure-intervention.md)) : qui reçoit l'alerte, qui se déplace, en combien de temps |
| Formation | Art. L4141-1 | Former les salariés équipés et les référents ; essai à la prise de poste |
| Suivi | – | Essais périodiques, bilan des alertes et des fausses alertes en réunion sécurité |

Associer le **service de prévention et de santé au travail** et, s'il existe, le service HSE.
L'INRS publie des ressources sur le travail isolé et les dispositifs d'alarme (www.inrs.fr).

## 3. Information et consultation des représentants du personnel

- **CSE** : l'outil collecte des données de localisation et d'activité (mouvements, état de la
  protection). Le CSE doit être **informé et consulté avant la mise en œuvre** des moyens
  permettant un contrôle de l'activité des salariés (art. L2312-38 du Code du travail).
- **Information individuelle** : aucune information concernant personnellement un salarié ne peut
  être collectée par un dispositif qui n'a pas été porté préalablement à sa connaissance
  (art. L1222-4). Remettre la [notice d'information](modeles/notice-information-salaries.md).

## 4. Protection des données (RGPD)

### Données traitées

| Donnée | Où | Quand | Qui la voit |
|---|---|---|---|
| Mouvements (accéléromètre, gyroscope) | Téléphone uniquement | Pendant la protection | Personne : analysés sur place, jamais transmis |
| Position GPS | Téléphone, serveur, SMS | Suivie pendant la protection | **Uniquement jointe à une alerte** (console, SMS, notification) |
| État (protégé, pré-alarme, alarme), batterie | Serveur | Toutes les 5 min pendant la protection | Superviseurs |
| Alertes (type, heure, position, prise en charge) | Serveur | En cas d'alerte | Superviseurs ; supprimées 30 jours après clôture |
| Journal des événements | Téléphone uniquement | – | Le salarié |
| Nom, numéros des contacts | Téléphone, serveur (nom) | – | Superviseurs |

Le serveur conserve la **dernière position** de chaque téléphone uniquement pour pouvoir la
joindre à une alerte « perte de contact » ; la console ne l'affiche jamais en dehors d'une alerte.
Aucune donnée n'est collectée quand la protection est arrêtée : le salarié l'arrête en fin de
poste, pendant ses pauses et en dehors du temps de travail.

### Démarches

1. **Base légale** : obligation de sécurité de l'employeur (et/ou intérêt légitime). Le
   consentement n'est pas adapté dans la relation de travail, car il n'est pas libre.
2. **Registre des traitements** (art. 30 RGPD) : ajouter le traitement
   ([fiche pré-remplie](modeles/fiche-registre.md)).
3. **Analyse d'impact (AIPD)** (art. 35 RGPD) : la géolocalisation de salariés figure parmi les
   cas où la CNIL recommande, voire exige, une AIPD. À trancher avec le DPO ; en cas de doute, la
   réaliser.
4. **Information** des salariés (art. 13 RGPD) : notice remise avant l'équipement.
5. **Minimisation** : ne pas utiliser les données pour autre chose que la sécurité (pas de
   contrôle des horaires, de la productivité ou des déplacements, pas de sanction disciplinaire
   fondée sur ces données).
6. **Sécurité** : serveur en HTTPS, mot de passe de console robuste et individuel si possible,
   accès limité aux référents, sauvegarde du fichier de données, mise à jour du serveur.
7. **Sous-traitants** : si le serveur est hébergé chez un prestataire, ou si l'on utilise
   ntfy.sh, Teams ou Slack pour les notifications, vérifier le contrat (art. 28 RGPD) et la
   localisation des données. Une instance ntfy auto-hébergée évite de faire transiter des
   positions par un tiers.
8. **Contacts SMS** : privilégier des numéros professionnels (référents, poste de garde) plutôt
   que des proches. Leur numéro est une donnée personnelle de tiers.

## 5. Check-list avant mise en service

- [ ] Risque « travail isolé » évalué et inscrit au DUERP
- [ ] Procédure d'intervention écrite, référents désignés et formés, délais définis
- [ ] CSE informé et consulté
- [ ] Traitement inscrit au registre, AIPD réalisée ou écartée par écrit avec le DPO
- [ ] Notice d'information remise et expliquée à chaque salarié équipé
- [ ] Serveur en HTTPS, mot de passe robuste, sauvegarde en place
- [ ] Application signée avec la clé de l'entreprise, téléphones testés une nuit complète
- [ ] Phase pilote réalisée, seuils ajustés, exercice d'intervention chronométré
- [ ] Essais périodiques planifiés (au moins hebdomadaires)
