(ns ont-app.igraph-jena.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as spec]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos]]
   [ont-app.igraph-jena.core :as jena]
   [ont-app.rdf.core :as rdf]
   [ont-app.rdf.test-support :as rdf-test]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph.core :as igraph :refer [add
                                           normal-form
                                           subjects
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
   (log-reset! :glog/TRACE))
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
  (jena/load-rdf data))


(deftest test-normal-form
  "Confirms expectation of normal for for `test-data.ttl`"
  (let [g (jena/load-rdf data)]
    (is (= {:http:%2F%2Frdf.example.com%2Ftest-file.ttl
            #:rdf{:type #{:eg/TestFile}},

            :eg/Thing1
            {:eg/number #{1},
             :rdfs/label #{#voc/lstr "Thing 1@en"},
             :rdf/type #{:eg/Thing}},

            :eg/Thing2
            {:eg/number #{#voc/dstr "2^^eg:USDollars"},
             :rdfs/label #{#voc/lstr "Thing 2@en"},
             :rdf/type #{:eg/Thing}}}

           (normal-form g)))))

(def readme-test-report
  "Holds the contents of the readme-examples report to examine in case of failure"
  (atom nil))

(defn make-test-graph
  "The value provided as the makeGraphFn for rdf test support"
  [data]
  (-> (jena/make-jena-graph)
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
                             jena/standard-io-context
                             g
                             ttl-file
                             :formats/Turtle))
        ]
  (-> (native-normal/make-graph)
      (add [:rdf-app/RDFImplementationReport
            :rdf-app/makeGraphFn jena/make-jena-graph
            :rdf-app/loadFileFn jena/load-rdf
            :rdf-app/readFileFn jena/read-rdf
            :rdf-app/writeFileFn call-write-method
            ]))))

;; Resources kept in the jar are not accessible to jena
;; so we declare File Resources to be Cached Resources
;; and access the contents as a cached Local File
(derive ont_app.igraph_jena.core.JenaGraph :rdf-app/IGraph)
(derive :rdf-app/FileResource :rdf-app/CachedResource)

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
  (rdf/write-rdf jena/standard-io-context
                 (jena/make-jena-graph)
                 (io/file "/tmp/test-write-method.ttl")
                 :formats/Turtle)
  (is (.exists   (io/file "/tmp/test-write-method.ttl"))))


