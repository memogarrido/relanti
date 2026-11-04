import SwiftUI
import RelantiShared

@main
struct RelantiApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
    }
}

/// Keeps progress in one JSON file in Application Support.
final class FileStorage: ProgressStorage {
    private let url: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("progress.json")
    }

    func load() -> String? {
        try? String(contentsOf: url, encoding: .utf8)
    }

    func save(json: String) {
        try? json.write(to: url, atomically: true, encoding: .utf8)
    }
}

final class DeviceClock: PlatformClock {
    func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    func utcOffsetMinutes(atMillis: Int64) -> Int32 {
        let date = Date(timeIntervalSince1970: TimeInterval(atMillis) / 1000)
        return Int32(TimeZone.current.secondsFromGMT(for: date) / 60)
    }
}

/// Test launch arguments (a stable contract, like accessibility ids):
/// `--uitest-state=<fresh|onboarded|ticket-ready|midweek|autopista>` starts from a shared fixture.
enum UITest {
    static let state: String? = ProcessInfo.processInfo.arguments
        .first { $0.hasPrefix("--uitest-state=") }
        .map { String($0.dropFirst("--uitest-state=".count)) }
    static var active: Bool { state != nil }
}

enum Screen {
    case onboarding
    case taller
    case home
    case panel
    case quiz(QuizSession)
    case arrival(Llegada)
}

/// Holds the shared core and the latest snapshot. Views redraw when `snapshot` or `tick` change.
@Observable
final class AppModel {
    let core: RelantiCore
    var snapshot: Snapshot
    var screen: Screen
    var message: String?
    /// Bumped after every call into a session, which is plain Kotlin state.
    var tick = 0

    init() {
        let url = Bundle.main.url(forResource: "relanti-b", withExtension: "json")!
        let pack = try! String(contentsOf: url, encoding: .utf8)
        if let state = UITest.state {
            // UI tests: in-memory storage and a pinned clock, from the shared fixtures.
            core = Fixtures.shared.core(packJson: pack, state: state)
        } else {
            core = RelantiCore(packJson: pack, storage: FileStorage(), clock: DeviceClock())
        }
        snapshot = core.snapshot()
        screen = core.isOnboarded ? .home : .onboarding
    }

    var settings: Settings { snapshot.settings ?? core.settingsOrDefault() }

    func refresh() { snapshot = core.snapshot() }

    func open(_ session: QuizSession?) {
        if let session { screen = .quiz(session) } else { message = "No hay preguntas para eso todavía." }
    }

    func startTramo(_ code: String) {
        let view = snapshot.etapas.flatMap { $0.tramos }.first { $0.tramo.code == code }
        if let view, !view.open {
            message = "Autopista de peaje: esta etapa se abre con el pago único. Para probarla, activa el pase en Yo."
            return
        }
        open(core.startTramo(code: code))
    }

    func finish(_ session: QuizSession) {
        let llegada = core.finish(session: session)
        refresh()
        if let llegada { screen = .arrival(llegada) } else { screen = .home }
    }

    func save(_ change: (SettingsDraft) -> SettingsDraft) {
        let draft = change(SettingsDraft(settings))
        core.saveSettings(settings: draft.build())
        refresh()
    }
}

/// A Swift copy of Settings, since Kotlin's copy() with defaults isn't available from Swift.
struct SettingsDraft {
    var carBody: CarBody
    var carColour: String
    var carName: String
    var gpsName: String
    var examDay: Int64
    var voice: Bool
    var theme: String
    var autopista: Bool

    init(_ s: Settings) {
        carBody = s.carBody
        carColour = s.carColour
        carName = s.carName
        gpsName = s.gpsName
        examDay = s.examDay
        voice = s.voice
        theme = s.theme
        autopista = s.autopista
    }

    func build() -> Settings {
        Settings(carBody: carBody, carColour: carColour, carName: carName, gpsName: gpsName,
                 examDay: examDay, voice: voice, theme: theme, autopista: autopista, updatedAt: 0)
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        ZStack {
            Palette.bg.ignoresSafeArea()
            switch model.screen {
            case .onboarding:
                OnboardingView(editing: false)
            case .taller:
                OnboardingView(editing: true)
            case .home:
                MainTabs()
            case .panel:
                PanelView()
            case .quiz(let session):
                QuizView(session: session)
            case .arrival(let llegada):
                LlegadaView(llegada: llegada)
            }
        }
        .preferredColorScheme(scheme)
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("Entendido", role: .cancel) {}
        }
    }

    private var scheme: ColorScheme? {
        switch model.snapshot.settings?.theme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}

struct MainTabs: View {
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            RutaView().tabItem { Label("Ruta", systemImage: "road.lanes") }.tag(0)
            ExamenView().tabItem { Label("Examen", systemImage: "light.beacon.max") }.tag(1)
            GuanteraView().tabItem { Label("Guantera", systemImage: "ticket") }.tag(2)
            YoView().tabItem { Label("Yo", systemImage: "person.text.rectangle") }.tag(3)
        }
        .tint(Palette.blue)
    }
}
