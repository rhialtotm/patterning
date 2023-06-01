package ux.panel;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PImage;
import ux.UXThemeManager;
import ux.informer.DrawingInfoSupplier;

public class Transition {

    private final int duration;
    private final TransitionDirection direction;
    private final TransitionType type;
    private final DrawingInfoSupplier drawingInformer;
    private long transitionStartTime = -1;

    private boolean isTransitioning = true;

    public Transition(DrawingInfoSupplier drawingInformer, TransitionDirection direction, TransitionType type) {
        this(drawingInformer, direction, type, UXThemeManager.getInstance().getShortTransitionDuration());
    }

    public Transition(DrawingInfoSupplier drawingInformer, TransitionDirection direction, TransitionType type, int duration) {
        this.drawingInformer = drawingInformer;
        this.direction = direction;
        this.type = type;
        this.duration = duration;
    }


/*    public void reset() {
        transitionStartTime = -1;
    }*/

    public boolean isTransitioning() {
        return isTransitioning;
    }

    public void image(PGraphics transitionBuffer, float x, float y) {
        if (transitionStartTime == -1) {
            transitionStartTime = System.currentTimeMillis();
        }

        PGraphics UXBuffer = drawingInformer.getPGraphics();

        long elapsed = System.currentTimeMillis() - transitionStartTime;
        float transitionProgress = PApplet.constrain((float) elapsed / duration, 0, 1);

        switch (type) {
            case EXPANDO -> drawExpandoTransition(UXBuffer, transitionBuffer, transitionProgress, x, y);
            case SLIDE -> drawSlideTransition(UXBuffer, transitionBuffer, transitionProgress, x, y);
            case DIAGONAL -> drawDiagonalTransition(UXBuffer, transitionBuffer, transitionProgress, x, y);
        }

        // let it do its last transition above otherwise you get a little screen flicker on the transition
        // from getting cut off too soon apparently
        if (transitionProgress == 1) {
            isTransitioning = false;
        }
    }

    private void drawExpandoTransition(PGraphics UXBuffer, PGraphics transitionBuffer, float animationProgress, float x, float y) {
        switch (direction) {
            case LEFT -> {
                int visibleWidth = (int) (transitionBuffer.width * animationProgress);
                int revealPointX = (int) (x + transitionBuffer.width - visibleWidth);

                UXBuffer.image(transitionBuffer, revealPointX, y, visibleWidth, transitionBuffer.height);
            }
            case RIGHT ->
                    UXBuffer.image(transitionBuffer, x, y, (int) (transitionBuffer.width * animationProgress), transitionBuffer.height);
            case UP -> {
                int visibleHeight = (int) (transitionBuffer.height * animationProgress);
                int revealPointY = (int) (y + transitionBuffer.height - visibleHeight);

                UXBuffer.image(transitionBuffer, x, revealPointY, transitionBuffer.width, visibleHeight);
            }case DOWN ->
                    UXBuffer.image(transitionBuffer, x, y, transitionBuffer.width, (int) (transitionBuffer.height * animationProgress));
        }
    }

    private void drawSlideTransition(PGraphics UXBuffer, PGraphics transitionBuffer, float animationProgress, float x, float y) {

        switch (direction) {
            case LEFT -> {
                int visibleWidth = (int) (transitionBuffer.width * animationProgress);
                int revealPointX = (int) (x + (transitionBuffer.width - visibleWidth));

                PImage visiblePart = transitionBuffer.get(0, 0, visibleWidth, transitionBuffer.height);
                UXBuffer.image(visiblePart, revealPointX, y);
            }
            case RIGHT -> {
                int visibleWidth = (int) (transitionBuffer.width * animationProgress);
                int revealPointX = (int) (x);

                PImage visiblePart = transitionBuffer.get(transitionBuffer.width - visibleWidth, 0, visibleWidth, transitionBuffer.height);
                UXBuffer.image(visiblePart, revealPointX, y);
            }
            case UP -> {
                int visibleHeight = (int) (transitionBuffer.height * animationProgress);
                int revealPointY = (int) (y + (transitionBuffer.height - visibleHeight));

                PImage visiblePart = transitionBuffer.get(0, 0, transitionBuffer.width, visibleHeight);
                UXBuffer.image(visiblePart, x, revealPointY);
            }
            case DOWN -> {
                int visibleHeight = (int) (transitionBuffer.height * animationProgress);
                int revealPointY = (int) (y);
                PImage visiblePart = transitionBuffer.get(0, transitionBuffer.height - visibleHeight, transitionBuffer.width, visibleHeight);
                UXBuffer.image(visiblePart, x, revealPointY);
            }
        }
    }

    private void drawDiagonalTransition(PGraphics UXBuffer, PGraphics transitionBuffer, float animationProgress, float x, float y) {
        switch (direction) {
            case LEFT -> {
                int visibleWidth = (int) (transitionBuffer.width * animationProgress);
                int revealPointX = (int) (x + (transitionBuffer.width - visibleWidth));

                UXBuffer.image(transitionBuffer.get(transitionBuffer.width - visibleWidth, (int) (transitionBuffer.height * (1 - animationProgress)), visibleWidth, (int) (transitionBuffer.height * animationProgress)), revealPointX, y);
            }
            case RIGHT ->
                    UXBuffer.image(transitionBuffer.get((int) (transitionBuffer.width - transitionBuffer.width * animationProgress), (int) (transitionBuffer.height - transitionBuffer.height * animationProgress), (int) (transitionBuffer.width * animationProgress), (int) (transitionBuffer.height * animationProgress)), x, y);
            case UP ->
                UXBuffer.image(transitionBuffer.get(0, 0, (int) (transitionBuffer.width * animationProgress), (int) (transitionBuffer.height * animationProgress)), x + (transitionBuffer.width * (1 - animationProgress)), y + (transitionBuffer.height * (1 - animationProgress)));
            case DOWN ->
                    UXBuffer.image(transitionBuffer.get(0, (int) (transitionBuffer.height * (1 - animationProgress)), (int) (transitionBuffer.width * animationProgress), (int) (transitionBuffer.height * animationProgress)), x, y);
        }
    }

    public enum TransitionDirection {
        LEFT, RIGHT, UP, DOWN
    }

    public enum TransitionType {
        EXPANDO, SLIDE, DIAGONAL
    }


}