(deftest issue-5-support-subtraction-of-lstr
  "We should be able to subtract language strings, and assert unique should subtract whatever may have been there before."
  (let [g (jena/load-rdf data)
        ]
    (igraph/subtract! g [:eg/Thing1 :rdfs/label #voc/lstr "Thing 1@en"])
    (is (false? (g :eg/Thing1 :rdfs/label #voc/lstr "Thing 1@en")))

    (igraph/assert-unique! g :eg/Thing2 :rdfs/label #voc/lstr "Thing Two@en")
    (is (= #{#voc/lstr "Thing Two@en"}
           (g :eg/Thing2 :rdfs/label)))
    ))


(deftest test-transit-support
  (testing "Create typed literal"
    (let [g (jena/load-rdf data)
          v-literal (.createTypedLiteral (:model g)
                                         (rdf/render-transit-json [1 2 3])
                                         (voc/uri-for :transit/json))
          ]
      (is (=  "[1 2 3]")
          (.getLexicalForm v-literal))
      (is (= (voc/uri-for :transit/json)
             (.getDatatypeURI v-literal)))
      )))

(defn do-import-raw-ttl-from-github
  []
  (let [g (jena/make-jena-graph)
        rectangle-test (java.net.URL. "https://raw.githubusercontent.com/TopQuadrant/shacl/master/src/test/resources/sh/tests/rules/sparql/rectangle.test.ttl")
        ]
    (rdf/read-rdf-dispatch jena/standard-io-context g rectangle-test)
    (jena/read-rdf g rectangle-test)))


(deftest test-dstr-round-trip
  "Writes and reads a #voc/dstr tag"
  (let [test-file "/tmp/dstr-round-trip.ttl"
        g (-> (jena/make-jena-graph)
              (igraph/add! [:eg/Test :eg/amount #voc/dstr "2^^eg:USDollars"]))
        ]
    (jena/write-with-jena-writer g test-file "ttl")
    (let [g' (jena/load-rdf test-file)
          ]
      (debug ::g-prime :g g')
      (is (= (igraph/normal-form g)
             (igraph/normal-form g')))
      (io/delete-file test-file))))


(defn do-bnode-round-trip
  "Adds a bnode to a graph, writes the graph and reads it back in."
  []
  (let [test-file "/tmp/bnode-round-trip.ttl"
        g (-> (jena/make-jena-graph)
              (igraph/add! [[:rdf-app/_:i-am-blank :rdf/type :eg/Blank]
                            [:rdf-app/_:i-am-also-blank :rdf/type :eg/Blank]
                            [:eg/NonBlank :rdf/type :eg/NotBlank]]))
        ]
    (jena/write-with-jena-writer g test-file "ttl")
    (let [g' (jena/load-rdf test-file)
          ]
      g')))

(defn do-non-bnode
  []
  (let [test-file "/tmp/bnode-round-trip.ttl"
        g (-> (jena/make-jena-graph)
              (igraph/add! ^::rdf/no-bnodes
                           [[:eg/i-am-not-blank :rdf/type :eg/NotBlank]
                            [:eg/i-am-also-not-blank :rdf/type :eg/NotBlank]
                            [:eg/NonBlank :rdf/type :eg/NotBlank]]))
        ]
    (jena/write-with-jena-writer g test-file "ttl")
    (let [g' (jena/load-rdf test-file)
          ]
      g')))

(deftest test-bnode-round-trip
  (let [g (do-bnode-round-trip)
        blank-and-not-blank (igraph/query g (rdf/prefixed  "Select * Where {?s a eg:Blank. ?s a eg:NonBlank}")) ;; should be empty
        blanks (igraph/query g (rdf/prefixed  "Select * Where {?s a eg:Blank.}"))
        not-blanks (igraph/query g (rdf/prefixed  "Select * Where {?s a eg:NotBlank.}"))
        ]
    (is (= (count (subjects g))
           3))
    (is (empty? blank-and-not-blank))
    (is (= (count blanks) 2))
    (is (= (count not-blanks) 1))
    (is (igraph/ask g :eg/NonBlank :rdf/type :eg/NotBlank))
  (doseq [s (subjects g)]
    (is (= (count (igraph/get-o g s :rdf/type)) 1)))
  ;; use the ^::rdf/no-bnodes meta data tag
  (let [g (do-non-bnode)]
    (is (= (igraph/normal-form g)
           #:eg{:i-am-also-not-blank #:rdf{:type #{:eg/NotBlank}},
                :NonBlank #:rdf{:type #{:eg/NotBlank}},
                :i-am-not-blank #:rdf{:type #{:eg/NotBlank}}}))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (igraph/add! g ^::rdf/no-bnodes [[:rdf-app/_:i-am-blank :rdf/type :eg/Blank]]))))))
(comment

  (def rectangle-test "https://raw.githubusercontent.com/TopQuadrant/shacl/master/src/test/resources/sh/tests/rules/sparql/rectangle.test.ttl")

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

  (def g-with-vector (let [g (jena/load-rdf data)
                           ]
                       (igraph/add! g [:eg/Thing3 :eg/hasVector [1 2 3]])))

  (jena/write-rdf g-with-vector "/tmp/g-with-vector.ttl" "turtle")
  
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
  (jena/write-rdf g-with-map "/tmp/g-with-map.ttl" org.apache.jena.riot.RDFFormat/TURTLE )
  (def g-as-read (jena/read-rdf "/tmp/g-with-map.ttl"))
  (defn ask-result [] (g-as-read :eg/Thing3 :eg/hasMap test-map))
  (rdf/bnode-kwi? :http:%2F%2Frdf.naturallexicon.org%2FMagnitude)
  
  (def g (rdf/load-rdf jena/standard-io-context rdf-test/bnode-test-data))

  (def rr (init-rdf-report))
  (def m (-> (glog/ith-entry 55) second ::rdf/g unique :model))
  (def f (-> (glog/ith-entry 55) second ::rdf/to-read unique))
  (.read m (str f))
  
  (pprint (test-support/query-for-failures (deref (do-rdf-implementation-tests))))

  (def g (rdf/load-rdf jena/standard-io-context "/home/eric/Data/RDF/natural-lexicon.ttl"))
  (jena/read-rdf g "/home/eric/Data/RDF/devops.ttl")
  (def g (rdf/load-rdf jena/standard-io-context "/home/eric/Data/RDF/devops.ttl"))

  (def ont-g (igraph/union @rdf/resource-catalog
                           @ont-app.igraph-jena.ont/ontology-atom))
  (def media-types (igraph/query ont-g
                                 [[:?url :dcat/mediaType :?media-type]]))

  (def suffixes (igraph/query ont-g
                                 [[:?x :formats/preferred_suffix :?suffix]]))


  (jena/write-with-jena-writer g  "/tmp/test.nt"
                                 (unique (ont-g :formats/N-triples :formats/media_type)))


  (jena/write-with-jena-writer g  "/tmp/test.json-ld" "JSON-LD")
  (jena/write-with-jena-writer g 
                               "/tmp/test.ttl"
                               (unique (ont-g :formats/Turtle :formats/media_type)))

  (jena/write-with-jena-writer g 
                               "/tmp/test.json-ld"
                               "application/ld+json" ;;"JSON-LD"
                               #_(unique (ont-g :formats/JSON-LD :formats/media_type)))
  (rdf/write-rdf jena/standard-io-context g (io/file "/tmp/test.json") :formats/JSON-LD)
  (log-reset! :glog/TRACE)
  
  (def g (jena/make-jena-graph))
  (igraph/add! g (@rdf/resource-catalog))

  (rdf/write-rdf jena/standard-io-context g (io/file "/tmp/test.ttl") :formats/Turtle)
  (rdf/write-rdf jena/standard-io-context g (io/file "/tmp/test.json-ld") :formats/JSON-LD)
  
  )

(defn describe-api
  "Returns [`member`, ...] for `obj`, for public members of `obj`, sorted by :name,  possibly filtering on `name-re`
  - Where
    - `obj` is an object subject to reflection
    - `name-re` is a regular expression to match against (:name `member`)
    - `member` := m, s.t. (keys m) = #{:name, :parameter-types, :return-type}
  "
  ([obj]
   (let [collect-public-member (fn [acc member]
                                (if (not
                                     (empty?
                                      (clojure.set/intersection #{:public}
                                                                (:flags member))))
                                  (conj acc (select-keys member
                                                         [:name
                                                          :parameter-types
                                                          :return-type]))
                                  ;;else member is not public
                                  acc))]
     (sort (fn compare-names [this that] (compare (:name this) (:name that)))
           (reduce collect-public-member [] (:members (reflect obj))))))
  ([obj name-re]
   (filter (fn [member]
             (re-matches name-re (str (:name member))))
           (describe-api obj))))
