// site.json 에 적힌 사이트를 주소창 없는 창으로 띄우는 데스크톱 앱
const { app, BrowserWindow, Menu, shell } = require('electron');
const fs = require('fs');
const path = require('path');

const site = require('./site.json');
const START_URL = site.desktopUrl || site.url;
const startHost = new URL(START_URL).hostname;

// 같은 사이트(서브도메인 포함)인지 판단: youtube.com, m.youtube.com, www.youtube.com …
const hostParts = startHost.split('.');
// example.co.kr, school.ac.kr 같은 2단계 국가 도메인은 3칸까지 사용
const twoLevel = /^(co|or|ac|go|ne|re|pe|com|net|org|edu|gov)$/.test(hostParts[hostParts.length - 2] || '')
  && (hostParts[hostParts.length - 1] || '').length === 2;
const baseDomain = hostParts.slice(twoLevel ? -3 : -2).join('.');
const isSameSite = (url) => {
  try {
    const h = new URL(url).hostname;
    return h === baseDomain || h.endsWith('.' + baseDomain);
  } catch {
    return false;
  }
};

app.setName(site.name);

// 앱은 하나만 실행
if (!app.requestSingleInstanceLock()) {
  app.quit();
}

let win;
const stateFile = () => path.join(app.getPath('userData'), 'window-state.json');

function loadBounds() {
  try {
    return JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
  } catch {
    return { width: 1280, height: 820 };
  }
}

function saveBounds() {
  if (!win || win.isDestroyed()) return;
  try {
    const b = win.getNormalBounds();
    fs.writeFileSync(stateFile(), JSON.stringify({ ...b, maximized: win.isMaximized() }));
  } catch { /* 저장 실패해도 무시 */ }
}

function createWindow() {
  const bounds = loadBounds();
  win = new BrowserWindow({
    ...bounds,
    minWidth: 400,
    minHeight: 300,
    title: site.name,
    backgroundColor: site.themeColor || '#ffffff',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      partition: 'persist:site', // 로그인·쿠키 유지
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });

  if (bounds.maximized) win.maximize();
  win.once('ready-to-show', () => win.show());
  win.on('close', saveBounds);

  // 새 창으로 열리는 링크: 같은 사이트면 앱 안에서, 다른 사이트면 기본 브라우저로
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (isSameSite(url)) {
      win.loadURL(url);
    } else if (/^https?:/.test(url)) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  // http/https 가 아닌 링크(mailto: 등)는 운영체제에 넘김
  win.webContents.on('will-navigate', (event, url) => {
    if (!/^https?:/.test(url)) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  // 연결 실패 시 안내 화면
  win.webContents.on('did-fail-load', (_e, code, _desc, url, isMainFrame) => {
    if (!isMainFrame || code === -3) return; // -3: 사용자가 취소한 로드
    const retry = JSON.stringify(url || START_URL);
    const html = `<!doctype html><meta charset="utf-8"><body style="margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;font-family:sans-serif;background:#111;color:#eee">
      <div style="font-size:20px;font-weight:600">연결할 수 없어요</div>
      <div style="margin-top:8px;color:#aaa">인터넷 연결을 확인해 주세요.</div>
      <button style="margin-top:20px;padding:10px 26px;font-size:15px;border:0;border-radius:20px;background:#3b82f6;color:#fff;cursor:pointer" onclick='location.href=${retry}'>다시 시도</button></body>`;
    win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html));
  });

  win.loadURL(START_URL);
}

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    { role: 'editMenu' },
    {
      label: '이동',
      submenu: [
        {
          label: '뒤로',
          accelerator: isMac ? 'Cmd+[' : 'Alt+Left',
          click: () => win?.webContents.navigationHistory.canGoBack() && win.webContents.navigationHistory.goBack(),
        },
        {
          label: '앞으로',
          accelerator: isMac ? 'Cmd+]' : 'Alt+Right',
          click: () => win?.webContents.navigationHistory.canGoForward() && win.webContents.navigationHistory.goForward(),
        },
        { label: '홈', accelerator: 'CmdOrCtrl+Shift+H', click: () => win?.loadURL(START_URL) },
        { type: 'separator' },
        { role: 'reload', label: '새로고침' },
        { role: 'forceReload', label: '강제 새로고침' },
      ],
    },
    {
      label: '보기',
      submenu: [
        { role: 'resetZoom', label: '실제 크기' },
        { role: 'zoomIn', label: '확대' },
        { role: 'zoomOut', label: '축소' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '전체 화면' },
        { role: 'toggleDevTools', label: '개발자 도구' },
      ],
    },
    { role: 'windowMenu' },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore();
    win.focus();
  }
});

app.whenReady().then(() => {
  buildMenu();
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
