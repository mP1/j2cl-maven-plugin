package example.dependencyexclusions;

import com.google.gwt.core.client.EntryPoint;
import java.util.HashMap;
import java.util.Map;

public class App implements EntryPoint {

    @Override
    public void onModuleLoad() {
        map.put("A", "1");
        map.put("B", "2");
    }

    public String message() {
        return map.toString();
    }

    private final Map<String, String> map = new HashMap<>();
}
