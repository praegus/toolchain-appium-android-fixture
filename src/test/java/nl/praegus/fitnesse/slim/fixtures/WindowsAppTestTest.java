package nl.praegus.fitnesse.slim.fixtures;

import io.appium.java_client.windows.WindowsDriver;
import io.appium.java_client.windows.WindowsElement;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import nl.hsac.fitnesse.fixture.util.ReflectionHelper;
import nl.praegus.fitnesse.slim.util.WindowsHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WindowsAppTestTest {

    @Mock
    private WindowsHelper windowsHelper;
    @Mock
    private ReflectionHelper reflectionHelper;
    @Mock
    private WindowsElement element;
    @Mock
    private WindowsDriver driver;
    @Mock
    private WebDriver.TargetLocator targetLocator;
    @Mock
    private Robot robot;
    @Mock
    private Clipboard clipboard;

    @InjectMocks
    private WindowsAppTest windowsAppTest;

    @Test
    public void successfully_dubble_clicking_returns_true() {
        when(windowsHelper.isInteractable(element)).thenReturn(true);

        boolean result = windowsAppTest.doubleClick(element);

        assertThat(result).isTrue();
    }

    @Test
    public void when_splash_screen_is_gone_true_is_returned() {
        when(windowsHelper.driver()).thenReturn(driver);
        when(driver.getWindowHandles()).thenReturn(Collections.singleton("window"));
        when(driver.switchTo()).thenReturn(targetLocator);

        boolean result = windowsAppTest.waitForSplashScreenToDisappear();

        assertThat(result).isTrue();
    }

    @Test
    public void when_splash_screen_does_not_go_away_false_is_returned() {
        when(windowsHelper.driver()).thenReturn(driver);
        when(driver.getWindowHandles()).thenReturn(Collections.singleton(""));

        boolean result = windowsAppTest.waitForSplashScreenToDisappear();

        assertThat(result).isFalse();
    }

    @Test
    public void switch_to_next_window_can_be_done_with_two_windows() {
        when(windowsHelper.driver()).thenReturn(driver);
        when(driver.getWindowHandles()).thenReturn(new LinkedHashSet<>(asList("window1", "window2")));
        when(driver.switchTo()).thenReturn(targetLocator);

        windowsAppTest.switchToNextWindow();
        assertThat(windowsAppTest.getFocusedWindow()).isEqualTo("window1");
        windowsAppTest.switchToNextWindow();
        assertThat(windowsAppTest.getFocusedWindow()).isEqualTo("window2");
    }

    @Test
    public void switch_to_next_window_cannot_be_done_with_only_one_window() {
        when(windowsHelper.driver()).thenReturn(driver);
        when(driver.getWindowHandles()).thenReturn(Collections.singleton("window"));

        assertThatThrownBy(() -> windowsAppTest.switchToNextWindow())
                .isInstanceOf(SlimFixtureException.class)
                .hasMessage("There is only one window in WinAppDriver's scope. Cannot Switch to next window");
    }

    @Test
    public void when_paste_key_is_used_clipboard_text_is_pasted() throws IOException, UnsupportedFlavorException {
        boolean result = windowsAppTest.pasteText("text to be pasted");

        assertThat(result).isTrue();
        verify(robot, times(1)).keyPress(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyPress(KeyEvent.VK_V);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_V);

        ArgumentCaptor<StringSelection> captor = ArgumentCaptor.forClass(StringSelection.class);
        verify(clipboard, times(1)).setContents(captor.capture(), any());
        assertThat(captor.getValue().getTransferData(DataFlavor.stringFlavor)).isEqualTo("text to be pasted");
    }

    @Test
    public void when__key_is_pressed_true_is_returned() {
        boolean result = windowsAppTest.pressKey("v");

        assertThat(result).isTrue();
        verify(robot, times(1)).keyPress(KeyEvent.VK_V);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_V);
    }

    @Test
    public void when_two_keys_are_pressed_true_is_returned() {
        boolean result = windowsAppTest.pressAnd("control", "v");

        assertThat(result).isTrue();
        verify(robot, times(1)).keyPress(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyPress(KeyEvent.VK_V);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot, times(1)).keyRelease(KeyEvent.VK_V);
    }

    @Test
    public void right_click() {
        when(windowsHelper.getElementToClick("place")).thenReturn(element);
        when(windowsHelper.isInteractable(element)).thenReturn(true);

        boolean result = windowsAppTest.rightClick("place");

        assertThat(result).isTrue();
        verify(windowsHelper, times(1)).rightClick(element);
    }

    @Test
    public void shift_click() {
        when(windowsHelper.getElementToClick("place")).thenReturn(element);
        when(windowsHelper.isInteractable(element)).thenReturn(true);

        boolean result = windowsAppTest.shiftClick("place");

        System.out.println(mockingDetails(windowsHelper).printInvocations());
        assertThat(result).isTrue();
        verify(windowsHelper, times(1)).clickWithKeyDown(element, Keys.SHIFT);
    }

    @Test
    public void control_click() {
        when(windowsHelper.getElementToClick("place")).thenReturn(element);
        when(windowsHelper.isInteractable(element)).thenReturn(true);

        boolean result = windowsAppTest.controlClick("place");

        System.out.println(mockingDetails(windowsHelper).printInvocations());
        assertThat(result).isTrue();
        verify(windowsHelper, times(1)).clickWithKeyDown(element, Keys.CONTROL);
    }
}