(defproject pe-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojurewerkz/buffy "1.1.0-SNAPSHOT"] ;; need version post August, 2017
                                                        ;; checkout from github, then do `lein install`
                                                        ;; also ensure #32 is merged: https://github.com/clojurewerkz/buffy/pull/32
                 [org.clojure/tools.logging "0.4.0"]]
  :main ^:skip-aot pe-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
