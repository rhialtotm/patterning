import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class HUDInfo {
    private Map<String, Integer> data;
    private String cachedFormattedString;
    private int lastUpdateFrame;
    private NumberFormat numberFormat;

    public HUDInfo() {
        data = new LinkedHashMap<>(); // Use LinkedHashMap to maintain the insertion order
        cachedFormattedString = "";
        lastUpdateFrame = 0;
        numberFormat = NumberFormat.getInstance();
    }

    public void addOrUpdate(String key, int value) {
        data.put(key, value);
    }

    public String getFormattedString(int frameCount, int updateFrequency, String delimiter) {
        if (frameCount - lastUpdateFrame >= updateFrequency || cachedFormattedString.isEmpty()) {
            StringBuilder formattedString = new StringBuilder();
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                formattedString.append(entry.getKey())
                        .append(" ")
                        .append(numberFormat.format(entry.getValue()))
                        .append(delimiter);
            }
            // Remove the last delimiter
            if (formattedString.length() > 0) {
                formattedString.setLength(formattedString.length() - delimiter.length());
            }
            cachedFormattedString = formattedString.toString();
            lastUpdateFrame = frameCount;
        }
        return cachedFormattedString;
    }

    public String getFormattedString(int frameCount, int updateFrequency) {
        return getFormattedString(frameCount, updateFrequency, ", ");
    }
}