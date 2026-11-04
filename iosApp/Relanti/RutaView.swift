import SwiftUI
import RelantiShared

/// 2 · La Ruta: the windscreen, what's next, and the traffic light.
struct RutaView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let s = model.snapshot
        ScrollView {
            VStack(spacing: 12) {
                if s.placeholder { PlaceholderBanner() }
                HStack {
                    Text(s.streakBroken ? "Nueva ruta: día 1" : "\(s.streak) \(s.streak == 1 ? "día" : "días") al volante")
                        .font(.subheadline.weight(.bold)).foregroundStyle(Palette.sunset)
                    Spacer()
                    Text("\(kmLabel(s.km)) km").font(.subheadline.weight(.black)).foregroundStyle(Palette.yellowInk)
                }
                Windscreen(landscape: s.landscape, kmToExam: s.kmToExam)
                if s.daysToExam >= 0 {
                    Text("Examen el \(s.examDayLabel) · \(s.daysToExam == 0 ? "¡hoy!" : "faltan \(s.daysToExam) días")")
                        .font(.footnote).foregroundStyle(Palette.ink2)
                }

                Tile {
                    VStack(spacing: 10) {
                        HStack(spacing: 10) {
                            GpsFace(mood: .idle).frame(width: 64)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(s.gpsName) dice").font(.footnote.weight(.bold)).foregroundStyle(Palette.ink2)
                                Text(s.next.line).foregroundStyle(Palette.ink)
                            }
                            Spacer(minLength: 0)
                        }
                        Button("Arrancar") { arrancar(s) }.buttonStyle(PrimaryButtonStyle())
                    }
                }

                if !s.desvio.isEmpty {
                    Tile(color: Palette.blueSoft) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(s.desvioLine).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.ink)
                            ForEach(s.desvio, id: \.etapa.code) { e in
                                ExitSign(number: e.salida, name: e.etapa.name,
                                         detail: e.open ? e.etapa.topic : "Autopista de peaje", toll: !e.open) {
                                    if let t = e.tramos.first(where: { !$0.ticketed }) { model.startTramo(t.tramo.code) }
                                }
                            }
                        }
                    }
                }

                Tile {
                    HStack(spacing: 14) {
                        SemaforoLight(level: s.semaforo.level)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Semáforo de examen: \(s.semaforo.title)").font(.headline).foregroundStyle(Palette.ink)
                            Text(s.semaforo.message).font(.subheadline).foregroundStyle(Palette.ink2)
                        }
                    }
                }

                HStack(spacing: 10) {
                    Button("Panel de tramos") { model.screen = .panel }.buttonStyle(SecondaryButtonStyle())
                    Button("Retrovisor\(s.retrovisorDue > 0 ? " (\(s.retrovisorDue))" : "")") {
                        model.open(model.core.startRetrovisor())
                    }
                    .buttonStyle(SecondaryButtonStyle())
                    .disabled(s.retrovisorDue == 0)
                }
            }
            .padding(16)
        }
        .background(Palette.bg)
    }

    private func arrancar(_ s: Snapshot) {
        switch s.next.kind {
        case .tramo:
            if let t = s.next.tramo { model.startTramo(t.code) }
        case .retrovisor:
            model.open(model.core.startRetrovisor())
        default:
            if s.canStartSimulacro { model.open(model.core.startSimulacro()) }
            else { model.message = "Ya has hecho el simulacro gratis de hoy. Mañana hay otro." }
        }
    }
}

/// 3 · Panel: every tramo on an amber LED motorway panel, with an exit sign per etapa.
struct PanelView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let s = model.snapshot
        VStack(spacing: 0) {
            HStack {
                Button("‹ Ruta") { model.screen = .home }
                Text("Panel · \(s.etapas.count) etapas · \(s.totalTramos) tramos").font(.headline).foregroundStyle(Palette.ink)
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(s.etapas, id: \.etapa.code) { e in
                        VStack(spacing: 6) {
                            ExitSign(number: e.salida, name: e.etapa.name, detail: detail(e), toll: !e.open) {
                                if let t = e.tramos.first(where: { !$0.ticketed }) { model.startTramo(t.tramo.code) }
                            }
                            if !e.tramos.isEmpty {
                                VStack(spacing: 0) {
                                    ForEach(e.tramos, id: \.tramo.code) { t in
                                        Button { model.startTramo(t.tramo.code) } label: {
                                            HStack(spacing: 10) {
                                                Text(t.tramo.code).font(.system(.body, design: .monospaced).bold()).foregroundStyle(Palette.led)
                                                Text(t.tramo.title).font(.subheadline).foregroundStyle(Color(hex: 0xFFD27A))
                                                Spacer()
                                                Text(t.statusLabel).font(.system(size: 12, weight: .bold, design: .monospaced))
                                                    .foregroundStyle(statusColor(t.status))
                                            }
                                            .padding(.horizontal, 12).padding(.vertical, 9)
                                            .contentShape(Rectangle())
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(.vertical, 4)
                                .background(Palette.ledBg, in: RoundedRectangle(cornerRadius: 10))
                            }
                        }
                    }
                }
                .padding(16)
            }
        }
        .background(Palette.bg)
    }

    private func detail(_ e: EtapaView) -> String {
        if !e.open { return "Autopista de peaje · \(e.etapa.topic)" }
        if e.tramos.isEmpty { return "\(e.etapa.topic) · próximamente" }
        return "\(e.etapa.topic) · \(e.ticketed) de \(e.tramos.count)" + (e.magnet ? " · imán ✓" : "")
    }

    private func statusColor(_ s: TramoStatus) -> Color {
        switch s {
        case .llegado: return Color(hex: 0x7CE0A3)
        case .retencion: return Color(hex: 0xFF8A5C)
        case .enMarcha: return .white
        case .peaje: return Color(hex: 0x9AA0AA)
        default: return Palette.led
        }
    }
}
