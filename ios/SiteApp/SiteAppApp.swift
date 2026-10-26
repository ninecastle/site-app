import SwiftUI

@main
struct SiteAppApp: App {
    var body: some Scene {
        WindowGroup {
            ZStack {
                Color(SiteConfig.themeColor).ignoresSafeArea()
                WebView(url: SiteConfig.startURL)
            }
            .preferredColorScheme(SiteConfig.isDarkTheme ? .dark : .light)
        }
    }
}
