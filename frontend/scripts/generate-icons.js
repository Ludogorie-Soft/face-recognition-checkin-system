// Generates PWA icons and favicon
// Uses sharp (bundled with Next.js) — run with: node scripts/generate-icons.js

const sharp = require('sharp')
const fs = require('fs')
const path = require('path')

const iconsDir = path.join(__dirname, '..', 'public', 'icons')
const appDir = path.join(__dirname, '..', 'app')
fs.mkdirSync(iconsDir, { recursive: true })

// SVG icon: blue rounded square with a white checkmark
function makeSvg(size) {
  const r = Math.round(size * 0.18)
  const pad = Math.round(size * 0.18)
  const stroke = Math.round(size * 0.09)
  const x1 = pad, y1 = size / 2
  const x2 = size * 0.42, y2 = size - pad
  const x3 = size - pad, y3 = pad
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" rx="${r}" fill="#2563EB"/>
  <polyline points="${x1},${y1} ${x2},${y2} ${x3},${y3}"
    fill="none" stroke="white" stroke-width="${stroke}"
    stroke-linecap="round" stroke-linejoin="round"/>
</svg>`
}

async function generatePng(size, outPath) {
  await sharp(Buffer.from(makeSvg(size))).png().toFile(outPath)
}

// Wraps a PNG buffer in a minimal .ico container
function pngToIco(pngBuffer) {
  const numImages = 1
  const headerSize = 6
  const entrySize = 16
  const dataOffset = headerSize + entrySize * numImages

  // ICONDIR header
  const header = Buffer.alloc(6)
  header.writeUInt16LE(0, 0)          // reserved
  header.writeUInt16LE(1, 2)          // type: 1 = ICO
  header.writeUInt16LE(numImages, 4)  // image count

  // ICONDIRENTRY
  const entry = Buffer.alloc(16)
  entry.writeUInt8(32, 0)             // width (32 = 32px; 0 means 256px)
  entry.writeUInt8(32, 1)             // height
  entry.writeUInt8(0, 2)              // color count (0 = truecolor)
  entry.writeUInt8(0, 3)              // reserved
  entry.writeUInt16LE(1, 4)           // color planes
  entry.writeUInt16LE(32, 6)          // bits per pixel
  entry.writeUInt32LE(pngBuffer.length, 8)  // image data size
  entry.writeUInt32LE(dataOffset, 12)       // offset to image data

  return Buffer.concat([header, entry, pngBuffer])
}

;(async () => {
  // PWA icons
  await generatePng(192, path.join(iconsDir, 'icon-192.png'))
  console.log('✓ icon-192.png (192×192)')

  await generatePng(512, path.join(iconsDir, 'icon-512.png'))
  console.log('✓ icon-512.png (512×512)')

  // Favicon: 32×32 PNG wrapped in ICO
  const tmpPng = await sharp(Buffer.from(makeSvg(32))).png().toBuffer()
  const ico = pngToIco(tmpPng)
  fs.writeFileSync(path.join(appDir, 'favicon.ico'), ico)
  console.log('✓ app/favicon.ico (32×32)')

  console.log('\nDone.')
})()
