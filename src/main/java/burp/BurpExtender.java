package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burp.websocketlogger.WebSocketLoggerExtension;

public class BurpExtender extends WebSocketLoggerExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        super.initialize(api);
    }
}
