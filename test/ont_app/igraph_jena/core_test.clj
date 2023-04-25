(ns ont-app.igraph-jena.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as spec]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos]]
   [ont-app.igraph-jena.core :as core]
   [ont-app.rdf.core :as rdf]
   [ont-app.rdf.test-support :as rdf-test]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph.core :as igraph :refer [add
                                           normal-form
                                           unique
                                           ]]
   [ont-app.igraph.graph :as native-normal]
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.igraph.test-support :as test-support]
   )
  (:import
   [org.apache.jena.riot
    RDFDataMgr
    ]
   [org.apache.jena.query
    Dataset
    DatasetFactory
    QueryExecution
    QueryExecutionFactory
    QueryFactory
    ]
   [org.apache.jena.rdf.model
    Model
    ResourceFactory
    ]
   ))

(voc/put-ns-meta!
 'com.example.rdf
 {
  :vann/preferredNamespacePrefix "eg"
  :vann/preferredNamespaceUri "http://rdf.example.com#"
  })

;; Graph-log configuration
(glog/log-reset!)
(glog/set-level! :glog/LogGraph :glog/OFF)

(defn log-reset!
  "Side-effect: enables graph-log with `level` (default `:glog/DEBUG`)"
  ([]
   (log-reset! :glog/DEBUG))
  ([level]
   (glog/log-reset!)
   (glog/set-level! level)))

(def ds
  "The Jena dataset to use for testing named graphs in a dataset"
  (DatasetFactory/create))

(def data
  "A small turtle file with test data"
  (io/file  "test/resources/test-data.ttl"))

(def g
  "An jena graph whose contents are `core-test/data`"
  (core/load-rdf data))


