import SwiftUI
import RelantiShared

/// 6 · Llegada: km for effort, a clear result, and the toll ticket on the second good day.
struct LlegadaView: View {
    @Environment(AppModel.self) private var model
    let llegada: Llegada

    var body: some View {
        let l = llegada
        ScrollView {
            VStack(spacing: 14) {
                GpsFace(mood: l.good ? .happy : .idle).frame(width: 150)
                HStack(spacing: 24) {
                    Stat(value: "+\(l.km) km", label: "kilómetros", color: Palette.yellowInk)
                    Stat(value: "\(l.correct) de \(l.total)", label: "aciertos", color: l.good ? Palette.go : Palette.ink)
                }
                Text(title).font(.largeTitle.weight(.black)).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
                Text(l.line).foregroundStyle(Palette.ink2).multilineTextAlignment(.center)
                if l.kind == .tramo, l.good, !l.ticketEarned, l.goodDays < Rules.shared.ticketDays {
                    Text("Día \(l.goodDays) de \(Rules.shared.ticketDays) para el ticket de peaje")
                        .font(.footnote).foregroundStyle(Palette.ink3)
                }
                if l.ticketEarned, let t = l.tramo {
                    TollTicket(code: t.code, title: t.title, day: l.dayLabel, km: l.kmTotal)
                }
                ForEach(l.newPoints, id: \.self) { label in
                    Tile(color: Palette.warnSoft) {
                        Text("+1 punto del carnet · \(label)").font(.headline).foregroundStyle(Palette.ink)
                    }
                }
                if let u = l.upgrade {
                    Tile(color: Palette.blueSoft) {
                        VStack(alignment: .leading) {
                            Text("Mejora para tu Relanti: \(u.name)").font(.headline.weight(.black)).foregroundStyle(Palette.ink)
                            Text(u.detail).foregroundStyle(Palette.ink2)
                        }
                    }
                }
                let misses = l.review.filter { !$0.correct }
                if l.kind == .simulacro && !misses.isEmpty {
                    SectionTitle("Repaso de fallos")
                    ForEach(Array(misses.enumerated()), id: \.offset) { _, r in
                        Tile {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(r.item.tramo) · \(r.item.question)").font(.subheadline.weight(.semibold)).foregroundStyle(Palette.ink)
                                Text("Tu respuesta: \(r.choiceLabel)").font(.subheadline).foregroundStyle(Palette.stop)
                                Text("Correcta: \(r.answerLabel)").font(.subheadline).foregroundStyle(Palette.go)
                                if !r.item.explanation.isEmpty {
                                    Text(r.item.explanation).font(.subheadline).foregroundStyle(Palette.ink2)
                                }
                                if let src = r.item.source { Text(src.label).font(.caption).foregroundStyle(Palette.ink3) }
                            }
                        }
                    }
                }
                Button(l.ticketEarned ? "Guardar en el parasol" : "Continuar") { model.screen = .home }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.top, 4)
            }
            .padding(20)
        }
        .background(Palette.bg)
    }

    private var title: String {
        switch llegada.kind {
        case .simulacro: return llegada.good ? "¡Apto!" : "No apto esta vez"
        case .tramo: return llegada.good ? "¡Tramo superado!" : "Recalculando ruta"
        default: return "Retrovisor hecho"
        }
    }
}

/// Centro de examen: simulacros, the Semáforo and Puntos negros.
struct ExamenView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let s = model.snapshot
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Centro de examen").font(.largeTitle.weight(.black)).foregroundStyle(Palette.ink)
                Tile {
                    HStack(spacing: 14) {
                        SemaforoLight(level: s.semaforo.level, size: 20)
                        VStack(alignment: .leading) {
                            Text("Semáforo: \(s.semaforo.title)").font(.title3.weight(.black)).foregroundStyle(Palette.ink)
                            Text(s.semaforo.message).foregroundStyle(Palette.ink2)
                        }
                    }
                }
                Tile {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Simulacro").font(.title2.weight(.black)).foregroundStyle(Palette.ink)
                        Text("\(Rules.shared.simQuestions) preguntas · \(Rules.shared.simMinutes) minutos · máximo \(Rules.shared.simMaxMistakes) fallos. \(s.gpsName) va en silencio y repasa cada fallo al final.")
                            .foregroundStyle(Palette.ink2)
                        if Int32(s.pack.items.count) < Rules.shared.simQuestions {
                            Text("Con el paquete de muestra salen \(s.pack.items.count) preguntas.")
                                .font(.footnote).foregroundStyle(Palette.warn)
                        }
                        Button("Empezar simulacro") { model.open(model.core.startSimulacro()) }
                            .buttonStyle(PrimaryButtonStyle())
                            .disabled(!s.canStartSimulacro)
                        if !s.canStartSimulacro {
                            Text("Ya has hecho el simulacro gratis de hoy. Mañana hay otro, o abre la autopista.")
                                .font(.footnote).foregroundStyle(Palette.ink3)
                        }
                    }
                }
                if !s.puntosNegros.isEmpty {
                    Button("Practicar puntos negros (\(s.puntosNegros.count))") { model.open(model.core.startPuntosNegros()) }
                        .buttonStyle(SecondaryButtonStyle())
                }
                if !s.simulacros.isEmpty {
                    SectionTitle("Tus simulacros")
                    ForEach(Array(s.simulacros.reversed().enumerated()), id: \.offset) { _, r in
                        HStack {
                            Text(r.dayLabel).foregroundStyle(Palette.ink2).frame(width: 70, alignment: .leading)
                            Text("\(r.mistakes) \(r.mistakes == 1 ? "fallo" : "fallos") de \(r.total)").foregroundStyle(Palette.ink)
                            Spacer()
                            Text(r.passed ? "APTO" : "NO APTO").font(.headline.weight(.black)).foregroundStyle(r.passed ? Palette.go : Palette.stop)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.bg)
    }
}

