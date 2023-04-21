import processing.core.PApplet;

import java.util.HashSet;
import java.util.Set;

public class KeyHandler {
    private PApplet p;
    private LifeUniverse life;
    private LifeDrawer drawer;
    private Set<Integer> pressedKeys;
    private int lastDirection = 0;
    private long lastIncreaseTime;
    private float initialMoveAmount = 1;
    private float moveAmount = initialMoveAmount;
    private long increaseInterval = 500;

    private boolean displayBounds = false;


    public KeyHandler(PApplet p, LifeUniverse life, LifeDrawer drawer) {
        this.p = p;
        this.drawer = drawer;
        this.life = life;
        this.pressedKeys = new HashSet<Integer>();
        this.lastIncreaseTime = System.currentTimeMillis();
    }

    public void handleKeyPressed() {
        int keyCode = p.keyCode;
        pressedKeys.add(keyCode);

        // zoom, fit_bounds, center_view, etc. - they don't actually draw
        // they just put the drawer into a state that on the next redraw
        // the right thing will happen
        // that's why it's cool for them to be invoked on keypress

        switch (p.key) {
            case '+', '=' -> zoom(false);
            case '-' -> zoom(true);
            case 'B', 'b' -> displayBounds = !displayBounds;
            case 'C', 'c' -> drawer.center_view();
            case 'F', 'f' -> drawer.fit_bounds(life.getRootBounds());
            default -> {
                // System.out.println("key: " + key + " keycode: " + keyCode);

            }
        }

        handleMovementKeys();
    }

    private void zoom(boolean out) {
        Bounds bounds = life.getRootBounds();
        drawer.zoom_bounds(out, bounds);
    }

    public void handleKeyReleased() {
        int keyCode = p.keyCode;
        pressedKeys.remove(keyCode);

        handleMovementKeys();
    }

    private void handleMovementKeys() {

        float moveX = 0;
        float moveY = 0;

        int[][] directions = {{p.LEFT, 0, -1}, {p.UP, -1, 0}, {p.RIGHT, 0, 1}, {p.DOWN, 1, 0}, {p.UP + p.LEFT, -1, -1}, {p.UP + p.RIGHT, -1, 1}, {p.DOWN + p.LEFT, 1, -1}, {p.DOWN + p.RIGHT, 1, 1}};

        for (int[] direction : directions) {
            boolean isMoving = pressedKeys.contains(direction[0]);
            if (isMoving) {
                moveX += direction[2] * moveAmount;
                moveY += direction[1] * moveAmount;
            }
        }

        // Check if the direction has changed
        int currentDirection = 0;
        for (int[] direction : directions) {
            boolean isMoving = pressedKeys.contains(direction[0]);
            if (isMoving) {
                currentDirection += direction[0];
            }
        }

        if (currentDirection != lastDirection) {
            // Reset moveAmount if direction has changed
            moveAmount = initialMoveAmount;
            lastIncreaseTime = System.currentTimeMillis();
        } else {
            // Increase moveAmount if enough time has passed since last increase
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastIncreaseTime >= increaseInterval) {
                moveAmount += 0.5f;
                lastIncreaseTime = currentTime;
            }
        }

        drawer.move(moveX, moveY);
        lastDirection = currentDirection;
    }

    // update is invoked by the draw of the main sketch
    // so that it can delegate responsibility to dealing with
    // draw functions related to the keyboard driven interface of this thing
    // if we ever create buttons for the user experience
    // we may have to refactor this so the drawing operations are separated out to be invoked
    // either by user interface elements or by key presses that are bound to the user interface elements
    public void update() {
        for (int i = 0; i < 2; i++) {
            handleMovementKeys();
        }

        if (displayBounds)
            drawer.draw_bounds(life.getRootBounds());
    }
}
