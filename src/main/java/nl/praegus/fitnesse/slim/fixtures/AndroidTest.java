package nl.praegus.fitnesse.slim.fixtures;

import fitnesse.slim.fixtureInteraction.FixtureInteraction;
import io.appium.java_client.MobileElement;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidElement;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.touch.offset.PointOption;
import nl.hsac.fitnesse.fixture.slim.SlimFixture;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import nl.hsac.fitnesse.fixture.slim.StopTestException;
import nl.hsac.fitnesse.fixture.slim.web.TimeoutStopTestException;
import nl.hsac.fitnesse.fixture.slim.web.annotation.TimeoutPolicy;
import nl.hsac.fitnesse.fixture.slim.web.annotation.WaitUntil;
import nl.hsac.fitnesse.fixture.util.ReflectionHelper;
import nl.hsac.fitnesse.fixture.util.selenium.PageSourceSaver;
import nl.hsac.fitnesse.fixture.util.selenium.SelectHelper;
import nl.hsac.fitnesse.fixture.util.selenium.StaleContextException;
import nl.hsac.fitnesse.fixture.util.selenium.by.OptionBy;
import nl.hsac.fitnesse.fixture.util.selenium.by.XPathBy;
import nl.hsac.fitnesse.slim.interaction.ExceptionHelper;
import nl.praegus.fitnesse.slim.util.AndroidHelper;
import nl.praegus.fitnesse.slim.util.KeyMapping;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Select;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.appium.java_client.touch.TapOptions.tapOptions;
import static io.appium.java_client.touch.offset.ElementOption.element;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static nl.hsac.fitnesse.fixture.util.selenium.SelectHelper.isSelect;

