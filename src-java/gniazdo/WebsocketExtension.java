package gniazdo;

import javax.websocket.Extension;
import java.util.*;

/**
 * Parse a Sec-WebSocket-Extensions header value
 * into {javax.websocket.Extension}.
 * See https://tools.ietf.org/html/rfc6455#section-9.1
 *
 * Copied from org.eclipse.jetty.websocket.api.extensions.ExtensionConfig
 */
public class WebsocketExtension implements Extension {

    private final String name;
    private final List<Parameter> parameters;

    public WebsocketExtension(String parameterizedName)
    {
        Iterator<String> extListIter = JettyQuoteUtil.splitAt(parameterizedName,";");
        this.name = extListIter.next();
        this.parameters = new LinkedList<Parameter>();

        // now for parameters
        while (extListIter.hasNext())
        {
            String extParam = extListIter.next();
            Iterator<String> extParamIter = JettyQuoteUtil.splitAt(extParam,"=");
            String key = extParamIter.next().trim();
            String value = null;
            if (extParamIter.hasNext())
            {
                value = extParamIter.next();
            }
            parameters.add(new ExtensionParameter(key,value));
        }
    }

    public String getName() {
        return name;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    private static class ExtensionParameter implements Parameter {

        private String name;
        private String value;

        public ExtensionParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
