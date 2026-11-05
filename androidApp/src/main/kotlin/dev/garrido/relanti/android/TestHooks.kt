package dev.garrido.relanti.android

/**
 * Switches for UI tests and screenshots (Android extra `uitest_state`, or set
 * directly by Robolectric tests). They never change what a learner sees.
 */
object TestHooks {
    /** Shows the right answer's index in a hidden "quiz.oracle" node. */
    var oracle = false

    /** Draws the GPS without animation, so test clocks can go idle. */
    var still = false
}