/// 10 · Guantera: tickets on the sun visor, magnets on the fridge, trucos and puntos negros.
struct GuanteraView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let s = model.snapshot
        let trucos = s.etapas.flatMap { $0.tramos }
            .filter { $0.goodDays > 0 || $0.ticketed }
            .compactMap { s.pack.truco(id: $0.tramo.truco) }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .lastTextBaseline) {
                    Text("Guantera").font(.largeTitle.weight(.black)).foregroundStyle(Palette.ink)
                    Spacer()
                    Text("\(s.tickets.count) de \(s.totalTramos)").font(.headline).foregroundStyle(Palette.ink2)
                }
                SectionTitle("Parasol · tickets de peaje")
                if s.tickets.isEmpty {
                    Text("Supera un tramo en dos días distintos y su ticket aparece aquí.").foregroundStyle(Palette.ink3)
                }
                ForEach(Array(s.tickets.enumerated()), id: \.offset) { i, t in
                    TollTicket(code: t.tramo.code, title: t.tramo.title, day: t.dayLabel, km: t.kmAt)
                        .rotationEffect(.degrees(i % 2 == 0 ? -1 : 1))
                }
                SectionTitle("Nevera · imanes de etapa · \(s.magnets.count) de \(s.etapas.count)")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                    ForEach(s.etapas, id: \.etapa.code) { e in
                        Text(e.magnet ? (e.etapa.name.uppercased().split(separator: " ").last.map(String.init) ?? "") : "?")
                            .font(.system(size: e.magnet ? 11 : 22, weight: .black))
                            .foregroundStyle(e.magnet ? .white : Palette.ink3)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity, minHeight: 72)
                            .background(e.magnet ? Palette.sunset : Palette.surface, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(e.magnet ? Palette.sunset : Palette.line))
                    }
                }
                SectionTitle("Trucos de \(s.gpsName) · \(trucos.count)")
                if trucos.isEmpty {
                    Text("Cada tramo empieza con un truco. Los que veas se guardan aquí.").foregroundStyle(Palette.ink3)
                }
                ForEach(trucos, id: \.id) { t in
                    Tile {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(t.title).font(.headline).foregroundStyle(Palette.ink)
                            Text(t.body).font(.subheadline).foregroundStyle(Palette.ink2)
                            if let src = t.source { Text(src.label).font(.caption).foregroundStyle(Palette.ink3) }
                        }
                    }
                }
                SectionTitle("Puntos negros · \(s.puntosNegros.count)")
                if s.puntosNegros.isEmpty {
                    Text("Las preguntas que falles a menudo aparecerán aquí.").foregroundStyle(Palette.ink3)
                }
                ForEach(s.puntosNegros, id: \.item.id) { pn in
                    Tile(color: Palette.stopSoft) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pn.item.question).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.ink)
                            Text("Correcta: \(pn.item.letter(index: pn.item.answer)). \(pn.item.options[Int(pn.item.answer)])")
                                .font(.subheadline).foregroundStyle(Palette.go)
                            Text("Fallada \(pn.misses) veces").font(.caption).foregroundStyle(Palette.ink3)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.bg)
    }
}

/// 11 · Yo: your licence card, your car and the settings.
struct YoView: View {
    @Environment(AppModel.self) private var model
    @State private var confirmReset = false
    @State private var aprobado = false

