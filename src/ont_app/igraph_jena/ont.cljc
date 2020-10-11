(ns ont-app.igraph-jena.ont
  (:require
   ;;
   [ont-app.igraph.core :as igraph :refer [add]]
   [ont-app.igraph.graph :as g :refer [make-graph]]
   [ont-app.vocabulary.core :as voc]
   )
  )
(voc/put-ns-meta!
 'ont-app.validation.ont
 {
  :vann/preferredNamespacePrefix "igraph-jena"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/igraph-jena/ont#"
  })

(def ontology-atom (atom (make-graph)))

(defn update-ontology! [to-add]
  (swap! ontology-atom add to-add))

