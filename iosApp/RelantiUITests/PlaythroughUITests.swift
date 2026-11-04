import XCTest

/// Plays Relanti the way a learner does, from the shared fixtures
/// (`--uitest-state=`), and attaches a screenshot of every screen.
final class PlaythroughUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func launch(_ state: String, dark: Bool = false) {
        app.launchArguments = ["--uitest-state=\(state)"]
        if dark { app.launchArguments += ["-AppleInterfaceStyle", "Dark"] }
        app.launch()
    }

    private func shoot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "ios-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func tap(_ label: String, timeout: TimeInterval = 10) {
        let button = app.buttons[label].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: timeout), "missing button: \(label)")
        scrollTo(button)
        button.tap()
    }

    private func scrollTo(_ element: XCUIElement) {
        for _ in 0..<6 where !element.isHittable { app.swipeUp() }
    }

    private func see(_ text: String, timeout: TimeInterval = 10) -> Bool {
        app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", text)).firstMatch.waitForExistence(timeout: timeout)
    }

    private func answer(right: Bool) {
        let oracle = app.staticTexts["quiz.oracle"]
        XCTAssertTrue(oracle.waitForExistence(timeout: 10), "missing oracle")
        let correct = Int(oracle.label) ?? 0
        let pick = right ? correct : (correct + 1) % 3
        let option = app.buttons["option.\(pick)"]
        scrollTo(option)
        option.tap()
    }

    func testFirstLaunchToLaRuta() {
        launch("fresh")
        XCTAssertTrue(see("Te llevo hasta el examen"))
        shoot("01-subir-al-coche")
        tap("Arrancar")
        XCTAssertTrue(see("dice"))
        shoot("02-la-ruta-dia-1")
    }

    func testSecondGoodDayEarnsTheTollTicket() {
        launch("ticket-ready")
        tap("Arrancar")
        XCTAssertTrue(see("Truco de"))
        shoot("03-truco")
        tap("Entendido, ¡vamos!")
        shoot("04-pregunta")
        answer(right: false)
        XCTAssertTrue(see("Recalculando"))
        shoot("05-recalculando")
        tap("Continuar")
        for i in 0..<7 {
            answer(right: true)
            if i == 0 { shoot("06-acierto") }
            tap(i == 6 ? "Llegar" : "Continuar")
        }
        XCTAssertTrue(see("Ticket de peaje"))
        shoot("07-llegada-ticket")
        tap("Guardar en el parasol")
        app.tabBars.buttons["Guantera"].tap()
        XCTAssertTrue(see("AUTOPISTA RELANTI"))
        shoot("08-guantera")
    }

    func testMidweekScreens() {
        launch("midweek")
        XCTAssertTrue(see("dice"))
        shoot("10-la-ruta-midweek")
        tap("Panel de tramos")
        shoot("11-panel")
        tap("‹ Ruta")
        for (i, tab) in ["Examen", "Guantera", "Yo"].enumerated() {
            app.tabBars.buttons[tab].tap()
            shoot("\(12 + i)-\(tab.lowercased())")
        }
    }

    func testMidweekDark() {
        launch("midweek", dark: true)
        XCTAssertTrue(see("dice"))
        shoot("20-la-ruta-oscuro")
        tap("Panel de tramos")
        shoot("21-panel-oscuro")
    }
}
