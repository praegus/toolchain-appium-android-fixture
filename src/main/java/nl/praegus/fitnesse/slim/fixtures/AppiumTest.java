package nl.praegus.fitnesse.slim.fixtures;

import fitnesse.slim.fixtureInteraction.FixtureInteraction;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileElement;
import nl.hsac.fitnesse.fixture.slim.SlimFixture;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import nl.hsac.fitnesse.fixture.slim.StopTestException;
import nl.hsac.fitnesse.fixture.slim.web.TimeoutStopTestException;
import nl.hsac.fitnesse.fixture.slim.web.annotation.TimeoutPolicy;
import nl.hsac.fitnesse.fixture.slim.web.annotation.WaitUntil;
import nl.hsac.fitnesse.fixture.util.ReflectionHelper;
import nl.hsac.fitnesse.fixture.util.selenium.AllFramesDecorator;
import nl.hsac.fitnesse.fixture.util.selenium.PageSourceSaver;
import nl.hsac.fitnesse.fixture.util.selenium.SelectHelper;
import nl.hsac.fitnesse.fixture.util.selenium.SeleniumHelper;
import nl.hsac.fitnesse.fixture.util.selenium.StaleContextException;
import nl.hsac.fitnesse.fixture.util.selenium.by.AltBy;
import nl.hsac.fitnesse.fixture.util.selenium.by.GridBy;
import nl.hsac.fitnesse.fixture.util.selenium.by.ListItemBy;
import nl.hsac.fitnesse.fixture.util.selenium.by.OptionBy;
import nl.hsac.fitnesse.fixture.util.selenium.by.XPathBy;
import nl.hsac.fitnesse.slim.interaction.ExceptionHelper;
import nl.praegus.fitnesse.slim.util.AppiumHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Select;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static nl.hsac.fitnesse.fixture.util.selenium.SelectHelper.isSelect;

/**
 * Specialized class to test applications (iOS, Android, Windows) using Appium.
 */
@SuppressWarnings({"unused", "WeakerAccess", "squid:S1172"})
public abstract class AppiumTest<T extends MobileElement, D extends AppiumDriver<T>> extends SlimFixture {
    private final List<String> currentSearchContextPath = new ArrayList<>();
    protected AppiumHelper<T, D> appiumHelper;
    private ReflectionHelper reflectionHelper;
    private boolean implicitFindInFrames = true;
    private int secondsBeforeTimeout;
    private int secondsBeforePageLoadTimeout;
    protected int waitAfterScroll = 500;
    private String screenshotBase = new File(filesDir, "screenshots").getPath() + "/";
    private String screenshotHeight = "200";
    private String pageSourceBase = new File(filesDir, "pagesources").getPath() + "/";
    private boolean sendCommandForControlOnMac = false;
    private boolean trimOnNormalize = true;

    protected List<String> getCurrentSearchContextPath() {
        return currentSearchContextPath;
    }

    protected int minStaleContextRefreshCount = 5;

    private boolean abortOnException;

    public AppiumTest() {
        this.appiumHelper = (AppiumHelper<T, D>) getEnvironment().getSeleniumHelper();
        this.reflectionHelper = getEnvironment().getReflectionHelper();
        secondsBeforeTimeout(getEnvironment().getSeleniumDriverManager().getDefaultTimeoutSeconds());
        setImplicitFindInFramesTo(false);
    }

    public AppiumTest(int secondsBeforeTimeout) {
        this.appiumHelper = (AppiumHelper<T, D>) getEnvironment().getSeleniumHelper();
        this.reflectionHelper = getEnvironment().getReflectionHelper();
        secondsBeforeTimeout(secondsBeforeTimeout);
        setImplicitFindInFramesTo(false);
    }

    public AppiumTest(AppiumHelper<T, D> appiumHelper, ReflectionHelper reflectionHelper) {
        this.appiumHelper = appiumHelper;
        this.reflectionHelper = reflectionHelper;
        secondsBeforeTimeout(getEnvironment().getSeleniumDriverManager().getDefaultTimeoutSeconds());
        setImplicitFindInFramesTo(false);
    }

    public boolean launchApp() {
        getDriver().launchApp();
        return true;
    }

    public boolean closeApp() {
        getDriver().closeApp();
        return true;
    }

