const site = require('../site.json');

module.exports = {
  appId: site.desktopId || 'com.siteapp.desktop',
  productName: site.name,
  files: ['main.js', 'site.json', 'package.json'],
  directories: { buildResources: 'build', output: 'dist' },
  mac: {
    target: [{ target: 'dmg', arch: ['arm64', 'x64'] }],
    category: 'public.app-category.utilities',
  },
  win: {
    target: [
      { target: 'nsis', arch: ['x64'] },     // 설치 파일
      { target: 'portable', arch: ['x64'] }, // 설치 없이 실행하는 exe
    ],
  },
  nsis: {
    oneClick: true,
    perMachine: false,
    createDesktopShortcut: true,
    createStartMenuShortcut: true,
  },
  linux: { target: ['AppImage'] },
};
