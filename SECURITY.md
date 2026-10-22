# Security policy

## Supported versions

Only the latest release on the [Releases page](https://github.com/bileldh-ing/findora/releases)
and the current `main` branch receive security fixes.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub: **Security → Report a vulnerability**
(<https://github.com/bileldh-ing/findora/security/advisories/new>), with:

- a description of the issue and its impact,
- steps or a proof of concept to reproduce it,
- the app version (or commit) and device / Android version.

You will get an acknowledgement within 3 working days and a status update at least every 7 days
until the issue is resolved. Fixed issues are credited in the release notes unless you prefer to
stay anonymous.

## Scope

In scope: the Findora Android app in this repository, its build and release pipeline
(GitHub Actions workflows), and how it stores data on the device.

Release signing keys and Play service-account credentials are never stored in this repository;
they live only in GitHub Actions secrets (see [docs/RELEASING.md](docs/RELEASING.md)).
