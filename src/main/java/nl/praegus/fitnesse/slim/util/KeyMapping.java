package nl.praegus.fitnesse.slim.util;

import io.appium.java_client.android.nativekey.AndroidKey;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;

import java.util.HashMap;
import java.util.Map;

public class KeyMapping {

    private static Map<String, AndroidKey> keyMap = new HashMap<>();

    static {
        keyMap.put("home", AndroidKey.HOME);
        keyMap.put("back", AndroidKey.BACK);
        keyMap.put("menu", AndroidKey.MENU);
        keyMap.put("enter", AndroidKey.NUMPAD_ENTER);
    }

    private KeyMapping() {
        // constructor is private cause everything is static
    }

    public static AndroidKey getKey(String key) {
        AndroidKey keyStroke = keyMap.get(key.toLowerCase().trim());
        if (keyStroke != null) {
            return keyStroke;
        }
        throw new SlimFixtureException("Key: " + key + " does not exist or is not supported yet.");
    }
}
