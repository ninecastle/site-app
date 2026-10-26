const site = require('../site.json');

module.exports = {
  appId: site.desktopId || 'com.siteapp.desktop',
  productName: site.name,
  files: ['main.js', 'site.json', 'package.json'],
  directories: { buildResources: 'build', output: 'dist' },
  afterPack: './after-pack.js',
  mac: {
    target: [{ target: 'dmg', arch: ['arm64', 'x64'] }],
    category: 'public.app-category.utilities',
    identity: null, // 유료 인증서 서명은 건너뛰고 after-pack.js 의 ad-hoc 서명 사용
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
