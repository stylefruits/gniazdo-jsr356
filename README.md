# Gniazdo-jsr356

[Gniazdo-jsr356][def] is a fork of [Gniazdo](https://github.com/stylefruits/gniazdo/),
the [WebSocket][ws] client for Clojure, that exposes the same API as Gniazdo but
uses internally the [`javax.websockets`][jxws] (JSR 356) API. Thus it is possible to use it with any
implementation of that API, f.ex. [Tyrus][tyrus] (default) or [Jetty][jetty-ws]. That makes it possible to pick the
implementation that fits your needs best - f.ex. as of now, Jetty (at least <= 9.3) - contrary to Tyrus - does [not support HTTP(s) proxies](https://github.com/eclipse/jetty.project/issues/117).
Just as Gnizado, it  supports both `ws://` and `wss://` schemas.

We use Tyrus by default since it provides [more configuration options][tyruscfg]
(HTTP proxy, automatic reconnection, etc.). You can either
pass in a custom `client` already configured or set Java [System properties with the names expected by
Tyrus][tyrusprop]
(i.e. starting with `org.glassfish.tyrus.client.`).

If you would prefer another implementation, [exclude the default dependency][lein-exc]
of Gniazdo and add your own dependency on the desired implementation.

<!-- TODO [![Build Status](https://travis-ci.org/stylefruits/gniazdo.svg)](https://travis-ci.org/stylefruits/gniazdo) -->

## Usage

Add the following artifact to `:dependencies` in your project.clj:

<!-- [![Latest version](https://clojars.org/stylefruits/gniazdo/latest-version.svg)](https://clojars.org/stylefruits/gniazdo) -->
```
[stylefruits/gniazdo-jsr356 "2.0.0"]
```

Or, if you would prefer another implementation, f.ex. Jetty:

```
[stylefruits/gniazdo-jsr356 "2.0.0" :exclusions [org.glassfish.tyrus/tyrus-client org.glassfish.tyrus/tyrus-container-grizzly-client]]
[org.eclipse.jetty.websocket/javax-websocket-client-impl "9.4.0.M1"]
```

Here's a minimal usage example:

```clojure
(require [gniazdo.core :as ws])
(def socket
  (ws/connect
    "ws://example.org:1234/socket"
    :on-receive #(prn 'received %)))
(ws/send-msg socket "hello")
(ws/close socket)
```

### `(gniazdo.core/connect uri & options)`

`gniazdo.core/connect` opens a WebSocket connection using a
given `uri`. The following `options`/callbacks are available:

 - `:on-connect` – a unary function called after the connection has been
   established. The handler is a [`Session`][session] instance.
 - `:on-receive` – a unary function called when a message is received. The
   argument is a received `String`.
 - `:on-binary` – a ternary function called when a message is received.
   Arguments are the raw payload byte array, and two integers: the offset
   in the array where the data starts and the length of the payload.
 - `:on-error` – a unary function called on in case of errors. The argument is
   a `Throwable` describing the error.
 - `:on-close` – a binary function called when the connection is closed.
   Arguments are an `int` status code and a `String` description of reason.
 - `:headers` – a map of string keys and either string or string seq values to be
   used as headers for the initial websocket connection request.
 - `:client` – an optional [`WebSocketContainer`][wscontrainer] instance to be used for connection
   establishment; by default, a new one is created internally on each call.
 - `:subprotocols` – an optional sequence of `String`s specifying the subprotocols
   to announce.
 - `:extensions` – an optional sequence of `String`s specifying protocol
   extensions.

`gniazdo.core/connect` returns an opaque representation of the connection.

### `(gniazdo.core/send-msg [conn message])`

`gniazdo.core/send-msg` sends a given message using a connection established
with `gniazdo.core/connect`. The message should be a `String`, `byte[]` or
`java.nio.ByteBuffer`.

### `(gniazdo.core/close [conn])`

`gniazdo.core/close` closes a connection established with
`gniazdo.core/connect`.

## Differences from Gniazdo

### Different classes

1. If you pass in a custom `:client`, it needs to implement [`javax.websocket.WebSocketContainer`][wscontrainer],
   not `org.eclipse.jetty.websocket.client.WebSocketClient`.
2. The `session` passed to `:on-connect` is [`javax.websocket.Session`][session], not `org.eclipse.jetty.websocket.api.Session`
3. The argument `e` of the `Sendable` protocol is `javax.websocket.RemoteEndpoint`, not `org.eclipse.jetty.websocket.api.RemoteEndpoint`
4. The Java exceptions that you might get when `connect` fails are likely different.

### Different functionality

When using Tyrus, it is possible to configure it not only in the code by passing a custom `client` instance
but also using Java System properties:

```
java -Dorg.glassfish.tyrus.client.proxy=http://proxy.example.com:8080 -jar your-app-including-gniazdo.jar
```

## License

    Copyright 2013 stylefruits GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[def]: https://en.wiktionary.org/wiki/gniazdo
[ws]: https://en.wikipedia.org/wiki/WebSocket
[jetty]: http://www.eclipse.org/jetty/
[jxws]: https://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html
[tyrus]: https://tyrus.java.net/
[jetty-ws]: https://github.com/jetty-project/embedded-jetty-websocket-examples/tree/master/javax.websocket-example
[tyruscfg]: https://tyrus.java.net/documentation/1.9/index/tyrus-proprietary-config.html
[tyrusprop]: https://github.com/tyrus-project/tyrus/blob/ecc6941e5264f63d62d3f882960806c82209640f/client/src/main/java/org/glassfish/tyrus/client/ClientProperties.java
[lein-exc]: http://stackoverflow.com/questions/6802026/how-do-i-exclude-jars-from-a-leiningen-project

[session]: https://docs.oracle.com/javaee/7/api/javax/websocket/Session.html
[wscontrainer]: https://docs.oracle.com/javaee/7/api/javax/websocket/WebSocketContainer.html
