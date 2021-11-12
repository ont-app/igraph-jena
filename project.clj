(defproject ont-app/igraph-jena "0.1.2-SNAPSHOT"
  :description "Library to port the Apache Jena APIs to the ont-app/iGraph protocol"
  :url "https://github.com/ont-app/igraph-jena"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; deps ambiguities
                 [cheshire "5.10.1"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.12.4"]
                 ;; clojure
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/spec.alpha "0.2.194"]
                 ;; 3rd party libs
                 [org.apache.jena/jena-core "3.17.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               ]
                  ]
                 [org.apache.jena/jena-arq "3.17.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               ]
                  ]
                 [org.apache.jena/jena-base "3.17.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               ]
                  ]
                 [org.apache.jena/jena-iri  "3.17.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               ]
                  ]
                 [ont-app/graph-log "0.1.4"
                  :exclusions [org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations
                               ]]
                 [ont-app/igraph "0.1.7"
                  :exclusions [
                               org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations
                                ]]
                 [ont-app/igraph-vocabulary "0.1.2"
                  :exclusions [org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations
                               ]]
                 [ont-app/vocabulary "0.1.3"
                  :exclusions [org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations]]
                 [ont-app/rdf "0.1.3"
                  :exclusions [org.clojure/clojurescript
                               com.google.errorprone/error_prone_annotations
                               ]]
                 ]

  ;; :main ^:skip-aot ont-app.igraph-jena.core
  :target-path "target/%s"
  :resource-paths ["resources"]
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :codox {:output-path "doc"}
  :profiles {:uberjar {}
             :dev {:dependencies []
                   :resource-paths  ["resources" "test/resources" ]
                   :source-paths ["src"]
                   :clean-targets
                   ^{:protect false}
                   ["resources/test"
                    :target-path
                    ]
                   }
             })
