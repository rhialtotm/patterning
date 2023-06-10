package ux

import actions.KeyFactory
import actions.MouseEventManager.Companion.instance
import actions.MovementHandler
import patterning.Bounds
import patterning.LifeUniverse
import patterning.Node
import patterning.Patterning
import processing.core.PApplet
import processing.core.PGraphics
import processing.core.PVector
import ux.informer.DrawingInfoSupplier
import ux.informer.DrawingInformer
import ux.panel.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.*
import java.util.function.IntSupplier
import kotlin.collections.ArrayDeque
import kotlin.math.roundToInt

class PatternDrawer(
    private val processing: PApplet,
    private val drawRateManager: DrawRateManager
) {
    private val cellBorderWidthRatio = .05f
    private val drawingInformer: DrawingInfoSupplier
    private val patterning: Patterning = processing as Patterning
    private val hudInfo: HUDStringBuilder
    private val movementHandler: MovementHandler

    // ain't no way to do drawing without a singleton drawables manager
    private val drawables = DrawableManager.getInstance()
    private val keyFactory: KeyFactory
    private var cellBorderWidth = 0.0f
    private var theme: UXThemeManager = UXThemeManager.getInstance()

    // lifeFormPosition is used because we now separate the drawing speed from the framerate
    // we may not draw an image every frame
    // if we haven't drawn an image, we still want to be able to move and drag
    // the image around so this allows us to keep track of the current position
    // for the lifeFormBuffer and move that buffer around regardless of whether we've drawn an image
    // whenever an image is drawn, ths PVector is reset to 0,0 to match the current image state
    // it's a nifty way to handle things - just follow lifeFormPosition through the code
    // to see what i'm talking about
    private var lifeFormPosition = PVector(0f, 0f)
    private var isDrawing = false
    private var cell: Cell
    private var countdownText: TextPanel? = null
    private var hudText: TextPanel? = null
    private var lifeFormBuffer: PGraphics
    private var uXBuffer: PGraphics
    private var drawBounds: Boolean
    private var canvasOffsetX = BigDecimal.ZERO
    private var canvasOffsetY = BigDecimal.ZERO

    // if we're going to be operating in BigDecimal then we keep these that way so
    // that calculations can be done without conversions until necessary
    private var canvasWidth: BigDecimal
    private var canvasHeight: BigDecimal

    // surprisingly caching the result of the half size calculation provides
    // a remarkable speed boost
    private val halfSizeMap: MutableMap<BigDecimal, BigDecimal> = HashMap()

    // used for resize detection
    private var prevWidth: Int
    private var prevHeight: Int

    init {
        uXBuffer = buffer
        lifeFormBuffer = buffer
        drawingInformer = DrawingInformer({ uXBuffer }, { isWindowResized }) { isDrawing }
        // resize trackers
        prevWidth = processing.width
        prevHeight = processing.height
        cell = Cell(DEFAULT_CELL_WIDTH.toFloat())
        canvasWidth = processing.width.toBigDecimal()
        canvasHeight = processing.height.toBigDecimal()
        movementHandler = MovementHandler(this)
        drawBounds = false
        hudInfo = HUDStringBuilder()

        createTextPanel(null) {
            TextPanel.Builder(drawingInformer, theme.startupText, AlignHorizontal.RIGHT, AlignVertical.TOP)
                .textSize(theme.startupTextSize)
                .fadeInDuration(theme.startupTextFadeInDuration)
                .fadeOutDuration(theme.startupTextFadeOutDuration)
                .displayDuration(theme.startupTextDisplayDuration.toLong())
        }

        keyFactory = KeyFactory(patterning, this)
        setupControls()
    }

    private val buffer: PGraphics
        get() = processing.createGraphics(processing.width, processing.height)

    private val isWindowResized: Boolean
        get() {
            val widthChanged = prevWidth != processing.width
            val heightChanged = prevHeight != processing.height
            return widthChanged || heightChanged
        }

    private fun setupControls() {

        // all callbacks have to invoke work - either on the Patterning or PatternDrawer
        // so give'em what they need
        keyFactory.setupKeyHandler()
        val panelLeft: ControlPanel
        val panelTop: ControlPanel
        val panelRight: ControlPanel
        val transitionDuration = UXThemeManager.getInstance().controlPanelTransitionDuration
        panelLeft = ControlPanel.Builder(drawingInformer, AlignHorizontal.LEFT, AlignVertical.CENTER)
            .transition(Transition.TransitionDirection.RIGHT, Transition.TransitionType.SLIDE, transitionDuration)
            .setOrientation(Orientation.VERTICAL)
            .addControl("zoomIn.png", keyFactory.callbackZoomInCenter)
            .addControl("zoomOut.png", keyFactory.callbackZoomOutCenter)
            .addControl("fitToScreen.png", keyFactory.callbackFitUniverseOnScreen)
            .addControl("center.png", keyFactory.callbackCenterView)
            .addControl("undo.png", keyFactory.callbackUndoMovement)
            .build()
        panelTop = ControlPanel.Builder(drawingInformer, AlignHorizontal.CENTER, AlignVertical.TOP)
            .transition(Transition.TransitionDirection.DOWN, Transition.TransitionType.SLIDE, transitionDuration)
            .setOrientation(Orientation.HORIZONTAL)
            .addControl("random.png", keyFactory.callbackRandomLife)
            .addControl("stepSlower.png", keyFactory.callbackStepSlower)
            .addControl("drawSlower.png", keyFactory.callbackDrawSlower)
            .addToggleIconControl("pause.png", "play.png", keyFactory.callbackPause, keyFactory.callbackSingleStep)
            .addControl("drawFaster.png", keyFactory.callbackDrawFaster)
            .addControl("stepFaster.png", keyFactory.callbackStepFaster)
            .addControl("rewind.png", keyFactory.callbackRewind)
            .build()
        panelRight = ControlPanel.Builder(drawingInformer, AlignHorizontal.RIGHT, AlignVertical.CENTER)
            .transition(Transition.TransitionDirection.LEFT, Transition.TransitionType.SLIDE, transitionDuration)
            .setOrientation(Orientation.VERTICAL)
            .addToggleHighlightControl("boundary.png", keyFactory.callbackDisplayBounds)
            .addToggleHighlightControl("darkmode.png", keyFactory.callbackThemeToggle)
            .addToggleHighlightControl("singleStep.png", keyFactory.callbackSingleStep)
            .build()
        val panels = listOf(panelLeft, panelTop, panelRight)
        Objects.requireNonNull(instance)?.addAll(panels)
        drawables.addAll(panels)
    }

    fun toggleDrawBounds() {
        drawBounds = !drawBounds
    }

    private fun calcCenterOnResize(dimension: BigDecimal, offset: BigDecimal): BigDecimal {
        return dimension.divide(BigTWO, mc) - offset
    }

    private fun createTextPanel(
        existingTextPanel: TextPanel?, // could be null
        builderFunction: () -> TextPanel.Builder
    ): TextPanel {
        existingTextPanel?.let {
            if (drawables.isManaging(it)) {
                drawables.remove(it)
            }
        }

        return builderFunction()
            .build()
            .also { newTextPanel ->
                drawables.add(newTextPanel)
            }
    }

    fun setupNewLife(life: LifeUniverse) {

        val bounds = life.rootBounds
        center(bounds, fitBounds = true, saveState = false)

        // todo: on maximum volatility gun, not clearing the previousStates when doing a setStep seems to cause it to freak out - see if that's causal
        // clear previous states ArrayDeque keeping track of movement and positioning and such
        clearUndoDeque()

        countdownText = createTextPanel(countdownText) {
            TextPanel.Builder(
                drawingInformer,
                theme.countdownText,
                AlignHorizontal.CENTER,
                AlignVertical.CENTER
            )
                .runMethod { patterning.run() }
                .fadeInDuration(2000)
                .countdownFrom(3)
                .textWidth(Optional.of(IntSupplier { canvasWidth.toInt() / 2 }))
                .wrap()
                .textSize(24)
        }

        hudText = createTextPanel(hudText) {
            TextPanel.Builder(drawingInformer, getHUDMessage(life), AlignHorizontal.RIGHT, AlignVertical.BOTTOM)
                .textSize(24)
                .textWidth(Optional.of(IntSupplier { canvasWidth.toInt() }))
        }
    }

    fun center(bounds: Bounds, fitBounds: Boolean, saveState: Boolean) {
        if (saveState) {
            saveUndoState()
        }

        // remember, bounds are inclusive - if you want the count of discrete items, then you need to add one back to it
        val patternWidth = BigDecimal(bounds.right - bounds.left + BigInteger.ONE)
        val patternHeight = BigDecimal(bounds.bottom - bounds.top + BigInteger.ONE)


        if (fitBounds) {
            // val widthRatio = if (patternWidth.compareTo(BigDecimal.ZERO) > 0) canvasWidth.divide(
            val widthRatio = if (patternWidth > BigDecimal.ZERO) canvasWidth.divide(
                patternWidth,
                mc
            ) else BigDecimal.ONE
            val heightRatio = if (patternHeight > BigDecimal.ZERO) canvasHeight.divide(
                patternHeight,
                mc
            ) else BigDecimal.ONE
            val newCellSize = if (widthRatio < heightRatio) widthRatio else heightRatio
            cell.width = (newCellSize.toFloat() * .9f)
        }
        val bigCell = cell.widthBigDecimal
        val drawingWidth = patternWidth.multiply(bigCell, mc)
        val drawingHeight = patternHeight.multiply(bigCell, mc)
        val halfCanvasWidth = canvasWidth.divide(BigTWO, mc)
        val halfCanvasHeight = canvasHeight.divide(BigTWO, mc)
        val halfDrawingWidth = drawingWidth.divide(BigTWO, mc)
        val halfDrawingHeight = drawingHeight.divide(BigTWO, mc)

        // Adjust offsetX and offsetY calculations to consider the bounds' topLeft corner
        val offsetX = halfCanvasWidth - halfDrawingWidth - bounds.leftToBigDecimal() * bigCell.negate()
        val offsetY = halfCanvasHeight - halfDrawingHeight - bounds.topToBigDecimal() * bigCell.negate()

        canvasOffsetX = offsetX
        canvasOffsetY = offsetY
    }

    fun clearUndoDeque() {
        undoDeque.clear()
    }

    private fun getHUDMessage(life: LifeUniverse): String {
        val patternInfo = life.patternInfo
        hudInfo.apply {
            addOrUpdate("fps", processing.frameRate.roundToInt())
            addOrUpdate("dps", drawRateManager.currentDrawRate.roundToInt())
            addOrUpdate("cell", getCellWidth())
            addOrUpdate("running", if (patterning.isRunning) "running" else "stopped")
            addOrUpdate("level ", patternInfo["level"])
            addOrUpdate("step", patternInfo["step"])
            addOrUpdate("generation", patternInfo["generation"])
            addOrUpdate("population", patternInfo["population"])
            addOrUpdate("maxLoad", patternInfo["maxLoad"])
            addOrUpdate("lastID", patternInfo["lastId"])
            addOrUpdate("width", patternInfo["width"])
            addOrUpdate("height", patternInfo["height"])
        }
        return hudInfo.getFormattedString(processing.frameCount, 12)
    }

    fun saveUndoState() {
        undoDeque.add(CanvasState(cell, canvasOffsetX, canvasOffsetY))
    }

    private fun getCellWidth(): Float {
        return cell.width
    }

    fun handlePause() {
        if (drawables.isManaging(countdownText)) {
            countdownText?.interruptCountdown()
            keyFactory.callbackPause.notifyKeyObservers()
        } else {
            patterning.toggleRun()
        }
    }

    private fun updateWindowResized() {

        val bigWidth = processing.width.toBigDecimal()
        val bigHeight = processing.height.toBigDecimal()

        // create new buffers
        uXBuffer = buffer
        lifeFormBuffer = buffer

        // Calculate the center of the visible portion before resizing
        val centerXBefore = calcCenterOnResize(canvasWidth, canvasOffsetX)
        val centerYBefore = calcCenterOnResize(canvasHeight, canvasOffsetY)

        // Update the canvas size
        canvasWidth = bigWidth
        canvasHeight = bigHeight

        // Calculate the center of the visible portion after resizing
        val centerXAfter = calcCenterOnResize(bigWidth, canvasOffsetX)
        val centerYAfter = calcCenterOnResize(bigHeight, canvasOffsetY)

        updateCanvasOffsets(centerXAfter - centerXBefore, centerYAfter - centerYBefore)
    }

    private fun fillSquare(x: Float, y: Float, size: Float) {
        val width = size - cellBorderWidth

        lifeFormBuffer.apply {
            fill(theme.cellColor)
            noStroke()
            rect(x, y, width, width)
        }

    }

    private fun drawNode(node: Node, size: BigDecimal, left: BigDecimal, top: BigDecimal) {
        if (node.population == BigInteger.ZERO) {
            return
        }

        val leftWithOffset = left + canvasOffsetX
        val topWithOffset = top + canvasOffsetY
        val leftWithOffsetAndSize = leftWithOffset + size
        val topWithOffsetAndSize = topWithOffset + size

        // no need to draw anything not visible on screen
        if (leftWithOffsetAndSize < BigDecimal.ZERO
            || topWithOffsetAndSize < BigDecimal.ZERO
            || leftWithOffset >= canvasWidth
            || topWithOffset >= canvasHeight
        ) {
            return
        }

        // if we have done a recursion down to a very small size and the population exists,
        // draw a unit square and be done
        if (size <= BigDecimal.ONE) {
            if (node.population > BigInteger.ZERO) {
                //fillSquare(Math.round(leftWithOffset.floatValue()), Math.round(topWithOffset.floatValue()),1);
                fillSquare(leftWithOffset.toInt().toFloat(), topWithOffset.toInt().toFloat(), 1f)
            }
        } else if (node.level == 0) {
            if (node.population == BigInteger.ONE) {
                // fillSquare(Math.round(leftWithOffset.floatValue()), Math.round(topWithOffset.floatValue()), cellWidth.get());
                fillSquare(leftWithOffset.toInt().toFloat(), topWithOffset.toInt().toFloat(), cell.width)
            }
        } else {
            val halfSize = getHalfSize(size)
            val leftHalfSize = left + halfSize
            val topHalfSize = top + halfSize
            drawNode(node.nw, halfSize, left, top)
            drawNode(node.ne, halfSize, leftHalfSize, top)
            drawNode(node.sw, halfSize, left, topHalfSize)
            drawNode(node.se, halfSize, leftHalfSize, topHalfSize)
        }
    }

    fun move(dx: Float, dy: Float) {
        saveUndoState()
        updateCanvasOffsets(dx.toBigDecimal(), dy.toBigDecimal())
        lifeFormPosition.add(dx, dy)
    }

    private fun updateCanvasOffsets(offsetX: BigDecimal, offsetY: BigDecimal) {
        canvasOffsetX += offsetX
        canvasOffsetY += offsetY
    }

    fun zoomXY(`in`: Boolean, x: Float, y: Float) {
        zoom(`in`, x, y)
    }

    private fun zoom(zoomIn: Boolean, x: Float, y: Float) {
        saveUndoState()
        val previousCellWidth = cell.width

        // Adjust cell width to align with grid
        cell.zoom(zoomIn)

        // Calculate zoom factor
        val zoomFactor = cell.width / previousCellWidth

        // Calculate the difference in canvas offset-s before and after zoom
        val offsetX = (1 - zoomFactor) * (x - canvasOffsetX.toFloat())
        val offsetY = (1 - zoomFactor) * (y - canvasOffsetY.toFloat())

        // Update canvas offsets
        updateCanvasOffsets(offsetX.toBigDecimal(), offsetY.toBigDecimal())
    }

    fun undoMovement() {
        if (undoDeque.isNotEmpty()) {
            val previous = undoDeque.removeLast()
            cell = previous.cell
            canvasOffsetX = previous.canvasOffsetX
            canvasOffsetY = previous.canvasOffsetY
        }
    }

    private fun drawBounds(life: LifeUniverse) {
        if (!drawBounds) return

        val bounds = life.rootBounds

        // use the bounds of the "living" section of the universe to determine
        // a visible boundary based on the current canvas offsets and cell size
        val screenBounds = bounds.getScreenBounds(cell.width, canvasOffsetX, canvasOffsetY)
        lifeFormBuffer.apply {
            pushStyle()
            noFill()
            stroke(200)
            strokeWeight(1f)
            rect(
                screenBounds.leftToFloat(), screenBounds.topToFloat(), screenBounds.rightToFloat(),
                screenBounds.bottomToFloat()
            )
            popStyle()
        }
    }

    fun draw(life: LifeUniverse, shouldDraw: Boolean) {

        // lambdas are interested in this fact
        isDrawing = true

        if (isWindowResized) {
            updateWindowResized()
        }

        prevWidth = processing.width
        prevHeight = processing.height

        processing.apply {
            background(theme.backGroundColor)
        }

        uXBuffer.apply {
            beginDraw()
            clear()
        }

        movementHandler.handleRequestedMovement()
        cellBorderWidth = cellBorderWidthRatio * cell.width

        // make this threadsafe
        val hudMessage = getHUDMessage(life)
        hudText?.setMessage(hudMessage)
        drawables.drawAll(uXBuffer)

        uXBuffer.endDraw()

        if (shouldDraw) {
            val node = life.root
            lifeFormBuffer.apply {
                beginDraw()
                clear()
            }

            val size = BigDecimal(LifeUniverse.pow2(node.level - 1), mc).multiply(cell.widthBigDecimal, mc)
            drawNode(node, size.multiply(BigTWO, mc), size.negate(), size.negate())
            drawBounds(life)

            lifeFormBuffer.endDraw()
            // reset the position in case you've had mouse moves
            lifeFormPosition[0f] = 0f
        }

        processing.apply {
            image(lifeFormBuffer, lifeFormPosition.x, lifeFormPosition.y)
            image(uXBuffer, 0f, 0f)
        }

        isDrawing = false
    }

    // the cell width times 2 ^ level will give you the size of the whole universe
    // you'll need it it to draw the viewport on screen
    private class Cell {

        var width: Float
            set(value) {
                field = if (value > CELL_WIDTH_ROUNDING_THRESHOLD) {
                    (value * CELL_WIDTH_ROUNDING_FACTOR).roundToInt() / CELL_WIDTH_ROUNDING_FACTOR
                } else {
                    value
                }
                widthBigDecimal = field.toBigDecimal()
            }

        var widthBigDecimal: BigDecimal = BigDecimal.ZERO
            private set

        constructor(width: Float) {
            this.width = width
        }

        fun zoom(zoomIn: Boolean) {
            val factor = if (zoomIn) 1.25f else 1 / 1.25f
            width *= factor
        }

        override fun toString() = "Cell{width=$width}"

        companion object {
            private const val CELL_WIDTH_ROUNDING_THRESHOLD = 1.6f
            private const val CELL_WIDTH_ROUNDING_FACTOR = 1.0f
        }
    }

    private class CanvasState(
        cell: Cell,
        val canvasOffsetX: BigDecimal,
        val canvasOffsetY: BigDecimal
    ) {
        val cell: Cell

        init {
            this.cell = Cell(cell.width)
        }
    }

    // re-using these really seems to make a difference
    private fun getHalfSize(size: BigDecimal): BigDecimal {
        return halfSizeMap.getOrPut(size) { size.divide(BigTWO, mc) }
    }

    companion object {
        // without this precision on the MathContext, small imprecision propagates at
        // large levels on the LifeUniverse - sometimes this will cause the image to jump around or completely
        // off the screen.  don't skimp on precision!
        private val mc = MathContext(100)
        private val BigTWO = BigDecimal(2)
        private val undoDeque = ArrayDeque<CanvasState>()
        private const val DEFAULT_CELL_WIDTH = 4
    }
}