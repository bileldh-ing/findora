# Google Play listing metadata

Store listing text in the [fastlane supply](https://docs.fastlane.tools/actions/supply/) layout, one
folder per Play locale (`en-US`, `fr-FR`, `ar`):

| File | Play Console field | Limit |
|---|---|---|
| `title.txt` | App name | 30 characters |
| `short_description.txt` | Short description | 80 characters |
| `full_description.txt` | Full description | 4000 characters |
| `changelogs/default.txt` | Release notes ("What's new") | 500 characters |

The Release workflow sends the "What's new" text from `distribution/whatsnew/whatsnew-<locale>`
with each Play upload; keep those files in sync with `changelogs/default.txt` for the release.

The full description presents Findora's product scope. Before publishing to production, check every
line against the features the release actually ships (Google Play's metadata policy forbids
describing functionality the app does not have), and add screenshots, the 512×512 icon and the
1024×500 feature graphic under `images/` or directly in Play Console.
