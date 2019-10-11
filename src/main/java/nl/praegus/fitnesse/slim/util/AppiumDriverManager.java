package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.windows.WindowsDriver;
import nl.hsac.fitnesse.fixture.Environment;
import nl.hsac.fitnesse.fixture.util.selenium.SeleniumHelper;
import nl.hsac.fitnesse.fixture.util.selenium.by.BestMatchBy;
import nl.hsac.fitnesse.fixture.util.selenium.driverfactory.DriverFactory;
import nl.hsac.fitnesse.fixture.util.selenium.driverfactory.DriverManager;
import nl.praegus.fitnesse.slim.util.element.MobileElementConverter;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Driver manager which creates platform specific SeleniumHelpers.
 */
public class AppiumDriverManager extends DriverManager {
    public AppiumDriverManager(DriverManager manager) {
        DriverFactory factory = manager.getFactory();
        setFactory(factory);
        setDefaultTimeoutSeconds(manager.getDefaultTimeoutSeconds());
    }

    @Override
    protected SeleniumHelper createHelper(WebDriver driver) {
        SeleniumHelper helper;
        if (driver instanceof IOSDriver) {
            helper = createHelperForIos();
        } else if (driver instanceof AndroidDriver) {
            helper = createHelperForAndroid();
        } else if (driver instanceof WindowsDriver) {
            helper = createHelperForWindows();
        } else {
            helper = super.createHelper(driver);
        }
        if (driver instanceof AppiumDriver) {
            AppiumDriver d = (AppiumDriver) driver;
            setBestFunction();
            setMobileElementConverter(d);
        }
        return helper;
    }

    protected void setBestFunction() {
        // selecting the 'best macth' should not be done by checking whats on top via Javascript
        BestMatchBy.setBestFunction((sc, elements) -> selectBestElement(elements));
    }

    protected void setMobileElementConverter(AppiumDriver d) {
        MobileElementConverter converter = createMobileElementConverter(d);
        Environment.getInstance().getReflectionHelper().setField(d, "converter", converter);
    }

    protected MobileElementConverter createMobileElementConverter(AppiumDriver d) {
        return new MobileElementConverter(d, d);
    }

    protected SeleniumHelper createHelperForIos() {
        return new IosHelper();
    }

    protected SeleniumHelper createHelperForAndroid() {
        return new AndroidHelper();
    }

    protected SeleniumHelper createHelperForWindows() {
        return new WindowsHelper();
    }

    protected WebElement selectBestElement(List<WebElement> elements) {
        WebElement element = elements.get(0);
        if (!element.isDisplayed()) {
            for (int i = 1; i < elements.size(); i++) {
                WebElement otherElement = elements.get(i);
                if (otherElement.isDisplayed()) {
                    element = otherElement;
                    break;
                }
            }
        }
        return element;
    }
}
