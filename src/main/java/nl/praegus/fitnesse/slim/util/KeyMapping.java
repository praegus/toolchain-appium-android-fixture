package nl.praegus.fitnesse.slim.util;

import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class KeyMapping {

    private KeyMapping() {
        // constructor is private cause everything is static
    }

    private static Map<String, Integer> keyMap = new HashMap<>();

    static {
        keyMap.put("control", KeyEvent.VK_CONTROL);
        keyMap.put("v", KeyEvent.VK_V);
    }

    public static Integer getKey(String key) {
        Integer keyStoke = keyMap.get(key.toLowerCase().trim());

        if (keyStoke != null) {
            return keyStoke;
        }
        throw new SlimFixtureException("Key: " + key + " does not exist or is not supported yet");
    }
}
