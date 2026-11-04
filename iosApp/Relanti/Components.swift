import SwiftUI
import RelantiShared

func carImageName(_ body: CarBody) -> String {
    switch body {
    case .familiar: return "relanti-estate.jpg"
    case .furgo: return "relanti-van.jpg"
    default: return "relanti-city.jpg"
    }
}

func bodyName(_ body: CarBody) -> String {
    switch body {
    case .familiar: return "Familiar"
    case .furgo: return "Furgo"
    default: return "Utilitario"
    }
}

func kmLabel(_ km: Int32) -> String { kmLabel(Int64(km)) }
func kmLabel(_ km: Int64) -> String {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.locale = Locale(identifier: "es_ES")
    f.usesGroupingSeparator = true
    return f.string(from: NSNumber(value: km)) ?? "\(km)"
}

/// The GPS: a rendered device with eyes drawn on its screen, so they blink, squint and spin.
struct GpsFace: View {
    let mood: TurboMood
    var clock: String = ""
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            bundleImage("satnav-blank.jpg").resizable().scaledToFill()
            TimelineView(.animation(paused: reduceMotion || UITest.active)) { context in
                Canvas { ctx, size in
                    draw(ctx, size, t: context.date.timeIntervalSinceReferenceDate)
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityLabel("GPS")
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize, t: Double) {
        let w = size.width
        let cy = w * 0.45
        let left = CGPoint(x: w * 0.40, y: cy)
        let right = CGPoint(x: w * 0.62, y: cy)
        let ew = w * 0.075
        let eh = w * 0.14
        // Blink for 0.18 s every 4 s.
        let phase = t.truncatingRemainder(dividingBy: 4.0)
        let still = reduceMotion || UITest.active
        let blink: Double = still ? 1 : (phase < 0.18 ? max(0.08, abs(phase - 0.09) / 0.09) : 1)

        func eye(_ c: CGPoint, _ scaleY: Double, dx: Double = 0, dy: Double = 0, grow: Double = 1) {
            let h = eh * scaleY * grow
            let rect = CGRect(x: c.x - ew * grow / 2 + dx, y: c.y - h / 2 + dy, width: ew * grow, height: h)
            ctx.fill(Path(roundedRect: rect, cornerRadius: ew * grow / 2), with: .color(Palette.glow))
        }

        switch mood {
        case .curious:
            eye(left, blink, dx: w * 0.015, dy: -w * 0.015)
            eye(right, blink, dx: w * 0.015, dy: -w * 0.02, grow: 1.15)
        case .explain:
            let bob = still ? 0 : sin(t * 2.2) * w * 0.004
            eye(left, blink * 0.8, dx: -w * 0.02 + bob)
            eye(right, blink * 0.8, dx: -w * 0.02 + bob)
        case .happy:
            for c in [left, right] {
                var p = Path()
                p.addArc(center: CGPoint(x: c.x, y: c.y + ew * 0.3), radius: ew * 0.8,
                         startAngle: .degrees(200), endAngle: .degrees(340), clockwise: false)
                ctx.stroke(p, with: .color(Palette.glow), style: StrokeStyle(lineWidth: ew * 0.45, lineCap: .round))
            }
        case .recalculando:
            let r = w * 0.09
            let c = CGPoint(x: w * 0.51, y: cy)
            let start = still ? 45 : (t * 400).truncatingRemainder(dividingBy: 360)
            var p = Path()
            p.addArc(center: c, radius: r, startAngle: .degrees(start), endAngle: .degrees(start + 270), clockwise: false)
            ctx.stroke(p, with: .color(Palette.glow), style: StrokeStyle(lineWidth: w * 0.03, lineCap: .round))
        case .silent:
            ctx.draw(
                Text(clock).font(.system(size: w * 0.13, weight: .bold, design: .rounded)).foregroundColor(Palette.glow),
                at: CGPoint(x: w * 0.51, y: cy)
            )
        default:
            eye(left, blink)
            eye(right, blink)
        }
    }
}

/// La Ruta: the windscreen render for the etapa, the light of the hour and the km to the exam.
struct Windscreen: View {
    let landscape: String
    let kmToExam: Int32

    private static let spots: [String: (CGFloat, CGFloat, CGFloat, CGFloat)] = [
        "ciudad": (0.50, 0.355, 0.27, 0.06),
        "meseta": (0.495, 0.335, 0.34, 0.075),
        "costa": (0.50, 0.32, 0.38, 0.085),
        "montana": (0.50, 0.30, 0.42, 0.095),
    ]

