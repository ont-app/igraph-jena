(ns ont-app.igraph-jena.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [ont-app.igraph-jena.core :as core :refer :all]
   [ont-app.rdf.core :as rdf]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph.core :refer :all]
   [ont-app.graph-log.core :as glog]
   )
  (:import
   [org.apache.jena.riot RDFDataMgr]
   [org.apache.jena.query Dataset
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

(def data (io/file  "test/resources/cedict-schema.ttl"))

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

  

         ;; (def x (first (iterator-seq (query-jena-model
;; m "Select * where {?s ?p ?o}"))))


;; (def v (.varNames x))

;; (def s (str (.get x "s")))

;; (defn collect-binding-values [b acc k]
;;   (assoc acc k (str (.get b k))))

;; (def m (reduce (partial collect-binding-values x) {} (iterator-seq (.varNames x))))

;; (def x (first (iterator-seq (query-jena-model g "Select * where {?s ?p ?o}"))))


;;(.close qe)

(deftest dummy-test
  (testing "fixme"
    (is (= 1 2))))
