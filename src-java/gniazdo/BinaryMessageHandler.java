package gniazdo;

/**
 * Implement a Text message handler.
 * We cannot use `(reify)` because Tyrus uses reflection magic to find out the actual
 * type parameter and ignores the handler if not present and Clojure can't (I believe)
 * supply it.
 *
 * Intended to be subclassed via (proxy)
 */
public class BinaryMessageHandler implements javax.websocket.MessageHandler.Whole<byte[]> {
    public void onMessage(byte[] data) {

    }
}
