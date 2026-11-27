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
      if (fs.existsSync('app-ads.txt')) {
        fs.copyFileSync('app-ads.txt', path.join(outDir, 'app-ads.txt'));
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
