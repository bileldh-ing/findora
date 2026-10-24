# Google Play listing metadata

Store listing text and graphics in the [fastlane supply](https://docs.fastlane.tools/actions/supply/) layout, one
folder per Play locale (`en-US`, `fr-FR`, `ar`):

| File | Play Console field | Limit |
|---|---|---|
| `title.txt` | App name | 30 characters |
| `short_description.txt` | Short description | 80 characters |
| `full_description.txt` | Full description | 4000 characters |
| `changelogs/<versionCode>.txt` | Release notes ("What's new") | 500 characters |
| `en-US/images/icon.png` | App icon | 512 x 512, 32-bit PNG |
| `en-US/images/featureGraphic.png` | Feature graphic | 1024 x 500, 24-bit PNG |
| `en-US/images/phoneScreenshots/` | Phone screenshots | 2 to 8; 1080 x 1920, 24-bit PNG |

The graphics are rendered from the app by `StoreAssetsTest`; the text describes only what the current version ships.
`scripts/store/check_store_readiness.py` checks all of it on every CI run. How each file is produced, verified and
published: [docs/STORE_LISTING.md](../docs/STORE_LISTING.md).
