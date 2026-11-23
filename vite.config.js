import { defineConfig } from 'vite';
import fs from 'fs';
import path from 'path';

function copyLogoPlugin() {
  return {
    name: 'copy-logo',
    closeBundle() {
      const outDir = path.resolve('dist');
      if (fs.existsSync('asbily-logo.jpg')) {
        fs.copyFileSync('asbily-logo.jpg', path.join(outDir, 'asbily-logo.jpg'));
      }
    }
  };
}

export default defineConfig({
  base: './',
  publicDir: false,
  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: true
  },
  preview: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: true
  },
  plugins: [copyLogoPlugin()]
});