(deftest test-normal-form
  "Confirms expectation of normal for for `test-data.ttl`"
  (is (= {:http:%2F%2Frdf.example.com%2Ftest-file.ttl
          #:rdf{:type #{:eg/TestFile}},

          :eg/Thing1
          {:eg/number #{1},
           :rdfs/label #{#voc/lstr "Thing 1@en"},
           :rdf/type #{:eg/Thing}},
          
          :eg/Thing2
          {:eg/number #{2},
           :rdfs/label #{#voc/lstr "Thing 2@en"},
           :rdf/type #{:eg/Thing}}}
         
         (normal-form g))))

(def readme-test-report
  "Holds the contents of the readme-examples report to examine in case of failure"
  (atom nil))

(defn make-test-graph
  "The value provided as the makeGraphFn for rdf test support"
  [data]
  (-> (core/make-jena-graph)
      (igraph/add! data)))


(defn init-standard-report
  []
  (-> (native-normal/make-graph)
      (igraph/add [::test-support/StandardIGraphImplementationReport
                   ::test-support/makeGraphFn make-test-graph])))

(defn run-implementation-tests
  "One-liner to test a fully-featured implemenation of all IGraph protcols except IGraphSet."
  [report]
  (assert (= (test-support/test-readme-eg-mutation-dispatch report)
             ::igraph/mutable))
  (-> report
      (test-support/test-readme-eg-access)
      (test-support/test-readme-eg-mutation)
      (test-support/test-readme-eg-traversal)
      (test-support/test-cardinality-1)
      ))

(defn prepare-standard-igraph-report
  []
  (-> (init-standard-report)
      (run-implementation-tests)))

(defn do-standard-implementation-tests
  []
  (let [report (prepare-standard-igraph-report)
        ]
    ;; `report` with be a graph of test results, some of which might be of type Failed...
    (reset! readme-test-report report)
    report))
  
(deftest standard-implementation-tests
  "Standard tests against examples in the IGraph README for immutable set-enabled graphs"
  (let [report (do-standard-implementation-tests)
        ]
    (is (empty? (test-support/query-for-failures report)))))


(def rdf-test-report (atom nil))

(defn init-rdf-report
  []
  (let [call-write-method (fn call-write-method [g ttl-file]
                            (rdf/write-rdf
                             core/standard-io-context
                             g
                             ttl-file
                             :formats/Turtle))
        ]
  (-> (native-normal/make-graph)
      (add [:rdf-app/RDFImplementationReport
            :rdf-app/makeGraphFn core/make-jena-graph
            :rdf-app/loadFileFn core/load-rdf
            :rdf-app/readFileFn core/read-rdf
            :rdf-app/writeFileFn call-write-method
            ]))))

;; Resources kept in the jar are not accessible to jena
;; so we declare File Resources to be Cached Resources
;; and access the contents as a cached Local File
(derive ont_app.igraph_jena.core.JenaGraph :rdf-app/IGraph)
(derive :rdf-app/FileResource :rdf-app/CachedResource)
(prefer-method rdf/load-rdf
               [:rdf-app/IGraph
                :rdf-app/CachedResource]
               [ont_app.igraph_jena.core.JenaGraph
                :ont-app.igraph-jena.core/LoadableByName])

(defn do-rdf-implementation-tests
  []
  (reset! rdf-test-report (init-rdf-report))
  (-> rdf-test-report
      (rdf-test/test-bnode-support)
      (rdf-test/test-load-of-web-resource)
      (rdf-test/test-read-rdf-methods)
      (rdf-test/test-write-rdf-methods)
      (rdf-test/test-transit-support)))

(deftest rdf-implementation-tests
  (let [report (do-rdf-implementation-tests)]
    (is (empty? (test-support/query-for-failures @report)))))


(deftest test-write-method
  (rdf/write-rdf core/standard-io-context
                 (core/make-jena-graph)
                 (io/file "/tmp/test-write-method.ttl")
                 :formats/Turtle)
  (is (.exists   (io/file "/tmp/test-write-method.ttl"))))


(deftest issue-5-support-subtraction-of-lstr
  "We should be able to subtract language strings, and assert unique should subtract whatever may have been there before."
  (let [g (core/load-rdf data)
        ]
    (igraph/subtract! g [:eg/Thing1 :rdfs/label #voc/lstr "Thing 1@en"])
    (is (false? (g :eg/Thing1 :rdfs/label #voc/lstr "Thing 1@en")))

    (igraph/assert-unique! g :eg/Thing2 :rdfs/label #voc/lstr "Thing Two@en")
    (is (= #{#voc/lstr "Thing Two@en"}
           (g :eg/Thing2 :rdfs/label)))
    ))


(deftest test-transit-support
  (testing "Create typed literal"
    (let [g (core/load-rdf data)
          v-literal (.createTypedLiteral (:model g)
                                         (rdf/render-transit-json [1 2 3])
                                         (voc/uri-for :transit/json))
          ]
      (is (=  "[1 2 3]")
          (.getLexicalForm v-literal))
      (is (= (voc/uri-for :transit/json)
             (.getDatatypeURI v-literal)))
      )))






(comment

  (def g (RDFDataMgr/loadModel (str data)))

  (def q "Select * where {?s ?p ?o}")

  (def qe (-> (QueryFactory/create q)
              (QueryExecutionFactory/create g)))

  (def bindings (iterator-seq (.execSelect qe)))


  (def r (query-jena-model g "Select * where {?s ?p ?o}"))

  (def s (get-subjects g))

  (def G (make-jena-graph g))


  (def s (ResourceFactory/createResource
          (voc/uri-for :http://rdf.naturallexicon.org/en/ont#gloss)))
  
  (def p (ResourceFactory/createProperty
          (voc/uri-for :rdfs/subPropertyOf)))

  (def o (ResourceFactory/createResource
          (voc/uri-for :skos/definition)))

  (def stmt (ResourceFactory/createStatement s p o))

  (def v-rdf (rdf/render-transit-json [1]))

  (def g-with-vector (let [g (core/load-rdf data)
                           ]
                       (igraph/add! g [:eg/Thing3 :eg/hasVector [1 2 3]])))

  (core/write-rdf g-with-vector "/tmp/g-with-vector.ttl" "turtle")
  
  (def test-map {:b {:a 1} :v [1 2 3] :string "this is only a test" :set #{1 2 3}})
  (def test-map {:string "this is only a test"})
  (def test-map {:int 1})
  (def test-map {:sub-map {:a "this is a string"}
                 :vector [1 2 3]
                 :set #{:a :b :c}
                 :list '(1 2 3)
                 :lstr #voc/lstr "jail@en-US"
                 })
  (def g-with-map (let [g (read-rdf data)
                           ]
                    (add! g [:eg/Thing4 :eg/hasMap test-map])))
  (core/write-rdf g-with-map "/tmp/g-with-map.ttl" org.apache.jena.riot.RDFFormat/TURTLE )
  (def g-as-read (core/read-rdf "/tmp/g-with-map.ttl"))
  (defn ask-result [] (g-as-read :eg/Thing3 :eg/hasMap test-map))
  (rdf/bnode-kwi? :http:%2F%2Frdf.naturallexicon.org%2FMagnitude)
  
  (def g (rdf/load-rdf core/standard-io-context rdf-test/bnode-test-data))

  (def rr (init-rdf-report))
  (def m (-> (glog/ith-entry 55) second ::rdf/g unique :model))
  (def f (-> (glog/ith-entry 55) second ::rdf/to-read unique))
  (.read m (str f))
  
  (pprint (test-support/query-for-failures (deref (do-rdf-implementation-tests))))

  (def g (rdf/load-rdf core/standard-io-context "/home/eric/Data/RDF/natural-lexicon.ttl"))
  (core/read-rdf g "/home/eric/Data/RDF/devops.ttl")
  (def g (rdf/load-rdf core/standard-io-context "/home/eric/Data/RDF/devops.ttl"))

  (def ont-g (igraph/union @rdf/resource-catalog
                           @ont-app.igraph-jena.ont/ontology-atom))
  (def media-types (igraph/query ont-g
                                 [[:?url :dcat/mediaType :?media-type]]))

  (def suffixes (igraph/query ont-g
                                 [[:?x :formats/preferred_suffix :?suffix]]))


  (core/write-with-jena-writer g  "/tmp/test.nt"
                                 (unique (ont-g :formats/N-triples :formats/media_type)))


  (core/write-with-jena-writer g  "/tmp/test.json-ld" "JSON-LD")
  (core/write-with-jena-writer g 
                               "/tmp/test.ttl"
                               (unique (ont-g :formats/Turtle :formats/media_type)))

  (core/write-with-jena-writer g 
                               "/tmp/test.json-ld"
                               "application/ld+json" ;;"JSON-LD"
                               #_(unique (ont-g :formats/JSON-LD :formats/media_type)))
  (rdf/write-rdf core/standard-io-context g (io/file "/tmp/test.json") :formats/JSON-LD)
  (log-reset! :glog/TRACE)
  
  (def g (core/make-jena-graph))
  (igraph/add! g (@rdf/resource-catalog))

  (rdf/write-rdf core/standard-io-context g (io/file "/tmp/test.ttl") :formats/Turtle)
  (rdf/write-rdf core/standard-io-context g (io/file "/tmp/test.json-ld") :formats/JSON-LD)
  
  )
