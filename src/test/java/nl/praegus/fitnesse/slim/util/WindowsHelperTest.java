package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileBy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.By;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WindowsHelperTest {

    @Mock
    private AppiumDriver webDriver;

    private WindowsHelper windowsHelper = new WindowsHelper();

    @Before
    public void setMocks() {
        windowsHelper.setWebDriver(webDriver, 10);
    }

    @Test
    public void when_element_is_checked_for_visibility_several_attempts_are_made_to_find_the_element() {
        windowsHelper.getElementToCheckVisibility("banaan");

        verify(webDriver, times(1)).findElements(By.name("banaan"));
        verify(webDriver, times(1)).findElement(any(MobileBy.ByAccessibilityId.ByAccessibilityId.class));
        verify(webDriver, times(1)).findElements(eq(By.xpath("//*[@Value='banaan' or @HelpText='banaan']")));
        verify(webDriver, times(1)).findElements(eq(By.xpath("//*[contains(@Name, 'banaan') or contains(@AutomationId, 'banaan') or contains(@Value, 'banaan') or contains(@HelpText, 'banaan')]")));
        verify(webDriver, times(1)).findElements(eq(By.name("banaan")));
    }
}