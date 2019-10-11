package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.windows.WindowsDriver;
import io.appium.java_client.windows.WindowsElement;
import nl.praegus.fitnesse.slim.util.by.AppiumHeuristicBy;
import nl.praegus.fitnesse.slim.util.by.IsDisplayedFilter;
import nl.praegus.fitnesse.slim.util.by.WindowsBy;
import nl.praegus.fitnesse.slim.util.scroll.ScrollHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static nl.hsac.fitnesse.fixture.util.FirstNonNullHelper.firstNonNull;

public class WindowsHelper extends AppiumHelper<WindowsElement, WindowsDriver<WindowsElement>> {

    public WindowsHelper() {
        setScrollHelper(new ScrollHelper<>(this));
    }

    @Override
    public By placeToBy(String place) {
        if (place.startsWith("id=")) {
            return WindowsBy.accessibilityId(place.substring(3));
        } else if (place.startsWith("name=")) {
            return WindowsBy.name(place.substring(5));
        } else {
            return firstNonNull(place, super::placeToBy);
        }
    }

    protected By getElementBy(String place) {
        return WindowsBy.heuristic(place);
    }

    protected By getClickBy(String place) {
        return WindowsBy.heuristic(place);
    }

    protected By getContainerBy(String container) {
        return WindowsBy.heuristic(container);
    }

    protected By getElementToCheckVisibilityBy(String text) {
        return new AppiumHeuristicBy<>(new IsDisplayedFilter<WindowsElement>(),
                WindowsBy.name(text), WindowsBy.accessibilityId(text), WindowsBy.exactText(text), WindowsBy.partialText(text));
    }

    @Override
    public void scrollTo(WebElement element) {
        // todo goede implementatie maken
    }
}
