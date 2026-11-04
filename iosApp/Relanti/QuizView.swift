import SwiftUI
import RelantiShared

/// 4, 5, 8 and 9 · Truco, question, miss and simulacro, all in one question frame.
struct QuizView: View {
    @Environment(AppModel.self) private var model
    let session: QuizSession

    @State private var questionStart = Date()
    @State private var secondsLeft: Int = 0
    @State private var askExit = false
    @State private var finished = false

    var body: some View {
        let _ = model.tick // redraw after each call into the session
        VStack(spacing: 0) {
            HStack {
                Button { askExit = true } label: {
                    Image(systemName: "xmark").font(.title3).foregroundStyle(Palette.ink2)
                }
                Text(session.phase == .intro ? session.title : session.progressLabel)
                    .font(.headline).foregroundStyle(Palette.ink)
                Spacer()
                if session.isSimulacro {
                    Text(clock).font(.headline.monospacedDigit()).foregroundStyle(secondsLeft < 300 ? Palette.stop : Palette.ink2)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            ProgressView(value: session.total == 0 ? 0 : Double(session.answeredCount) / Double(session.total))
                .tint(Palette.blue)
                .padding(.horizontal, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    switch session.phase {
                    case .intro: intro
                    case .done: Text("…").foregroundStyle(Palette.ink2)
                    default: question
                    }
                }
                .padding(16)
            }
        }
        .background(Palette.bg)
        .onAppear {
            secondsLeft = Int(session.timeLimitSeconds)
            questionStart = Date()
        }
        .task(id: session.isSimulacro) {
            guard session.isSimulacro else { return }
            while secondsLeft > 0 && session.phase != .done {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                secondsLeft -= 1
            }
            if session.phase != .done {
                session.timeUp()
                bump()
            }
        }
        .confirmationDialog("¿Salir?", isPresented: $askExit, titleVisibility: .visible) {
            Button("Salir", role: .destructive) { finish() }
            Button("Seguir", role: .cancel) {}
        } message: {
            Text(session.answeredCount > 0
                 ? "Guardamos lo que llevas respondido. El tramo cuenta entero solo si lo terminas."
                 : "Aún no has respondido nada.")
        }
    }

    private var clock: String { String(format: "%d:%02d", secondsLeft / 60, secondsLeft % 60) }

    private func bump() {
        model.tick += 1
        if session.phase == .done { finish() }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        model.finish(session)
    }

    @ViewBuilder private var intro: some View {
        if let truco = session.truco {
            GpsFace(mood: .explain).frame(width: 170).frame(maxWidth: .infinity)
            Tile {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Truco de \(session.gpsName) · \(session.tramo?.code ?? "")")
                        .font(.footnote.weight(.bold)).foregroundStyle(Palette.ink2)
                    Text(truco.title).font(.title2.weight(.black)).foregroundStyle(Palette.ink)
                    Text(truco.body).foregroundStyle(Palette.ink)
                    if let src = truco.source { Text(src.label).font(.footnote).foregroundStyle(Palette.ink3) }
                }
            }
            Button("Entendido, ¡vamos!") {
                session.start()
                questionStart = Date()
                bump()
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    @ViewBuilder private var question: some View {
        let item = session.current
        let feedback = session.phase == .feedback
        HStack(alignment: .top, spacing: 10) {
            Text(item.question).font(.title3.weight(.semibold)).foregroundStyle(Palette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            // The GPS always sits in the corner, watching the road with you.
            GpsFace(mood: session.mood, clock: clock).frame(width: 76)
        }
        if item.novedad {
            Text("NOVEDAD").font(.caption.weight(.black)).foregroundStyle(Palette.sunset)
        }
        ForEach(Array(item.options.enumerated()), id: \.offset) { i, option in
            let isAnswer = Int32(i) == item.answer
            let isChoice = Int32(i) == session.lastChoice
            let bg: Color = feedback && isAnswer ? Palette.goSoft : (feedback && isChoice ? Palette.stopSoft : Palette.surface)
            let border: Color = feedback && isAnswer ? Palette.go : (feedback && isChoice ? Palette.stop : Palette.line)
            Button {
                session.answer(choice: Int32(i), ms: Int64(Date().timeIntervalSince(questionStart) * 1000))
                questionStart = Date()
                bump()
            } label: {
                HStack(spacing: 12) {
                    Text(item.letter(index: Int32(i)))
                        .font(.headline.weight(.black))
                        .foregroundStyle(feedback && isAnswer ? .white : Palette.blueInk)
                        .frame(width: 32, height: 32)
                        .background(feedback && isAnswer ? Palette.go : Palette.blueSoft, in: Circle())
                    Text(option).foregroundStyle(Palette.ink).multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    if feedback && isAnswer { Image(systemName: "checkmark").foregroundStyle(Palette.go) }
                    if feedback && isChoice && !isAnswer { Image(systemName: "xmark").foregroundStyle(Palette.stop) }
                }
                .padding(14)
                .background(bg, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(border, lineWidth: feedback && (isAnswer || isChoice) ? 2 : 1))
            }
            .buttonStyle(.plain)
            .disabled(feedback)
        }
        if feedback {
            Tile(color: session.lastCorrect ? Palette.goSoft : Palette.blueSoft) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.lastCorrect ? session.gpsLine : Lines.shared.recalculando)
                        .font(.headline.weight(.black))
                        .foregroundStyle(session.lastCorrect ? Palette.go : Palette.blueInk)
                    if !session.lastCorrect {
                        Text(session.gpsLine).foregroundStyle(Palette.ink)
                    } else if !item.explanation.isEmpty {
                        Text(item.explanation).foregroundStyle(Palette.ink)
                    }
                    if let src = item.source { Text(src.label).font(.footnote).foregroundStyle(Palette.ink3) }
                }
            }
            Button(session.index + 1 >= session.total ? "Llegar" : "Continuar") {
                session.next()
                questionStart = Date()
                bump()
            }
            .buttonStyle(PrimaryButtonStyle())
        } else if session.isSimulacro {
            Text("\(session.gpsName) va en silencio: como en el examen, la corrección llega al final.")
                .font(.footnote).foregroundStyle(Palette.ink3)
        }
    }
}
