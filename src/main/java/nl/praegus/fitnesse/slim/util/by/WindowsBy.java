package nl.praegus.fitnesse.slim.util.by;

import io.appium.java_client.MobileBy;
import io.appium.java_client.windows.WindowsElement;
import org.openqa.selenium.By;

public class WindowsBy {

    private WindowsBy(){
        // hide constructor
    }

    private static final String CONTAINS_EXACT = "//*[@Value='%1$s' or @HelpText='%1$s']";

    private static final String CONTAINS_PARTIAL = "//*[contains(@Name, '%1$s') " +
            "or contains(@AutomationId, '%1$s') " +
            "or contains(@Value, '%1$s') " +
            "or contains(@HelpText, '%1$s')]";

    public static By name(String name) {
        return MobileBy.name(name);
    }

    public static By accessibilityId(String id) {
        return MobileBy.AccessibilityId(id);
    }

    public static By exactText(String text) {
        return MobileBy.xpath(String.format(CONTAINS_EXACT, text));
    }

    public static By partialText(String text) {
        return MobileBy.xpath(String.format(CONTAINS_PARTIAL, text));
    }

    public static AppiumHeuristicBy<WindowsElement> heuristic(String text) {
        return new AppiumHeuristicBy<>(name(text), accessibilityId(text), exactText(text), partialText(text));
    }

}
