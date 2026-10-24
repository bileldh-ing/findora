#!/usr/bin/env bash
# Store-image gate (StoreAssetsTest). Verifies the Google Play graphics committed in fastlane/metadata/android/en-US/images
# against a fresh render of the app. When they are missing or no longer match, records them again so the job's
# artifact holds the new images to review and commit, and fails.
set -o pipefail
images=fastlane/metadata/android/en-US/images
run() {
  ./gradlew :app:testDebugUnitTest --tests 'com.findora.app.store.*' -Pfindora.store="$1" --stacktrace 2>&1 | tee -a store-assets.log
}
if [ -f "$images/icon.png" ] && [ -f "$images/featureGraphic.png" ] && compgen -G "$images/phoneScreenshots/*.png" > /dev/null; then
  echo "Store images: verify"
  if run verify; then exit 0; fi
  echo "::error title=Store images out of date::The app no longer renders the committed store images. Re-recorded images are in the screenshots artifact ($images); review and commit them if the change is intended."
else
  echo "::error title=Store images missing::Recording them; download the screenshots artifact and commit $images."
fi
run record
exit 1
