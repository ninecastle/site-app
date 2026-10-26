import UIKit

/// 아이폰 앱 설정 — 사이트를 바꿀 때 여기와 맨 위의 site.json 을 같이 고치세요.
/// (앱 이름은 Xcode → 타깃 → General → Display Name 에서도 바꿀 수 있어요.)
enum SiteConfig {
    static let startURL = URL(string: "https://m.youtube.com/")!
    static let themeColorHex = "#0f0f0f"

    static var themeColor: UIColor { UIColor(hex: themeColorHex) }

    /// 배경색이 어두우면 상태바 글자를 흰색으로
    static var isDarkTheme: Bool {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        themeColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (0.299 * r + 0.587 * g + 0.114 * b) < 0.5
    }
}

extension UIColor {
    convenience init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        self.init(red: CGFloat((v >> 16) & 0xFF) / 255,
                  green: CGFloat((v >> 8) & 0xFF) / 255,
                  blue: CGFloat(v & 0xFF) / 255,
                  alpha: 1)
    }
}
