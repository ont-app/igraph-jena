(ns ont-app.igraph-jena.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [ont-app.igraph-jena.core :as core :refer :all]
   [ont-app.rdf.core :as rdf]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph.core :refer :all]
   [ont-app.igraph.core-test :as ig-test]
   [ont-app.graph-log.core :as glog]
   [ont-app.graph-log.levels :refer :all]
   )
  (:import
   [org.apache.jena.riot
    RDFDataMgr]
   [org.apache.jena.query
    Dataset
    DatasetFactory
    QueryExecution
    QueryExecutionFactory
    QueryFactory]
   [org.apache.jena.rdf.model
    Model
    ResourceFactory
    ]
   ))

(glog/log-reset!)
(glog/set-level! :glog/LogGraph :glog/OFF)

(defn log-reset!
  ([]
   (log-reset! :glog/DEBUG))
  ([level]
   (glog/log-reset!)
   (glog/set-level! level)))

(voc/put-ns-meta!
 'com.example.rdf
 {
  :vann/preferredNamespacePrefix "eg"
  :vann/preferredNamespaceUri "http://rdf.example.com#"
  })

(def ds (DatasetFactory/create))

(def data (io/file  "test/resources/test-data.ttl"))

#_(def data (io/file  "test/resources/cedict-schema.ttl"))

(def g (read-rdf data))


(deftest test-normal-form
  (is (= {:eg/Thing1
          {:eg/number #{1},
           :rdfs/label #{#lstr "Thing 1@en"},
           :rdf/type #{:eg/Thing}},
          :http://rdf.example.com/test-file.ttl #:rdf{:type #{:eg/TestFile}},
          :eg/Thing2
          {:eg/number #{2},
           :rdfs/label #{#lstr "Thing 2@en"},
           :rdf/type #{:eg/Thing}}}
         (normal-form g))))

(let [eg (make-jena-graph ds :ig-test/eg-graph)]
  (add! eg ig-test/eg-data)
  (reset! ig-test/eg eg))

(let [other-eg (make-jena-graph ds :ig-test/other-eg)]
  (add! other-eg ig-test/other-eg-data)
  (reset! ig-test/other-eg other-eg))

(let [eg-with-types (make-jena-graph ds :ig-test/eg-with-types)]
  (add! eg-with-types ig-test/types-data)
  (add! eg-with-types ig-test/eg-data)
  (reset! ig-test/eg-with-types eg-with-types))

(let [eg-for-cardinality-1 (make-jena-graph ds :ig-test/eg-for-cardinality-1)]
  (add! eg-for-cardinality-1 (@ig-test/eg-with-types))
  (add! eg-for-cardinality-1 ig-test/cardinality-1-appendix)
  (reset! ig-test/eg-for-cardinality-1 eg-for-cardinality-1))




(deftest igraph-readme-examples
  (reset! ig-test/mutable-eg (-> (make-jena-graph);;  :ig-test/initial-graph)
                                 (add! ig-test/eg-data)))
  (testing "core test readme"
    (ig-test/readme))
  (testing "readme mutable"
    (ig-test/readme-mutable)
    ;; test issue #1...
    (ig-test/readme-add!-long-vector)))

;; TODO: We should be interpreting xsd and tagged literals.

(comment 
  (def g (RDFDataMgr/loadModel (str data)))

  (def q "Select * where {?s ?p ?o}")

  (def qe (-> (QueryFactory/create q)
              (QueryExecutionFactory/create g)))

  (def bindings (iterator-seq (.execSelect qe)))


  (def r (query-jena-model g "Select * where {?s ?p ?o}"))

  #_(def s (rdf/query-for-subjects nil query-jena-model g))
  (def s (get-subjects g))

  (def spo (rdf/query-for-normal-form nil query-jena-model  g))

  (def n (get-normal-form g))

  (def gloss-description (do-get-p-o g :http://rdf.naturallexicon.org/en/ont#gloss))

  (def gloss-subPropertyOf (do-get-o g
                                     :http://rdf.naturallexicon.org/en/ont#gloss
                                     :rdfs/subPropertyOf))
  (def answer (do-ask g
                      :http://rdf.naturallexicon.org/en/ont#gloss
                      :rdfs/subPropertyOf
                      :skos/definition))

  (def G (make-jena-graph g))


  (def s (ResourceFactory/createResource
          (voc/uri-for :http://rdf.naturallexicon.org/en/ont#gloss)))
  (def p (ResourceFactory/createProperty
          (voc/uri-for :rdfs/subPropertyOf)))

  (def o (ResourceFactory/createResource
          (voc/uri-for :skos/definition)))

  (def stmt (ResourceFactory/createStatement s p o))

  (def G (add-to-graph G [:http://eg.com/blah :http://eg.com/blah :http://eg.com/blah]))

  (def G (remove-from-graph G [:http://eg.com/blah :http://eg.com/blah :http://eg.com/blah]))

  (def G (add-to-graph G
                       [[:http://eg.com/blah :http://eg.com/blah :http://eg.com/blah]
                        [:http://eg.com/blah :http://eg.com/blah :http://eg.com/blih]]
                       ))


  )
