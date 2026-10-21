# ADR 0001: Single :app module, package-by-feature, strict layers

Status: accepted (Phase 0)

## Context
Findora must be easy to clone and build for recruiters, while still showing clean architecture.

## Decision
Start in one `:app` module, organised by feature (`feature/home`, `feature/auth`, ...) with shared code in `core/`.
Inside each feature, `ui -> domain <- data`: UI and data depend on domain; domain depends on nothing Android.
Detekt and review keep imports honest. Phase 8 splits `core/*` and `feature/*` into Gradle modules.

## Consequences
Fast builds and simple navigation early; module boundaries are a mechanical extraction later rather than a redesign.
