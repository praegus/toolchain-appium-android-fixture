package nl.praegus.fitnesse.slim.fixtures;

import io.appium.java_client.android.AndroidElement;
import nl.hsac.fitnesse.fixture.util.ReflectionHelper;
import nl.praegus.fitnesse.slim.util.AndroidHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;

import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AndroidTestTest {

    @Mock
    private AndroidHelper androidHelper;
    @Mock
    private ReflectionHelper reflectionHelper;
    @Mock
    private AndroidElement element;

    @InjectMocks
    private AndroidTest androidTest;

    @Test
    public void when_element_can_be_found_and_is_interactable_and_can_be_clicked_text_is_sent() {
        String place = "locator";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.enterAs("inputtekst", place);

        assertThat(result).isTrue();
    }

    @Test
    public void when_element_cannot_be_found_no_text_is_sent() {
        String place = "locator";
        when(androidHelper.getElement(place)).thenReturn(null);

        boolean result = androidTest.enterAs("inputtekst", place);

        assertThat(result).isFalse();
    }

    @Test
    public void when_element_can_be_found_but_is_not_interactable_no_text_is_sent() {
        String place = "locator";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(false);

        boolean result = androidTest.enterAs("inputtekst", place);

        assertThat(result).isFalse();
    }

    @Test
    public void clear_find_element_first() {
        String place = "locator";
        when(androidHelper.getElement(place)).thenReturn(element);

        boolean result = androidTest.clear(place);

        assertThat(result).isTrue();
    }

    @Test
    public void when_element_is_not_found_it_is_not_cleared() {
        String place = "locator";
        when(androidHelper.getElement(place)).thenReturn(null);

        boolean result = androidTest.clear(place);

        assertThat(result).isFalse();
    }

    @Test
    public void clear_element() {
        boolean result = androidTest.clear(element);

        assertThat(result).isTrue();
    }

    @Test
    public void clear_doesnt_work_because_element_is_null() {
        boolean result = androidTest.clear((AndroidElement) null);

        assertThat(result).isFalse();
    }

    @Test
    public void find_and_click_element() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.click(place);

        assertThat(result).isTrue();
    }

    @Test
    public void find_and_click_element_when_available() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.clickIfAvailable(place);

        assertThat(result).isTrue();
    }

    @Test
    public void click_element_then_throw_webdriver_exception_with_message() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenThrow(new WebDriverException("Exeption"));

        assertThatThrownBy(() -> androidTest.clickIfAvailable(place))
                .isInstanceOf(WebDriverException.class)
                .hasMessageStartingWith("Exeption");
    }

    @Test
    public void find_and_click_element_in_container() {
        String container = "container";
        String place = "locator";
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);
        when(androidHelper.doInContext(any(), any())).thenReturn(element);

        boolean result = androidTest.clickIn(place, container);

        assertThat(result).isTrue();
        verify(element, times(1)).click();
    }

    @Test
    public void find_and_double_click_with_string() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.doubleClick(place);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).doubleClick(element);
    }

    @Test
    public void find_and_double_click_in_container() {
        String container = "container";
        String place = "locator";
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);
        when(androidHelper.doInContext(any(), any())).thenReturn(element);

        boolean result = androidTest.doubleClickIn(place, container);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).doubleClick(element);
    }

    @Test
    public void try_to_click_element_but_element_not_found() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenReturn(null);

        boolean result = androidTest.click(place);

        assertThat(result).isFalse();
    }

    @Test
    public void try_to_click_element_but_element_not_interactable() {
        String place = "locator";
        when(androidHelper.getElementToClick(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(false);

        boolean result = androidTest.click(place);

        assertThat(result).isFalse();
    }

    @Test
    public void when_the_element_is_visible_wait_for_visible_returns_true() {
        String place = "place";
        when(androidHelper.findByTechnicalSelectorOr(eq(place), any(Supplier.class))).thenReturn(element);
        when(element.isDisplayed()).thenReturn(true);

        boolean result = androidTest.waitForVisible(place);

        assertThat(result).isTrue();
    }

    @Test
    public void when_the_element_is_not_found_wait_for_visible_returns_false() {
        String place = "place";
        when(androidHelper.findByTechnicalSelectorOr(eq(place), any(Supplier.class))).thenReturn(null);

        boolean result = androidTest.waitForVisible(place);

        assertThat(result).isFalse();
    }

    @Test
    public void when_the_element_is_not_visible_wait_for_visible_returns_false() {
        String place = "place";
        when(androidHelper.findByTechnicalSelectorOr(eq(place), any(Supplier.class))).thenReturn(element);
        when(element.isDisplayed()).thenReturn(false);

        boolean result = androidTest.waitForVisible(place);

        assertThat(result).isFalse();
    }

    @Test
    public void when_no_select_element_is_to_be_found_false_is_returned() {
        boolean result = androidTest.clickSelectOption(null, "value");

        assertThat(result).isFalse();
    }

    @Test
    public void when_the_option_of_the_select_element_is_not_found_false_is_returned() {
        when(element.getTagName()).thenReturn("select");
        when(element.findElement(any())).thenReturn(null);

        boolean result = androidTest.clickSelectOption(element, "value");

        assertThat(result).isFalse();
    }

    @Test
    public void when_the_select_element_is_found_and_value_is_selected_true_is_returned() {
        when(element.getTagName()).thenReturn("select");
        when(element.findElement(any())).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.clickSelectOption(element, "value");

        assertThat(result).isTrue();
    }

    @Test
    public void enter_value_in_hidden_field_then_true_is_returned() {
        String idOrName = "idorname";
        String value = "value";
        when(androidHelper.setHiddenInputValue(any(), any())).thenReturn(true);

        boolean result = androidTest.enterForHidden(idOrName, value);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).setHiddenInputValue(any(), any());
    }

    @Test
    public void select_option_for_element_then_true_is_returned() {
        AndroidElement option = mock(AndroidElement.class);

        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(element.getTagName()).thenReturn("select");
        when(element.findElement(any())).thenReturn(option);
        when(androidHelper.isInteractable(option)).thenReturn(true);

        boolean result = androidTest.selectFor("value", place);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).getElement(place);
        verify(option, times(1)).click();
    }

    @Test
    public void select_option_for_element_in_container_then_true_is_returned() {
        String container = "container";
        String place = "place";

        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(element);
        when(androidHelper.doInContext(eq(element), any(Supplier.class))).thenReturn(true);

        boolean result = androidTest.selectForIn("value", place, container);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).doInContext(eq(element), any(Supplier.class));
    }

    @Test
    public void select_option_as_element_then_true_is_returned() {
        AndroidElement option = mock(AndroidElement.class);

        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(element.getTagName()).thenReturn("select");
        when(element.findElement(any())).thenReturn(option);
        when(androidHelper.isInteractable(option)).thenReturn(true);
        when(element.getAttribute("multiple")).thenReturn("multiple");

        boolean result = androidTest.selectAs("value", place);

        assertThat(result).isTrue();
        verify(element, times(1)).findElements(By.tagName("option"));
    }

    @Test
    public void select_option_as_element_in_container_then_true_is_returned() {
        String container = "container";
        String place = "place";

        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(element);
        when(androidHelper.doInContext(eq(element), any(Supplier.class))).thenReturn(true);

        boolean result = androidTest.selectAsIn("value", place, container);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).doInContext(eq(element), any(Supplier.class));
    }

    @Test
    public void send_value_to_web_element_then_true_is_returned() {
        androidTest.sendValue(element, "value");

        verify(element, times(1)).sendKeys("value");
    }

    @Test
    public void press_single_key_then_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        boolean result = androidTest.press("control");

        assertThat(result).isTrue();
        ArgumentCaptor<CharSequence> sentKeys = ArgumentCaptor.forClass(CharSequence.class);
        verify(element, times(1)).sendKeys(sentKeys.capture());

        assertThat(sentKeys.getValue().charAt(0)).isEqualTo('\uE009');
    }

    @Test
    public void press_double_key_then_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        boolean result = androidTest.press("control + v");

        assertThat(result).isTrue();
        ArgumentCaptor<CharSequence> sentKeys = ArgumentCaptor.forClass(CharSequence.class);
        verify(element, times(1)).sendKeys(sentKeys.capture());

        assertThat(sentKeys.getValue().charAt(0)).isEqualTo('\uE009');
        assertThat(sentKeys.getValue().charAt(1)).isEqualTo('v');
    }

    @Test
    public void press_command_then_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        androidTest.setSendCommandForControlOnMacTo(true);
        boolean result = androidTest.press("control");

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).getControlOrCommand();
    }

    @Test
    public void when_escape_is_successfully_pressed_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        boolean result = androidTest.pressEsc();

        assertThat(result).isTrue();
        ArgumentCaptor<CharSequence> sendKeys = ArgumentCaptor.forClass(CharSequence.class);
        verify(element, times(1)).sendKeys(sendKeys.capture());
        assertThat(sendKeys.getValue().charAt(0)).isEqualTo('\ue00c');
    }

    @Test
    public void when_enter_is_successfully_pressed_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        boolean result = androidTest.pressEnter();

        assertThat(result).isTrue();
        ArgumentCaptor<CharSequence> sendKeys = ArgumentCaptor.forClass(CharSequence.class);
        verify(element, times(1)).sendKeys(sendKeys.capture());
        assertThat(sendKeys.getValue().charAt(0)).isEqualTo('\ue007');
    }

    @Test
    public void when_tab_is_successfully_pressed_true_is_returned() {
        when(androidHelper.getActiveElement()).thenReturn(element);

        boolean result = androidTest.pressTab();

        assertThat(result).isTrue();
        ArgumentCaptor<CharSequence> sendKeys = ArgumentCaptor.forClass(CharSequence.class);
        verify(element, times(1)).sendKeys(sendKeys.capture());
        assertThat(sendKeys.getValue().charAt(0)).isEqualTo('\ue004');
    }

    @Test
    public void when_get_element_to_send_value_is_used_successfully_then_return_element() {
        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);

        AndroidElement result = androidTest.getElementToSendValue(place);

        assertThat(result).isEqualTo(element).isNotNull();
    }

    @Test
    public void when_enter_as_date_is_used_successfully_then_return_true() {
        String date = "date";
        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.enterDateAs(date, place);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).fillDateInput(element, date);
    }

    @Test
    public void when_enter_as_date_with_non_interactable_element_failed_then_false_is_returned() {
        String date = "date";
        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(false);

        boolean result = androidTest.enterDateAs(date, place);

        assertThat(result).isFalse();
        verify(androidHelper, times(0)).fillDateInput(element, date);
    }

    @Test
    public void when_the_values_of_a_non_existing_element_are_retreived_an_empty_list_is_returned() {
        String container = "container";
        String place = "place";

        // can't find item!
        when(androidHelper.doInContext(any(), any())).thenReturn(null);

        // container that has list
        AndroidElement containerElement = mock(AndroidElement.class);
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(containerElement);

        List<String> result = androidTest.valuesForIn(place, container);

        assertThat(result).isEmpty();
    }

    @Test
    public void when_the_values_of_a_list_are_retreived_these_are_returned() {
        String container = "container";
        String place = "place";

        // list item
        AndroidElement li = mock(AndroidElement.class);
        when(li.isDisplayed()).thenReturn(true);
        when(androidHelper.getText(eq(li))).thenReturn("text!");

        // list
        when(androidHelper.doInContext(any(), any())).thenReturn(element);
        when(element.getTagName()).thenReturn("ul");
        when(element.findElements(eq(By.tagName("li")))).thenReturn(singletonList(li));

        // container that has list
        AndroidElement containerElement = mock(AndroidElement.class);
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(containerElement);

        List<String> result = androidTest.valuesForIn(place, container);

        assertThat(result).containsExactly("text!");
    }

    @Test
    public void when_the_values_of_a_select_are_retreived_these_are_returned() {
        String container = "container";
        String place = "place";

        // list item
        AndroidElement option = mock(AndroidElement.class);
        when(option.isDisplayed()).thenReturn(true);
        when(androidHelper.getText(eq(option))).thenReturn("text!");

        // select
        when(element.getTagName()).thenReturn("select");
        when(androidHelper.doInContext(any(), any())).thenReturn(element);
        when(element.findElements(any())).thenReturn(singletonList(option));

        // container that has select
        AndroidElement containerElement = mock(AndroidElement.class);
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(containerElement);

        List<String> result = androidTest.valuesForIn(place, container);

        assertThat(result).containsExactly("text!");
    }

    @Test
    public void when_the_values_of_a_checkbox_is_retreived_these_are_returned() {
        String container = "container";
        String place = "place";

        // checkbox
        when(androidHelper.doInContext(any(), any())).thenReturn(element);
        when(element.getTagName()).thenReturn("div");
        when(element.getAttribute("type")).thenReturn("checkbox");
        when(element.isSelected()).thenReturn(true);

        // container that has checkbox
        AndroidElement containerElement = mock(AndroidElement.class);
        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(containerElement);

        List<String> result = androidTest.valuesForIn(place, container);

        assertThat(result).containsExactly("true");
    }

    @Test
    public void when_enter_with_webelement_is_successfully_used_then_true_is_returned() {
        String value = "value";
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.enter(element, value, true);

        assertThat(result).isTrue();
        verify(element, times(1)).sendKeys(value);
    }

    @Test
    public void when_enter_is_used_on_select_element_then_true_is_returned() {
        AndroidElement option = mock(AndroidElement.class);
        String value = "value";
        when(androidHelper.isInteractable(element)).thenReturn(true);
        when(androidHelper.isInteractable(option)).thenReturn(true);
        when(element.getTagName()).thenReturn("Select");
        when(element.findElement(any())).thenReturn(option);

        boolean result = androidTest.enter(element, value, true);

        assertThat(result).isTrue();
        verify(option, times(1)).click();
    }

    @Test
    public void when_enter_with_place_is_successfully_used_then_true_is_returned() {
        String value = "value";
        String place = "place";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.enter(value, place, true);

        assertThat(result).isTrue();
        verify(element, times(1)).sendKeys(value);
    }

    @Test
    public void when_enter_for_is_successfully_used_then_true_is_returned() {
        String value = "value";
        String place = "place";

        when(androidHelper.getElement(place)).thenReturn(element);
        when(androidHelper.isInteractable(element)).thenReturn(true);

        boolean result = androidTest.enterFor(value, place);

        assertThat(result).isTrue();
        verify(element, times(1)).sendKeys(value);
    }

    @Test
    public void when_pagetitle_is_successfully_used_the_page_title_is_returned() {
        String pageTitle = "pageTitle";

        when(androidHelper.getPageTitle()).thenReturn(pageTitle);

        String result = androidTest.pageTitle();

        assertThat(result).isEqualTo(pageTitle);
        verify(androidHelper, times(1)).getPageTitle();
    }

    @Test
    public void when_switch_to_parent_frame_is_successfully_used_the_function_is_used() {

        androidTest.switchToParentFrame();

        verify(androidHelper, times(1)).switchToParentFrame();
    }

    @Test
    public void when_switch_to_frame_is_successfully_used_true_is_returned() {
        String selector = "selector";

        when(androidHelper.getElement(selector)).thenReturn(element);

        boolean result = androidTest.switchToFrame(selector);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).switchToFrame(any());
    }

    @Test
    public void when_switch_to_frame_is_not_successfully_used_false_is_returned() {
        String selector = "selector";

        when(androidHelper.getElement(selector)).thenReturn(null);

        boolean result = androidTest.switchToFrame(selector);

        assertThat(result).isFalse();
        verify(androidHelper, times(0)).switchToFrame(any());
        verify(androidHelper, times(1)).getElement(selector);
    }

    @Test
    public void when_switch_to_default_context_is_successfully_used_the_function_is_used() {

        androidTest.switchToDefaultContent();

        verify(androidHelper, times(1)).switchToDefaultContent();
        verify(androidHelper, times(1)).setCurrentContext(null);
    }

    @Test
    public void when_on_alert_handled_is_successfully_used_the_function_is_used() {
        androidTest.onAlertHandled(true);

        verify(androidHelper, times(1)).resetFrameDepthOnAlertError();
    }

    @Test
    public void when_wait_for_to_contain_is_successfully_used_true_is_returned() {
        String place = "place";
        String text = "text";
        when(androidHelper.getElement(place)).thenReturn(element);
        when(element.getText()).thenReturn(text);

        boolean result = androidTest.waitForToContain(place, text);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).getElement(place);
    }

    @Test
    public void when_set_search_context_to_is_successfully_used_true_is_returned() {
        String container = "container";

        when(androidHelper.findByTechnicalSelectorOr(eq(container), any(Supplier.class))).thenReturn(element);

        boolean result = androidTest.setSearchContextTo(container);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).setCurrentContext(element);
    }

    @Test
    public void when_get_page_title_is_successfully_used_true_is_returned() {
        String pageName = "pageName";

        when(androidHelper.getPageTitle()).thenReturn(pageName);

        boolean result = androidTest.waitForPage(pageName);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).getPageTitle();
    }

    @Test
    public void when_wait_for_tag_with_text_is_() {
        String tagName = "tagName";
        String expectedText = "expectedText";
        androidTest.secondsBeforeTimeout(5);

        when(androidHelper.waitUntil(eq(5), any(ExpectedCondition.class))).thenReturn(true);

        boolean result = androidTest.waitForTagWithText(tagName, expectedText);

        assertThat(result).isTrue();
        verify(androidHelper, times(1)).waitUntil(eq(5), any(ExpectedCondition.class));
    }

    @Test
    public void scroll_to_element() {
        androidTest.scrollTo(element);

        verify(androidHelper, times(1)).scrollTo(element);
    }


    @Test
    public void scroll_up() {
        when(androidHelper.scrollUpOrDown(true)).thenReturn(true);

        boolean result = androidTest.scrollUp();

        assertThat(result).isTrue();
    }

    @Test
    public void scroll_up_to() {
        when(androidHelper.findByTechnicalSelectorOr(eq("place"), any(Supplier.class))).thenReturn(null).thenReturn(element);
        when(androidHelper.checkVisible(any(), anyBoolean())).thenReturn(false).thenReturn(true);
        when(androidHelper.scrollUpOrDown(true)).thenReturn(true);

        boolean result = androidTest.scrollUpTo("place");

        assertThat(result).isTrue();

        verify(androidHelper, times(2)).findByTechnicalSelectorOr(eq("place"), any(Supplier.class));
        verify(androidHelper, times(1)).scrollUpOrDown(true);
    }

    @Test
    public void scroll_down() {
        when(androidHelper.scrollUpOrDown(false)).thenReturn(true);

        boolean result = androidTest.scrollDown();

        assertThat(result).isTrue();
    }

    @Test
    public void scroll_down_to() {
        when(androidHelper.findByTechnicalSelectorOr(eq("place"), any(Supplier.class))).thenReturn(null).thenReturn(element);
        when(androidHelper.checkVisible(any(), anyBoolean())).thenReturn(false).thenReturn(true);
        when(androidHelper.scrollUpOrDown(false)).thenReturn(true);

        boolean result = androidTest.scrollDownTo("place");

        assertThat(result).isTrue();

        verify(androidHelper, times(2)).findByTechnicalSelectorOr(eq("place"), any(Supplier.class));
        verify(androidHelper, times(1)).scrollUpOrDown(false);
    }

    @Test
    public void is_visible_on_page(){
        when(androidHelper.findByTechnicalSelectorOr(eq("place"), any(Supplier.class))).thenReturn(element);
        when(androidHelper.checkVisible(any(), anyBoolean())).thenReturn(true);

        boolean result = androidTest.isVisibleOnPage("place");

        assertThat(result).isTrue();
    }


    @Test
    public void when_both_elements_are_visible_drag_and_drop_is_performed() {
        AndroidElement sourceElement = mock(AndroidElement.class);
        AndroidElement destinationElement = mock(AndroidElement.class);

        when(androidHelper.getElementToClick("place 1")).thenReturn(sourceElement);
        when(androidHelper.getElementToClick("place 2")).thenReturn(destinationElement);
        when(androidHelper.isInteractable(sourceElement)).thenReturn(true);
        when(destinationElement.isDisplayed()).thenReturn(true);

        boolean result = androidTest.dragAndDropTo("place 1", "place 2");

        assertThat(result).isTrue();

        verify(androidHelper, times(1)).dragAndDrop(sourceElement, destinationElement);
    }

    @Test
    public void when_one_element_is_not_visible_drag_and_drop__not_is_performed() {
        AndroidElement sourceElement = mock(AndroidElement.class);
        AndroidElement destinationElement = mock(AndroidElement.class);

        when(androidHelper.getElementToClick("place 1")).thenReturn(sourceElement);
        when(androidHelper.getElementToClick("place 2")).thenReturn(destinationElement);
        when(androidHelper.isInteractable(sourceElement)).thenReturn(true);
        when(destinationElement.isDisplayed()).thenReturn(false);

        boolean result = androidTest.dragAndDropTo("place 1", "place 2");

        assertThat(result).isFalse();

        verify(androidHelper, times(0)).dragAndDrop(any(), any());
    }
}