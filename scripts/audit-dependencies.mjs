#!/usr/bin/env node
/**
 * Prints native dependency versions from Gradle and documents for release audits.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const gradle = readFileSync(join(root, 'android/build.gradle'), 'utf8');

const patterns = [
  ['Glide', /glide:([\d.]+)/],
  ['OkHttp', /okhttp:([\d.]+)/],
  ['Firebase BOM', /firebase-bom:([\d.]+)/],
  ['Material', /material:([\d.]+)/],
  ['Kotlin stdlib', /kotlin-stdlib:([\d.]+)/],
  ['AppCompat', /androidxAppCompatVersion[^\n]*'([\d.]+)'/],
];

console.log('pushapp-ionic native dependency audit\n');

const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
console.log(`Package version: ${pkg.version}`);
console.log(`Capacitor peer: ${pkg.peerDependencies['@capacitor/core']}`);
console.log(`Push notifications peer: ${pkg.peerDependencies['@capacitor/push-notifications']}\n`);

console.log('Android (android/build.gradle):');
for (const [name, re] of patterns) {
  const m = gradle.match(re);
  console.log(`  ${name}: ${m ? m[1] : 'not found'}`);
}

console.log('\niOS (PushappIonic.podspec):');
const podspec = readFileSync(join(root, 'PushappIonic.podspec'), 'utf8');
const iosMin = podspec.match(/deployment_target = '([^']+)'/);
const easyTip = podspec.includes('EasyTipView');
console.log(`  iOS deployment target: ${iosMin ? iosMin[1] : '?'}`);
console.log(`  EasyTipView (CocoaPods): ${easyTip ? 'yes' : 'no'}`);

console.log('\nFull inventory: docs/dependencies.md');
