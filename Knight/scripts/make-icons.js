const path = require('path');
const { chromium } = require('playwright');
const { faChessKnight } = require('@fortawesome/free-solid-svg-icons');
const [GLYPH_W, GLYPH_H, , , GLYPH_PATH] = faChessKnight.icon;

const out = path.join(__dirname, '..', 'assets');
const GREEN_LIGHT = '#2EDC6E';
const GREEN_DARK = '#0B6B30';

const gradient = `
  <defs>
    <radialGradient id="g" cx="50%" cy="38%" r="80%">
      <stop offset="0" stop-color="${GREEN_LIGHT}"/>
      <stop offset="1" stop-color="${GREEN_DARK}"/>
    </radialGradient>
    <filter id="s" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="14" stdDeviation="18" flood-color="#00200c" flood-opacity="0.45"/>
    </filter>
  </defs>`;

function knight(height, fill, shadow) {
  const scale = height / GLYPH_H;
  const x = (1024 - GLYPH_W * scale) / 2;
  const y = (1024 - GLYPH_H * scale) / 2;
  return `<g transform="translate(${x} ${y}) scale(${scale})" ${shadow ? 'filter="url(#s)"' : ''}>
    <path d="${GLYPH_PATH}" fill="${fill}"/></g>`;
}

const jobs = [
  ['icon.png', 1024, `${gradient}<rect width="1024" height="1024" fill="url(#g)"/>${knight(600, '#F4FFF7', true)}`, false],
  ['android-icon-background.png', 1024, `${gradient}<rect width="1024" height="1024" fill="url(#g)"/>`, false],
  ['android-icon-foreground.png', 1024, `${gradient}${knight(460, '#F4FFF7', true)}`, true],
  ['android-icon-monochrome.png', 1024, knight(460, '#FFFFFF', false), true],
  ['splash-icon.png', 1024, `${gradient}${knight(600, '#F4FFF7', true)}`, true],
  ['favicon.png', 128, `${gradient}<rect width="1024" height="1024" fill="url(#g)"/>${knight(600, '#F4FFF7', false)}`, false],
];

(async () => {
  const browser = await chromium.launch();
  for (const [name, size, body, transparent] of jobs) {
    const page = await browser.newPage({ viewport: { width: size, height: size } });
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">${body}</svg>`;
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    await page.screenshot({ path: path.join(out, name), omitBackground: transparent });
    await page.close();
  }
  await browser.close();
})();
