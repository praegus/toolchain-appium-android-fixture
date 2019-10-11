package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.AppiumDriver;
import nl.praegus.fitnesse.slim.util.scroll.AndroidScrollHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AndroidHelperTest {

    @Mock
    private AppiumDriver webDriver;

    @Mock
    private AndroidScrollHelper scrollHelper;

    @InjectMocks
    private AndroidHelper androidHelper;

    @Before
    public void setMocks() {
        androidHelper.setWebDriver(webDriver, 10);
    }

    @Test
    public void when_element_is_checked_for_visibility_several_attempts_are_made_to_find_the_element() {
        androidHelper.getElementToCheckVisibility("banaan");

        verify(webDriver, times(1)).findElements(eq(By.xpath(".//*[@enabled='true' and (contains(@text,'banaan') or contains(@name,'banaan') or contains(@content-desc,'banaan') or contains(@resource-id,'banaan'))]")));
    }

    @Test
    public void when_scroll_to_is_used_the_scrollhelper_is_used() {
        when(scrollHelper.scrollTo(anyString(), any())).thenReturn(true);

        boolean result = androidHelper.scrollTo("place");

        assertThat(result).isTrue();

        verify(scrollHelper).scrollTo(eq("place"), any());
    }
}