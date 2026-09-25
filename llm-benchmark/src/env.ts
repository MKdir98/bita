import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

export const BENCH_DIR = join(dirname(fileURLToPath(import.meta.url)), '..')
export const REPO_DIR = join(BENCH_DIR, '..')

/** Loads KEY=VALUE pairs from the repo-root .env without overriding real env vars. */
export function loadEnv(): void {
  const file = join(REPO_DIR, '.env')
  if (!existsSync(file)) return
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/)
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2]
  }
}

export function requireEnv(name: string): string {
  const v = process.env[name]
  if (!v) throw new Error(`Missing ${name} (set it in ${join(REPO_DIR, '.env')})`)
  return v
}
