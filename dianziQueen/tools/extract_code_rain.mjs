import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const src = path.join(root, 'app/src/main/java/com/dianziqueen/app/CodeRainPhrases.kt');
const text = fs.readFileSync(src, 'utf8');

function extract(name) {
  const re = new RegExp(`private const val ${name} = """([\\s\\S]*?)"""`);
  const m = text.match(re);
  if (!m) throw new Error(`missing ${name}`);
  return m[1].split('\n').map((l) => l.trim()).filter(Boolean);
}

const esc = (s) =>
  s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, "\\'");

const before = extract('RAW_BEFORE');
const after = extract('RAW_AFTER');
const out = path.join(root, 'app/src/main/res/values/code_rain_arrays.xml');
const xml = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="code_rain_before">
${before.map((l) => `        <item>${esc(l)}</item>`).join('\n')}
    </string-array>
    <string-array name="code_rain_after">
${after.map((l) => `        <item>${esc(l)}</item>`).join('\n')}
    </string-array>
</resources>
`;
fs.writeFileSync(out, xml, 'utf8');
console.log(`Wrote ${out} (${before.length} before, ${after.length} after)`);
