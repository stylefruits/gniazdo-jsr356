(ns gniazdo.core-test
  (:require [clojure.string :as str])
  (:use clojure.test
        gniazdo.core
        [clojure.string :only [split trim lower-case]]
        [org.httpkit.server :only [with-channel
                                   on-receive
                                   run-server
                                   send!
                                   accept]])
  (:import [java.util.concurrent Future]
           [javax.websocket Session]
           [org.httpkit.server AsyncChannel]))

(declare ^:dynamic *recv*)

(defn subprotocol? [proto req]
  (if-let [protocols (get-in req [:headers "sec-websocket-protocol"])]
    (some #{proto}
          (map #(lower-case (trim %))
               (split protocols #",")))))

(defn add-headers [headers req]
  (let [extensions (get-in req [:headers "sec-websocket-extensions"])
        protocols (get-in req [:headers "sec-websocket-protocol"])]
    (cond-> headers
            extensions (assoc "Sec-WebSocket-Extensions" extensions) ;; accept all
            protocols (assoc "Sec-WebSocket-Protocol" protocols))))

(def latest-headers (atom nil))

;; From https://gist.github.com/cgmartin/5880732
;; TODO Use this for subproto test
(defmacro with-accept-all-channel
  [request ch-name & body]
  `(let [~ch-name (:async-channel ~request)]
     (reset! latest-headers (:headers ~request))
     (if (:websocket? ~request)
       (if-let [key# (get-in ~request [:headers "sec-websocket-key"])]
         (do
           (.sendHandshake ~(with-meta ch-name {:tag `AsyncChannel})
                           (add-headers
                             {"Upgrade"    "websocket"
                              "Sec-WebSocket-Accept" (accept key#)
                              "Connection" "Upgrade"}
                             ~request)
                           #_
                           {"Upgrade"    "websocket"
                            "Sec-WebSocket-Accept" (accept key#)
                            "Connection" "Upgrade"})
           ~@body
           {:body ~ch-name})
         {:status 400 :body "missing or bad WebSocket-Key"})
       {:status 400 :body "not websocket protocol"})))

(defn- ws-srv
  [req]
  (with-accept-all-channel req conn
    (on-receive conn (partial *recv* req conn))))

(use-fixtures
  :each
  (fn [f]
    (let [srv (run-server ws-srv {:port 65432})]
      (try
        (f)
        (finally
          (srv))))))

(def ^:private uri "ws://localhost:65432/")

(defmacro ^:private with-timeout
  [& body]
  `(let [f# (future ~@body)]
     (try
       (.get ^Future f# 1 java.util.concurrent.TimeUnit/SECONDS)
       (finally
         (future-cancel f#)))))

(deftest on-receive-test
  (with-redefs [*recv* (fn [_ conn msg]
                         (send! conn (str/upper-case msg)))]
    (let [result (atom nil)
          sem (java.util.concurrent.Semaphore. 0)
          conn (connect
                 uri
                 :on-receive #(do (reset! result %)
                                  (.release sem)))]
      (is (= @result nil))
      (send-msg conn "foo")
      (with-timeout (.acquire sem))
      (is (= @result "FOO"))
      (send-msg conn "bar")
      (with-timeout (.acquire sem))
      (is (= @result "BAR"))
      (close conn))))

(deftest on-binary-test
  (with-redefs [*recv* (fn [_ conn msg]
                         (send! conn (if (string? msg)
                                       (.getBytes (str/upper-case msg))
                                       msg)))]
    (let [result (atom nil)
          sem (java.util.concurrent.Semaphore. 0)
          conn (connect
                 uri
                 :on-binary (fn [data offset length]
                              (reset! result (String. data offset length))
                              (.release sem)))]
      (is (= @result nil))
      (send-msg conn "foo")
      (with-timeout (.acquire sem))
      (is (= @result "FOO"))
      (send-msg conn "bar")
      (with-timeout (.acquire sem))
      (is (= @result "BAR"))
      (send-msg conn (.getBytes "bar"))
      (with-timeout (.acquire sem))
      (is (= @result "bar"))
      (close conn))))

(deftest on-connect-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :on-connect (fn [_]
                             (reset! result :connected)
                             (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= @result :connected))
    (close conn)))

(deftest on-close-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :on-close (fn [& _]
                           (reset! result :closed)
                           (.release sem)))]
    (is (= @result nil))
    (close conn)
    (with-timeout (.acquire sem))
    (is (= @result :closed))))

(deftest on-error-test
  (testing "invalid arity"
    (testing ":on-connect"
      (let [result (promise)
            conn (connect
                   uri
                   :on-error (fn on-error [ex] (deliver result ex))
                   :on-connect (fn on-connect [_ _ _ _ _]))]
        (is (instance? clojure.lang.ArityException
                       (with-timeout @result)))
        (close conn)))
    (testing ":on-receive"
      (with-redefs [*recv* (fn [_ conn msg] (send! conn ""))]
        (let [result (promise)
              conn (connect
                     uri
                     :on-error (fn on-error [ex] (deliver result ex))
                     :on-receive (fn on-receive [_ _ _ _ _]))]
          (send-msg conn "")
          (is (instance? clojure.lang.ArityException
                         (with-timeout @result)))
          (close conn))))))

(deftest subprotocols-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
              uri
              :subprotocols ["wamp"]
              :on-connect (fn [^Session session]
                            (reset! result (.getNegotiatedSubprotocol session))
                            (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= @result "wamp"))
    (close conn)))

(deftest extensions-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :extensions ["permessage-deflate"]
               :on-connect (fn [^Session session]
                             (reset! result (.getNegotiatedExtensions session))
                             (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= (count @result) 1))
    (is (= (map #(.getName %) @result) ["permessage-deflate"]))
    (close conn)))

(deftest headers-test
  (let [sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :headers {"X-Header-1" "hi", "X-Header-2" "there"}
               :on-connect (fn [^Session session]
                             (.release sem)))]
    (with-timeout (.acquire sem))
    (is (=
          (select-keys @latest-headers ["x-header-1" "x-header-2"])
          {"x-header-1" "hi", "x-header-2" "there"}))
    (close conn)))

;; connect should throw an exception
(deftest connect-failure-test
  (with-redefs [*recv* (constantly nil)]
    (let [on-error-result (atom nil)]
      (try (connect
             "ws://nonexistent-server.example.com"
             :on-error #(reset! on-error-result %))
         (is false "It should not reach here for connect should have thrown an exception")
         (catch javax.websocket.DeploymentException e
           (is (.contains (.getMessage e) "Connection failed."))
           (is (nil? @on-error-result) "on-error shouldn't be called when the connection itself fails; instead, connect throws"))))))
