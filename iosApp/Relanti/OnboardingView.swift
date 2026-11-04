import SwiftUI
import RelantiShared

/// 1 · Subir al coche: car, colour, names and an optional exam date. Also the Taller from Yo.
struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    let editing: Bool

    @State private var draft: SettingsDraft?
    @State private var hasDate = false
    @State private var date = Date()

    private let bodies: [CarBody] = [.utilitario, .familiar, .furgo]

    var body: some View {
        let d = draft ?? SettingsDraft(model.settings)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if editing {
                    HStack {
                        Text("Taller").font(.largeTitle.weight(.black)).foregroundStyle(Palette.ink)
                        Spacer()
                        Button("Cancelar") { model.screen = .home }
                    }
                } else {
                    HStack(alignment: .center, spacing: 10) {
                        GpsFace(mood: .idle).frame(width: 110)
                        Bubble(text: Lines.shared.hello(gps: d.gpsName.isEmpty ? "Turbo" : d.gpsName))
                    }
                }

                SectionTitle("Elige tu Relanti")
                HStack(spacing: 10) {
                    ForEach(bodies, id: \.self) { b in
                        let selected = b == d.carBody
                        Button {
                            update { $0.carBody = b }
                        } label: {
                            VStack(spacing: 4) {
                                bundleImage(carImageName(b)).resizable().scaledToFill()
                                    .aspectRatio(1, contentMode: .fit)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                Text(bodyName(b)).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.ink)
                            }
                            .padding(8)
                            .background(selected ? Palette.blueSoft : Palette.surface, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(selected ? Palette.blue : Palette.line, lineWidth: selected ? 2 : 1))
                        }
                        .buttonStyle(.plain)
                    }
                }

                SectionTitle("Color")
                HStack(spacing: 10) {
                    ForEach(CarColour.Companion.shared.all, id: \.id) { c in
                        Circle()
                            .fill(Color(hex: UInt32(truncatingIfNeeded: c.hex)))
                            .frame(width: 42, height: 42)
                            .overlay(Circle().stroke(c.id == d.carColour ? Palette.blue : Palette.line, lineWidth: c.id == d.carColour ? 3 : 1))
                            .onTapGesture { update { $0.carColour = c.id } }
                    }
                }
                Text(CarColour.Companion.shared.byId(id: d.carColour).name + (editing ? "" : " · empieza apagado y brilla con Pintura nueva"))
                    .font(.footnote).foregroundStyle(Palette.ink2)

                TextField("Nombre de tu coche (opcional)", text: binding(\.carName))
                    .textFieldStyle(.roundedBorder)

                SectionTitle("¿Cómo se llama tu GPS?")
                HStack(spacing: 8) {
                    ForEach(Rules.shared.gpsNameSuggestions, id: \.self) { n in
                        let selected = n == d.gpsName
                        Text(n)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(selected ? .white : Palette.ink)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(selected ? Palette.blue : Palette.surface, in: Capsule())
                            .overlay(Capsule().stroke(selected ? Palette.blue : Palette.line))
                            .onTapGesture { update { $0.gpsName = n } }
                    }
                }
                TextField("O escribe el tuyo", text: binding(\.gpsName))
                    .textFieldStyle(.roundedBorder)

                SectionTitle("Fecha del examen (opcional)")
                Toggle("Ya tengo fecha", isOn: $hasDate).tint(Palette.blue)
                if hasDate {
                    DatePicker("Examen", selection: $date, in: Date()..., displayedComponents: .date)
                        .environment(\.locale, Locale(identifier: "es_ES"))
                }

                Button(editing ? "Guardar" : "Arrancar") { done() }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.top, 6)
                if !editing {
                    Text("No hace falta cuenta. Todo se guarda en tu móvil.").font(.footnote).foregroundStyle(Palette.ink3)
                }
            }
            .padding(20)
        }
        .onAppear {
            if draft == nil {
                let s = SettingsDraft(model.settings)
                draft = s
                if s.examDay >= 0 {
                    hasDate = true
                    date = Date(timeIntervalSince1970: TimeInterval(s.examDay) * 86_400 + 12 * 3600)
                }
            }
        }
    }

    private func update(_ change: (inout SettingsDraft) -> Void) {
        var d = draft ?? SettingsDraft(model.settings)
        change(&d)
        draft = d
    }

    private func binding(_ key: WritableKeyPath<SettingsDraft, String>) -> Binding<String> {
        Binding(
            get: { (draft ?? SettingsDraft(model.settings))[keyPath: key] },
            set: { v in update { $0[keyPath: key] = String(v.prefix(24)) } }
        )
    }

    private func done() {
        var d = draft ?? SettingsDraft(model.settings)
        d.carName = d.carName.trimmingCharacters(in: .whitespaces)
        d.gpsName = d.gpsName.trimmingCharacters(in: .whitespaces)
        if d.gpsName.isEmpty { d.gpsName = "Turbo" }
        if hasDate {
            let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
            d.examDay = Days.shared.fromCivil(year: Int32(c.year!), month: Int32(c.month!), dayOfMonth: Int32(c.day!))
        } else {
            d.examDay = -1
        }
        let final = d
        model.save { _ in final }
        model.screen = .home
    }
}