/**
 * Specialized class to test Android applications using Appium.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class AndroidTest extends SlimFixture {
    private final List<String> currentSearchContextPath = new ArrayList<>();
    private AndroidHelper androidHelper;
    private int waitAfterScroll = 500;
    private int minStaleContextRefreshCount = 5;
    private ReflectionHelper reflectionHelper;
    private boolean implicitFindInFrames = true;
    private int secondsBeforeTimeout;
    private int secondsBeforePageLoadTimeout;
    private String screenshotBase = new File(filesDir, "screenshots").getPath() + "/";
    private String screenshotHeight = "200";
    private String pageSourceBase = new File(filesDir, "pagesources").getPath() + "/";
    private boolean abortOnException;

    public AndroidTest() {
        this.androidHelper = (AndroidHelper) getEnvironment().getSeleniumHelper();
        this.reflectionHelper = getEnvironment().getReflectionHelper();
        secondsBeforeTimeout(getEnvironment().getSeleniumDriverManager().getDefaultTimeoutSeconds());
    }

    public AndroidTest(int secondsBeforeTimeout) {
        this.androidHelper = (AndroidHelper) getEnvironment().getSeleniumHelper();
        this.reflectionHelper = getEnvironment().getReflectionHelper();
        secondsBeforeTimeout(secondsBeforeTimeout);
    }

    public AndroidTest(AndroidHelper androidHelper, ReflectionHelper reflectionHelper) {
        this.androidHelper = androidHelper;
        this.reflectionHelper = reflectionHelper;
    }

    public boolean pressKey(String key) {
        getDriver().pressKey(new KeyEvent(KeyMapping.getKey(key)));
        return true;
    }

    public boolean launchApp() {
        getDriver().launchApp();
        return true;
    }

    public boolean closeApp() {
        getDriver().closeApp();
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

    private boolean tap(String place, String container) {
        TouchAction touchAction = new TouchAction(getDriver());
        AndroidElement element = getElementToClick(cleanupValue(place), container);
        return tapElement(touchAction, element);
    }

    private boolean tapElement(TouchAction touchAction, AndroidElement element) {
        return doIfInteractable(element, () -> touchAction.tap(tapOptions().withElement(element(element))).perform());
    }

    @WaitUntil
    public boolean swipeFromTo(String from, String to) {
        PointOption pointFrom = getPointFromString(from);
        PointOption pointTo = getPointFromString(to);
        return swipe(pointFrom, pointTo);
    }

    private PointOption getPointFromString(String coordinates) {
        String[] coordinatesList = coordinates.split(",");
        return PointOption.point(Integer.parseInt(coordinatesList[0]), Integer.parseInt(coordinatesList[1]));
    }

    private boolean swipe(PointOption from, PointOption to) {
        TouchAction action = new TouchAction(getDriver());
        action.longPress(from).moveTo(to).release().perform();
        return true;
    }

    private List<String> getCurrentSearchContextPath() {
        return currentSearchContextPath;
    }

    @Override
    protected Throwable handleException(Method method, Object[] arguments, Throwable t) {
        Throwable result = super.handleException(method, arguments, t);
        if (abortOnException) {
            String msg = result.getMessage();
            if (msg.startsWith("message:<<") && msg.endsWith(">>")) {
                msg = msg.substring(10, msg.length() - 2);
            }
            result = new StopTestException(false, msg);
        }
        return result;
    }

    private AndroidDriver<AndroidElement> getDriver() {
        return androidHelper.driver();
    }

    protected boolean clear(AndroidElement element) {
        if (element != null) {
            element.clear();
            return true;
        }
        return false;
    }

    @Override
    protected Object invoke(FixtureInteraction interaction, Method method, Object[] arguments)
            throws Throwable {
        try {
            Object result;
            WaitUntil waitUntil = reflectionHelper.getAnnotation(WaitUntil.class, method);
            if (waitUntil == null) {
                result = super.invoke(interaction, method, arguments);
            } else {
                result = invokedWrappedInWaitUntil(waitUntil, interaction, method, arguments);
            }
            return result;
        } catch (StaleContextException e) {
            // current context was no good to search in
            if (getCurrentSearchContextPath().isEmpty()) {
                throw e;
            } else {
                refreshSearchContext();
                return invoke(interaction, method, arguments);
            }
        }
    }

    private Object invokedWrappedInWaitUntil(WaitUntil waitUntil, FixtureInteraction interaction, Method method, Object[] arguments) {
        ExpectedCondition<Object> condition = webDriver -> {
            try {
                return super.invoke(interaction, method, arguments);
            } catch (Throwable e) {
                Throwable realEx = ExceptionHelper.stripReflectionException(e);
                if (realEx instanceof RuntimeException) {
                    throw (RuntimeException) realEx;
                } else if (realEx instanceof Error) {
                    throw (Error) realEx;
                } else {
                    throw new RuntimeException(realEx);
                }
            }
        };
        condition = wrapConditionForFramesIfNeeded(condition);

        Object result;
        switch (waitUntil.value()) {
            case STOP_TEST:
                result = waitUntilOrStop(condition);
                break;
            case RETURN_NULL:
                result = waitUntilOrNull(condition);
                break;
            case RETURN_FALSE:
                result = waitUntilOrNull(condition) != null;
                break;
            case THROW:
            default:
                result = waitUntil(condition);
                break;
        }
        return result;
    }

    public String pageTitle() {
        return androidHelper.getPageTitle();
    }

    /**
     * Replaces content at place by value.
     *
     * @param value value to set.
     * @param place element to set value on.
     * @return true, if element was found.
     */
    @WaitUntil
    public boolean enterAs(String value, String place) {
        return enterAsIn(value, place, null);
    }

    /**
     * Replaces content at place by value.
     *
     * @param value     value to set.
     * @param place     element to set value on.
     * @param container element containing place.
     * @return true, if element was found.
     */
    @WaitUntil
    public boolean enterAsIn(String value, String place, String container) {
        return enter(value, place, container, true);
    }

    /**
     * Adds content to place.
     *
     * @param value value to add.
     * @param place element to add value to.
     * @return true, if element was found.
     */
    @WaitUntil
    public boolean enterFor(String value, String place) {
        return enterForIn(value, place, null);
    }

    /**
     * Adds content to place.
     *
     * @param value     value to add.
     * @param place     element to add value to.
     * @param container element containing place.
     * @return true, if element was found.
     */
    @WaitUntil
    public boolean enterForIn(String value, String place, String container) {
        return enter(value, place, container, false);
    }

    protected boolean enter(String value, String place, boolean shouldClear) {
        return enter(value, place, null, shouldClear);
    }

    protected boolean enter(String value, String place, String container, boolean shouldClear) {
        AndroidElement element = getElement(place, container);
        return enter(element, value, shouldClear);
    }

    protected boolean enter(AndroidElement element, String value, boolean shouldClear) {
        boolean result = element != null && androidHelper.isInteractable(element);
        if (result) {
            if (isSelect(element)) {
                result = clickSelectOption(element, value);
            } else {
                if (shouldClear) {
                    result = clear(element);
                }
                if (result) {
                    sendValue(element, value);
                }
            }
        }
        return result;
    }

    /**
     * Simulates typing a text to the current active element.
     *
     * @param text text to type.
     * @return true, if an element was active the text could be sent to.
     */
    public boolean type(String text) {
        String value = cleanupValue(text);
        return sendKeysToActiveElement(value);
    }

    /**
     * Simulates pressing keys.
     *
     * @param keys keys to press.
     * @return true, if an element was active the keys could be sent to.
     */
    private boolean sendKeysToActiveElement(CharSequence... keys) {
        boolean result = false;
        AndroidElement element = androidHelper.getActiveElement();
        if (element != null) {
            element.sendKeys(keys);
            result = true;
        }
        return result;
    }

    /**
     * Sends Fitnesse cell content to element.
     *
     * @param element element to call sendKeys() on.
     * @param value   cell content.
     */
    protected void sendValue(AndroidElement element, String value) {
        if (StringUtils.isNotEmpty(value)) {
            String keys = cleanupValue(value);
            element.sendKeys(keys);
        }
    }

    @WaitUntil
    public boolean selectAs(String value, String place) {
        AndroidElement element = androidHelper.getElement(place);
        Select select = new Select(element);
        if (select.isMultiple()) {
            select.deselectAll();
        }
        return clickSelectOption(element, value);
    }

    @WaitUntil
    public boolean selectAsIn(String value, String place, String container) {
        return Boolean.TRUE.equals(doInContainer(container, () -> selectAs(value, place)));
    }

    @WaitUntil
    public boolean selectFor(String value, String place) {
        AndroidElement element = androidHelper.getElement(place);
        return clickSelectOption(element, value);
    }

    @WaitUntil
    public boolean selectForIn(String value, String place, String container) {
        return Boolean.TRUE.equals(doInContainer(container, () -> selectFor(value, place)));
    }

    @WaitUntil
    public boolean enterForHidden(String value, String idOrName) {
        return androidHelper.setHiddenInputValue(idOrName, value);
    }

    protected boolean clickSelectOption(AndroidElement element, String optionValue) {
        if (element != null && isSelect(element)) {
            optionValue = cleanupValue(optionValue);
            By optionBy = new OptionBy(optionValue);
            AndroidElement option = (AndroidElement) element.findElement(optionBy);
            return clickSelectOption(element, option);
        }
        return false;
    }

    private boolean clickSelectOption(AndroidElement element, AndroidElement option) {
        boolean result = false;
        if (option != null) {
            // we scroll containing select into view (not the option)
            // based on behavior for option in https://www.w3.org/TR/webdriver/#element-click
            scrollIfNotOnScreen(element);
            if (androidHelper.isInteractable(option)) {
                option.click();
                result = true;
            }
        }
        return result;
    }

    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean clickIfAvailable(String place) {
        return clickIfAvailableIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean clickIfAvailableIn(String place, String container) {
        return click(place, container);
    }

    @WaitUntil
    public boolean clickIn(String place, String container) {
        return click(place, container);
    }

    @WaitUntil
    public boolean click(String place) {
        return click(place, null);
    }

    private boolean click(String place, String container) {
        AndroidElement element = getElementToClick(cleanupValue(place), container);
        return clickElement(element);
    }


    @WaitUntil
    public boolean dragAndDropTo(String source, String destination) {
        AndroidElement sourceElement = androidHelper.getElementToClick(cleanupValue(source));
        AndroidElement destinationElement = androidHelper.getElementToClick(cleanupValue(destination));

        if ((sourceElement != null) && (destinationElement != null)) {
            scrollIfNotOnScreen(sourceElement);
            if (androidHelper.isInteractable(sourceElement) && destinationElement.isDisplayed()) {
                androidHelper.dragAndDrop(sourceElement, destinationElement);
                return true;
            }
        }
        return false;
    }

    private AndroidElement getElementToClick(String place, String container) {
        return doInContainer(container, () -> androidHelper.getElementToClick(place));
    }

    private <R> R doInContainer(String container, Supplier<R> action) {
        if (container == null) {
            return action.get();
        } else {
            return doInContainer(() -> getContainerElement(cleanupValue(container)), action);
        }
    }

    private <R> R doInContainer(Supplier<AndroidElement> containerSupplier, Supplier<R> action) {
        R result = null;
        int retryCount = minStaleContextRefreshCount;
        do {
            try {
                AndroidElement containerElement = containerSupplier.get();
                if (containerElement != null) {
                    result = androidHelper.doInContext(containerElement, action);
                }
                retryCount = 0;
            } catch (StaleContextException e) {
                // containerElement went stale
                retryCount--;
                if (retryCount < 1) {
                    throw e;
                }
            }
        } while (retryCount > 0);
        return result;
    }

    @WaitUntil
    public boolean setSearchContextTo(String container) {
        container = cleanupValue(container);
        AndroidElement containerElement = getContainerElement(container);
        boolean result = false;
        if (containerElement != null) {
            getCurrentSearchContextPath().add(container);
            androidHelper.setCurrentContext(containerElement);
            result = true;
        }
        return result;
    }

    public void clearSearchContext() {
        getCurrentSearchContextPath().clear();
        androidHelper.setCurrentContext(null);
    }

    private AndroidElement getContainerElement(String container) {
        return findByTechnicalSelectorOr(container, container1 -> androidHelper.getElement(container1));
    }

    private boolean clickElement(AndroidElement element) {
        return doIfInteractable(element, () -> element.click());
    }

    private boolean doIfInteractable(AndroidElement element, Runnable action) {
        boolean result = false;
        if (element != null) {
            scrollIfNotOnScreen(element);
            if (androidHelper.isInteractable(element)) {
                action.run();
                result = true;
            }
        }
        return result;
    }

    private String cleanExpectedValue(String expectedText) {
        return cleanupValue(expectedText);
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForVisible(String place) {
        return waitForVisibleIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForVisibleIn(String place, String container) {
        AndroidElement element = getElementToCheckVisibility(place, container);
        if (element != null) {
            scrollIfNotOnScreen(element);
            return element.isDisplayed();
        }
        return false;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOf(String place) {
        return valueFor(place);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueFor(String place) {
        return valueForIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfIn(String place, String container) {
        return valueForIn(place, container);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueForIn(String place, String container) {
        AndroidElement element = getElement(place, container);
        return valueFor(element);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueOf(String place) {
        return normalizedValueFor(place);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueFor(String place) {
        return normalizedValueForIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueOfIn(String place, String container) {
        return normalizedValueForIn(place, container);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueForIn(String place, String container) {
        String value = valueForIn(place, container);
        return normalizeValue(value);
    }

    private String normalizeValue(String value) {
        String text = XPathBy.getNormalizedText(value);
        boolean trimOnNormalize = true;
        if (text != null && trimOnNormalize) {
            text = text.trim();
        }
        return text;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String tooltipFor(String place) {
        return tooltipForIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String tooltipForIn(String place, String container) {
        return valueOfAttributeOnIn("title", place, container);
    }

    @WaitUntil
    public String targetOfLink(String place) {
        AndroidElement linkElement = androidHelper.getLink(place);
        return getLinkTarget(linkElement);
    }

    private String getLinkTarget(AndroidElement linkElement) {
        String target = null;
        if (linkElement != null) {
            target = linkElement.getAttribute("href");
            if (target == null) {
                target = linkElement.getAttribute("src");
            }
        }
        return target;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfAttributeOn(String attribute, String place) {
        return valueOfAttributeOnIn(attribute, place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfAttributeOnIn(String attribute, String place, String container) {
        String result = null;
        AndroidElement element = getElement(place, container);
        if (element != null) {
            result = element.getAttribute(attribute);
        }
        return result;
    }

    private String valueFor(AndroidElement element) {
        String result = null;
        if (element != null) {
            if (isSelect(element)) {
                AndroidElement selected = getFirstSelectedOption(element);
                result = getElementText(selected);
            } else {
                String elementType = element.getAttribute("type");
                if ("checkbox".equals(elementType) || "radio".equals(elementType)) {
                    result = String.valueOf(element.isSelected());
                } else if ("li".equalsIgnoreCase(element.getTagName())) {
                    result = getElementText(element);
                } else {
                    result = element.getAttribute("value");
                    if (result == null) {
                        result = getElementText(element);
                    }
                }
            }
        }
        return result;
    }

    private AndroidElement getFirstSelectedOption(AndroidElement selectElement) {
        SelectHelper s = new SelectHelper(selectElement);
        return (AndroidElement) s.getFirstSelectedOption();
    }

    private List<WebElement> getSelectedOptions(AndroidElement selectElement) {
        SelectHelper s = new SelectHelper(selectElement);
        return s.getAllSelectedOptions();
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> valuesOf(String place) {
        return valuesFor(place);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> valuesOfIn(String place, String container) {
        return valuesForIn(place, container);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> valuesFor(String place) {
        return valuesForIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> valuesForIn(String place, String container) {
        AndroidElement element = getElement(place, container);
        if (element == null) {
            return emptyList();
        } else if ("ul".equalsIgnoreCase(element.getTagName()) || "ol".equalsIgnoreCase(element.getTagName())) {
            return getValuesFromList(element);
        } else if (isSelect(element)) {
            return getValuesFromSelect(element);
        } else {
            return singletonList(valueFor(element));
        }
    }

    private List<String> getValuesFromList(AndroidElement element) {
        ArrayList<String> values = new ArrayList<>();
        List<MobileElement> items = element.findElements(By.tagName("li"));
        for (MobileElement item : items) {
            if (item.isDisplayed()) {
                values.add(getElementText((AndroidElement) item));
            }
        }
        return values;
    }

    private List<String> getValuesFromSelect(AndroidElement element) {
        ArrayList<String> values = new ArrayList<>();
        List<WebElement> options = getSelectedOptions(element);
        for (WebElement item : options) {
            values.add(getElementText((AndroidElement) item));
        }
        return values;
    }

    @WaitUntil
    public boolean clear(String place) {
        return clearIn(place, null);
    }

    @WaitUntil
    public boolean clearIn(String place, String container) {
        AndroidElement element = getElement(place, container);
        if (element != null) {
            return clear(element);
        }
        return false;
    }

    private AndroidElement getElement(String place, String container) {
        return doInContainer(container, () -> androidHelper.getElement(place));
    }

    public void waitMilliSecondAfterScroll(int msToWait) {
        waitAfterScroll = msToWait;
    }

    private String getElementText(AndroidElement element) {
        if (element != null) {
            scrollIfNotOnScreen(element);
            return androidHelper.getText(element);
        }
        return null;
    }

    public boolean scrollUp() {
        boolean result = androidHelper.scrollUpOrDown(true);
        waitAfterScroll(waitAfterScroll);
        return result;
    }

    public boolean scrollDown() {
        boolean result = androidHelper.scrollUpOrDown(false);
        waitAfterScroll(waitAfterScroll);
        return result;
    }

    public boolean scrollDownTo(String place) {
        return scrollUpOrDown(place, false);
    }

    public boolean scrollDownToIn(String place, String container) {
        return doInContainer(container, () -> scrollDownTo(place));
    }

    public boolean scrollUpTo(String place) {
        return scrollUpOrDown(place, true);
    }

    public boolean scrollUpToIn(String place, String container) {
        return doInContainer(container, () -> scrollUpTo(place));
    }

    private boolean scrollUpOrDown(String place, boolean up) {
        boolean isVisible = isVisibleOnPage(place);
        if (isVisible) {
            return true;
        }
        int counter = 0;
        while (counter < 5) {
            androidHelper.scrollUpOrDown(up);
            waitAfterScroll(waitAfterScroll);
            counter = counter + 1;
            isVisible = isVisibleOnPage(place);
            if (isVisible) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scrolls window so top of element becomes visible.
     *
     * @param element element to scroll to.
     */
    protected void scrollTo(AndroidElement element) {
        androidHelper.scrollTo(element);
        waitAfterScroll(waitAfterScroll);
    }

    /**
     * Wait after the scroll if needed
     *
     * @param msToWait amount of ms to wait after the scroll
     */
    private void waitAfterScroll(int msToWait) {
        if (msToWait > 0) {
            waitMilliseconds(msToWait);
        }
    }

    /**
     * Scrolls window if element is not currently visible so top of element becomes visible.
     *
     * @param element element to scroll to.
     */
    private void scrollIfNotOnScreen(AndroidElement element) {
        if (!element.isDisplayed()) {
            scrollTo(element);
        }
    }

    /**
     * Determines whether element is enabled (i.e. can be clicked).
     *
     * @param place element to check.
     * @return true if element is enabled.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isEnabled(String place) {
        return isEnabledIn(place, null);
    }

    /**
     * Determines whether element is enabled (i.e. can be clicked).
     *
     * @param place     element to check.
     * @param container parent of place.
     * @return true if element is enabled.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isEnabledIn(String place, String container) {
        boolean result = false;
        AndroidElement element = getElementToCheckVisibility(place, container);
        if (element != null) {
            if ("label".equalsIgnoreCase(element.getTagName())) {
                // for labels we want to know whether their target is enabled, not the label itself
                AndroidElement labelTarget = androidHelper.getLabelledElement(element);
                if (labelTarget != null) {
                    element = labelTarget;
                }
            }
            result = element.isEnabled();
        }
        return result;
    }

    /**
     * Determines whether element can be see in window.
     *
     * @param place element to check.
     * @return true if element is displayed and in viewport.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isVisible(String place) {
        return isVisibleIn(place, null);
    }

    /**
     * Determines whether element can be see in window.
     *
     * @param place     element to check.
     * @param container parent of place.
     * @return true if element is displayed and in viewport.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isVisibleIn(String place, String container) {
        return isVisibleImpl(place, container, true);
    }

    /**
     * Determines whether element is somewhere in window.
     *
     * @param place element to check.
     * @return true if element is displayed.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isVisibleOnPage(String place) {
        return isVisibleOnPageIn(place, null);
    }

    /**
     * Determines whether element is somewhere in window.
     *
     * @param place     element to check.
     * @param container parent of place.
     * @return true if element is displayed.
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isVisibleOnPageIn(String place, String container) {
        return isVisibleImpl(place, container, false);
    }

    /**
     * Determines whether element is not visible (or disappears within the specified timeout)
     *
     * @param place element to check
     * @return true if the element is not displayed (anymore)
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isNotVisible(String place) {
        return isNotVisibleIn(place, null);
    }

    /**
     * Determines whether element is not visible (or disappears within the specified timeout)
     *
     * @param place     element to check.
     * @param container parent of place.
     * @return true if the element is not displayed (anymore)
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isNotVisibleIn(String place, String container) {
        return !isVisibleImpl(place, container, true);
    }

    /**
     * Determines whether element is not on the page (or disappears within the specified timeout)
     *
     * @param place element to check.
     * @return true if element is not on the page (anymore).
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isNotVisibleOnPage(String place) {
        return isNotVisibleOnPageIn(place, null);
    }

    /**
     * Determines whether element is not on the page (or disappears within the specified timeout)
     *
     * @param place     element to check.
     * @param container parent of place.
     * @return true if the element is not on the page (anymore)
     */
    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean isNotVisibleOnPageIn(String place, String container) {
        return !isVisibleImpl(place, container, false);
    }

    private boolean isVisibleImpl(String place, String container, boolean checkOnScreen) {
        AndroidElement element = getElementToCheckVisibility(place, container);
        return androidHelper.checkVisible(element, checkOnScreen);
    }

    private AndroidElement getElementToCheckVisibility(String place, String container) {
        return doInContainer(container, () -> findByTechnicalSelectorOr(place, place1 -> androidHelper.getElementToCheckVisibility(place1)));
    }

    /**
     * @param timeout number of seconds before waitUntil() throw TimeOutException.
     */
    public void secondsBeforeTimeout(int timeout) {
        secondsBeforeTimeout = timeout;
        secondsBeforePageLoadTimeout();
    }

    /**
     * @return number of seconds Selenium will wait at most for a request to load a page.
     */
    public int secondsBeforePageLoadTimeout() {
        return secondsBeforePageLoadTimeout;
    }

    /**
     * @param directory sets base directory where screenshots will be stored.
     */
    public void screenshotBaseDirectory(String directory) {
        if (directory.equals("") || directory.endsWith("/") || directory.endsWith("\\")) {
            screenshotBase = directory;
        } else {
            screenshotBase = directory + "/";
        }
    }

    /**
     * @param height height to use to display screenshot images
     */
    public void screenshotShowHeight(String height) {
        screenshotHeight = height;
    }

    /**
     * @return (escaped) xml content of current page.
     */
    public String pageSource() {
        return getEnvironment().getHtml(androidHelper.getSourceXml());
    }

    private String savePageSource(String fileName, String linkText) {
        PageSourceSaver saver = androidHelper.getPageSourceSaver(pageSourceBase);
        // make href to file
        String url = saver.savePageSource(fileName);
        return String.format("<a href=\"%s\" target=\"_blank\">%s</a>", url, linkText);
    }

    /**
     * Takes screenshot from current page
     *
     * @param basename filename (below screenshot base directory).
     * @return location of screenshot.
     */
    public String takeScreenshot(String basename) {
        String screenshotFile = createScreenshot(basename);
        if (screenshotFile == null) {
            throw new SlimFixtureException(false, "Unable to take screenshot: does the webdriver support it?");
        } else {
            screenshotFile = getScreenshotLink(screenshotFile);
        }
        return screenshotFile;
    }

    private String getScreenshotLink(String screenshotFile) {
        String wikiUrl = getWikiUrl(screenshotFile);
        if (wikiUrl != null) {
            // make href to screenshot

            if ("".equals(screenshotHeight)) {
                wikiUrl = String.format("<a href=\"%s\" target=\"_blank\">%s</a>",
                        wikiUrl, screenshotFile);
            } else {
                wikiUrl = String.format("<a href=\"%1$s\" target=\"_blank\"><img src=\"%1$s\" title=\"%2$s\" height=\"%3$s\"/></a>",
                        wikiUrl, screenshotFile, screenshotHeight);
            }
            screenshotFile = wikiUrl;
        }
        return screenshotFile;
    }

    private String createScreenshot(String basename) {
        String name = getScreenshotBasename(basename);
        return androidHelper.takeScreenshot(name);
    }

    private String createScreenshot(String basename, Throwable t) {
        String screenshotFile;
        byte[] screenshotInException = androidHelper.findScreenshot(t);
        if (screenshotInException == null || screenshotInException.length == 0) {
            screenshotFile = createScreenshot(basename);
        } else {
            String name = getScreenshotBasename(basename);
            screenshotFile = androidHelper.writeScreenshot(name, screenshotInException);
        }
        return screenshotFile;
    }

    private String getScreenshotBasename(String basename) {
        return screenshotBase + basename;
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. Because of this contract, the return type must not be Void.
     *
     * @param <T>       the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @return result of condition.
     * @throws SlimFixtureException if condition was not met before secondsBeforeTimeout.
     */
    private <T> T waitUntil(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            String message = getTimeoutMessage(e);
            return lastAttemptBeforeThrow(condition, new SlimFixtureException(false, message, e));
        }
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. If that does not occur the whole test is stopped.
     * Because of this contract, the return type must not be Void.
     *
     * @param <T>       the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @return result of condition.
     * @throws TimeoutStopTestException if condition was not met before secondsBeforeTimeout.
     */
    private <T> T waitUntilOrStop(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            try {
                return handleTimeoutException(e);
            } catch (TimeoutStopTestException tste) {
                return lastAttemptBeforeThrow(condition, tste);
            }
        }
    }

    /**
     * Tries the condition one last time before throwing an exception.
     * This to prevent exception messages in the wiki that show no problem, which could happen if the
     * window content has changed between last (failing) try at condition and generation of the exception.
     *
     * @param <T>       the return type of the method, which must not be Void
     * @param condition condition that caused exception.
     * @param e         exception that will be thrown if condition does not return a result.
     * @return last attempt results, if not null or false.
     * @throws SlimFixtureException throws e if last attempt returns null.
     */
    private <T> T lastAttemptBeforeThrow(ExpectedCondition<T> condition, SlimFixtureException e) {
        T lastAttemptResult = null;
        try {
            // last attempt to ensure condition has not been met
            // this to prevent messages that show no problem
            lastAttemptResult = condition.apply(androidHelper.driver());
        } catch (Exception t) {
            // ignore
        }
        if (lastAttemptResult == null || Boolean.FALSE.equals(lastAttemptResult)) {
            throw e;
        }
        return lastAttemptResult;
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. If that does not occur null is returned.
     * Because of this contract, the return type must not be Void.
     *
     * @param <T>       the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @return result of condition.
     */
    private <T> T waitUntilOrNull(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            return null;
        }
    }

    private <T> T waitUntilImpl(ExpectedCondition<T> condition) {
        return androidHelper.waitUntil(secondsBeforeTimeout, condition);
    }

    public boolean refreshSearchContext() {
        // copy path so we can retrace after clearing it
        List<String> fullPath = new ArrayList<>(getCurrentSearchContextPath());
        return refreshSearchContext(fullPath, Math.min(fullPath.size(), minStaleContextRefreshCount));
    }

    private boolean refreshSearchContext(List<String> fullPath, int maxRetries) {
        clearSearchContext();
        for (String container : fullPath) {
            try {
                setSearchContextTo(container);
            } catch (RuntimeException se) {
                if (maxRetries < 1 || !(se instanceof WebDriverException)
                        || !androidHelper.isStaleElementException((WebDriverException) se)) {
                    // not the entire context was refreshed, clear it to prevent an 'intermediate' search context
                    clearSearchContext();
                    throw new SlimFixtureException("Search context is 'stale' and could not be refreshed. Context was: " + fullPath
                            + ". Error when trying to refresh: " + container, se);
                } else {
                    // search context went stale while setting, retry
                    return refreshSearchContext(fullPath, maxRetries - 1);
                }
            }
        }
        return true;
    }

    private <T> T handleTimeoutException(TimeoutException e) {
        String message = getTimeoutMessage(e);
        throw new TimeoutStopTestException(false, message, e);
    }

    private String getTimeoutMessage(TimeoutException e) {
        String messageBase = String.format("Timed-out waiting (after %ss)", secondsBeforeTimeout);
        return getSlimFixtureExceptionMessage("timeouts", "timeout", messageBase, e);
    }

    private String getSlimFixtureExceptionMessage(String screenshotFolder, String screenshotFile, String messageBase, Throwable t) {
        String screenshotBaseName = String.format("%s/%s/%s", screenshotFolder, getClass().getSimpleName(), screenshotFile);
        return getSlimFixtureExceptionMessage(screenshotBaseName, messageBase, t);
    }

    private String getSlimFixtureExceptionMessage(String screenshotBaseName, String messageBase, Throwable t) {
        String exceptionMsg = getExceptionMessageText(messageBase, t);
        // take a screenshot of what was on screen
        String screenshotTag = getExceptionScreenshotTag(screenshotBaseName, messageBase, t);
        String label = getExceptionPageSourceTag(screenshotBaseName, messageBase, t);

        return String.format("<div><div>%s.</div><div>%s:%s</div></div>", exceptionMsg, label, screenshotTag);
    }

    private String getExceptionMessageText(String messageBase, Throwable t) {
        String message = messageBase;
        if (message == null) {
            if (t == null) {
                message = "";
            } else {
                message = ExceptionUtils.getStackTrace(t);
            }
        }
        return formatExceptionMsg(message);
    }

    private String getExceptionScreenshotTag(String screenshotBaseName, String messageBase, Throwable t) {
        String screenshotTag = "(Screenshot not available)";
        try {
            String screenShotFile = createScreenshot(screenshotBaseName, t);
            screenshotTag = getScreenshotLink(screenShotFile);
        } catch (UnhandledAlertException e) {
            // https://code.google.com/p/selenium/issues/detail?id=4412

            logger.error("Unable to take screenshot while alert is present for exception: {}", messageBase);
        } catch (Exception sse) {
            logger.error("Unable to take screenshot for exception: {}\n {}", messageBase, sse);
        }
        return screenshotTag;
    }

    private String getExceptionPageSourceTag(String screenshotBaseName, String messageBase, Throwable t) {
        String label = "Page content";
        try {
            String fileName;
            if (t != null) {
                fileName = t.getClass().getName();
            } else if (screenshotBaseName != null) {
                fileName = screenshotBaseName;
            } else {
                fileName = "exception";
            }
            label = savePageSource(fileName, label);
        } catch (UnhandledAlertException e) {
            // https://code.google.com/p/selenium/issues/detail?id=4412
            logger.error("Unable to capture page source while alert is present for exception: {}", messageBase);
        } catch (Exception e) {
            logger.error("Unable to capture page source for exception: {}\n {}", messageBase, e);
        }
        return label;
    }

    private String formatExceptionMsg(String value) {
        return StringEscapeUtils.escapeHtml4(value);
    }

    /**
     * Sets SeleniumHelper to use, for testing purposes.
     *
     * @param helper helper to use.
     */
    private void setAndroidHelper(AndroidHelper helper) {
        androidHelper = helper;
    }

    public AndroidElement findByTechnicalSelectorOr(String place, Function<String, AndroidElement> supplierF) {
        return androidHelper.findByTechnicalSelectorOr(place, () -> supplierF.apply(place));
    }

    public boolean clickUntilValueOfIs(String clickPlace, String checkPlace, String expectedValue) {
        return repeatUntil(getClickUntilValueIs(clickPlace, checkPlace, expectedValue));
    }

    public boolean clickUntilValueOfIsNot(String clickPlace, String checkPlace, String expectedValue) {
        return repeatUntilNot(getClickUntilValueIs(clickPlace, checkPlace, expectedValue));
    }

    private RepeatCompletion getClickUntilValueIs(String clickPlace, String checkPlace, String expectedValue) {
        String place = cleanupValue(clickPlace);
        return getClickUntilCompletion(place, checkPlace, expectedValue);
    }

    private RepeatCompletion getClickUntilCompletion(String place, String checkPlace, String expectedValue) {
        return new ConditionBasedRepeatUntil(true, d -> click(place), d -> checkValueIs(checkPlace, expectedValue));
    }

    private <T> ExpectedCondition<T> wrapConditionForFramesIfNeeded(ExpectedCondition<T> condition) {
        if (implicitFindInFrames) {
            condition = androidHelper.conditionForAllFrames(condition);
        }
        return condition;
    }

    @Override
    protected boolean repeatUntil(RepeatCompletion repeat) {
        // During repeating we reduce the timeout used for finding elements,
        // but the page load timeout is kept as-is (which takes extra work because secondsBeforeTimeout(int)
        // also changes that.
        int previousTimeout = secondsBeforeTimeout;
        try {
            int timeoutDuringRepeat = Math.max((Math.toIntExact(repeatInterval() / 1000)), 1);
            secondsBeforeTimeout(timeoutDuringRepeat);
            secondsBeforePageLoadTimeout();
            return super.repeatUntil(repeat);
        } finally {
            secondsBeforeTimeout(previousTimeout);
            secondsBeforePageLoadTimeout();
        }
    }

    private boolean checkValueIs(String place, String expectedValue) {
        boolean match;
        String actual = valueOf(place);
        if (expectedValue == null) {
            match = actual == null;
        } else {
            match = cleanExpectedValue(expectedValue).equals(actual);
        }
        return match;
    }

    public void setImplicitFindInFramesTo(boolean implicitFindInFrames) {
        this.implicitFindInFrames = implicitFindInFrames;
    }

    /**
     * Simulates 'select all' (e.g. Ctrl+A on Windows) on the active element.
     *
     * @return whether an active element was found.
     */
    @WaitUntil
    public boolean selectAll() {
        return androidHelper.selectAll();
    }

    /**
     * @return text currently selected in window, or empty string if no text is selected.
     */
    public String getSelectionText() {
        return androidHelper.getSelectionText();
    }

    private class ConditionBasedRepeatUntil extends FunctionalCompletion {
        public ConditionBasedRepeatUntil(boolean wrapIfNeeded,
                                         ExpectedCondition<?> repeatCondition,
                                         ExpectedCondition<Boolean> finishedCondition) {
            this(wrapIfNeeded, repeatCondition, wrapIfNeeded, finishedCondition);
        }

        public ConditionBasedRepeatUntil(boolean wrapRepeatIfNeeded,
                                         ExpectedCondition<?> repeatCondition,
                                         boolean wrapFinishedIfNeeded,
                                         ExpectedCondition<Boolean> finishedCondition) {
            if (wrapRepeatIfNeeded) {
                repeatCondition = wrapConditionForFramesIfNeeded(repeatCondition);
            }
            ExpectedCondition<?> finalRepeatCondition = repeatCondition;
            if (wrapFinishedIfNeeded) {
                finishedCondition = wrapConditionForFramesIfNeeded(finishedCondition);
            }
            ExpectedCondition<Boolean> finalFinishedCondition = finishedCondition;

            setIsFinishedSupplier(() -> waitUntilOrNull(finalFinishedCondition));
            setRepeater(() -> waitUntil(finalRepeatCondition));
        }
    }
}
