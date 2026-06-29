# Contributing

This guide provides instructions for contributing to this Capacitor plugin.

## Developing

### Local Setup

1. Fork and clone the repo.
1. Install the dependencies.

    ```shell
    npm install
    ```

1. Install SwiftLint if you're on macOS.

    ```shell
    brew install swiftlint
    ```

### Scripts

#### `npm run build`

Build the plugin web assets and generate plugin API documentation using [`@capacitor/docgen`](https://github.com/ionic-team/capacitor-docgen).

It will compile the TypeScript code from `src/` into ESM JavaScript in `dist/esm/`. These files are used in apps with bundlers when your plugin is imported.

Then, Rollup will bundle the code into a single file at `dist/plugin.js`. This file is used in apps without bundlers by including it as a script in `index.html`.

#### `npm run verify`

Build and validate the web and native projects.

This is useful to run in CI to verify that the plugin builds for all platforms.

#### `npm run lint` / `npm run fmt`

Check formatting and code quality, autoformat/autofix if possible.

This template is integrated with ESLint, Prettier, and SwiftLint. Using these tools is completely optional, but the [Capacitor Community](https://github.com/capacitor-community/) strives to have consistent code style and structure for easier cooperation.

## Publishing

There is a `prepublishOnly` hook in `package.json` which prepares the plugin before publishing.

See **[docs/RELEASE.md](docs/RELEASE.md)** for the full semver policy, tag checklist, and npm publish steps.

Quick path:

```shell
npm run lint && npm test && npm run test:contract && npm run verify
npm publish --access public
```

> **Note**: The [`files`](https://docs.npmjs.com/cli/v7/configuring-npm/package-json#files) array in `package.json` specifies which files get published. If you rename files/directories or add files elsewhere, you may need to update it.

### Release tags

Pushing a tag `vX.Y.Z` triggers [`.github/workflows/release.yml`](../.github/workflows/release.yml), which validates the version, runs tests, and creates a GitHub Release from `CHANGELOG.md`.

```shell
git tag -a v0.1.1 -m "pushapp-ionic 0.1.1"
git push origin v0.1.1
```

### Dependency audit

Before major releases, run:

```shell
npm run audit:deps
```

See [docs/dependencies.md](docs/dependencies.md) for the SBOM summary.
