package patterning.ux

open class UXConstants internal constructor() {
    // sizes
    val controlSize: Int
        get() = 35
    val controlHighlightCornerRadius: Int
        get() = 10
    val defaultTextMargin: Int
        get() = 5
    val defaultTextSize: Float
        get() = 30f
    val hoverTextMargin: Int
        get() = 5
    val hoverTextMaxWidth: Int
        get() = 225
    val hoverTextSize: Int
        get() = 14
    val iconMargin: Int
        get() = 5
    val startupTextSize: Int
        get() = 50

    // colors
    open val backgroundColor: UInt
        get() = 0xFFu
    open val cellColor: UInt
        get() = 0xFF000000u
    open val textColorStart: UInt
        get() = 0xFFFFFFFFu
    open val textColor: UInt
        get() = 0xFF000000u

    val controlColor: UInt
        get() = 0xC8404040u // partially transparent 40
    val controlHighlightColor: UInt
        get() = 0xC8909090u // partially transparent 60
    val controlMousePressedColor: UInt
        get() = 0xC8E1E1E1u // partially transparent 225
    val defaultPanelColor: UInt
        get() = 0x00FFFFFFu

    // durations
    val controlHighlightDuration: Int
        get() = 1000
    val controlPanelTransitionDuration: Int
        get() = 1500
    val shortTransitionDuration: Int
        get() = 300
    val singleModeToggleDuration: Int
        get() = 200
    val startupTextDisplayDuration: Int
        get() = 5000
    val startupTextFadeInDuration: Int
        get() = 2000
    val startupTextFadeOutDuration: Int
        get() = 2000
    val themeTransitionDuration: Int
        get() = 500
    val countdownText: String
        // names
        get() = "press space to begin immediately"
    val fontName: String
        get() = "Verdana"
    val iconPath: String
        get() = "icon/"
    val shortcutParenStart: String
        get() = " ("
    val shortcutParenEnd: String
        get() = ")"
    val startupText: String
        get() = "patterning"
}