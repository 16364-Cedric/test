import { readFileSync, writeFileSync, renameSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const empty = () => ({ devices: [], alerts: [], zones: [], zonesVersion: 0 });

/** Persistance dans un fichier JSON, écrit de façon atomique. */
export class FileStore {
  constructor(path) {
    this.path = path;
    try {
      this.data = { ...empty(), ...JSON.parse(readFileSync(path, 'utf8')) };
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
      this.data = empty();
    }
  }

  save() {
    mkdirSync(dirname(this.path), { recursive: true });
    const tmp = `${this.path}.tmp`;
    writeFileSync(tmp, JSON.stringify(this.data, null, 2));
    renameSync(tmp, this.path);
  }
}

/** Stockage en mémoire, pour les tests. */
export class MemoryStore {
  constructor() {
    this.data = empty();
  }

  save() {}
}
