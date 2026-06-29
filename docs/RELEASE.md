# Release process

This document defines how **pushapp-ionic** is versioned, validated, and published.

## Semantic versioning

We follow [SemVer 2.0](https://semver.org/):

| Bump | When |
|------|------|
| **MAJOR** | Breaking public API, minimum platform change, or behavior change that requires integrator action |
| **MINOR** | New backward-compatible features (new methods, optional init flags) |
| **PATCH** | Bug fixes, security patches, internal/native fixes with no API change |

**Public API** includes:

- TypeScript exports (`src/definitions.ts`, `src/errors.ts`, `src/index.ts`)
- Capacitor plugin method names and option shapes
- Documented minimum platforms (iOS, Android)
- Stable `PushAppErrorCode` string values (treat renames as breaking)

## Pre-release checklist

Before tagging `vX.Y.Z`:

```bash
npm install
npm run lint
npm test
npm run test:contract
npm run verify        # build + Android tests + SwiftLint (macOS for SwiftLint)
```

- [ ] `CHANGELOG.md` updated under `[X.Y.Z]`
- [ ] `package.json` version matches tag
- [ ] Native platform mins documented in README + Integration Guide
- [ ] No secrets/webhooks in repo
- [ ] [QA Test Plan](QA-Test-Plan.md) executed for release candidate (device matrix)

## Publishing to npm

1. Merge release PR to `main`.
2. Create and push an annotated tag:

   ```bash
   git tag -a v0.1.1 -m "pushapp-ionic 0.1.1"
   git push origin v0.1.1
   ```

3. GitHub Actions **Release** workflow creates a GitHub Release from the tag.
4. Publish to npm (maintainers):

   ```bash
   npm publish --access public
   ```

   `prepublishOnly` runs `npm run build` automatically.

## CocoaPods / SPM

- **CocoaPods:** version is read from `package.json` via `PushappIonic.podspec`. After npm publish, push the same git tag; integrators run `pod update PushappIonic`.
- **SPM:** consumers pin the git tag (e.g. `0.1.1`) in `Package.swift`. Note: SPM does not include `EasyTipView`; CocoaPods is the supported path for tooltip campaigns on iOS.

## Rocket / enterprise rollout (recommended)

| Stage | Action |
|-------|--------|
| Sandbox | Pin `0.1.1`, `sandbox: true`, no `slackWebhookUrl` in prod builds |
| Staging | Full [QA Test Plan](QA-Test-Plan.md); verify error-code handling in app |
| Production | Pin exact version; legal/privacy review; monitor Logcat/Xcode for token leaks |

## Support policy

- **1.x** receives bug fixes and security patches.
- Report issues: [GitHub Issues](https://github.com/mehery-soccom/PushApp-Capacitor/issues).
