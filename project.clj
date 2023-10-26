(defproject io.github.tonsky/extend-clj "0.0.0"
  :description "Easily extend clojure.core built-in protocols"
  :license     {:name "MIT" :url "https://github.com/tonsky/extend-clj/blob/master/LICENSE"}
  :url         "https://github.com/tonsky/extend-clj"
  :dependencies
  [[org.clojure/clojure "1.11.1"]]
  :deploy-repositories
  {"clojars"
   {:url "https://clojars.org/repo"
    :username "tonsky"
    :password :env/clojars_token
    :sign-releases false}})