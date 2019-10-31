package nl.praegus.fitnesse.slim.util;

import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KeyMappingTest {


    @Test
    public void when_non_existing_key_is_entered_exeption_is_thowed() {
        assertThatThrownBy(()->KeyMapping.getKey("not existing key"))
                .isInstanceOf(SlimFixtureException.class)
                .hasMessage("Key: not existing key does not exist or is not supported yet.");
    }
}