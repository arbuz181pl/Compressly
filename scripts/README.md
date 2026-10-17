# Release signing

`Build & release` needs four repository secrets. Generate them once, keep the keystore
backed up somewhere safe, and **never commit it**.

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of the `.jks` file |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

> Losing the keystore means you can never update the app on Play under the same listing.
> Back it up outside the repo.

## 1. Create the keystore

```bash
keytool -genkeypair -v \
  -keystore compressly-release.jks \
  -alias compressly \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for the keystore password and key password. Use the same value for both if you prefer
simplicity — the workflow accepts separate values either way.

## 2. Base64-encode it

**macOS / Linux**

```bash
base64 -i compressly-release.jks | tr -d '\n' > compressly-release.jks.b64
```

**Windows (PowerShell)**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("compressly-release.jks")) |
  Set-Content -NoNewline compressly-release.jks.b64
```

## 3. Add the secrets

Repo -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**,
four times, one per row in the table above.

## 4. Publish

**Actions** -> **Build & release** -> **Run workflow** -> type a version such as `1.0.1` -> run.

A signed APK and an App Bundle are attached to the release tagged `v1.0.1`.

---

## Local release builds

`keystore.properties` is gitignored. To build signed APKs locally, create it in the repo root:

```properties
storeFile=keystore/release.jks
storePassword=your-keystore-password
keyAlias=compressly
keyPassword=your-key-password
```

```bash
./gradlew assembleRelease -PversionName=1.0.1 -PversionCode=1000001
```

Without that file the project still builds — `assembleRelease` just produces an **unsigned**
APK, which is what `Verify build` relies on.

## Version codes

`versionCode` must be an integer that increases with every Play upload. If you leave the field
blank the workflow derives it from the version name:

| Version | versionCode |
|---|---|
| `1.0.1` | `1000001` |
| `1.2.3` | `1000003` |
| `2.0.0` | `2000000` |

Override it in the workflow input when you need to.