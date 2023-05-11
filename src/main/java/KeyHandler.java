import processing.event.KeyEvent;
import processing.core.PApplet;

import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class KeyHandler {
    private final Map<Set<Integer>, KeyCallback> keyCallbacks;

    private final Set<Integer> pressedKeys;


    public KeyHandler(PApplet processing) {

        this.keyCallbacks = new LinkedHashMap<>();
        this.pressedKeys = new HashSet<>();

        processing.registerMethod("keyEvent", this);
    }

    public void addKeyCallback(KeyCallback callback) {
        Set<Integer> keyCodes = callback.getKeyCodes();
        Set<Integer> duplicateKeyCodes = findDuplicateKeyCodes(keyCodes);

        if (!duplicateKeyCodes.isEmpty()) {
            String duplicateKeyCodesString = duplicateKeyCodes.stream()
                    .map(k -> keyCodeToText(k, 0))
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("The following key codes are already associated with another callback: " + duplicateKeyCodesString);
        }

        keyCallbacks.put(keyCodes, callback);
    }

    private Set<Integer> findDuplicateKeyCodes(Set<Integer> keyCodes) {
        Set<Integer> duplicateKeyCodes = new HashSet<>();

        for (Map.Entry<Set<Integer>, KeyCallback> entry : keyCallbacks.entrySet()) {
            Set<Integer> existingKeyCodes = entry.getKey();
            for (Integer keyCode : keyCodes) {
                if (existingKeyCodes.contains(keyCode)) {
                    duplicateKeyCodes.add(keyCode);
                }
            }
        }

        return duplicateKeyCodes;
    }

    // this is registered with processing in the constructor
    // which is why you don't see any usages in the IDE
    public void keyEvent(KeyEvent event) {

        int keyCode = event.getKeyCode();

        for (KeyCallback callback : keyCallbacks.values()) {

            boolean matched = callback.matches(event);

            if (event.getAction() == KeyEvent.PRESS) {
                if (matched) {
                    callback.onKeyEvent(event);

                    if (callback instanceof KeyCallbackMovement) {
                        ((KeyCallbackMovement)callback).move(pressedKeys);
                    }

                }

                pressedKeys.add(keyCode);
            }

            if (event.getAction() == KeyEvent.RELEASE) {
                pressedKeys.remove(keyCode);

                if (callback instanceof KeyCallbackMovement  && pressedKeys.size()==0) {
                    ((KeyCallbackMovement)callback).stopMoving();
                }
            }

            if (matched)
                break;
        }
    }

    public String generateUsageText() {
        StringBuilder usageText = new StringBuilder("Key Usage:\n\n");

        Set<KeyCallback> processedCallbacks = new HashSet<>();

        int maxKeysWidth = keyCallbacks.entrySet().stream()
                .filter(e -> e.getValue().isValidForCurrentOS())
                .mapToInt(entry -> {
                    StringBuilder keysStringBuilder = new StringBuilder();
                    for (Integer k : entry.getKey()) {
                        if (entry.getValue().isValidForCurrentOS()) {
                            String keyStr = keyCodeToText(k, entry.getValue().getModifiers());
                            keysStringBuilder.append(keyStr).append(", ");
                        }
                    }
                    return keysStringBuilder.length() - 2;
                })
                .max().orElse(0);

        for (Map.Entry<Set<Integer>, KeyCallback> entry : keyCallbacks.entrySet()) {
            Set<Integer> keyCodes = entry.getKey();
            KeyCallback callback = entry.getValue();

            if (!callback.isValidForCurrentOS() || processedCallbacks.contains(callback)) {
                continue;
            }

            StringBuilder keysStringBuilder = new StringBuilder();
            for (Integer keyCode : keyCodes) {
                if (callback.isValidForCurrentOS()) {
                    String keyStr = keyCodeToText(keyCode, callback.getModifiers());
                    keysStringBuilder.append(keyStr).append(", ");
                }
            }

            String keysString = keysStringBuilder.toString().replaceAll(", $", "");

            String usageDescription = callback.getUsageText();
            usageText.append(String.format("%-" + (maxKeysWidth + 1) + "s: %s\n", keysString, usageDescription));

            processedCallbacks.add(callback);
        }

        return usageText.toString();
    }


    private String keyCodeToText(int keyCode, int modifiers) {
        StringBuilder keyTextBuilder = new StringBuilder();

        if ((modifiers & KeyEvent.META) != 0) {
            keyTextBuilder.append("Cmd+");
        }
        if ((modifiers & KeyEvent.CTRL) != 0) {
            keyTextBuilder.append("Ctrl+");
        }

        switch (keyCode) {
            case PApplet.UP -> keyTextBuilder.append("↑");
            case PApplet.DOWN -> keyTextBuilder.append("↓");
            case PApplet.LEFT -> keyTextBuilder.append("←");
            case PApplet.RIGHT -> keyTextBuilder.append("→");
            case 32 -> keyTextBuilder.append("Space");
            default -> keyTextBuilder.append((char) keyCode);
        }

        return keyTextBuilder.toString();
    }

}