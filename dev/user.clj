(ns user
  (:require
    [duti.core :as duti]))

(duti/set-dirs "src" "test")

(def reload
  duti/reload)

(defn -main [& {:as args}]
  (reload {:only #"extend-clj\..*"})
  (duti/start-socket-repl))

(defn test-all []
  (reload)
  (duti/test #"extend-clj\..*-test"))

(defn -test-main [_]
  (reload {:only #"extend-clj\..*-test"})
  (duti/test-exit #"extend-clj\..*-test"))
