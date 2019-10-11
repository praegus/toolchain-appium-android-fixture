package nl.praegus.fitnesse.slim.util.scroll;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidElement;
import io.appium.java_client.touch.offset.PointOption;
import nl.praegus.fitnesse.slim.util.AndroidHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.Dimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ScrollHelperTest {

    @Mock
    private AndroidHelper helper;

    @Mock
    private TouchAction touchAction;

    @Mock
    private AndroidElement element;

    @InjectMocks
    private AndroidScrollHelper scrollHelper;

    @Test
    public void when_element_is_already_visible_true_is_returned() {
        String place = "place";
        when(helper.getElementToCheckVisibility(place)).thenReturn(element);
        when(element.isDisplayed()).thenReturn(true);

        boolean result = scrollHelper.scrollTo(place, helper::getElementToCheckVisibility);

        assertThat(result).isTrue();
        verify(element, times(1)).isDisplayed();
        verify(helper, times(1)).getElementToCheckVisibility(place);
        verify(touchAction, times(0)).moveTo(any());
    }

    @Test
    public void when_element_becomes_visible_true_is_eventually_returned() {
        String place = "place";
        when(helper.getWindowSize()).thenReturn(new Dimension(100, 200));
        when(helper.getTouchAction()).thenReturn(touchAction);
        when(helper.getElementToCheckVisibility(place)).thenReturn(null).thenReturn(element);
        when(element.isDisplayed()).thenReturn(true);
        mockTouchAction();

        boolean result = scrollHelper.scrollTo(place, helper::getElementToCheckVisibility);

        assertThat(result).isTrue();
        verify(helper, times(1)).getTouchAction();
        verify(element, times(1)).isDisplayed();
        verify(helper, times(2)).getElementToCheckVisibility(place);

        ArgumentCaptor<PointOption> argumentCaptor = ArgumentCaptor.forClass(PointOption.class);
        verify(touchAction, times(1)).moveTo(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().build().get("x")).isEqualTo(0);
        assertThat(argumentCaptor.getValue().build().get("y")).isEqualTo(200);
    }

    @Test
    public void when_element_remains_invisible_false_is_returned() {
        String place = "place";
        when(helper.getWindowSize()).thenReturn(new Dimension(100, 200));
        when(helper.getTouchAction()).thenReturn(touchAction);
        when(helper.getElementToCheckVisibility(place)).thenReturn(element);
        when(element.isDisplayed()).thenReturn(false);
        mockTouchAction();

        boolean result = scrollHelper.scrollTo(place, helper::getElementToCheckVisibility);

        assertThat(result).isFalse();
        verify(helper, times(2)).getTouchAction();
        verify(element, times(3)).isDisplayed();
        verify(helper, times(3)).getElementToCheckVisibility(place);

        ArgumentCaptor<PointOption> argumentCaptor = ArgumentCaptor.forClass(PointOption.class);
        verify(touchAction, times(2)).moveTo(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).build().get("x")).isEqualTo(0);
        assertThat(argumentCaptor.getAllValues().get(0).build().get("y")).isEqualTo(200);
        assertThat(argumentCaptor.getAllValues().get(1).build().get("x")).isEqualTo(0);
        assertThat(argumentCaptor.getAllValues().get(1).build().get("y")).isEqualTo(0);
    }

    @Test
    public void when_element_is_does_not_exist_false_is_returned() {
        String place = "place";
        when(helper.getWindowSize()).thenReturn(new Dimension(100, 200));
        when(helper.getTouchAction()).thenReturn(touchAction);
        when(helper.getElementToCheckVisibility(place)).thenReturn(null);
        mockTouchAction();
        boolean result = scrollHelper.scrollTo(place, helper::getElementToCheckVisibility);

        assertThat(result).isFalse();
        verify(helper, times(2)).getTouchAction();
        verify(helper, times(9)).getElementToCheckVisibility(place);
        verify(touchAction, times(2)).moveTo(any(PointOption.class));
    }

    @Test
    public void scroll_up_or_down_scrolls_down() {
        when(helper.getWindowSize()).thenReturn(new Dimension(100, 200));
        when(helper.getTouchAction()).thenReturn(touchAction);
        mockTouchAction();

        boolean result = scrollHelper.scrollUpOrDown(false);

        assertThat(result).isTrue();

        ArgumentCaptor<PointOption> argumentCaptor = ArgumentCaptor.forClass(PointOption.class);
        verify(touchAction, times(1)).moveTo(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).build().get("x")).isEqualTo(0);
        assertThat(argumentCaptor.getAllValues().get(0).build().get("y")).isEqualTo(50);
    }

    @Test
    public void scroll_up_or_down_scrolls_up() {
        when(helper.getWindowSize()).thenReturn(new Dimension(100, 200));
        when(helper.getTouchAction()).thenReturn(touchAction);
        mockTouchAction();

        boolean result = scrollHelper.scrollUpOrDown(true);

        assertThat(result).isTrue();

        ArgumentCaptor<PointOption> argumentCaptor = ArgumentCaptor.forClass(PointOption.class);
        verify(touchAction, times(1)).moveTo(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).build().get("x")).isEqualTo(0);
        assertThat(argumentCaptor.getAllValues().get(0).build().get("y")).isEqualTo(150);
    }

    private void mockTouchAction() {
        when(touchAction.press(any())).thenReturn(touchAction);
        when(touchAction.waitAction(any())).thenReturn(touchAction);
        when(touchAction.moveTo(any())).thenReturn(touchAction);
        when(touchAction.release()).thenReturn(touchAction);
    }
}