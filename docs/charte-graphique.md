# Adapter l'application à la charte graphique de l'entreprise

L'apparence de l'application est centralisée dans deux fichiers : il suffit de les remplacer,
sans toucher au code.

## 1. Le logo

Fichier actuel : `app/src/main/res/drawable/brand_logo.xml` (bouclier provisoire).

Il s'affiche en haut de l'écran principal et dans la fenêtre « À propos ».

- **Logo en PNG** : supprimer `brand_logo.xml` et déposer le fichier sous le nom
  `app/src/main/res/drawable-nodpi/brand_logo.png` (fond transparent, environ 600 px de large).
- **Logo en SVG** : le convertir en « vector drawable » Android (Android Studio : *New → Vector
  Asset → Local file*) et remplacer `brand_logo.xml` par le résultat. C'est la meilleure option :
  le logo reste net sur tous les écrans.

Le nom de fichier doit rester `brand_logo` (minuscules, sans espace).

## 2. Les couleurs

Fichier : `app/src/main/res/values/brand.xml`.

| Couleur | Rôle |
|---|---|
| `brand_primary` | barre de titre, barre d'état, bouton « Démarrer la protection » |
| `brand_on_primary` | texte posé sur la couleur principale (en général blanc) |
| `brand_secondary` | éléments secondaires (contours de boutons, liens) |
| `brand_background` | fond des écrans |

Saisir les codes hexadécimaux de la charte (`#RRGGBB`).

**Les couleurs de sécurité ne suivent pas la charte** et sont dans `colors.xml` : rouge pour
l'alarme et le SOS, vert pour « Je vais bien », orange pour la pré-alarme et les zones à risque.
Elles doivent rester identiques d'une application à l'autre pour être reconnues dans l'urgence.
Vérifier que la couleur principale de la charte se distingue bien du rouge d'alarme.

## 3. La police (facultatif)

Déposer les fichiers de police (`.ttf`) dans `app/src/main/res/font/` (nom en minuscules, par
exemple `marque_regular.ttf`), puis ajouter dans `values/themes.xml`, dans `Theme.Dati` :

```xml
<item name="fontFamily">@font/marque_regular</item>
<item name="android:fontFamily">@font/marque_regular</item>
```

Vérifier que la licence de la police autorise son intégration dans une application.

## 4. L'icône de l'application

Fichiers : `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (fond) et
`drawable/ic_launcher_foreground.xml` (motif). Pour la remplacer, Android Studio : *New → Image
Asset* à partir du logo.

## Droits d'utilisation

Le logo et la charte sont des éléments protégés de l'entreprise : leur intégration doit être
validée par le service communication, en particulier pour une application diffusée hors du
réseau interne.
