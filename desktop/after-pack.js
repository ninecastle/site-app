// 맥 앱에 ad-hoc 서명을 붙여서, 다운로드한 앱이 "손상되었습니다"로 막히지 않게 함.
// (애플 개발자 인증서 없이 할 수 있는 최선: 처음 실행 때만 "확인" 한 번 필요)
const { execFileSync } = require('child_process');
const path = require('path');

exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== 'darwin') return;
  const appPath = path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`);
  execFileSync('codesign', ['--force', '--deep', '--sign', '-', appPath], { stdio: 'inherit' });
  execFileSync('codesign', ['--verify', '--deep', '--strict', appPath], { stdio: 'inherit' });
};
