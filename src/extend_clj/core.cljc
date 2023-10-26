(ns extend-clj.core
  (:require
    [clojure.string :as str])
  #?(:cljs
     (:require-macros
       [extend-clj.core])))

(defprotocol IAtom3
  (deref-impl [this])
  (compare-and-set-impl [this oldv newv])
  
  (validate [this validator value])
  (notify-watches [this oldv newv])
  (swap* [this f args])
  )

#?(:clj
   (defn- cljs-env? [env]
     (boolean (:ns env))))

#?(:clj
   (defmacro deftype-atom
     "Allows you to declare new type that will behave like native Clojure Atom,
      but with custom `deref` and `compare-and-set` behaviors. Rest of swap!/reset!/
      swap-vals!/reset-vals! will be implemented through these two.
      
      Supports meta/validator/watches and ILookup for fields.
      
      Syntax:
      
          (deftype-atom <name> [<field> ...]
            (deref-impl [this]
              <impl>)
            (compare-and-set-impl [this oldv newv]
              <impl>))
      
      To use this structure, use `(-><name> <field> ...)` function. E.g. if you
      
          (deftype-atom X [a b c])
      
      then create instances like
      
          (->X a b c)
      
      You can add more interfaces after deref-impl/compare-and-set-impl and implement them as you wish:
      
          (deftype-atom <name> [<field> ...]
            (deref-impl [this]
              <impl>)
      
            (compare-and-set-impl [this oldv newv]
              <impl>)
            
            clojure.lang.Counted
            (count [this]
              7)
            
            clojure.lang.IBlockingDeref
            (deref [this ms timeout]
              (.deref this)))
      
      Warning: swap-vals! and reset-vals! don’t work in ClojureScript."
     [name fields & methods]
     (if (cljs-env? &env)
       (let [->name    (symbol (str "->" name))
             validator 'validator
             watches   'watches
             meta      'meta]
         `(do
            (deftype ~name [~@fields ~validator ~watches ~meta]
              ~'Object
              (~'equiv [this# other#]
                (cljs.core/-equiv this# other#))

              cljs.core/IEquiv
              (cljs.core/-equiv [o# other#]
                (identical? o# other#))

              cljs.core/IDeref
              (cljs.core/-deref [this#]
                (deref-impl this#))
           
              cljs.core/IReset
              (cljs.core/-reset! [this# newv#]
                (nth (swap* this# (constantly newv#) ()) 1))
           
              cljs.core/ISwap
              (cljs.core/-swap! [this# f#]
                (nth (swap* this# f# ()) 1))
           
              (cljs.core/-swap! [this# f# a#]
                (nth (swap* this# f# (list a#)) 1))
           
              (cljs.core/-swap! [this# f# a# b#]
                (nth (swap* this# f# (list a# b#)) 1))
           
              (cljs.core/-swap! [this# f# a# b# xs#]
                (nth (swap* this# f# (cons a# (cons b# xs#))) 1))

              cljs.core/IMeta
              (cljs.core/-meta [this#]
                ~meta)

              cljs.core/IWatchable
              (cljs.core/-notify-watches [this# oldv# newv#]
                (doseq [[k# f#] ~watches]
                  (f# k# this# oldv# newv#)))
           
              (cljs.core/-add-watch [this# key# f#]
                (set! (.-watches this#) (assoc ~watches key# f#))
                this#)
           
              (cljs.core/-remove-watch [this# key#]
                (set! (.-watches this#) (dissoc ~watches key#)))

              cljs.core/IHash
              (cljs.core/-hash [this#]
                (goog/getUid this#))
              
              cljs.core/ILookup
              (cljs.core/-lookup [this# k#]
                (cljs.core/-lookup this# k# nil))

              (cljs.core/-lookup [this# k# not-found#]
                (case k#
                  ~@(mapcat identity
                      (for [field fields]
                        [(keyword (str field))
                         field]))
                  not-found#))
              
              IAtom3
              (validate [this# validator# value#]
                (when (some? validator#)
                  (when-not (validator# value#)
                    (throw (ex-info "Invalid reference state" {:value value#})))))

              (notify-watches [this# oldv# newv#]
                (doseq [[k# w#] ~watches]
                  (w# k# this# oldv# newv#)))

              (swap* [this# f# args#]
                (let [oldv# (deref this#)
                      newv# (apply f# oldv# args#)]
                  (validate this# ~validator newv#)
                  (compare-and-set-impl this# oldv# newv#)
                  (notify-watches this# oldv# newv#)
                  [oldv# newv#]))
              
              ~@methods)
            (defn ~(with-meta ->name {:declared true})
              ([~@fields]
               (new ~name ~@fields nil {} {}))
              ([~@fields & rest#]
               (let [opts# (apply array-map rest#)
                     ref#  (new ~name ~@fields nil {} {})]
                 (when-some [validator# (:validator opts#)]
                   (validate ref# validator# @ref#)
                   (set! (.-validator ref#) validator#))
                 (when-some [meta# (:meta opts#)]
                   (reset-meta! ref# meta#))
                 ref#)))
            ~name))

       (let [->name      (symbol (str "->" name))
             class       (symbol (str (clojure.string/replace (str *ns*) "-" "_") "." name))
             __validator '__validator
             __watches   '__watches
             __meta      '__meta
             interfaces  (->> methods
                           (filter symbol?)
                           (map #(if (var? (resolve %)) 
                                   (:on (deref (resolve %)))
                                   %)))
             methods     (remove symbol? methods)]
         `(do
            (deftype* ~(symbol (str *ns*) (str name)) ~class
              [~@fields
               ~(with-meta __validator {:volatile-mutable true})
               ~(with-meta __watches {:volatile-mutable true})
               ~(with-meta __meta {:unsynchronized-mutable true})
               __extmap
               ^:unsynchronized-mutable ^int __hash
               ^:unsynchronized-mutable ^int __hasheq]
              :implements [clojure.lang.IMeta
                           clojure.lang.IReference
                           clojure.lang.IDeref
                           clojure.lang.IRef
                           clojure.lang.IAtom
                           clojure.lang.IAtom2
                           extend_clj.core.IAtom3
                           clojure.lang.ILookup
                           clojure.lang.IKeywordLookup
                           ~@interfaces]
              ~@methods
       
              ; clojure.lang.IMeta
              (meta [this#]
                ~__meta)
  
              ; clojure.lang.IReference
              (alterMeta [this# alter# args#]
                (locking this#
                  (.resetMeta this# (apply alter# ~__meta args#))))
      
              (resetMeta [this# m#]
                (locking this#
                  (set! ~__meta m#)
                  m#))

              ; clojure.lang.IDeref
              (deref [this#]
                (.deref-impl this#))

              ; clojure.lang.IRef
              (setValidator [this# vf#]
                (.validate this# vf# (.deref this#))
                (set! ~__validator vf#))

              (getValidator [this#]
                ~__validator)

              (getWatches [this#]
                ~__watches)

              (addWatch [this# key# callback#]
                (locking this#
                  (set! ~__watches (assoc ~__watches key# callback#))
                  this#))

              (removeWatch [this# key#]
                (locking this#
                  (set! ~__watches (dissoc ~__watches key#))
                  this#))
  
              ; clojure.lang.IAtom
              (swap [this# f#]
                (nth (.swap* this# f# ()) 1))
  
              (swap [this# f# arg#]
                (nth (.swap* this# f# (list arg#)) 1))
  
              (swap [this# f# arg1# arg2#]
                (nth (.swap* this# f# (list arg1# arg2#)) 1))
  
              (swap [this# f# arg1# arg2# rest#]
                (nth (.swap* this# f# (cons arg1# (cons arg2# rest#))) 1))

              (compareAndSet [this# oldv# newv#]
                (.validate this# ~__validator newv#)
                (if (.compare-and-set-impl this# oldv# newv#)
                  (do
                    (.notify-watches this# oldv# newv#)
                    true)
                  false))

              (reset [this# newv#]
                (nth (.resetVals this# newv#) 1))
  
              ; clojure.lang.IAtom2
              (swapVals [this# f#]
                (.swap* this# f# ()))
  
              (swapVals [this# f# arg#]
                (.swap* this# f# (list arg#)))
  
              (swapVals [this# f# arg1# arg2#]
                (.swap* this# f# (list arg1# arg2#)))
  
              (swapVals [this# f# arg1# arg2# rest#]
                (.swap* this# f# (cons arg1# (cons arg2# rest#))))

              (resetVals [this# newv#]
                (.validate this# ~__validator newv#)
                (loop []
                  (let [oldv# (.deref this#)]
                    (if (.compare-and-set-impl this# oldv# newv#)
                      (do
                        (.notify-watches this# oldv# newv#)
                        [oldv# newv#])
                      (recur)))))

              ; IAtom3
              (validate [this# validator# value#]
                (when (some? validator#)
                  (when-not (validator# value#)
                    (throw (ex-info "Invalid reference state" {:value value#})))))

              (notify-watches [this# oldv# newv#]
                (doseq [[k# w#] ~__watches]
                  (w# k# this# oldv# newv#)))

              (swap* [this# f# args#]
                (loop []
                  (let [oldv# (.deref this#)
                        newv# (apply f# oldv# args#)]
                    (if (.compareAndSet this# oldv# newv#)
                      [oldv# newv#]
                      (recur)))))
  
              ; clojure.lang.ILookup
              (valAt [this# key#]
                (.valAt this# key# nil))

              (valAt [this# key# else#]
                (case key#
                  ~@(mapcat identity
                      (for [field fields]
                        [(keyword (clojure.core/name field)) (with-meta field {})]))
                  else#))
         
              ; clojure.lang.IKeywordLookup
              ~(let [gclass  (gensym "gclass")
                     gtarget (gensym "gtarget")] 
                 `(getLookupThunk [this# k#]
                    (let [~gclass (class this#)]
                      (case k#
                        ~@(mapcat identity
                            (for [field fields]
                              [(keyword (clojure.core/name field))
                               `(reify clojure.lang.ILookupThunk
                                  (get [thunk# ~gtarget]
                                    (if (identical? (class ~gtarget) ~gclass)
                                      (. ~(with-meta gtarget {:tag name}) ~(symbol (str "-" (str field))))
                                      thunk#)))]))
                        nil)))))
       
            (import ~class)
       
            (defn ~->name
              ([~@fields]
               (new ~name ~@fields nil {}))
              ([~@fields & rest#]
               (let [opts# (apply array-map rest#)
                     ref#  (new ~name ~@fields nil {})]
                 (when-some [validator# (:validator opts#)]
                   (.setValidator ref# validator#))
                 (when-some [meta# (:meta opts#)]
                   (.resetMeta ref# meta#))
                 ref#)))

            ~class)))))
