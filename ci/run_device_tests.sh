#!/usr/bin/env bash
# Roda no emulador (GitHub Actions): instala o app e os testes, roda os testes e tira as capturas de tela.
# Uso: ci/run_device_tests.sh <nome do aparelho: phone, tablet ou fold>
set -u
NAME="$1"
OUT="${OUT:-$PWD/device-out}"
mkdir -p "$OUT/logs" "$OUT/screenshots"
RUNNER="br.com.prumopdf.app.test/androidx.test.runner.AndroidJUnitRunner"
LANGS="pt,en,de,ja"

adb wait-for-device
adb logcat -G 16M > /dev/null 2>&1
adb shell getprop ro.build.version.release > "$OUT/logs/android-$NAME.txt"
adb shell wm size >> "$OUT/logs/android-$NAME.txt"
adb shell wm density >> "$OUT/logs/android-$NAME.txt"
adb install -r -g apks/app-debug.apk > "$OUT/logs/install-$NAME.txt" 2>&1
adb install -r -g apks/app-debug-androidTest.apk >> "$OUT/logs/install-$NAME.txt" 2>&1

# Se algo travar, guarda as pilhas de chamadas de todas as linhas de execução do app (para achar a causa).
dump_threads() {
  adb root > /dev/null 2>&1; sleep 2
  local pid
  pid=$(adb shell pidof br.com.prumopdf.app | tr -d '\r')
  [ -n "$pid" ] && adb shell debuggerd -j "$pid" > "$OUT/logs/threads-$1.txt" 2>&1
  adb shell am force-stop br.com.prumopdf.app
}

# Testes de interface e da biblioteca no aparelho (no máximo 25 minutos)
timeout 1500 adb shell am instrument -w -r -e class br.com.prumopdf.AppTest,br.com.prumopdf.PdfOpsTest,br.com.prumopdf.PagesUiTest "$RUNNER" > "$OUT/logs/tests-$NAME.txt" 2>&1
[ $? -eq 124 ] && { echo "TRAVOU (25 min)" >> "$OUT/logs/tests-$NAME.txt"; dump_threads "tests-$NAME"; }
if grep -q "^OK (" "$OUT/logs/tests-$NAME.txt"; then
  RESULT="ok: $(grep '^OK (' "$OUT/logs/tests-$NAME.txt")"
else
  RESULT="FALHOU: $(grep -E '^Tests run|INSTRUMENTATION_RESULT|shortMsg' "$OUT/logs/tests-$NAME.txt" | tr '\n' ' ')"
fi

shots() {
  timeout 900 adb shell am instrument -w -r -e class br.com.prumopdf.ScreenshotTest -e device "$1" -e langs "$LANGS" "$RUNNER" \
    > "$OUT/logs/shots-$1.txt" 2>&1
  [ $? -eq 124 ] && { echo "TRAVOU (15 min)" >> "$OUT/logs/shots-$1.txt"; dump_threads "shots-$1"; }
  grep -q "^OK (" "$OUT/logs/shots-$1.txt" || RESULT="$RESULT | capturas $1 com erro"
}

if [ "$NAME" = "fold" ]; then
  adb emu unfold > /dev/null 2>&1; sleep 4
  shots fold-aberto
  adb emu fold > /dev/null 2>&1; sleep 4
  shots fold-fechado
  adb emu unfold > /dev/null 2>&1
else
  shots "$NAME"
fi

adb pull /sdcard/Android/data/br.com.prumopdf.app/files/screenshots/. "$OUT/screenshots/" > /dev/null 2>&1
adb logcat -d -s Prumo:V AndroidRuntime:E TestRunner:V > "$OUT/logs/logcat-$NAME.txt" 2>&1
echo "$NAME: $RESULT" | tee "$OUT/result-$NAME.txt"
case "$RESULT" in ok:*) [[ "$RESULT" == *"com erro"* ]] && exit 1 || exit 0 ;; *) exit 1 ;; esac
