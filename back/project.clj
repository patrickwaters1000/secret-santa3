(defproject secret-santa3 "0.1.0-SNAPSHOT"
  :description "App for assigning names in a Christmas gift exchange"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "1.5.648"]
                 [cheshire "5.10.0"]
                 [jakarta.xml.bind/jakarta.xml.bind-api "2.3.2"] ;; Apparently required for http-kit
                 [org.glassfish.jaxb/jaxb-runtime "2.3.2"] ;; Apparently required for http-kit
                 [compojure "1.1.8"]
                 [http-kit "2.1.16"]
                 [clj-time "0.15.2"]]
  :plugins [[lein-ring "0.12.4"]]
  :ring {:handler secret-senta3.core/app}
  :repositories [["jitpack" "https://jitpack.io"]]
  :main ^:skip-aot secret-santa3.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