    var body: some View {
        let key = Self.spots[landscape] == nil ? "ciudad" : landscape
        let spot = Self.spots[key]!
        GeometryReader { geo in
            let w = geo.size.width
            ZStack(alignment: .topLeading) {
                bundleImage("ruta-\(key).jpg").resizable().scaledToFill().frame(width: w, height: w)
                tint.frame(width: w, height: w)
                Text("EXAMEN \(kmToExam) km")
                    .font(.system(size: w * spot.3 * 0.5, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(width: w * spot.2, height: w * spot.3)
                    .background(Palette.signBlue, in: RoundedRectangle(cornerRadius: 3))
                    .overlay(RoundedRectangle(cornerRadius: 3).stroke(.white, lineWidth: 1))
                    .position(x: w * spot.0, y: w * spot.1)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var tint: Color {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 6...8: return Color(hex: 0xFFB27A).opacity(0.2)
        case 9...17: return .clear
        case 18...20: return Palette.sunset.opacity(0.25)
        default: return Color(hex: 0x0F2240).opacity(0.4)
        }
    }
}

struct Tile<Content: View>: View {
    var color: Color = Palette.surface
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(color, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.line, lineWidth: 1))
    }
}

struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased())
            .font(.footnote.weight(.bold))
            .tracking(1)
            .foregroundStyle(Palette.ink2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(Palette.blue.opacity(enabled ? (configuration.isPressed ? 0.8 : 1) : 0.4),
                        in: RoundedRectangle(cornerRadius: 14))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Palette.blueInk.opacity(enabled ? 1 : 0.4))
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.line, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// A blue motorway exit sign: "Salida 7 · Taller · Tipos de vehículo". Paid ones are grey.
struct ExitSign: View {
    let number: Int32
    let name: String
    let detail: String
    let toll: Bool
    let action: () -> Void

    var body: some View {
        let bg = toll ? Color(hex: 0x6B717C) : Palette.signBlue
        Button(action: action) {
            HStack(spacing: 12) {
                Text("\(number)")
                    .font(.system(size: 18, weight: .black))
                    .foregroundStyle(bg)
                    .frame(width: 34, height: 34)
                    .background(.white, in: RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: 1) {
                    Text(name).font(.headline).foregroundStyle(.white)
                    Text(detail).font(.footnote).foregroundStyle(.white.opacity(0.85))
                }
                Spacer()
                Text("↗").font(.title2.bold()).foregroundStyle(.white)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(bg, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(.white.opacity(0.85), lineWidth: 2))
        }
        .buttonStyle(.plain)
    }
}

struct SemaforoLight: View {
    let level: SemaforoLevel
    var size: CGFloat = 16

    var body: some View {
        VStack(spacing: 4) {
            light(.rojo, Color(hex: 0xE5484D))
            light(.ambar, Color(hex: 0xF5B92E))
            light(.verde, Color(hex: 0x3DBB78))
        }
        .padding(5)
        .background(Color(hex: 0x16181C), in: RoundedRectangle(cornerRadius: 8))
    }

    private func light(_ l: SemaforoLevel, _ c: Color) -> some View {
        Circle().fill(c.opacity(l == level ? 1 : 0.18)).frame(width: size, height: size)
    }
}

struct PlaceholderBanner: View {
    var body: some View {
        Text("Contenido de MUESTRA: preguntas de prueba, sin verificar con el BOE.")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Palette.warn)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Palette.warnSoft, in: RoundedRectangle(cornerRadius: 10))
    }
}

struct Bubble: View {
    let text: String
    var body: some View {
        Text(text)
            .foregroundStyle(Palette.ink)
            .padding(12)
            .background(Palette.blueSoft, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct Stat: View {
    let value: String
    let label: String
    var color: Color = Palette.ink
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.title2.weight(.black)).foregroundStyle(color)
            Text(label).font(.caption).foregroundStyle(Palette.ink2).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }
}

struct TollTicket: View {
    let code: String
    let title: String
    let day: String
    let km: Int32

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("AUTOPISTA RELANTI · TICKET")
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(Color(hex: 0x7A5600))
            Text("\(code) \(title)").font(.headline.weight(.black)).foregroundStyle(Color(hex: 0x1F232A))
            Text("\(day) · km \(kmLabel(km))")
                .font(.system(size: 13, design: .monospaced))
                .foregroundStyle(Color(hex: 0x535A66))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(hex: 0xFFF6D6), in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: 0xE2C46A), lineWidth: 1))
    }
}
