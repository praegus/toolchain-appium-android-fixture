package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.android.AndroidDriver;
import nl.hsac.fitnesse.fixture.util.selenium.SeleniumHelper;
import nl.hsac.fitnesse.fixture.util.selenium.driverfactory.DriverManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class AppiumDriverManagerTest {

    @Mock
    private DriverManager driverManager;

    private AppiumDriverManager appiumDriverManager;

    @Before
    public void createAppiumDriverManager() {
        appiumDriverManager = new AppiumDriverManager(driverManager);
    }

    @Test
    public void create_helper_for_android_retuns_an_android_helper() {
        SeleniumHelper result = appiumDriverManager.createHelperForAndroid();

        assertThat(result).isInstanceOf(AndroidHelper.class);
    }

    @Test
    public void when_an_android_driver_is_the_argument_an_android_helper_is_created() {
        AndroidDriver driver = mock(AndroidDriver.class);

        SeleniumHelper result = appiumDriverManager.createHelper(driver);

        assertThat(result).isInstanceOf(AndroidHelper.class);
    }
}