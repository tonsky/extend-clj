(ns extend-clj.core-atom-test
  (:require
    [extend-clj.core :as core #?@(:clj [:refer [deftype-atom]]
                                  :cljs [:refer-macros [deftype-atom]])]
    [clojure.test :as t :refer [is are deftest testing]])
  #?(:clj
     (:import [clojure.lang ExceptionInfo])))

(deftype-atom Cursor [*atom path]
  (deref-impl [this]
    (get-in @*atom path))
  (compare-and-set-impl [this oldv newv]
    (compare-and-set!
      *atom
      (assoc-in @*atom path oldv)
      (assoc-in @*atom path newv)))
  
  #?@(:clj [clojure.lang.Counted
            (count [_] 7)]
     :cljs [ICounted
            (-count [_] 7)]))

(deftest test-core
  (let [*atom   (atom {:x 1 :y 2})
        *cursor (->Cursor *atom [:x])]
    (testing "deref"
      (is (= 1 @*cursor)))
    
    (testing "swap"
      (is (= 2 (swap! *cursor inc)))
      (is (= 2 @*cursor))
      
      (is (= 3 (swap! *cursor + 1)))
      (is (= 3 @*cursor))
      
      (is (= 6 (swap! *cursor + 1 2)))
      (is (= 6 @*cursor))
      
      (is (= 12 (swap! *cursor + 1 2 3)))
      (is (= 12 @*cursor)))
    
    (testing "reset"
      (is (= 1 (reset! *cursor 1)))
      (is (= 1 @*cursor)))
    
    #?(:clj
       (testing "swap-vals"
         (is (= [1 2] (swap-vals! *cursor inc)))
         (is (= 2 @*cursor))
      
         (is (= [2 3] (swap-vals! *cursor + 1)))
         (is (= 3 @*cursor))
      
         (is (= [3 6] (swap-vals! *cursor + 1 2)))
         (is (= 6 @*cursor))
      
         (is (= [6 12] (swap-vals! *cursor + 1 2 3)))
         (is (= 12 @*cursor))))
    
    #?(:clj
       (testing "reset-vals"
         (is (= [12 1] (reset-vals! *cursor 1)))
         (is (= 1 @*cursor))))
    
    (testing "compare-and-set"
      (is (= true (compare-and-set! *cursor 1 2)))
      (is (= 2 @*cursor))
      (is (= false (compare-and-set! *cursor 1 3)))
      (is (= 2 @*cursor)))))

(deftest test-lookup
  (let [*atom (atom {:x 1})
        *cursor (->Cursor *atom [:x])]
    (is (identical? *atom (:*atom *cursor)))
    (is (identical? *atom (:*atom *cursor nil)))
    (is (= nil (:abc *cursor)))
    (is (= :not-found (:abc *cursor :not-found)))
    (is (identical? *atom (get *cursor :*atom)))
    (is (identical? *atom (get *cursor :*atom nil)))
    (is (= nil (get *cursor :abc)))
    (is (= :not-found (get *cursor :abc :not-found)))
    (is (= [:x] (:path *cursor)))))

(deftest test-extend
  (is (= 7 (count (->Cursor (atom {:x 1}) [:x])))))

(deftest test-meta
  (let [*cursor (->Cursor (atom {:x 1}) [:x] :meta {:m true})]
    (is (= {:m true} (meta *cursor)))
    (is (= {:m false} (alter-meta! *cursor update :m not)))
    (is (= {:m false} (meta *cursor)))
    (is (= {:m 1} (reset-meta! *cursor {:m 1})))
    (is (= {:m 1} (meta *cursor)))))

(deftest test-watchers
  (let [*cursor (->Cursor (atom {:x 1}) [:x])
        *w1     (atom [])
        *w2     (atom [])]
    (add-watch *cursor :w1 (fn [_ _ old new] (swap! *w1 conj [old new])))
    (swap! *cursor inc)
    (is (= [[1 2]] @*w1))
    (add-watch *cursor :w2 (fn [_ _ old new] (swap! *w2 conj [old new])))
    (swap! *cursor inc)
    (is (= [[1 2] [2 3]] @*w1))
    (is (= [[2 3]] @*w2))
    
    (add-watch *cursor :w2 (fn [_ _ old new] (reset! *w2 [old new])))
    (swap! *cursor inc)
    (is (= [[1 2] [2 3] [3 4]] @*w1))
    (is (= [3 4] @*w2))
    
    (remove-watch *cursor :w2)
    (swap! *cursor inc)
    (is (= [[1 2] [2 3] [3 4] [4 5]] @*w1))
    (is (= [3 4] @*w2))))

(deftest test-validator
  (let [*atom   (atom {:x 1 :y 2})
        *cursor (->Cursor *atom [:x])]

    #?(:cljs
       (is (= false (instance? Atom *cursor))))
    
    (set-validator! *cursor odd?)
    (is (= odd? (get-validator *cursor)))
    (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (swap! *cursor inc)))
    (is (= 1 @*cursor))
    (is (= 3 (swap! *cursor + 2)))
      
    (testing "as option"
      (swap! *atom assoc :x 1)
      (let [*cursor (->Cursor *atom [:x] :validator odd?)]
        (is (= odd? (get-validator *cursor)))
        (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (swap! *cursor inc)))
        (is (= 1 @*cursor))
        (is (= 3 (swap! *cursor + 2))))
      
      (swap! *atom assoc :x 1)
      (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (->Cursor *atom [:x] :validator even?))))
      
    (testing "set-validator!"
      (swap! *atom assoc :x 1)
      (let [*cursor (->Cursor *atom [:x])]
        (set-validator! *cursor odd?)
        (is (= odd? (get-validator *cursor)))
        (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (swap! *cursor inc)))
        (is (= 1 @*cursor))
        (is (= 3 (swap! *cursor + 2)))
        
        #?(:clj
            (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (set-validator! *cursor even?)))
           :cljs
            (is (thrown-with-msg? js/Error #"Validator rejected reference state" (set-validator! *cursor even?))))
           
        (is (thrown-with-msg? ExceptionInfo #"Invalid reference state" (->Cursor *atom [:x] :validator even?)))))))
  

; (t/test-ns *ns*)
; (t/run-test-var #'var)
