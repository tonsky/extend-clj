# Easily extend clojure.core built-in protocols

Extending built-in Clojure protocols is not easy: usually there are tens of them for simple stuff like map or atom, with multiple methods in each. And then you’ll have to do it again for ClojureScript.

Look no further! With `extend-clj` only need to implement the absolute semantic minimun, and the library will take care of the rest:

```
(extend-clj.core/deftype-atom Cursor
  [*atom path]

  (deref-impl [this]
    (get-in @*atom path))

  (compare-and-set-impl [this oldv newv]
    (let [atom   @*atom
          before (if (= oldv (get-in atom path))
                   atom
                   (assoc-in atom path oldv))
          after  (assoc-in atom path newv)]
      (compare-and-set! *atom before after))))
```

To construct new Cursor, use

```
(->Cursor (atom {:x {:y 1}}) [:x :y])
```

Result will behave as close to `clojure.lang.Atom` as possible: swap!/reset!/swap-vals!/reset-vals!/validators/meta/watchers — all of it will work as expected.

In addition to that, `ILookup` is implemented so you can access fields:

```
(let [a (atom {:x 1})
      cursor (->Cursor a [:x])]
  (:atom cursor) ; => a
  (:path cursor) ; => [:x])
```

## Current state

Supported in Clojure:

- `clojure.lang.Atom`

Supported in ClojureScript:

- `cljs.core.Atom`

Known issues:

- `reset-vals!` and `swap-vals!` don’t work in ClojureScript

## Using

Add this to deps.edn:

```
io.github.tonsky/extend-clj {:mvn/version "0.1.0"}
```

## License

Copyright © 2023 Nikita Prokopov

Licensed under [MIT](LICENSE).
