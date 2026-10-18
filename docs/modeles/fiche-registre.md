# Fiche registre des traitements (art. 30 RGPD) – DATI

*Pré-remplie à partir du fonctionnement de l'application ; à compléter par le DPO.*

| Rubrique | Contenu |
|---|---|
| Nom du traitement | Dispositif d'alarme pour travailleur isolé (DATI) |
| Responsable du traitement | [Raison sociale] – représentant : [nom] |
| DPO | [nom, contact] |
| Finalité | Détecter une situation de détresse d'un salarié travaillant seul et organiser ses secours |
| Base légale | Obligation légale de sécurité de l'employeur (art. 6.1.c RGPD, art. L4121-1 C. trav.) ; à défaut, intérêt légitime (art. 6.1.f) |
| Personnes concernées | Salariés affectés à des postes isolés ; contacts d'urgence (numéro de téléphone) |
| Données | Identité (nom affiché) ; état de la protection et niveau de batterie ; position GPS (communiquée uniquement en cas d'alerte) ; type, heure et suivi des alertes ; numéros des contacts d'urgence |
| Données non collectées | Mouvements bruts (analysés sur le téléphone), position hors période de protection, contenu du téléphone |
| Destinataires | Référents sécurité et poste de garde désignés ; contacts d'urgence par SMS ; [hébergeur, service de notification] |
| Transferts hors UE | [Aucun / à préciser selon l'hébergeur et le service de notification] |
| Durée de conservation | Alertes : 30 jours après clôture (paramètre `RETENTION_DAYS`) ; état courant : tant que l'appareil est enrôlé ; journal local : 300 derniers événements sur le téléphone |
| Mesures de sécurité | HTTPS ; jeton par appareil stocké haché ; console protégée par mot de passe ; accès limité aux référents ; sauvegardes ; confirmation obligatoire avant toute configuration à distance |
| AIPD | [Réalisée le … / écartée le … – justification] |
| Information des personnes | Notice remise le … ; CSE consulté le … |
