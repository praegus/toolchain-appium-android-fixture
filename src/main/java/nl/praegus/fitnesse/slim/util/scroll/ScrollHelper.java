package nl.praegus.fitnesse.slim.util.scroll;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileElement;
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import nl.praegus.fitnesse.slim.util.AppiumHelper;
import nl.praegus.fitnesse.slim.util.by.IsDisplayedFilter;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Helper to deal with scrolling.
 */
public class ScrollHelper<T extends MobileElement, D extends AppiumDriver<T>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Duration waitBetweenScrollPressAndMove = Duration.ofMillis(1);
    private Duration waitAfterMoveDuration = Duration.ofMillis(100);

    protected final AppiumHelper<T, D> helper;

    public ScrollHelper(AppiumHelper<T, D> helper) {
        this.helper = helper;
    }

    public void waitBetweenScrollPressAndMove(int millis) {
        this.waitAfterMoveDuration = Duration.ofMillis(millis);
    }

    public boolean scrollTo(String place, Function<String, ? extends T> placeFinder) {
        T target = placeFinder.apply(place);
        boolean targetIsReached = targetIsReached(target);
        if (!targetIsReached) {
            LOGGER.debug("Scroll to: {}", place);
            T topScrollable = findTopScrollable();
            Dimension dimensions = getDimensions(topScrollable);
            Point center = getCenter(topScrollable, dimensions);

            Optional<?> prevRef = Optional.empty();

            // counter for hitting top/bottom: 0=no hit yet, 1=hit top, 2=hit bottom
            int bumps = 0;
            while (!targetIsReached && bumps < 2) {
                T refEl = findScrollRefElement(topScrollable);
                Optional<?> currentRef = createHashForElement(refEl);
                scrollUpOrDown(bumps == 0, center, (int) (dimensions.getHeight() / 2.0));

                if (currentRef.equals(prevRef)) {
                    // we either are: unable to find a reference element OR
                    // element remained same, we didn't actually scroll since last iteration
                    // this means we either hit top (if we were going up) or botton (if we were going down)
                    bumps++;
                }
                prevRef = currentRef;
                target = findTarget(placeFinder, place);
                targetIsReached = targetIsReached(target);
            }
        }
        return targetIsReached;
    }

    public boolean scrollUpOrDown(boolean up) {
        T topScrollable = findTopScrollable();
        Dimension dimensions = getDimensions(topScrollable);

        Point center = getCenter(topScrollable, dimensions);
        int heightDelta = (int) (dimensions.getHeight() / 2.0 * 0.5);
        scrollUpOrDown(up, center, heightDelta);
        return true;
    }

    protected boolean targetIsReached(T target) {
        return IsDisplayedFilter.mayPass(target);
    }

    protected Optional<?> createHashForElement(T refEl) {
        return refEl != null ? Optional.of(new ElementProperties(refEl)) : Optional.empty();
    }

    protected T findTopScrollable() {
        return helper.findByXPath("(.//*[@scrollable='true' or @type='UIAScrollView'])[1]");
    }

    protected T findScrollRefElement(T topScrollable) {
        T result;
        if (topScrollable == null || !topScrollable.isDisplayed()) {
            result = helper.findByXPath("(.//*[@scrollable='true' or @type='UIAScrollView']//*[@clickable='true' or @type='UIAStaticText'])[1]");
        } else {
            result = helper.findElement(topScrollable, By.xpath("(.//*[@clickable='true' or @type='UIAStaticText'])[1]"));
        }
        return result;
    }

    public void performScroll(int centerX, int centerY, int offset) {
        TouchAction ta = helper.getTouchAction()
                .press(PointOption.point(centerX, centerY))
                .waitAction(WaitOptions.waitOptions(waitBetweenScrollPressAndMove))
                .moveTo(PointOption.point(0, centerY + offset));

        if (waitAfterMoveDuration != null) {
            ta = ta.waitAction(WaitOptions.waitOptions(waitAfterMoveDuration));
        }

        ta.release().perform();
    }

    private T findTarget(Function<String, ? extends T> placeFinder, String place) {
        T result = placeFinder.apply(place);
        int retries = 0;
        while (result == null && retries < 3) {
            result = placeFinder.apply(place);
            try {
                Duration.ofMillis(300).wait();
            } catch (Exception e) {
                LOGGER.warn("wait failed!");
            }
            retries++;
        }
        return result;
    }

    private void scrollUpOrDown(boolean up, Point center, int heightDelta) {
        if (up) {
            // did not hit top of screen, yet
            LOGGER.debug("Going up!");
            performScroll(center.getX(), center.getY(), heightDelta);
        } else {
            LOGGER.debug("Going down!");
            performScroll(center.getX(), center.getY(), -heightDelta);
        }
    }

    private Point getCenter(T topScrollable, Dimension dimensions) {
        if (topScrollable == null) {
            return new Point(dimensions.getWidth() / 2, dimensions.getHeight() / 2);
        } else {
            return topScrollable.getCenter();
        }
    }

    private Dimension getDimensions(T topScrollable) {
        if (topScrollable == null) {
            return helper.getWindowSize();
        }
        return topScrollable.getSize();
    }
}