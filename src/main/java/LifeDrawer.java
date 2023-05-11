import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PGraphics;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class LifeDrawer {

    private class CellWidth {
        private float cellWidth;
        private BigDecimal cellWidthBigDecimal;

        public CellWidth(float cellWidth) {
            set(cellWidth);
        }

        public float get() {
            return cellWidth;
        }

        public void set(float cellWidth) {
            this.cellWidth = cellWidth;
            this.cellWidthBigDecimal = BigDecimal.valueOf(cellWidth);
        }

        public BigDecimal getAsBigDecimal() {
            return cellWidthBigDecimal;
        }

        public String toString() {
            return "CellWidth{" + cellWidth + "}";
        }

    }

    private class CanvasState {
        private CellWidth cellWidth;
        private BigDecimal canvasOffsetX;
        private BigDecimal canvasOffsetY;
    
        public CanvasState(CellWidth cellWidth, BigDecimal canvasOffsetX, BigDecimal canvasOffsetY) {
            this.cellWidth = new CellWidth(cellWidth.get());
            this.canvasOffsetX = canvasOffsetX;
            this.canvasOffsetY = canvasOffsetY;
        }
    
        public CellWidth getCellWidth() {
            return cellWidth;
        }
    
        public BigDecimal getCanvasOffsetX() {
            return canvasOffsetX;
        }
    
        public BigDecimal getCanvasOffsetY() {
            return canvasOffsetY;
        }
    }
    

    private final PApplet p;
    private CanvasState previousState;

    private boolean debugging = false;
    private boolean useImageCache = false;

    // for all of the conversions, this needed to be a number larger than 5 for
    // there not to be rounding errors
    // which caused cells to appear outside the bounds
    // despite all of taht, i think it would be more simple to to change this code
    // to operate with all BigDecimals to avoid conversions
    private static final MathContext mc = new MathContext(10);
    private static BigDecimal BigTWO = new BigDecimal(2);

    // this class operates on BigDecimals because the bounds of a drawing can be
    // arbitrarily large

    // to avoid conversion and rounding issues, we're going to change everything to
    // operate in bigdecimal and only change it to float values when we need to
    // output to screen
    private BigDecimal canvasOffsetX = BigDecimal.ZERO;
    private BigDecimal canvasOffsetY = BigDecimal.ZERO;

    // if we're going to be operating in BigDecimal then we keep these that way so
    // that calculations can be done without conversions until necessary
    private BigDecimal canvasWidth;
    private BigDecimal canvasHeight;

    // todo: implement an actual borderwidth so you cdan see space between cells if
    // you wish
    int cellBorderWidth;
    int cellColor = 0;
    private CellWidth cellWidth;
    float cellBorderWidthRatio = 0;

    private ImageCache imageCache;

    LifeDrawer(PApplet p, float cellWidth) {
        this.p = p;
        this.cellWidth = new CellWidth(cellWidth);
        this.canvasWidth = BigDecimal.valueOf(p.width);
        this.canvasHeight = BigDecimal.valueOf(p.height);
        this.imageCache = new ImageCache(p);
        previousState = new CanvasState(this.cellWidth,canvasOffsetX, canvasOffsetY);
    }

    public float getCellWidth() {
        return cellWidth.get();
    }


    // you probably don't need this nonsense after all...
    public void clearCache() {
        this.imageCache.clearCache();
    }

    class ImageCache {
        private final PApplet pApplet;
        private final int cacheSize = 8000; // Set your desired cache size
        private final Map<Node, ImageCacheEntry> cache = new LRUCache<>(cacheSize);
        private boolean removeEldestMode = false;

        public ImageCache(PApplet pApplet) {
            this.pApplet = pApplet;

        }

        public void clearCache() {
            cache.clear();
            removeEldestMode = false;
        }

        private class ImageCacheEntry {
            private PImage image;

            private boolean cached;

            private long retrievalCount;

            private ImageCacheEntry nw, ne, sw, se;

            public ImageCacheEntry(PImage image) {
                this(image, null, null, null, null);
            }

            public ImageCacheEntry(PImage image, ImageCacheEntry nw, ImageCacheEntry ne, ImageCacheEntry sw,
                    ImageCacheEntry se) {
                this.image = image;
                this.retrievalCount = 0;
                this.cached = false;
                this.nw = nw;
                this.ne = ne;
                this.sw = sw;
                this.se = se;
            }

            public PImage getImage() {
                return image;
            }

            public boolean isCached() {
                return cached;
            }

            public void incrementRetrievalCount() {
                retrievalCount++;
                cached = true;
            }

            public long getRetrievalCount() {
                return retrievalCount;
            }

            public long getTotalRetrievalCount() {
                long total = retrievalCount;
                if (nw != null)
                    total += nw.getTotalRetrievalCount();
                if (ne != null)
                    total += ne.getTotalRetrievalCount();
                if (sw != null)
                    total += sw.getTotalRetrievalCount();
                if (se != null)
                    total += se.getTotalRetrievalCount();
                return total;
            }

            public int combinedNodeCount() {
                int count = 1; // Counting itself

                // Counting child nodes, if they exist
                if (nw != null) {
                    count += nw.combinedNodeCount();
                }
                if (ne != null) {
                    count += ne.combinedNodeCount();
                }
                if (sw != null) {
                    count += sw.combinedNodeCount();
                }
                if (se != null) {
                    count += se.combinedNodeCount();
                }

                return count;
            }

        }

        // todo: return from the cache an object that
        // if it was cached and
        // how many times this node representation- was retrieved from the cache
        //
        // LRUCache class
        private class LRUCache<K, V> extends LinkedHashMap<K, ImageCacheEntry> {
            private final int cacheSize;

            public LRUCache(int cacheSize) {
                super(cacheSize + 1, 1.0f, true); // Set accessOrder to true for LRU behavior
                this.cacheSize = cacheSize;
            }

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, ImageCacheEntry> eldest) {
                boolean removeEldest = size() > cacheSize;

                if (removeEldest) {
                    if (!removeEldestMode) {
                        System.out.println("Remove eldest mode at size(): " + size());
                        removeEldestMode = true;
                    }

                    ImageCacheEntry entry = (ImageCacheEntry) eldest.getValue();
                    PImage imageToDispose = entry.getImage();
                    ((PGraphics) imageToDispose).dispose();
                }

                return removeEldest;
            }

        }

        public ImageCacheEntry getImageCacheEntry(Node node) {

            if (cache.containsKey(node)) {
                ImageCacheEntry entry = cache.get(node);
                entry.incrementRetrievalCount();
                return entry;
            }

            if (node.level > 0) {
                // Retrieve or generate ImageCacheEntries for child nodes
                ImageCacheEntry nwEntry = getImageCacheEntry(node.nw);
                ImageCacheEntry neEntry = getImageCacheEntry(node.ne);
                ImageCacheEntry swEntry = getImageCacheEntry(node.sw);
                ImageCacheEntry seEntry = getImageCacheEntry(node.se);

                // Combine child images into a single image for the current node
                PImage combinedImage = combineChildImages(
                        nwEntry.getImage(), neEntry.getImage(),
                        swEntry.getImage(), seEntry.getImage());

                // Create a new ImageCacheEntry for the combined image
                ImageCacheEntry combinedEntry = new ImageCacheEntry(combinedImage, nwEntry, neEntry, swEntry, seEntry);
                cache.put(node, combinedEntry);
                return combinedEntry;

            } else { // leaf node entry - should only have to do this a few times
                boolean[][] binaryBitArray = node.getBinaryBitArray();
                PImage img = createBinaryBitArrayImage(binaryBitArray);
                ImageCacheEntry entry = new ImageCacheEntry(img);
                cache.put(node, entry);
                return entry;
            }
        }

        private PImage combineChildImages(PImage nwImage, PImage neImage, PImage swImage, PImage seImage) {
            int childSize = nwImage.width;
            int combinedSize = childSize * 2;

            PGraphics combinedImage = pApplet.createGraphics(combinedSize, combinedSize);
            combinedImage.beginDraw();

            combinedImage.image(nwImage, 0, 0);
            combinedImage.image(neImage, childSize, 0);
            combinedImage.image(swImage, 0, childSize);
            combinedImage.image(seImage, childSize, childSize);

            combinedImage.endDraw();
            return combinedImage;
        }

        private PImage createBinaryBitArrayImage(boolean[][] binaryBitArray) {
            int rows = binaryBitArray.length;
            int cols = rows; // it is squares here...

            PGraphics img = pApplet.createGraphics(rows, cols);

            img.beginDraw();
            img.background(255, 255, 255, 0); // Transparent background
            img.noStroke();
            img.fill(cellColor);

            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (binaryBitArray[y][x]) {
                        img.rect(x, y, 1, 1);
                    }
                }
            }

            img.endDraw();
            return img;
        }
    }

    private void fillSquare(PGraphics buffer, float x, float y, float size) {

        float width = size - cellBorderWidth;
        buffer.noStroke();
        // p.fill(cell_color);
        buffer.rect(x, y, width, width);

    }

    private BigDecimal calcCenterOnResize(BigDecimal dimension, BigDecimal offset) {
        return (dimension.divide(BigTWO)).subtract(offset);
    }

    // formerly known as setSize()
    // make sure that when you resize it tries to show you the contents of the last
    // screen size
    // without updating the cellsize - by getting the center before and then
    // centering
    // around the center after
    public void surfaceResized(int width, int height) {

        BigDecimal bigWidth = BigDecimal.valueOf(width);
        BigDecimal bigHeight = BigDecimal.valueOf(height);

        if (bigWidth != canvasWidth || bigHeight != canvasHeight) {

            // Calculate the center of the visible portion before resizing
            BigDecimal centerXBefore = calcCenterOnResize(canvasWidth, canvasOffsetX);
            BigDecimal centerYBefore = calcCenterOnResize(canvasHeight, canvasOffsetY);

            // Update the canvas size
            canvasWidth = bigWidth;
            canvasHeight = bigHeight;

            // Calculate the center of the visible portion after resizing
            BigDecimal centerXAfter = calcCenterOnResize(bigWidth, canvasOffsetX);
            BigDecimal centerYAfter = calcCenterOnResize(bigHeight, canvasOffsetY);

            // Calculate the difference in the visible portion's center
            BigDecimal offsetX = centerXAfter.subtract(centerXBefore);
            BigDecimal offsetY = centerYAfter.subtract(centerYBefore);

            updateCanvasOffsets(offsetX, offsetY);
        }

    }

    public void move(float dx, float dy) {
        updateCanvasOffsets(BigDecimal.valueOf(dx), BigDecimal.valueOf(dy));
    }

    public void zoom(boolean in, float x, float y) {

        float previousCellWidth = cellWidth.get();

        // Adjust cell width to align with grid
        if (in) {
            cellWidth.set(cellWidth.get() * 2f);
        } else {
            cellWidth.set(cellWidth.get() / 2f);
        }

        // Apply rounding conditionally based on a threshold
        // if it's larger than a pixel then we may see unintended gaps between cells
        // so round them if they're over the 1 pixel threshold
        float threshold = 1.0f;
        if (cellWidth.get() >= threshold) {
            cellWidth.set(Math.round(cellWidth.get()));
        }

        // Calculate zoom factor
        float zoomFactor = (float) cellWidth.get() / previousCellWidth;

        // Calculate the difference in canvas offset-s before and after zoom
        float offsetX = (1 - zoomFactor) * (x - canvasOffsetX.floatValue());
        float offsetY = (1 - zoomFactor) * (y - canvasOffsetY.floatValue());

        // Update canvas offsets
        updateCanvasOffsets(BigDecimal.valueOf(offsetX), BigDecimal.valueOf(offsetY));

    }

    private void updateCanvasOffsets(BigDecimal offsetX, BigDecimal offsetY) {
        canvasOffsetX = canvasOffsetX.add(offsetX);
        canvasOffsetY = canvasOffsetY.add(offsetY);
    }

    void zoomXY(boolean in, float mouse_x, float mouse_y) {
        zoom(in, mouse_x, mouse_y);
    }

    /* 
        this will only undo one step. If you want to be able to undo multiple steps,
         you would need to store all previous states, not just the last one. 
         You could use a Stack<CanvasState> for this purpose.
     */
    public void undoCenter() {
        cellWidth = previousState.getCellWidth();
        canvasOffsetX = previousState.getCanvasOffsetX();
        canvasOffsetY = previousState.getCanvasOffsetY();
    }

    public void center(Bounds bounds, boolean fitBounds) {

        previousState = new CanvasState(cellWidth, canvasOffsetX, canvasOffsetY);

        BigDecimal patternWidth = new BigDecimal(bounds.right.subtract(bounds.left));
        BigDecimal patternHeight = new BigDecimal(bounds.bottom.subtract(bounds.top));

        if (fitBounds) {

            BigDecimal widthRatio = (patternWidth.compareTo(BigDecimal.ZERO) > 0) ? canvasWidth.divide(patternWidth, mc)
                    : BigDecimal.ONE;
            BigDecimal heightRatio = (patternHeight.compareTo(BigDecimal.ZERO) > 0)
                    ? canvasHeight.divide(patternHeight, mc)
                    : BigDecimal.ONE;

            BigDecimal newCellSize = (widthRatio.compareTo(heightRatio) < 0) ? widthRatio : heightRatio;

            cellWidth.set(newCellSize.floatValue() * .9F);

        }

        BigDecimal drawingWidth = patternWidth.multiply(cellWidth.getAsBigDecimal());
        BigDecimal drawingHeight = patternHeight.multiply(cellWidth.getAsBigDecimal());

        BigDecimal halfCanvasWidth = canvasWidth.divide(BigTWO, mc);
        BigDecimal halfCanvasHeight = canvasHeight.divide(BigTWO, mc);

        BigDecimal halfDrawingWidth = drawingWidth.divide(BigTWO, mc);
        BigDecimal halfDrawingHeight = drawingHeight.divide(BigTWO, mc);

        // Adjust offsetX and offsetY calculations to consider the bounds' topLeft corner
        BigDecimal offsetX = halfCanvasWidth.subtract(halfDrawingWidth)
                .add(new BigDecimal(bounds.left).multiply(cellWidth.getAsBigDecimal()).negate());
        BigDecimal offsetY = halfCanvasHeight.subtract(halfDrawingHeight)
                .add(new BigDecimal(bounds.top).multiply(cellWidth.getAsBigDecimal()).negate());

        canvasOffsetX = offsetX;
        canvasOffsetY = offsetY; 
    }

    public void drawBounds(Bounds bounds, PGraphics offscreenBuffer) {

        Bounds screenBounds = bounds.getScreenBounds(cellWidth.get(), canvasOffsetX, canvasOffsetY);

        offscreenBuffer.noFill();
        offscreenBuffer.stroke(200);
        offscreenBuffer.strokeWeight(1);
        offscreenBuffer.rect(screenBounds.leftToFloat(), screenBounds.topToFloat(), screenBounds.rightToFloat(),
                screenBounds.bottomToFloat());
    }

    // the cell width times 2 ^ level will give you the size of the whole universe
    // draws the screensize viewport on the universe
    public void redraw(Node node, PGraphics offscreenBuffer) {
        cellBorderWidth = (int) (cellBorderWidthRatio * cellWidth.get());

        BigDecimal size = new BigDecimal(LifeUniverse.pow2(node.level - 1), mc).multiply(cellWidth.getAsBigDecimal(), mc);

        DrawNodeContext ctx = new DrawNodeContext(offscreenBuffer);
        drawNode(node, size.multiply(BigTWO, mc), size.negate(), size.negate(), ctx);

    }

    private class DrawNodeContext {

        public final BigDecimal canvasWidthDecimal;
        public final BigDecimal canvasHeightDecimal;
        public final BigDecimal canvasOffsetXDecimal;
        public final BigDecimal canvasOffsetYDecimal;

        private final Map<BigDecimal, BigDecimal> halfSizeMap = new HashMap<>();

        PGraphics buffer;

        public DrawNodeContext(PGraphics offscreenBuffer) {
            this.canvasWidthDecimal = canvasWidth;
            this.canvasHeightDecimal = canvasHeight;
            this.canvasOffsetXDecimal = canvasOffsetX; // new BigDecimal(canvasOffsetX);
            this.canvasOffsetYDecimal = canvasOffsetY; // new BigDecimal(canvasOffsetY);
            this.buffer = offscreenBuffer;
        }

        public BigDecimal getHalfSize(BigDecimal size) {
            if (!halfSizeMap.containsKey(size)) {
                BigDecimal halfSize = size.divide(BigTWO, mc);
                halfSizeMap.put(size, halfSize);
            }
            return halfSizeMap.get(size);
        }

    }

    private void drawNode(Node node, BigDecimal size, BigDecimal left, BigDecimal top, DrawNodeContext ctx) {

        if (node.population.equals(BigInteger.ZERO)) {
            return;
        }

        BigDecimal leftWithOffset = left.add(ctx.canvasOffsetXDecimal);
        BigDecimal topWithOffset = top.add(ctx.canvasOffsetYDecimal);
        BigDecimal leftWithOffsetAndSize = leftWithOffset.add(size);
        BigDecimal topWithOffsetAndSize = topWithOffset.add(size);

        // no need to draw anything not visible on screen
        if (leftWithOffsetAndSize.compareTo(BigDecimal.ZERO) < 0
                || topWithOffsetAndSize.compareTo(BigDecimal.ZERO) < 0
                || leftWithOffset.compareTo(ctx.canvasWidthDecimal) >= 0
                || topWithOffset.compareTo(ctx.canvasHeightDecimal) >= 0) {
            return;
        }

        // if we've recursed down to a very small size and the population exists,
        // draw a unit square and be done
        if (size.compareTo(BigDecimal.ONE) <= 0) {
            if (node.population.compareTo(BigInteger.ZERO) > 0) {
                ctx.buffer.fill(cellColor);
                fillSquare(ctx.buffer, Math.round(leftWithOffset.floatValue()), Math.round(topWithOffset.floatValue()),
                        1);
            }
        } else if (node.level == 0) {
            if (node.population.equals(BigInteger.ONE)) {
                ctx.buffer.fill(cellColor);
                fillSquare(ctx.buffer, Math.round(leftWithOffset.floatValue()), Math.round(topWithOffset.floatValue()),
                        cellWidth.get());
            }
        } else {

            BigDecimal powResult = BigDecimal.valueOf(2).pow(node.level).multiply(cellWidth.getAsBigDecimal());
            BigDecimal minCanvasDimension = canvasWidth.min(canvasHeight);
            boolean fitsOnScreen = (powResult.compareTo(minCanvasDimension) <= 0);

            if (fitsOnScreen && useImageCache && !node.hasChanged() && node.level <= 4 // not new nodes
                    && fitsOnScreen
                    && (cellWidth.get() > Math.pow(2, -3)) // will work when cell_widths are small
            // && (node.level < 8 )// todo: just as an insurance policy for now...
            ) {

                ImageCache.ImageCacheEntry entry = imageCache.getImageCacheEntry(node);
                PImage cachedImage = entry.getImage();

                ctx.buffer.image(cachedImage,
                        Math.round(leftWithOffset.floatValue()),
                        Math.round(topWithOffset.floatValue()),
                        Math.round(cachedImage.width * cellWidth.get()),
                        Math.round(cachedImage.width * cellWidth.get()));

                if (debugging) {

                    if ((cellWidth.get() * Math.pow(2, node.level)) > 16) {

                        p.fill(0, 125); // Black color with alpha value of 178

                        // Draw text with 70% opacity
                        p.textSize(16);
                        p.textAlign(PConstants.LEFT, PConstants.TOP);
                        String cacheString = (entry.isCached()) ? "cache" : "nocache";

                        p.text("id: " + node.id + " node.level:" + node.level + " - " + cacheString,
                                Math.round(leftWithOffset.floatValue()), Math.round(topWithOffset.floatValue()));
                        p.text("retrievals: " + entry.getRetrievalCount(), Math.round(leftWithOffset.floatValue()),
                                Math.round(topWithOffset.floatValue()) + 20);
                        p.text("nodes: " + entry.combinedNodeCount(), Math.round(leftWithOffset.floatValue()),
                                Math.round(topWithOffset.floatValue()) + 40);
                        p.text("total: " + entry.getTotalRetrievalCount(), Math.round(leftWithOffset.floatValue()),
                                Math.round(topWithOffset.floatValue()) + 60);

                    }

                    p.fill(0, 5); // Black color with alpha value of 25
                    p.stroke(1);

                    p.rect(Math.round(leftWithOffset.floatValue()),
                            Math.round(topWithOffset.floatValue()),
                            Math.round(cachedImage.width * cellWidth.get()),
                            Math.round(cachedImage.width * cellWidth.get()));
                }

            } else {

                BigDecimal halfSize = ctx.getHalfSize(size);
                BigDecimal leftHalfSize = left.add(halfSize);
                BigDecimal topHalfSize = top.add(halfSize);

                drawNode(node.nw, halfSize, left, top, ctx);
                drawNode(node.ne, halfSize, leftHalfSize, top, ctx);
                drawNode(node.sw, halfSize, left, topHalfSize, ctx);
                drawNode(node.se, halfSize, leftHalfSize, topHalfSize, ctx);
            }
        }
    }

    /*
     * void draw_cell(int x, int y, boolean set) {
     * // todo: something is happening when you get to a step size at 1024
     * // you can go that large and because you are using a math context to do the
     * division
     * // everything seems to work but at step size of 1024, the drawing starts to
     * go wonky
     * // so can you... maybe keep everything in BigDecimal until you convert it
     * somehow?
     * // the initial size passed into draw_cell is the largest possible size of the
     * drawing
     * // based on the level - but that's so large it can't possibly matter. is
     * there a way
     * // to just keep track of the part of the drawing that is on screen and ask
     * // the lifeUniverse to only give you that much of it without having to use
     * all this recursion?
     * // seems inefficient
     * BigDecimal biCellWidth = new BigDecimal(getCell_width());
     * BigDecimal biX = new BigDecimal(x).multiply(biCellWidth).add(new
     * BigDecimal(canvas_offset_x));
     * BigDecimal biY = new BigDecimal(y).multiply(biCellWidth).add(new
     * BigDecimal(canvas_offset_y));
     * float width = (float) Math.ceil(getCell_width()) - (int) (getCell_width() *
     * border_width_ratio);
     * 
     * // todo: don't forget to use offscreenBuffer
     * if (set) {
     * p.fill(cell_color);
     * } else {
     * p.fill(background_color);
     * }
     * 
     * p.noStroke();
     * p.rect(biX.floatValue(), biY.floatValue(), width, width);
     * }
     */
}