    public void abortOnException(boolean abort) {
        abortOnException = abort;
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

    public String savePageSource() {
        String fileName = "xmlView_" + System.currentTimeMillis();
        return savePageSource(fileName, fileName + ".xml");
    }

    protected D getDriver() {
        return appiumHelper.driver();
    }

    protected boolean clear(WebElement element) {
        if (element != null) {
            element.clear();
            return true;
        }
        return false;
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForToContain(String place, String text) {
        T element = this.getElement(place, null);
        return null != element && element.getText().contains(text);
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

    protected Object invokedWrappedInWaitUntil(WaitUntil waitUntil, FixtureInteraction interaction, Method method, Object[] arguments) {
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

    /**
     * Called when an alert is either dismissed or accepted.
     *
     * @param accepted true if the alert was accepted, false if dismissed.
     */
    protected void onAlertHandled(boolean accepted) {
        // if we were looking in nested frames, we could not go back to original frame
        // because of the alert. Ensure we do so now the alert is handled.
        appiumHelper.resetFrameDepthOnAlertError();
    }

    /**
     * Activates main/top-level iframe (i.e. makes it the current frame).
     */
    public void switchToDefaultContent() {
        appiumHelper.switchToDefaultContent();
        clearSearchContext();
    }

    /**
     * Activates specified child frame of current iframe.
     *
     * @param technicalSelector selector to find iframe.
     * @return true if iframe was found.
     */
    public boolean switchToFrame(String technicalSelector) {
        boolean result = false;
        T iframe = appiumHelper.getElement(technicalSelector);
        if (iframe != null) {
            appiumHelper.switchToFrame(iframe);
            result = true;
        }
        return result;
    }

    /**
     * Activates parent frame of current iframe.
     * Does nothing if when current frame is the main/top-level one.
     */
    public void switchToParentFrame() {
        appiumHelper.switchToParentFrame();
    }

    public String pageTitle() {
        return appiumHelper.getPageTitle();
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
        WebElement element = getElement(place, container);
        return enter(element, value, shouldClear);
    }

    protected boolean enter(WebElement element, String value, boolean shouldClear) {
        boolean result = element != null && appiumHelper.isInteractable(element);
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

    @WaitUntil
    public boolean enterDateAs(String date, String place) {
        WebElement element = getElementToSendValue(place);
        boolean result = element != null && appiumHelper.isInteractable(element);
        if (result) {
            appiumHelper.fillDateInput(element, date);
        }
        return result;
    }

    protected T getElementToSendValue(String place) {
        return getElement(place, null);
    }

    /**
     * Simulates pressing the 'Tab' key.
     *
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressTab() {
        return sendKeysToActiveElement(Keys.TAB);
    }

    /**
     * Simulates pressing the 'Enter' key.
     *
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressEnter() {
        return sendKeysToActiveElement(Keys.ENTER);
    }

    /**
     * Simulates pressing the 'Esc' key.
     *
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressEsc() {
        return sendKeysToActiveElement(Keys.ESCAPE);
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
     * Simulates pressing a key (or a combination of keys).
     * (Unfortunately not all combinations seem to be accepted by all drivers, e.g.
     *
     * @param key key to press, can be a normal letter (e.g. 'M') or a special key (e.g. 'down').
     *            Combinations can be passed by separating the keys to send with '+' (e.g. Command + T).
     * @return true, if an element was active the key could be sent to.
     */
    public boolean press(String key) {
        CharSequence s;
        String[] parts = key.split("\\s*\\+\\s*");
        if (parts.length > 1
                && !"".equals(parts[0]) && !"".equals(parts[1])) {
            CharSequence[] sequence = new CharSequence[parts.length];
            for (int i = 0; i < parts.length; i++) {
                sequence[i] = parseKey(parts[i]);
            }
            s = Keys.chord(sequence);
        } else {
            s = parseKey(key);
        }

        return sendKeysToActiveElement(s);
    }

    protected CharSequence parseKey(String key) {
        CharSequence s;
        try {
            s = Keys.valueOf(key.toUpperCase());
            if (Keys.CONTROL.equals(s) && sendCommandForControlOnMac) {
                s = appiumHelper.getControlOrCommand();
            }
        } catch (IllegalArgumentException e) {
            s = key;
        }
        return s;
    }

    /**
     * Simulates pressing keys.
     *
     * @param keys keys to press.
     * @return true, if an element was active the keys could be sent to.
     */
    protected boolean sendKeysToActiveElement(CharSequence... keys) {
        boolean result = false;
        WebElement element = appiumHelper.getActiveElement();
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
    protected void sendValue(WebElement element, String value) {
        if (StringUtils.isNotEmpty(value)) {
            String keys = cleanupValue(value);
            element.sendKeys(keys);
        }
    }

    @WaitUntil
    public boolean selectAs(String value, String place) {
        WebElement element = appiumHelper.getElement(place);
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
        WebElement element = appiumHelper.getElement(place);
        return clickSelectOption(element, value);
    }

    @WaitUntil
    public boolean selectForIn(String value, String place, String container) {
        return Boolean.TRUE.equals(doInContainer(container, () -> selectFor(value, place)));
    }

    @WaitUntil
    public boolean enterForHidden(String value, String idOrName) {
        return appiumHelper.setHiddenInputValue(idOrName, value);
    }

    protected boolean clickSelectOption(WebElement element, String optionValue) {
        if (element != null && isSelect(element)) {
            optionValue = cleanupValue(optionValue);
            By optionBy = new OptionBy(optionValue);
            WebElement option = element.findElement(optionBy);
            return clickSelectOption(element, option);
        }
        return false;
    }

    protected boolean clickSelectOption(WebElement element, WebElement option) {
        boolean result = false;
        if (option != null) {
            // we scroll containing select into view (not the option)
            // based on behavior for option in https://www.w3.org/TR/webdriver/#element-click
            scrollIfNotOnScreen(element);
            if (appiumHelper.isInteractable(option)) {
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

    protected boolean click(String place, String container) {
        WebElement element = getElementToClick(cleanupValue(place), container);
        return clickElement(element);
    }

    @WaitUntil
    public boolean doubleClick(String place) {
        return doubleClickIn(place, null);
    }

    @WaitUntil
    public boolean doubleClickIn(String place, String container) {
        WebElement element = getElementToClick(cleanupValue(place), container);
        return doubleClick(element);
    }

    protected boolean doubleClick(WebElement element) {
        return doIfInteractable(element, () -> appiumHelper.doubleClick(element));
    }

    public void setSendCommandForControlOnMacTo(boolean sendCommand) {
        sendCommandForControlOnMac = sendCommand;
    }

    public boolean sendCommandForControlOnMac() {
        return sendCommandForControlOnMac;
    }

    protected Keys controlKey() {
        return sendCommandForControlOnMac ? appiumHelper.getControlOrCommand() : Keys.CONTROL;
    }

    @WaitUntil
    public boolean dragAndDropTo(String source, String destination) {
        WebElement sourceElement = appiumHelper.getElementToClick(cleanupValue(source));
        WebElement destinationElement = appiumHelper.getElementToClick(cleanupValue(destination));

        if ((sourceElement != null) && (destinationElement != null)) {
            scrollIfNotOnScreen(sourceElement);
            if (appiumHelper.isInteractable(sourceElement) && destinationElement.isDisplayed()) {
                appiumHelper.dragAndDrop(sourceElement, destinationElement);
                return true;
            }
        }
        return false;
    }

    protected T getElementToClick(String place, String container) {
        return doInContainer(container, () -> appiumHelper.getElementToClick(place));
    }

    /**
     * Convenience method to create custom heuristics in subclasses.
     *
     * @param container container to use (use <code>null</code> for current container), can be a technical selector.
     * @param place     place to look for inside container, can be a technical selector.
     * @param suppliers suppliers that will be used in turn until an element is found, IF place is not a technical selector.
     * @return first hit of place, technical selector or result of first supplier that provided result.
     */
    protected T findFirstInContainer(String container, String place, Supplier<? extends T>... suppliers) {
        return doInContainer(container, () -> appiumHelper.findByTechnicalSelectorOr(place, suppliers));
    }

    protected <R> R doInContainer(String container, Supplier<R> action) {
        if (container == null) {
            return action.get();
        } else {
            return doInContainer(() -> getContainerElement(cleanupValue(container)), action);
        }
    }

    protected <R> R doInContainer(Supplier<T> containerSupplier, Supplier<R> action) {
        R result = null;
        int retryCount = minStaleContextRefreshCount;
        do {
            try {
                T containerElement = containerSupplier.get();
                if (containerElement != null) {
                    result = appiumHelper.doInContext(containerElement, action);
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
        WebElement containerElement = getContainerElement(container);
        boolean result = false;
        if (containerElement != null) {
            getCurrentSearchContextPath().add(container);
            appiumHelper.setCurrentContext(containerElement);
            result = true;
        }
        return result;
    }

    public void clearSearchContext() {
        getCurrentSearchContextPath().clear();
        appiumHelper.setCurrentContext(null);
    }

    protected T getContainerElement(String container) {
        return findByTechnicalSelectorOr(container, container1 -> appiumHelper.getElement(container1));
    }

    protected boolean clickElement(WebElement element) {
        return doIfInteractable(element, () -> element.click());
    }

    protected boolean doIfInteractable(WebElement element, Runnable action) {
        boolean result = false;
        if (element != null) {
            scrollIfNotOnScreen(element);
            if (appiumHelper.isInteractable(element)) {
                action.run();
                result = true;
            }
        }
        return result;
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForPage(String pageName) {
        return pageTitle().equals(pageName);
    }

    public boolean waitForTagWithText(String tagName, String expectedText) {
        return waitForElementWithText(By.tagName(tagName), expectedText);
    }

    public boolean waitForClassWithText(String cssClassName, String expectedText) {
        return waitForElementWithText(By.className(cssClassName), expectedText);
    }

    protected boolean waitForElementWithText(By by, String expectedText) {
        String textToLookFor = cleanExpectedValue(expectedText);
        return waitUntilOrStop(webDriver -> {
            boolean ok = false;

            List<WebElement> elements = webDriver.findElements(by);
            if (elements != null) {
                for (WebElement element : elements) {
                    // we don't want stale elements to make single
                    // element false, but instead we stop processing
                    // current list and do a new findElements
                    ok = hasText(element, textToLookFor);
                    if (ok) {
                        // no need to continue to check other elements
                        break;
                    }
                }
            }
            return ok;
        });
    }

    protected String cleanExpectedValue(String expectedText) {
        return cleanupValue(expectedText);
    }

    protected boolean hasText(WebElement element, String textToLookFor) {
        boolean ok;
        String actual = getElementText(element);
        if (textToLookFor == null) {
            ok = actual == null;
        } else {
            if (StringUtils.isEmpty(actual)) {
                String value = element.getAttribute("value");
                if (!StringUtils.isEmpty(value)) {
                    actual = value;
                }
            }
            if (actual != null) {
                actual = actual.trim();
            }
            ok = textToLookFor.equals(actual);
        }
        return ok;
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForClass(String cssClassName) {
        boolean ok = false;

        WebElement element = appiumHelper.findElement(By.className(cssClassName));
        if (element != null) {
            ok = true;
        }
        return ok;
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForVisible(String place) {
        return waitForVisibleIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.STOP_TEST)
    public boolean waitForVisibleIn(String place, String container) {
        WebElement element = getElementToCheckVisibility(place, container);
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
        WebElement element = getElement(place, container);
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

    protected List<String> normalizeValues(List<String> values) {
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                String normalized = normalizeValue(value);
                values.set(i, normalized);
            }
        }
        return values;
    }

    protected String normalizeValue(String value) {
        String text = XPathBy.getNormalizedText(value);
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
        WebElement linkElement = appiumHelper.getLink(place);
        return getLinkTarget(linkElement);
    }

    protected String getLinkTarget(WebElement linkElement) {
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
        WebElement element = getElement(place, container);
        if (element != null) {
            result = element.getAttribute(attribute);
        }
        return result;
    }

    protected String valueFor(By by) {
        WebElement element = appiumHelper.findElement(by);
        return valueFor(element);
    }

    protected String valueFor(WebElement element) {
        String result = null;
        if (element != null) {
            if (isSelect(element)) {
                WebElement selected = getFirstSelectedOption(element);
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

    protected WebElement getFirstSelectedOption(WebElement selectElement) {
        SelectHelper s = new SelectHelper(selectElement);
        return s.getFirstSelectedOption();
    }

    protected List<WebElement> getSelectedOptions(WebElement selectElement) {
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
        WebElement element = getElement(place, container);
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

    private List<String> getValuesFromList(WebElement element) {
        ArrayList<String> values = new ArrayList<>();
        List<WebElement> items = element.findElements(By.tagName("li"));
        for (WebElement item : items) {
            if (item.isDisplayed()) {
                values.add(getElementText(item));
            }
        }
        return values;
    }

    private List<String> getValuesFromSelect(WebElement element) {
        ArrayList<String> values = new ArrayList<>();
        List<WebElement> options = getSelectedOptions(element);
        for (WebElement item : options) {
            values.add(getElementText(item));
        }
        return values;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> normalizedValuesOf(String place) {
        return normalizedValuesFor(place);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> normalizedValuesOfIn(String place, String container) {
        return normalizedValuesForIn(place, container);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> normalizedValuesFor(String place) {
        return normalizedValuesForIn(place, null);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> normalizedValuesForIn(String place, String container) {
        List<String> values = valuesForIn(place, container);
        return normalizeValues(values);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public Integer numberFor(String place) {
        Integer number = null;
        WebElement element = appiumHelper.findElement(ListItemBy.numbered(place));
        if (element != null) {
            scrollIfNotOnScreen(element);
            number = appiumHelper.getNumberFor(element);
        }
        return number;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public Integer numberForIn(String place, String container) {
        return doInContainer(container, () -> numberFor(place));
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> availableOptionsFor(String place) {
        ArrayList<String> result = null;
        WebElement element = appiumHelper.getElement(place);
        if (element != null) {
            scrollIfNotOnScreen(element);
            result = appiumHelper.getAvailableOptions(element);
        }
        return result;
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public List<String> normalizedAvailableOptionsFor(String place) {
        return normalizeValues(availableOptionsFor(place));
    }

    @WaitUntil
    public boolean clear(String place) {
        return clearIn(place, null);
    }

    @WaitUntil
    public boolean clearIn(String place, String container) {
        WebElement element = getElement(place, container);
        if (element != null) {
            return clear(element);
        }
        return false;
    }

    @WaitUntil
    public boolean enterAsInRowWhereIs(String value, String requestedColumnName, String selectOnColumn, String selectOnValue) {
        By cellBy = GridBy.columnInRowWhereIs(requestedColumnName, selectOnColumn, selectOnValue);
        WebElement element = appiumHelper.findElement(cellBy);
        return enter(element, value, true);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfColumnNumberInRowNumber(int columnIndex, int rowIndex) {
        By by = GridBy.coordinates(columnIndex, rowIndex);
        return valueFor(by);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfInRowNumber(String requestedColumnName, int rowIndex) {
        By by = GridBy.columnInRow(requestedColumnName, rowIndex);
        return valueFor(by);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String valueOfInRowWhereIs(String requestedColumnName, String selectOnColumn, String selectOnValue) {
        By by = GridBy.columnInRowWhereIs(requestedColumnName, selectOnColumn, selectOnValue);
        return valueFor(by);
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueOfColumnNumberInRowNumber(int columnIndex, int rowIndex) {
        return normalizeValue(valueOfColumnNumberInRowNumber(columnIndex, rowIndex));
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueOfInRowNumber(String requestedColumnName, int rowIndex) {
        return normalizeValue(valueOfInRowNumber(requestedColumnName, rowIndex));
    }

    @WaitUntil(TimeoutPolicy.RETURN_NULL)
    public String normalizedValueOfInRowWhereIs(String requestedColumnName, String selectOnColumn, String selectOnValue) {
        return normalizeValue(valueOfInRowWhereIs(requestedColumnName, selectOnColumn, selectOnValue));
    }

    @WaitUntil(TimeoutPolicy.RETURN_FALSE)
    public boolean rowExistsWhereIs(String selectOnColumn, String selectOnValue) {
        return appiumHelper.findElement(GridBy.rowWhereIs(selectOnColumn, selectOnValue)) != null;
    }

    @WaitUntil
    public boolean clickInRowNumber(String place, int rowIndex) {
        By rowBy = GridBy.rowNumber(rowIndex);
        return clickInRow(rowBy, place);
    }

    @WaitUntil
    public boolean clickInRowWhereIs(String place, String selectOnColumn, String selectOnValue) {
        By rowBy = GridBy.rowWhereIs(selectOnColumn, selectOnValue);
        return clickInRow(rowBy, place);
    }

    protected boolean clickInRow(By rowBy, String place) {
        return Boolean.TRUE.equals(doInContainer(() -> appiumHelper.findElement(rowBy), () -> click(place)));
    }

    protected T getElement(String place, String container) {
        return doInContainer(container, () -> appiumHelper.getElement(place));
    }

    protected String getTextByClassName(String className) {
        WebElement element = findByClassName(className);
        return getElementText(element);
    }

    protected T findByClassName(String className) {
        return appiumHelper.findElement(By.className(className));
    }

    protected T findByCss(String cssPattern, String... params) {
        return appiumHelper.findElement(appiumHelper.byCss(cssPattern, params));
    }

    protected List<T> findAllByXPath(String xpathPattern, String... params) {
        return findElements(appiumHelper.byXpath(xpathPattern, params));
    }

    protected List<T> findAllByCss(String cssPattern, String... params) {
        return findElements(appiumHelper.byCss(cssPattern, params));
    }

    public void waitMilliSecondAfterScroll(int msToWait) {
        waitAfterScroll = msToWait;
    }

    protected int getWaitAfterScroll() {
        return waitAfterScroll;
    }

    protected String getElementText(WebElement element) {
        if (element != null) {
            scrollIfNotOnScreen(element);
            return appiumHelper.getText(element);
        }
        return null;
    }

    public boolean scrollUp() {
        boolean result = appiumHelper.scrollUpOrDown(true);
        waitAfterScroll(waitAfterScroll);
        return result;
    }

    public boolean scrollDown() {
        boolean result = appiumHelper.scrollUpOrDown(false);
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
            appiumHelper.scrollUpOrDown(up);
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
    protected void scrollTo(WebElement element) {
        appiumHelper.scrollTo(element);
        waitAfterScroll(waitAfterScroll);
    }

    /**
     * Wait after the scroll if needed
     *
     * @param msToWait amount of ms to wait after the scroll
     */
    protected void waitAfterScroll(int msToWait) {
        if (msToWait > 0) {
            waitMilliseconds(msToWait);
        }
    }

    /**
     * Scrolls window if element is not currently visible so top of element becomes visible.
     *
     * @param element element to scroll to.
     */
    protected void scrollIfNotOnScreen(WebElement element) {
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
        T element = getElementToCheckVisibility(place, container);
        if (element != null) {
            if ("label".equalsIgnoreCase(element.getTagName())) {
                // for labels we want to know whether their target is enabled, not the label itself
                T labelTarget = appiumHelper.getLabelledElement(element);
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

    protected boolean isVisibleImpl(String place, String container, boolean checkOnScreen) {
        WebElement element = getElementToCheckVisibility(place, container);
        return appiumHelper.checkVisible(element, checkOnScreen);
    }

    public int numberOfTimesIsVisible(String text) {
        return numberOfTimesIsVisibleInImpl(text, true);
    }

    public int numberOfTimesIsVisibleOnPage(String text) {
        return numberOfTimesIsVisibleInImpl(text, false);
    }

    public int numberOfTimesIsVisibleIn(String text, String container) {
        Integer number = doInContainer(container, () -> numberOfTimesIsVisible(text));
        return number == null ? 0 : number;
    }

    public int numberOfTimesIsVisibleOnPageIn(String text, String container) {
        Integer number = doInContainer(container, () -> numberOfTimesIsVisibleOnPage(text));
        return number == null ? 0 : number;
    }

    protected int numberOfTimesIsVisibleInImpl(String text, boolean checkOnScreen) {
        if (implicitFindInFrames) {
            // sum over iframes
            AtomicInteger count = new AtomicInteger();
            new AllFramesDecorator<Integer>(appiumHelper).apply(() -> count.addAndGet(appiumHelper.countVisibleOccurrences(text, checkOnScreen)));
            return count.get();
        } else {
            return appiumHelper.countVisibleOccurrences(text, checkOnScreen);
        }
    }

    protected T getElementToCheckVisibility(String place, String container) {
        return doInContainer(container, () -> findByTechnicalSelectorOr(place, place1 -> appiumHelper.getElementToCheckVisibility(place1)));
    }

    @WaitUntil
    public boolean hoverOver(String place) {
        return hoverOverIn(place, null);
    }

    @WaitUntil
    public boolean hoverOverIn(String place, String container) {
        WebElement element = getElementToClick(place, container);
        return hoverOver(element);
    }

    protected boolean hoverOver(WebElement element) {
        if (element != null) {
            scrollIfNotOnScreen(element);
            if (element.isDisplayed()) {
                appiumHelper.hoverOver(element);
                return true;
            }
        }
        return false;
    }

    /**
     * @param timeout number of seconds before waitUntil() throw TimeOutException.
     */
    public void secondsBeforeTimeout(int timeout) {
        secondsBeforeTimeout = timeout;
        secondsBeforePageLoadTimeout(timeout);
        int timeoutInMs = timeout * 1000;
        appiumHelper.setScriptWait(timeoutInMs);
    }

    /**
     * @return number of seconds waitUntil() will wait at most.
     */
    public int getSecondsBeforeTimeout() {
        return secondsBeforeTimeout;
    }

    /**
     * @param timeout number of seconds before waiting for a new page to load will throw a TimeOutException.
     */
    public void secondsBeforePageLoadTimeout(int timeout) {
        secondsBeforePageLoadTimeout = timeout;
        int timeoutInMs = timeout * 1000;
        appiumHelper.setPageLoadWait(timeoutInMs);
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
        return getEnvironment().getHtml(appiumHelper.getSourceXml());
    }

    protected String savePageSource(String fileName, String linkText) {
        PageSourceSaver saver = appiumHelper.getPageSourceSaver(pageSourceBase);
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
        return appiumHelper.takeScreenshot(name);
    }

    private String createScreenshot(String basename, Throwable t) {
        String screenshotFile;
        byte[] screenshotInException = appiumHelper.findScreenshot(t);
        if (screenshotInException == null || screenshotInException.length == 0) {
            screenshotFile = createScreenshot(basename);
        } else {
            String name = getScreenshotBasename(basename);
            screenshotFile = appiumHelper.writeScreenshot(name, screenshotInException);
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
    protected <T> T waitUntil(ExpectedCondition<T> condition) {
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
    protected <T> T waitUntilOrStop(ExpectedCondition<T> condition) {
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
    protected <T> T lastAttemptBeforeThrow(ExpectedCondition<T> condition, SlimFixtureException e) {
        T lastAttemptResult = null;
        try {
            // last attempt to ensure condition has not been met
            // this to prevent messages that show no problem
            lastAttemptResult = condition.apply(appiumHelper.driver());
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
    protected <T> T waitUntilOrNull(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            return null;
        }
    }

    protected <T> T waitUntilImpl(ExpectedCondition<T> condition) {
        return appiumHelper.waitUntil(secondsBeforeTimeout, condition);
    }

    public boolean refreshSearchContext() {
        // copy path so we can retrace after clearing it
        List<String> fullPath = new ArrayList<>(getCurrentSearchContextPath());
        return refreshSearchContext(fullPath, Math.min(fullPath.size(), minStaleContextRefreshCount));
    }

    protected boolean refreshSearchContext(List<String> fullPath, int maxRetries) {
        clearSearchContext();
        for (String container : fullPath) {
            try {
                setSearchContextTo(container);
            } catch (RuntimeException se) {
                if (maxRetries < 1 || !(se instanceof WebDriverException)
                        || !appiumHelper.isStaleElementException((WebDriverException) se)) {
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

    protected <T> T handleTimeoutException(TimeoutException e) {
        String message = getTimeoutMessage(e);
        throw new TimeoutStopTestException(false, message, e);
    }

    private String getTimeoutMessage(TimeoutException e) {
        String messageBase = String.format("Timed-out waiting (after %ss)", secondsBeforeTimeout);
        return getSlimFixtureExceptionMessage("timeouts", "timeout", messageBase, e);
    }

    protected void handleRequiredElementNotFound(String toFind) {
        handleRequiredElementNotFound(toFind, null);
    }

    protected void handleRequiredElementNotFound(String toFind, Throwable t) {
        String messageBase = String.format("Unable to find: %s", toFind);
        String message = getSlimFixtureExceptionMessage("notFound", toFind, messageBase, t);
        throw new SlimFixtureException(false, message, t);
    }

    protected String getSlimFixtureExceptionMessage(String screenshotFolder, String screenshotFile, String messageBase, Throwable t) {
        String screenshotBaseName = String.format("%s/%s/%s", screenshotFolder, getClass().getSimpleName(), screenshotFile);
        return getSlimFixtureExceptionMessage(screenshotBaseName, messageBase, t);
    }

    protected String getSlimFixtureExceptionMessage(String screenshotBaseName, String messageBase, Throwable t) {
        String exceptionMsg = getExceptionMessageText(messageBase, t);
        // take a screenshot of what was on screen
        String screenshotTag = getExceptionScreenshotTag(screenshotBaseName, messageBase, t);
        String label = getExceptionPageSourceTag(screenshotBaseName, messageBase, t);

        return String.format("<div><div>%s.</div><div>%s:%s</div></div>", exceptionMsg, label, screenshotTag);
    }

    protected String getExceptionMessageText(String messageBase, Throwable t) {
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

    protected String getExceptionScreenshotTag(String screenshotBaseName, String messageBase, Throwable t) {
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

    protected String getExceptionPageSourceTag(String screenshotBaseName, String messageBase, Throwable t) {
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

    protected String formatExceptionMsg(String value) {
        return StringEscapeUtils.escapeHtml4(value);
    }

    /**
     * @return helper to use.
     */
    protected AppiumHelper<T, D> getAppiumHelper() {
        return appiumHelper;
    }

    /**
     * Sets SeleniumHelper to use, for testing purposes.
     *
     * @param helper helper to use.
     */
    protected void setAppiumHelper(AppiumHelper<T, D> helper) {
        appiumHelper = helper;
    }

    protected WebElement getElementToDownload(String place) {
        SeleniumHelper<T> helper = appiumHelper;
        return helper.findByTechnicalSelectorOr(place,
                () -> helper.getLink(place),
                () -> helper.findElement(AltBy.exact(place)),
                () -> helper.findElement(AltBy.partial(place)));
    }

    protected List<T> findElements(By by) {
        return appiumHelper.findElements(by);
    }

    public T findByTechnicalSelectorOr(String place, Function<String, ? extends T> supplierF) {
        return appiumHelper.findByTechnicalSelectorOr(place, () -> supplierF.apply(place));
    }

    /**
     * Selects a file using the first file upload control.
     *
     * @param fileName file to upload
     * @return true, if a file input was found and file existed.
     */
    @WaitUntil
    public boolean selectFile(String fileName) {
        return selectFileFor(fileName, "css=input[type='file']");
    }

    /**
     * Selects a file using a file upload control.
     *
     * @param fileName file to upload
     * @param place    file input to select the file for
     * @return true, if place was a file input and file existed.
     */
    @WaitUntil
    public boolean selectFileFor(String fileName, String place) {
        return selectFileForIn(fileName, place, null);
    }

    /**
     * Selects a file using a file upload control.
     *
     * @param fileName  file to upload
     * @param place     file input to select the file for
     * @param container part of screen containing place
     * @return true, if place was a file input and file existed.
     */
    @WaitUntil
    public boolean selectFileForIn(String fileName, String place, String container) {
        boolean result = false;
        if (fileName != null) {
            String fullPath = getFilePathFromWikiUrl(fileName);
            if (new File(fullPath).exists()) {
                WebElement element = getElementToSelectFile(place, container);
                if (element != null) {
                    element.sendKeys(fullPath);
                    result = true;
                }
            } else {
                throw new SlimFixtureException(false, "Unable to find file: " + fullPath);
            }
        }
        return result;
    }

    protected T getElementToSelectFile(String place, String container) {
        T result = null;
        T element = getElement(place, container);
        if (element != null
                && "input".equalsIgnoreCase(element.getTagName())
                && "file".equalsIgnoreCase(element.getAttribute("type"))) {
            result = element;
        }
        return result;
    }

    public boolean clickUntilValueOfIs(String clickPlace, String checkPlace, String expectedValue) {
        return repeatUntil(getClickUntilValueIs(clickPlace, checkPlace, expectedValue));
    }

    public boolean clickUntilValueOfIsNot(String clickPlace, String checkPlace, String expectedValue) {
        return repeatUntilNot(getClickUntilValueIs(clickPlace, checkPlace, expectedValue));
    }

    protected RepeatCompletion getClickUntilValueIs(String clickPlace, String checkPlace, String expectedValue) {
        String place = cleanupValue(clickPlace);
        return getClickUntilCompletion(place, checkPlace, expectedValue);
    }

    protected RepeatCompletion getClickUntilCompletion(String place, String checkPlace, String expectedValue) {
        return new ConditionBasedRepeatUntil(true, d -> click(place), d -> checkValueIs(checkPlace, expectedValue));
    }

    protected boolean repeatUntil(ExpectedCondition<Object> actionCondition, ExpectedCondition<Boolean> finishCondition) {
        return repeatUntil(new ConditionBasedRepeatUntil(true, actionCondition, finishCondition));
    }

    protected boolean repeatUntilIsNot(ExpectedCondition<Object> actionCondition, ExpectedCondition<Boolean> finishCondition) {
        return repeatUntilNot(new ConditionBasedRepeatUntil(true, actionCondition, finishCondition));
    }

    protected <T> ExpectedCondition<T> wrapConditionForFramesIfNeeded(ExpectedCondition<T> condition) {
        if (implicitFindInFrames) {
            condition = appiumHelper.conditionForAllFrames(condition);
        }
        return condition;
    }

    @Override
    protected boolean repeatUntil(RepeatCompletion repeat) {
        // During repeating we reduce the timeout used for finding elements,
        // but the page load timeout is kept as-is (which takes extra work because secondsBeforeTimeout(int)
        // also changes that.
        int previousTimeout = secondsBeforeTimeout;
        int pageLoadTimeout = secondsBeforePageLoadTimeout();
        try {
            int timeoutDuringRepeat = Math.max((Math.toIntExact(repeatInterval() / 1000)), 1);
            secondsBeforeTimeout(timeoutDuringRepeat);
            secondsBeforePageLoadTimeout(pageLoadTimeout);
            return super.repeatUntil(repeat);
        } finally {
            secondsBeforeTimeout(previousTimeout);
            secondsBeforePageLoadTimeout(pageLoadTimeout);
        }
    }

    protected boolean checkValueIs(String place, String expectedValue) {
        boolean match;
        String actual = valueOf(place);
        if (expectedValue == null) {
            match = actual == null;
        } else {
            match = cleanExpectedValue(expectedValue).equals(actual);
        }
        return match;
    }

    protected class ConditionBasedRepeatUntil extends FunctionalCompletion {
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
        return appiumHelper.selectAll();
    }

    /**
     * Simulates 'copy' (e.g. Ctrl+C on Windows) on the active element, copying the current selection to the clipboard.
     *
     * @return whether an active element was found.
     */
    @WaitUntil
    public boolean copy() {
        return appiumHelper.copy();
    }

    /**
     * Simulates 'cut' (e.g. Ctrl+X on Windows) on the active element, copying the current selection to the clipboard
     * and removing that selection.
     *
     * @return whether an active element was found.
     */
    @WaitUntil
    public boolean cut() {
        return appiumHelper.cut();
    }

    /**
     * Simulates 'paste' (e.g. Ctrl+V on Windows) on the active element, copying the current clipboard
     * content to the currently active element.
     *
     * @return whether an active element was found.
     */
    @WaitUntil
    public boolean paste() {
        return appiumHelper.paste();
    }

    /**
     * @return text currently selected in window, or empty string if no text is selected.
     */
    public String getSelectionText() {
        return appiumHelper.getSelectionText();
    }

    /**
     * @return should 'normalized' functions remove starting and trailing whitespace?
     */
    public boolean trimOnNormalize() {
        return trimOnNormalize;
    }

    /**
     * @param trimOnNormalize should 'normalized' functions remove starting and trailing whitespace?
     */
    public void setTrimOnNormalize(boolean trimOnNormalize) {
        this.trimOnNormalize = trimOnNormalize;
    }
}