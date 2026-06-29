#!/usr/bin/env node
/**
 * Ensures PushAppErrorCode constants stay aligned across TypeScript, Kotlin, and Swift.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

function extractTsCodes() {
  const source = readFileSync(join(root, 'src/errors.ts'), 'utf8');
  const codes = new Set();
  const re = /^\s+([A-Z][A-Z0-9_]+):\s+'([A-Z][A-Z0-9_]+)'/gm;
  let match;
  while ((match = re.exec(source)) !== null) {
    if (match[1] === match[2]) {
      codes.add(match[2]);
    }
  }
  return codes;
}

function extractKotlinCodes() {
  const source = readFileSync(
    join(root, 'android/src/main/java/com/mehery/pushapp/PushAppErrorCodes.kt'),
    'utf8',
  );
  const codes = new Set();
  const re = /const val ([A-Z][A-Z0-9_]+) = "([A-Z][A-Z0-9_]+)"/g;
  let match;
  while ((match = re.exec(source)) !== null) {
    if (match[1] === match[2]) {
      codes.add(match[2]);
    }
  }
  return codes;
}

function extractSwiftCodes() {
  const source = readFileSync(
    join(root, 'ios/Sources/PushAppPlugin/PushAppErrorCodes.swift'),
    'utf8',
  );
  const codes = new Set();
  const re = /static let \w+ = "([A-Z][A-Z0-9_]+)"/g;
  let match;
  while ((match = re.exec(source)) !== null) {
    codes.add(match[1]);
  }
  return codes;
}

function diff(label, missing, extra) {
  if (missing.length === 0 && extra.length === 0) {
    return;
  }
  const lines = [`${label} mismatch:`];
  if (missing.length) {
    lines.push(`  missing: ${missing.join(', ')}`);
  }
  if (extra.length) {
    lines.push(`  extra: ${extra.join(', ')}`);
  }
  throw new Error(lines.join('\n'));
}

function compareSets(name, a, b) {
  const missing = [...b].filter((code) => !a.has(code)).sort();
  const extra = [...a].filter((code) => !b.has(code)).sort();
  diff(name, missing, extra);
}

const ts = extractTsCodes();
const kotlin = extractKotlinCodes();
const swift = extractSwiftCodes();

// WEB_NOT_SUPPORTED is TypeScript-only (web stub).
const tsNative = new Set([...ts].filter((c) => c !== 'WEB_NOT_SUPPORTED'));

compareSets('Kotlin vs TypeScript (native)', kotlin, tsNative);
compareSets('Swift vs TypeScript (native)', swift, tsNative);

console.log(`Error code contract OK (${tsNative.size} native codes aligned across platforms).`);
