package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileBy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class IosHelperTest {

    @Mock
    private AppiumDriver webDriver;

    private IosHelper iosHelper = new IosHelper();

    @Before
    public void setMocks() {
        iosHelper.setWebDriver(webDriver, 10);
    }

    @Test
    public void when_element_is_checked_for_visibility_several_attempts_are_made_to_find_the_element() {
        iosHelper.getElementToCheckVisibility("banaan");

        verify(webDriver, times(1)).findElement(any(MobileBy.ByAccessibilityId.ByAccessibilityId.class));
        verify(webDriver, times(1)).findElements(eq(MobileBy.iOSNsPredicateString("name CONTAINS 'banaan' OR label CONTAINS 'banaan' OR value CONTAINS 'banaan'")));
    }
}