    var body: some View {
        let s = model.snapshot
        let settings = model.settings
        let colour = CarColour.Companion.shared.byId(id: settings.carColour)
        let faded = s.upgradeIndex == 0
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .lastTextBaseline) {
                    Text("Yo").font(.largeTitle.weight(.black)).foregroundStyle(Palette.ink)
                    Spacer()
                    Text("Rango: \(s.rango.name)").font(.headline).foregroundStyle(Palette.ink2)
                }
                // The licence card.
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("PERMISO B · RELANTI").font(.headline.weight(.black)).foregroundStyle(Color(hex: 0x14438A))
                        Spacer()
                        Text("\(s.points) / \(Rules.shared.maxPoints)").font(.title2.weight(.black)).foregroundStyle(Color(hex: 0x1F232A))
                    }
                    HStack(spacing: 4) {
                        ForEach(0..<Int(Rules.shared.maxPoints), id: \.self) { i in
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Color(hex: 0x1A56B0).opacity(i < Int(s.points) ? 1 : 0.2))
                                .frame(height: 8)
                        }
                    }
                    Text("Cómo se ganan: 1 punto por etapa completa y 3 por hitos de simulacro. Aquí nunca se pierden, al revés que en la vida real.")
                        .font(.footnote).foregroundStyle(Color(hex: 0x535A66))
                }
                .padding(16)
                .background(Color(hex: 0xF4E6C8), in: RoundedRectangle(cornerRadius: 18))

                Tile {
                    HStack(spacing: 12) {
                        bundleImage(carImageName(settings.carBody)).resizable().scaledToFill()
                            .frame(width: 96, height: 96)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .opacity(faded ? 0.6 : 1)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(settings.carName.isEmpty ? "Tu Relanti" : settings.carName).font(.headline.weight(.black)).foregroundStyle(Palette.ink)
                            HStack(spacing: 6) {
                                Circle().fill(Color(hex: UInt32(truncatingIfNeeded: colour.hex)).opacity(faded ? 0.5 : 1)).frame(width: 12, height: 12)
                                Text("\(bodyName(settings.carBody)) · \(colour.name)").font(.subheadline).foregroundStyle(Palette.ink2)
                            }
                            Text("Estado: \(s.upgrade.name)").font(.subheadline).foregroundStyle(Palette.ink)
                            if let next = s.nextUpgrade {
                                Text("Próxima mejora a \(next.points) pts: \(next.name.lowercased())").font(.footnote).foregroundStyle(Palette.ink3)
                            }
                        }
                    }
                }
                HStack {
                    Stat(value: kmLabel(s.km), label: "km", color: Palette.yellowInk)
                    Stat(value: "\(s.streak)", label: "días al volante", color: Palette.sunset)
                    Stat(value: "\(s.tickets.count)", label: "tickets")
                }
                Button("Taller: color, nombre y carrocería") { model.screen = .taller }.buttonStyle(SecondaryButtonStyle())

                SectionTitle("Apariencia")
                Picker("Apariencia", selection: Binding(
                    get: { settings.theme },
                    set: { v in model.save { var d = $0; d.theme = v; return d } }
                )) {
                    Text("Sistema").tag("system")
                    Text("Claro").tag("light")
                    Text("Oscuro").tag("dark")
                }
                .pickerStyle(.segmented)

                SectionTitle("Pase de autopista (prueba)")
                Tile {
                    Toggle(isOn: Binding(
                        get: { settings.autopista },
                        set: { v in model.save { var d = $0; d.autopista = v; return d } }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Autopista abierta").font(.headline).foregroundStyle(Palette.ink)
                            Text("Mientras no hay pagos, este interruptor abre todas las etapas y los simulacros sin límite.")
                                .font(.footnote).foregroundStyle(Palette.ink2)
                        }
                    }
                    .tint(Palette.blue)
                }

                SectionTitle("¿Ya tienes la L?")
                Button(s.aprobado ? "¡Aprobado! 🎉" : "He aprobado el examen") {
                    model.core.markAprobado()
                    model.refresh()
                    aprobado = true
                }
                .buttonStyle(SecondaryButtonStyle())

                SectionTitle("Fuentes")
                Text([s.pack.attribution?.boe, s.pack.attribution?.consolidatedNotice,
                      "Paquete de contenido v\(s.pack.contentVersion)" + (s.placeholder ? " (muestra)" : "")]
                    .compactMap { $0 }.joined(separator: "\n"))
                    .font(.footnote).foregroundStyle(Palette.ink2)

                SectionTitle("Tus datos")
                ShareLink(item: model.core.exportJson(), preview: SharePreview("Mis datos de Relanti")) {
                    Text("Descargar mis datos (JSON)")
                }
                .buttonStyle(SecondaryButtonStyle())
                Button("Borrar todo", role: .destructive) { confirmReset = true }
                    .foregroundStyle(Palette.stop)
                    .padding(.vertical, 8)
            }
            .padding(16)
        }
        .background(Palette.bg)
        .confirmationDialog("¿Borrar todo?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) {
                model.core.resetAll()
                model.refresh()
                model.screen = .onboarding
            }
        } message: {
            Text("Se borran tu progreso, tu coche y tus ajustes de este móvil. No se puede deshacer.")
        }
        .alert("¡APROBADO!", isPresented: $aprobado) {
            Button("¡Vamos!", role: .cancel) {}
        } message: {
            Text(Lines.shared.aprobado)
        }
    }
}
