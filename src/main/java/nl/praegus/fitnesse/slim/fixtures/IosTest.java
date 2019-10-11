package nl.praegus.fitnesse.slim.fixtures;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.IOSElement;
import nl.praegus.fitnesse.slim.util.IosHelper;

/**
 * Specialized class to test iOS applications using Appium.
 */
public class IosTest extends AppiumTest<IOSElement, IOSDriver<IOSElement>> {
    public IosTest() {
        super();
    }

    public IosTest(int secondsBeforeTimeout) {
        super(secondsBeforeTimeout);
    }

    @Override
    protected IosHelper getAppiumHelper() {
        return (IosHelper) super.getAppiumHelper();
    }

    public boolean resetApp() {
        getDriver().resetApp();
        return true;
    }
}
