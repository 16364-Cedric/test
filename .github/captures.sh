#!/usr/bin/env bash
# Captures d'écran de l'appli DATI sur l'émulateur (lancé par captures.yml).
set -u
OUT=captures
mkdir -p "$OUT"
PKG=fr.dati.app

pause() { sleep "${1:-3}"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "capture : $1"; }

# Touche le premier élément dont le texte ou la description contient $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'EOF'
import re, sys, xml.etree.ElementTree as ET
cible = sys.argv[1].lower()
for n in ET.parse('/tmp/ui.xml').iter('node'):
    t = (n.get('text') or '') + ' ' + (n.get('content-desc') or '')
    if cible in t.lower():
        a, b, c, d = map(int, re.findall(r'\d+', n.get('bounds')))
        print((a + c) // 2, (b + d) // 2)
        break
EOF
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "touché : $1"; else echo "absent : $1"; fi
}

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb emu geo fix 6.0915 45.8861 400
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow 2>/dev/null
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow 2>/dev/null
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null

# Configuration de démonstration par le lien de configuration
adb shell am start -W -a android.intent.action.VIEW \
  -d "'dati://config?name=Alice%20Martin&contacts=%2B33612345678,%2B33698765432&checkin=30'" $PKG
pause 4
shot 01-configuration
tap "Appliquer"; pause 3
shot 02-accueil

tap "Démarrer la protection"; pause 5
# Fenêtres système éventuelles (optimisation de batterie, alarme plein écran…)
for i in 1 2 3; do tap "Autoriser"; tap "Allow"; pause 1; done
adb shell am start -n $PKG/.MainActivity >/dev/null; pause 4
shot 03-protection-active

tap "Carte"; pause 10
shot 04-carte
adb shell input keyevent KEYCODE_BACK; pause 2

tap "Plus d'options"; tap "More options"; pause 2
tap "Réglages"; pause 3
shot 05-reglages
adb shell input swipe 540 1800 540 500 300; pause 2
shot 06-reglages-suite
adb shell input keyevent KEYCODE_BACK; pause 2

tap "Lancer un essai"; pause 2
shot 07-essai
tap "Démarrer l'essai"; pause 5
shot 08-pre-alarme
pause 40
shot 09-alarme
tap "Je vais bien"; tap "Arrêter"; pause 3
adb shell am start -n $PKG/.MainActivity >/dev/null; pause 2

tap "Plus d'options"; tap "More options"; pause 2
tap "Journal"; pause 3
shot 10-journal
ls -l "$OUT"
