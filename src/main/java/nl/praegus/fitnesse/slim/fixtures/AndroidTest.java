package nl.praegus.fitnesse.slim.fixtures;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidElement;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.touch.offset.PointOption;
import nl.hsac.fitnesse.fixture.slim.web.annotation.WaitUntil;
import nl.praegus.fitnesse.slim.util.KeyMapping;
import org.openqa.selenium.WebElement;

import static io.appium.java_client.touch.TapOptions.tapOptions;
import static io.appium.java_client.touch.offset.ElementOption.element;

/**
 * Specialized class to test Android applications using Appium.
 */
public class AndroidTest extends AppiumTest<AndroidElement, AndroidDriver<AndroidElement>> {
    public AndroidTest() {
        super();
    }

    public AndroidTest(int secondsBeforeTimeout) {
        super(secondsBeforeTimeout);
    }

    public boolean pressKey(String key) {
        getAppiumHelper().driver().pressKey(new KeyEvent(KeyMapping.getKey(key)));
        return true;
    }

    public boolean resetApp() {
        getDriver().resetApp();
        return true;
    }

    @WaitUntil
    public boolean tap(String place) {
        return tap(place, null);
    }

    @WaitUntil
    public boolean tapIn(String place, String container) {
        return tap(place, container);
    }

    protected boolean tap(String place, String container) {
        TouchAction touchAction = new TouchAction(getAppiumHelper().driver());
        WebElement element = getElementToClick(cleanupValue(place), container);
        return tapElement(touchAction, element);
    }

    protected boolean tapElement(TouchAction touchAction, WebElement element) {
        return doIfInteractable(element, () -> touchAction.tap(tapOptions().withElement(element(element))).perform());
    }

    @WaitUntil
    public boolean swipeFromTo(String from, String to) {
        PointOption pointFrom = getPointFromString(from);
        PointOption pointTo = getPointFromString(to);
        return swipe(pointFrom, pointTo);
    }

    protected PointOption getPointFromString(String coordinates) {
        String[] coordinatesList = coordinates.split(",");
        return PointOption.point(Integer.parseInt(coordinatesList[0]), Integer.parseInt(coordinatesList[1]));
    }

    protected boolean swipe(PointOption from, PointOption to) {
        TouchAction action = new TouchAction(getAppiumHelper().driver());
        action.longPress(from).moveTo(to).release().perform();
        return true;
    }
}
