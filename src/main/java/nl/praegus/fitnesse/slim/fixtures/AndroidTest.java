package nl.praegus.fitnesse.slim.fixtures;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidElement;
import io.appium.java_client.android.nativekey.KeyEvent;
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
}
