# Google Play listing – Arrow Escape

Package ID: `com.arrowescape.game` · Version 1.0.0 (code 1) · Upload file: `app/build/outputs/bundle/release/app-release.aab`

## 1. Create your upload key (once; only you should do this)

The key signs every version you upload. You choose its passwords, so run this yourself, in your own terminal:

```bash
mkdir -p ~/.arrow-escape-signing && keytool -genkeypair -v -keystore ~/.arrow-escape-signing/upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.example` to `~/.arrow-escape-signing/keystore.properties` and fill in the path and the passwords you chose. **Back up `upload.jks` and the passwords** (a password manager plus a second copy of the file). With Play App Signing a lost upload key can be reset through Google support, but it takes days.

Build the signed bundle:

```bash
./gradlew bundleRelease
```

## 2. Store listing

**App name** (max 30): `Arrow Escape: Puzzle`

**Short description** (max 80):
`Tap the arrows out in the right order. Hundreds of relaxing logic puzzles.`

**Full description** (max 4000):

```
Every arrow wants out. Tap one and it slides off the board the way it points, but only if nothing is in its way. Find the right order and clear the grid.

SIMPLE TO START, HARD TO PUT DOWN
- One tap per move, no timers, no rush
- 10 hand-made tutorial levels teach you the rules
- 500 levels that grow from small 5x5 grids to big, winding 12x16 mazes
- Boards shaped like hearts, circles, diamonds and more
- Every level is guaranteed solvable

THINK BEFORE YOU TAP
- A blocked arrow bounces back and costs a heart; three hearts per level
- Clear a level with no mistakes for 3 stars
- Stuck? A hint shows you an arrow that can leave right now

YOUR STYLE
- Classic: clean black arrows on white, calm and focused
- Playful: a bright colour for every arrow
- Light and dark themes, sound and haptics you can switch off

Works offline. No account needed. Your progress stays on your phone.
```

**App icon** (512x512): `store/icon-512.png`
**Feature graphic** (1024x500): `store/feature-graphic.png`
**Phone screenshots** (2–8): `store/screenshots/1-home.png`, `2-heart.png`, `3-playful.png`, `4-dense.png`

The launcher icon and `icon-512.png` both come from `tools/gen_icon.py` (edit the geometry there, then run it).

To refresh the graphics after a design change: put new 1080x2400 emulator screenshots in `store/raw/` (same names) and run `python3 tools/gen_store_assets.py`.

## 3. App content (Play Console)

- **App or game:** Game. **Category:** Puzzle. **Tags:** puzzle, logic, brain games, relaxing.
- **Free / paid:** Free. **In-app purchases:** No.
- **Contains ads:** Yes.
- **Contact email:** your own (it is shown publicly on the store page).
- **Privacy policy URL:** must be a public web page. The text is in `PRIVACY.md`: replace `[your-email@example.com]`, publish it on your website (or GitHub Pages), and paste the URL.
- **Target audience:** 13 and over only. Do not include under-13 age groups: that would put the app under Google's Families policy, which the current ad setup does not follow.
- **Content rating (IARC questionnaire):** answer No to violence, blood, sexual content, profanity, drugs, gambling, user-generated content, sharing location and digital purchases. Expected: Everyone / PEGI 3.
- **Data safety:** data is collected and shared by the AdMob SDK only. Follow AdMob's guide ("Prepare for Google Play's Data safety section") and declare at least **Device or other IDs** (advertising ID), **Approximate location**, **App interactions** and **Diagnostics**, collected and shared for **Advertising** and **Fraud prevention**, encrypted in transit. Game progress and settings stay on the device and are not collected.
- **Advertising ID:** Yes, used for advertising.
- **Government / news / health / financial features:** No.

## 4. AdMob (before or right after the first upload)

The real ad unit ids live in `~/.gradle/gradle.properties` on your machine (`ARROW_ADMOB_*`), not in the repo. The project's `gradle.properties` holds Google's test ids, and a release built without the real ones prints a warning. Debug builds always use test ids.

1. In AdMob, open **Privacy & messaging** and publish a **European regulations (GDPR)** message for Arrow Escape, so EEA/UK players see the consent form.
2. Once the app is live, open the app in AdMob and **link it to its Play Store listing** (it was registered as "not on a store yet").
3. Add an `app-ads.txt` file at the root of the developer website shown on your Play listing, with the line AdMob gives you.
4. Never tap your own live ads. Use the debug build, or add your phone as a test device in AdMob.

## 5. Upload and release

1. Play Console → **Create app**: name above, default language, Game, Free. Accept the declarations.
2. Fill **App content** (section 3), then the **Main store listing** (section 2) and upload the graphics.
3. **Testing → Closed testing** → create a track → **Create release** → upload `app-release.aab`. Keep **Play App Signing** on (default): Google holds the app signing key, and your `upload.jks` is the upload key.
4. New personal developer accounts must run a closed test with a minimum number of testers for a minimum period before production access. Check the current requirement shown in your Console, then apply for production.
5. **Production → Create release** with the same bundle → submit for review.

## Every update

Increase `VERSION_CODE` (and `VERSION_NAME`) in `gradle.properties`, run `./gradlew bundleRelease`, and upload the new `app-release.aab`.
