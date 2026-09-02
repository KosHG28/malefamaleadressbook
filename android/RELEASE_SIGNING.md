# Ключ подписи для Google Play

Google Play не принимает сборки, подписанные debug-ключом (тем, чем сейчас подписан
`app-release.apk` в GitHub Releases). Нужен отдельный ключ загрузки (upload key).

**Это единственный шаг во всей публикации, где потерянный секрет нельзя восстановить.**
Если потерять upload-ключ, обновить уже опубликованное в Play приложение под тем же
пакетом (`com.koshg.calendar`) станет невозможно — только новая карточка с нуля и
потерей всех отзывов/установок. Поэтому:

- Генерируй и храни ключ сам, на своей машине (или в терминале при себе) — не проси
  ассистента сделать это за тебя автономно.
- Сразу сделай резервную копию файла `.jks` и всех трёх паролей в отдельном
  менеджере паролей — не только в этом репозитории (он туда и не попадёт, см. ниже).

## 1. Сгенерировать keystore

Нужен установленный JDK (тот же `keytool`, что идёт с Android Studio).

```bash
keytool -genkeypair -v \
  -keystore interlude-upload.jks \
  -alias interlude-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Ответишь на вопросы (имя, организация — можно формально) и дважды введёшь пароль —
один раз для самого keystore, второй раз для ключа внутри него (можно совпадающие).
Файл `interlude-upload.jks` держи вне репозитория.

## 2. Локальная сборка (по желанию)

Если хочешь собрать подписанный `.aab` у себя локально:

```bash
cd android
cat > app/keystore.properties <<EOF
storeFile=/absolute/path/to/interlude-upload.jks
storePassword=твой_пароль_keystore
keyAlias=interlude-upload
keyPassword=твой_пароль_ключа
EOF

./gradlew bundleRelease
```

`app/keystore.properties` уже в `.gitignore` — Gradle сам подхватит его и подпишет
`app/build/outputs/bundle/release/app-release.aab` этим ключом. Без этого файла
сборка `bundleRelease`/`assembleRelease` по-прежнему подписывается debug-ключом,
как раньше — ничего в существующих CI-потоках (GitHub Releases APK) не ломается.

## 3. Сборка через GitHub Actions (без хранения ключа на диске)

Есть workflow `android-playstore-bundle.yml` (запускается только вручную,
`workflow_dispatch`) — он ожидает четыре секрета репозитория
(Settings → Secrets and variables → Actions → New repository secret):

| Секрет | Значение |
| --- | --- |
| `PLAYSTORE_KEYSTORE_BASE64` | `base64 -w0 interlude-upload.jks` (весь вывод целиком) |
| `PLAYSTORE_KEYSTORE_PASSWORD` | пароль keystore |
| `PLAYSTORE_KEY_ALIAS` | `interlude-upload` |
| `PLAYSTORE_KEY_PASSWORD` | пароль ключа |

После этого запуск workflow вручную (Actions → Android Play Store bundle → Run workflow)
соберёт подписанный `.aab` и приложит его как build-артефакт для скачивания — дальше
загружаешь файл в Play Console сам.

## 4. Google Play App Signing (рекомендация Google)

При первой загрузке `.aab` в Play Console Google предложит включить **Play App
Signing** — тогда именно этот upload-ключ используется только чтобы подтвердить,
что сборка от тебя, а настоящим ключом, которым подписываются файлы для пользователей,
владеет и управляет сам Google. Это дополнительная страховка: даже если upload-ключ
когда-нибудь всё же потеряется, Google восстановит доступ по документам, а не откажет
насовсем. Рекомендуется соглашаться на это при первой публикации.
