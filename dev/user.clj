(ns user
  (:require
    [clojure.core.server :as server]
    [clojure.string :as str]
    [clojure.test :as test]
    [clojure.tools.namespace.repl :as ns]))

(ns/disable-reload!)

(ns/set-refresh-dirs "src" "dev" "test")

(def lock
  (Object.))

(defn position []
  (let [trace (->> (Thread/currentThread)
                (.getStackTrace)
                (seq))
        el    ^StackTraceElement (nth trace 4)]
    (str "[" (clojure.lang.Compiler/demunge (.getClassName el)) " " (.getFileName el) ":" (.getLineNumber el) "]")))

(defn p [form]
  `(let [t# (System/currentTimeMillis)
         res# ~form]
     (locking lock
       (println (str "#p" (position) " " '~form " => (" (- (System/currentTimeMillis) t#) " ms) " (pr-str res#))))
     res#))

(def *reloaded
  (atom #{}))

(add-watch #'ns/refresh-tracker ::log
  (fn [_ _ _ new]
    (swap! *reloaded into (:clojure.tools.namespace.track/load new))))

(defn reload []
  (set! *warn-on-reflection* true)
  (reset! *reloaded #{})
  (let [res (ns/refresh)]
    (when (not= :ok res)
      (.printStackTrace ^Throwable res)
      (throw res)))
  (str "Ready – " (count @*reloaded) " ns" (when (> (count @*reloaded) 1) "es")))

(defn -main [& args]
  (alter-var-root #'*command-line-args* (constantly args))
  ; (require 'extend-clj.core-test)
  (reload)
  (let [args (apply array-map args)
        port (parse-long (get args "--repl-port" "5555"))]
    (server/start-server
      {:name          "repl"
       :port          port
       :accept        'clojure.core.server/repl
       :server-daemon false})
    (println "Started Socket REPL server on port" port)))

(defn test-all []
  (reload)
  (let [{:keys [fail error] :as res} (test/run-all-tests #"extend-clj\..*-test")
        res (dissoc res :type)]
    (if (pos? (+ fail error))
      (throw (ex-info "Tests failed" res))
      res)))

(defn -test [_]
  ; (require 'extend-clj.core-test)
  (reload)
  (let [{:keys [fail error]} (test/run-all-tests #"extend-clj\..*-test")]
    (System/exit (+ fail error))))
