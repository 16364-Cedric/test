# Charte graphique NTN appliquée à l'application et à la console

Source : *Charte graphique NTN – V2*. L'apparence est centralisée : pour la faire évoluer, il
suffit de remplacer les fichiers ci-dessous, sans toucher au code.

## Ce qui est appliqué

| Élément de la charte | Application Android | Console de supervision |
|---|---|---|
| Logo NTN sur fond blanc (p. 5) | en tête de l'écran principal | – |
| Logo NTN blanc sur fond bleu (p. 5) | – | bandeau d'en-tête |
| Bleu NTN `#009ADF` (p. 13) | couleur principale, bouton « Démarrer », icône | titres, boutons courants |
| Bleu SNR `#005CA9` (p. 13) | barre d'état, couleur secondaire | – |
| Gris anthracite `#4A4A49` (p. 13) | texte, état « Protection désactivée » | texte |
| Dégradé bleu → violet `#7C2C7E`, 45° (p. 17) | barre de titre | bandeau d'en-tête |
| Quicksand pour les titres (p. 11) | titres, barre de titre, état de la protection | titres |
| Fond blanc à privilégier (p. 5) | fond des écrans | fond des cartes |

**Helvetica** (texte courant, p. 11) n'est pas intégrée : c'est une police sous licence
commerciale. L'application utilise la police du téléphone (Roboto), visuellement proche. Pour
l'intégrer, il faut une licence d'intégration logicielle (« app embedding ») ; déposer alors les
fichiers dans `app/src/main/res/font/` et les déclarer dans `values/themes.xml`.

**Couleurs de sécurité hors charte**, volontairement : rouge (alarme, SOS, écran de pré-alarme),
vert (« Je vais bien », protection active), orange (pré-alarme, zones à risque). Elles doivent
rester identiques et reconnaissables dans l'urgence.

## Fichiers

| Fichier | Contenu |
|---|---|
| `app/src/main/res/values/brand.xml` | couleurs de la charte |
| `app/src/main/res/drawable/brand_gradient.xml` | dégradé bleu → violet |
| `app/src/main/res/drawable-nodpi/brand_logo.png` | logo bleu, fond transparent |
| `app/src/main/res/drawable-nodpi/brand_logo_white.png` | logo blanc, fond transparent |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` + `mipmap-anydpi-v26/ic_launcher.xml` | icône (lettres NTN blanches sur bleu NTN) |
| `app/src/main/res/font/quicksand_*.ttf` | police Quicksand (licence SIL OFL, voir `docs/QUICKSAND-OFL.txt`) |
| `app/src/main/res/values/themes.xml` | application des couleurs et polices |
| `server/public/vendor/brand/` | logo blanc et police pour la console |
| `server/public/index.html` (début du `<style>`) | couleurs de la console |

## Logo

Les logos ont été extraits de la page 5 de la charte (rendu à 400 dpi, fond rendu transparent).
Pour une qualité maximale, les remplacer par les fichiers sources fournis par le service
communication (PNG haute définition à fond transparent, ou SVG à convertir en « vector drawable »
via Android Studio : *New → Vector Asset*), en gardant les mêmes noms de fichiers.

L'utilisation du logo dans l'application doit être validée par le service communication.
