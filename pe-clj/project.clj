(defproject com.williballenthin/pe "0.1.0-SNAPSHOT"
  :description "A library for parsing Microsoft PE/COFF files."
  :url "https://github.com/williballenthin/reversing-clj/tree/master/pe-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojurewerkz/buffy "1.1.0"]
                 [org.clojure/tools.logging "0.4.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
