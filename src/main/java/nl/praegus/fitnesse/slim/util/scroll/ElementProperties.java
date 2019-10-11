package nl.praegus.fitnesse.slim.util.scroll;

import lombok.EqualsAndHashCode;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebElement;

/**
 * Container for properties of an element that will be compared to determine whether it is considered
 * the same when scrolling.
 */
@EqualsAndHashCode
class ElementProperties {
    private String tag;
    private String text;
    private Dimension size;
    private Point location;

    ElementProperties(WebElement element) {
        tag = element.getTagName();
        text = element.getText();
        size = element.getSize();
        location = element.getLocation();
    }
}
