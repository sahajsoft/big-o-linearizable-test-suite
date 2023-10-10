(defproject jepsen.bigo "0.1.0-SNAPSHOT"
  :description "A Jepsen test for Big(O)"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main bigo.main
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.2.1-SNAPSHOT"]
                 [cheshire "5.6.3"]
                 [clj-http "3.12.3"]])