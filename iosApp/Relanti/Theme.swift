import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    /// A colour that follows light and dark mode.
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}

/// The Autopista palette from the design proposal.
enum Palette {
    static let bg = Color.dynamic(0xEEF1F5, 0x111419)
    static let surface = Color.dynamic(0xFFFFFF, 0x1A1E25)
    static let ink = Color.dynamic(0x1F232A, 0xE9ECF1)
    static let ink2 = Color.dynamic(0x535A66, 0xAEB5C0)
    static let ink3 = Color.dynamic(0x8A919C, 0x7B8390)
    static let line = Color.dynamic(0xD5DAE2, 0x2C323C)
    static let blue = Color.dynamic(0x1A56B0, 0x4F8DF0)
    static let blueInk = Color.dynamic(0x14438A, 0x8DB6F7)
    static let blueSoft = Color.dynamic(0xE2EBF8, 0x18273F)
    static let yellow = Color(hex: 0xF2B705)
    static let yellowInk = Color.dynamic(0x7A5600, 0xF5C02E)
    static let sunset = Color(hex: 0xEA5B2A)
    static let sunsetSoft = Color.dynamic(0xFCE6DC, 0x3A2219)
    static let go = Color.dynamic(0x1E8E52, 0x3DBB78)
    static let goSoft = Color.dynamic(0xDDF2E6, 0x15301F)
    static let stop = Color.dynamic(0xC8233A, 0xF0566B)
    static let stopSoft = Color.dynamic(0xF8DEE2, 0x3A1A20)
    static let warn = Color.dynamic(0x8A5A00, 0xF5C02E)
    static let warnSoft = Color.dynamic(0xFDF0D2, 0x3A2E12)
    static let led = Color(hex: 0xFFB020)
    static let ledBg = Color.dynamic(0x16181C, 0x0B0C0E)
    static let glow = Color(hex: 0xE9F4FF)
    static let signBlue = Color(hex: 0x1A56B0)
}

func bundleImage(_ name: String) -> Image {
    if let ui = UIImage(named: name) { return Image(uiImage: ui) }
    return Image(systemName: "photo")
}
