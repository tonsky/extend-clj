(ns extend-clj.core-test
  (:require
    [extend-clj.core-atom-test]
    [clojure.test :as t]))

(def *summary
  (atom nil))

(defmethod t/report [::t/default :end-run-tests] [m]
  (reset! *summary (dissoc m :type)))

(defn ^:export test-cljs []
  (t/run-all-tests #"extend-clj\..*-test")
  (let [{:keys [fail error pass test]} @*summary]
    (when (pos? (+ fail error))
      (js/process.exit 1))))
