(defproject ont-app/igraph-jena "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "https://github.com/ont-app/igraph-jena"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; deps ambiguities
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.fasterxml.jackson.core/jackson-core "2.10.2"]
                 ;; clojure
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/spec.alpha "0.2.187"]
                 ;; 3rd party libs
                 [org.apache.jena/jena-core "3.14.0"]
                 [org.apache.jena/jena-arq "3.14.0"]
                 [org.apache.jena/jena-base "3.14.0"]
                 [org.apache.jena/jena-iri "3.14.0"]
                 [org.slf4j/slf4j-simple "1.7.30"]
                 ;; Ont-app libs
                 [ont-app/graph-log "0.1.1"]
                 [ont-app/igraph "0.1.5"]
                 [ont-app/igraph-vocabulary "0.1.2-SNAPSHOT"]
                 [ont-app/vocabulary "0.1.2-SNAPSHOT"]
                 [ont-app/rdf "0.1.1-SNAPSHOT"
                  :exclusions [org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations]]
                 ]

  ;; :main ^:skip-aot ont-app.igraph-jena.core
  :target-path "target/%s"
  :resource-paths ["resources"]
  :plugins [[lein-codox "0.10.6"]
            [lein-ancient "0.6.15"]
            ]
  :source-paths ["src"]
  :test-paths ["src" "test"]

  :codox {:output-path "doc"}

  :profiles {:uberjar {}
             :dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  ]
                   :resource-paths  ["resources" "test/resources" ]
                   :source-paths ["src"]
                   :clean-targets
                   ^{:protect false}
                   ["resources/test"
                    :target-path
                    ]
                   }
             })
