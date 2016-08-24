(ns gniazdo.core
  (:import java.net.URI
           java.nio.ByteBuffer
           java.util.List
           (javax.websocket Endpoint
                            EndpointConfig
                            ClientEndpointConfig$Builder CloseReason ClientEndpointConfig ClientEndpointConfig$Configurator RemoteEndpoint$Basic Session WebSocketContainer ContainerProvider)
           (gniazdo TextMessageHandler BinaryMessageHandler WebsocketExtension)
           (org.glassfish.tyrus.client ClientManager)))

(set! *warn-on-reflection* 1)

;; ## Messages

(defprotocol Sendable
  (send-to-endpoint [this ^RemoteEndpoint$Basic e]
    "Sends an entity to a given WebSocket endpoint."))

(extend-protocol Sendable
  java.lang.String
  (send-to-endpoint [msg ^RemoteEndpoint$Basic e]
    (.sendText e msg))

  java.nio.ByteBuffer
  (send-to-endpoint [buf ^RemoteEndpoint$Basic e]
    (.sendBinary e buf)))

(extend-type (class (byte-array 0))
  Sendable
  (send-to-endpoint [data ^RemoteEndpoint$Basic e]
    (.sendBinary e (ByteBuffer/wrap data))))

;; ## Client

(defprotocol ^:private Client
  (send-msg [this msg]
    "Sends a message (implementing `gniazdo.core/Sendable`) to the given WebSocket.")
  (close [this]
    "Closes the WebSocket."))

;; ## WebSocket Helpers

(defn- add-headers!
  ^ClientEndpointConfig$Builder
  [^ClientEndpointConfig$Builder builder headers]
  {:pre [(every? string? (keys headers))]}
  (.configurator
    builder
    (proxy [ClientEndpointConfig$Configurator] []
      (beforeRequest [^java.util.Map headersMap]
        (doseq [[header value] headers]
          (let [header-values (if (sequential? value)
                                value
                                [value])]
            (assert (every? string? header-values))
            (.put headersMap header header-values)))))))

(defn- add-subprotocols!
  ^ClientEndpointConfig$Builder
  [^ClientEndpointConfig$Builder builder subprotocols]
  {:pre [(or (nil? subprotocols) (sequential? subprotocols))
         (every? string? subprotocols)]}
  (if (seq subprotocols)
    (.preferredSubprotocols builder ^List (into () subprotocols))
    builder))

(defn- add-extensions!
  ^ClientEndpointConfig$Builder
  [^ClientEndpointConfig$Builder builder extensions]
  {:pre [(or (nil? extensions) (sequential? extensions))
         (every? string? extensions)]}
  (if (seq extensions)
    (.extensions builder ^List (map #(WebsocketExtension. ^String %)
                                    extensions))
    builder))

(defn- make-config
  ^ClientEndpointConfig
  [{:keys [headers subprotocols extensions]}]
  "Add extensions, headers, and subprotocols if any. (former upgrade-request)"
  (as-> (ClientEndpointConfig$Builder/create) b
        ;(.decoders b []) ;; remove defaults so they will not mess up with our processing?
        ;(.encoders b []) ;; remove defaults so they will not mess up with our processing?
        (add-headers! b headers)
        (add-subprotocols! b subprotocols)
        (add-extensions! b extensions)
        (.build b)))

(defn listener [{:keys  [on-connect on-receive on-binary on-error on-close]
                    :or {on-connect (constantly nil)
                         on-receive (constantly nil)
                         on-binary  (constantly nil)
                         on-error   (constantly nil)
                         on-close   (constantly nil)}}]
  (proxy [Endpoint] []

    (onOpen ^void [^Session session, ^EndpointConfig _]
      (.addMessageHandler
        session
        (proxy [TextMessageHandler] []
          (onMessage [msg]
            (on-receive msg))))
      (.addMessageHandler
        session
        (proxy [BinaryMessageHandler] []
          (onMessage [data]
            (on-binary data 0 (count data)))))
      (on-connect session)) ;; FIXME This is javax.websockets.Session, not org.eclipse.jetty.websocket.api.Session (similar but not same)
    (onError [^Session _, ^Throwable throwable]
      (on-error throwable))
    (onClose [^Session _, ^CloseReason closeReason]
      (on-close
        ;; Note: The codes are standardized, see
        ;; <a href="https://tools.ietf.org/html/rfc6455#section-7.4.1">RFC 6455, Section 7.4.1 Defined Status Codes</a>
        (.getCode (.getCloseCode closeReason))
        (.getReasonPhrase closeReason)))))

;; ## WebSocket Client + Connection (API)

(defn- connect-internal
  "Connect to a WebSocket."
  [^WebSocketContainer client ^URI uri opts]
  (let [endpoint-config (make-config opts)
        ^Endpoint listener (listener opts)]
    (let [session (.connectToServer
                    client
                    listener
                    endpoint-config
                    uri)]
      (reify Client
        (send-msg [_ msg]
          (send-to-endpoint msg (.getBasicRemote session)))
        (close [_]
          (.close session))))))

(defn client []
  (let [tyrus-props (->> (System/getProperties)
                         (filter (fn [[^String k]] (.startsWith k "org.glassfish.tyrus.client.")))
                         (into {}))]
    (if (empty? tyrus-props)
      (ContainerProvider/getWebSocketContainer)
      (do
        (println "gniazdo: Creating a websocket client with the properties" tyrus-props)
        (doto (ClientManager/createClient)
          (.. (getProperties) (putAll tyrus-props)))))))

(defn connect
  "Connects to a WebSocket at a given URI (e.g. ws://example.org:1234/socket).
   Optionally provide a `client` (see org.glassfish.tyrus.client.ClientManager.createClient(),
   its `.getProperties().put(..)`'8. Tyrus proprietary configuration' in the Tyrus documentation)
   if you want any custom configuration (automatic reconnect, HTTP(S) proxy, custom SSL, ...).
  "
  [uri & {:keys [on-connect on-receive on-binary on-error on-close headers client
                 subprotocols extensions]
          :as opts}]
  (let [uri' (URI. uri)
        ^WebSocketContainer actual-client (or client (gniazdo.core/client))]
    (connect-internal actual-client uri' opts)))


;; TODO
;; TODO See 8.7 Client reconnect @ https://tyrus.java.net/documentation/1.9/user-guide.html
;; If you need semi-persistent client connection, you can always implement some reconnect logic by yourself, but Tyrus Client offers useful feature which should be much easier to use:
;; ClientManager client = ClientManager.createClient();
;; ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {
;; onDisconnect => return true to reconnect; similarly onConnectFailure}
;; TODO 8.8. Client behind proxy
;;   client.getProperties().put(ClientProperties.PROXY_URI, "http://my.proxy.com:80");
