(ns ont-app.igraph-jena.core
  (:require
   [clojure.java.io :as io]
   [ont-app.igraph.core :as igraph :refer :all]
   [ont-app.igraph.graph :as native-normal]
   [ont-app.vocabulary.core :as voc]
   [ont-app.vocabulary.format :as fmt]
   [ont-app.igraph-jena.ont :as ont]
   [ont-app.rdf.core :as rdf]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.graph-log.core :as glog]
   )
  (:import
   [org.apache.jena.rdf.model.impl
    LiteralImpl]
   [org.apache.jena.riot
    RDFDataMgr
    RDFFormat]
   [org.apache.jena.query
    Dataset
    QueryExecution
    QueryExecutionFactory
    QueryFactory]
    [org.apache.jena.rdf.model
     Model
     ModelFactory
     Resource
     ResourceFactory
     ]
   ))

(voc/put-ns-meta!
 'ont-app.igraph-jena.core
 {
  :voc/mapsTo 'ont-app.igraph-jena.ont
  }
 )


(def ontology @ont/ontology-atom)

(defonce query-template-defaults
  (merge @rdf/query-template-defaults
         {:rebind-_s "IRI(?_s)"
          :rebind-_p "IRI(?_p)"
          :rebind-_o "IF(isBlank(?_o), IRI(?_o), ?_o)"
          }))

(reset! rdf/query-template-defaults query-template-defaults)
;; TODO: Eplore the trade-offs this way vs. (binding [rdf/query-template-defaults query-template-defaults]

(defmethod rdf/render-literal LiteralImpl
  [elt]
  (if (re-find #"langString$" (.getDatatypeURI elt))
    (ont-app.vocabulary.lstr/->LangStr (.getLexicalForm elt)
                                       (.getLanguage elt))
    ;; else it's some other kinda literal
    (.getValue elt)))


(defn interpret-binding-element
  "Returns a KWI or literal value as appropriate to `elt`
  Where
  - `elt` is bound to some variable in a query posed to a jena model."
  [elt]
  (trace ::StartingInterpretBindingElement
         :elt elt)
  (if (instance? Resource elt)
    (if-let [uri (.getURI elt)]
      (if (re-matches #"^_:.*" uri) ;; it's a URI-ified bnode
        (keyword "_" (str "<" uri ">"))
        ;; else it's a regular uri...
        (voc/keyword-for (str uri)))
      ;; else it's a bnode ResourceImpl
      (keyword "_" (str "<_:" (.getId elt) ">")))
    ;; else it's a literal
    (rdf/render-literal elt)))

(defn ask-jena-model
  "Returns true/false for ASK query `q` posed to Jena model `g`"
  [g q]

  (let [qe (-> (QueryFactory/create q)
               (QueryExecutionFactory/create g))]
    (try
      (.execAsk qe)
      (finally
        (.close qe)))))

(defn query-jena-model
  "Returns (`binding-map`, ...) for `q` posed to `g` using `query-op`
  Where
  - `binding-map` := {`var` `value`, ...}
  - `q` is a SPARQL query
  - `g` is a jena model
  - `query-op` is the jena query operation (optional. defualt is #(.execSelect %)
  - `var` is a keyword corresponding to a variable in `q`
  - `value` is an interpretation of the value bound to `var`
    this will be either a KWI or a literal, as appropriate.
  "
  ([g q]
   (query-jena-model #(.execSelect %) g q))
  
  ([query-op g q]
   (debug ::StartingQueryJenaModel
          :g g
          :q q)
   (let [qe (-> (QueryFactory/create q)
                (QueryExecutionFactory/create g))]
     (try
       (let [collect-binding-values (fn [b m k]
                                      (assoc m (keyword k)
                                             (let [v (.get b k)]
                                               (interpret-binding-element
                                                v))))
             render-binding-as-map (fn [b]
                                     (reduce (partial collect-binding-values
                                                      b)
                                             {}
                                             (iterator-seq (.varNames b))))

             bindings (iterator-seq (query-op qe)) ;;(.execSelect qe))
             result (doall (map render-binding-as-map bindings))
             ]
         result)
       (finally
         (.close qe))))))

(defn make-statement
  "Returns a Jena Statment for `s` `p` and `o`"
  [s p o]
  (ResourceFactory/createStatement
   (ResourceFactory/createResource
    (voc/uri-for s))
   (ResourceFactory/createProperty
    (voc/uri-for p))
   (if (keyword? o)
     (ResourceFactory/createResource
      (voc/uri-for o))
     ;; else it's not a uri
     (if (instance? ont_app.vocabulary.lstr.LangStr o)
       (ResourceFactory/createLangLiteral (str o) (.lang o))
       ;; else not a lang tag
       (ResourceFactory/createTypedLiteral o)))))

(defn get-subjects
  "Returns a sequence of KWIs corresponding ot subjects in `jena-model`"
  [jena-model]
  (->> (.listSubjects jena-model)
       (iterator-seq)
       (map interpret-binding-element)
       ))

(defn- get-normal-form
  "Returns IGraph normal form representaion of `g`."
  [g]
  ;;(binding [rdf/query-template-defaults query-template-defaults]
  (rdf/query-for-normal-form query-jena-model g)
  )

(defn- do-get-p-o
  "Implements get-p-o for Jena"
  [g s]
  (rdf/query-for-p-o query-jena-model g s)
  #_(binding [rdf/query-template-defaults query-template-defaults]
    (rdf/query-for-p-o query-jena-model g s)))

(defn- do-get-o
  "Implements get-o for Jena"
  [g s p]
  (rdf/query-for-o query-jena-model g s p)
  #_(binding [rdf/query-template-defaults query-template-defaults]
    (rdf/query-for-o query-jena-model g s p)))

(defn- do-ask
  "Implements ask for Jena"
  [g s p o]
  (rdf/ask-s-p-o ask-jena-model  g s p o)
  #_(binding [rdf/query-template-defaults query-template-defaults]
    (rdf/ask-s-p-o ask-jena-model  g s p o)))

(defrecord JenaGraph
    [model]
  igraph/IGraph
  (subjects [_] (get-subjects model))
  (normal-form [_] (get-normal-form model))
  (get-p-o [_ s] (do-get-p-o model s))
  (get-o [_ s p] (do-get-o model s p))
  (ask [_ s p o] (do-ask model s p o))
  (query [_ q] (query-jena-model model q))
  (mutability [_] ::igraph/mutable)

  clojure.lang.IFn
  (invoke [g] (normal-form g))
  (invoke [g s] (get-p-o g s))
  (invoke [g s p] (match-or-traverse g s p))
  (invoke [g s p o] (match-or-traverse g s p o))

  igraph/IGraphMutable
  (add! [g to-add] (add-to-graph g to-add))
  (subtract! [g to-subtract] (remove-from-graph g to-subtract))
  )

(defn make-jena-graph
  "Returns an implementation of igraph using a `model` or a named model in `ds` named `kwi`
  Where
  - `model` is a jena model (optional; default is a jena default model)
  - `ds` is a jena dataset to which we will declare a graph name with `kwi`
  - `kwi` is a keyword identifier translatable to the URI of a named graph in `ds`
  "
  ([]
   (make-jena-graph (ModelFactory/createDefaultModel)))
  ([model]
   (new JenaGraph model))
  ([ds kwi]
   (new JenaGraph (.getNamedModel ds (voc/uri-for kwi)))))


(defmethod add-to-graph [JenaGraph :normal-form]
  [g to-add]
  (let [g' (native-normal/make-graph :contents to-add)
        ]
    (add-to-graph g
                  ^:vector-of-vectors
                  (reduce-spo (fn [v s p o] (conj v [s p o])) [] g'))))

(defmethod add-to-graph [JenaGraph :vector]
  [g v]
  {:pre [(odd? (count v))
         (>= (count v) 3)
         ]
   }
  (let [collect-triple (fn [s g [p o]]
                         (.add (:model g) (apply make-statement [s p o]))
                         g)
        ]
    (reduce (partial collect-triple (first v)) g (partition 2 (rest v)))
    g))

(defmethod add-to-graph [JenaGraph :underspecified-triple]
  [g v]
  (case (count v)
    1 (let [[s] v
            po (g s)]
        (doseq [p (keys po)]
          (doseq [o (po p)]
            (add-to-graph g ^:vector [s p o]))))
    2 (let [[s p] v]
        (doseq [o (g s p)]
          (add-to-graph ^:vector [s p o])))))


(defmethod add-to-graph [JenaGraph :vector-of-vectors]
  [g vs]
  ;; todo: is there a more efficient way to do this?
  (doseq [v vs]
    (add-to-graph g ^:vector v))
  g)

(defmethod remove-from-graph [JenaGraph :normal-form]
  [g v]
  (let [g' (native-normal/make-graph :contents v)
        ]
    (remove-from-graph
     g
     ^:vector-of-vectors
     (reduce-spo (fn [v s p o] (conj v [s p o])) [] g'))))

(defmethod remove-from-graph [JenaGraph :vector]
  [g [s p o]]
  (.removeAll
   (:model g)
   (ResourceFactory/createResource
    (voc/uri-for s))
   (ResourceFactory/createProperty
    (voc/uri-for p))
   (if (keyword? o)
     (ResourceFactory/createResource
      (voc/uri-for o))
     ;; else it's not a uri
     (ResourceFactory/createTypedLiteral
      o)))
  g)

(defmethod remove-from-graph [JenaGraph :underspecified-triple]
  [g v]
  (case (count v)
    1 (let [[s] v
            po (g s)]
        (doseq [p (keys po)]
          (doseq [o (po p)]
            (remove-from-graph g ^:vector [s p o]))))
    2 (let [[s p] v]
        (doseq [o (g s p)]
          (remove-from-graph ^:vector [s p o]))))
  g)


(defmethod remove-from-graph [JenaGraph :vector-of-vectors]
  [g vs]
  ;; todo: is there a more efficient way to do this?
  (doseq [v vs]
    (remove-from-graph g ^:vector v))
  g)

(defn read-rdf
  "Returns `g` for `rdf-file`
  Where
  - `g` is an igraph
  - `rdf-file` is an io/file in one of the RDF formats"
  [rdf-file]
  (make-jena-graph (RDFDataMgr/loadModel (str rdf-file))))


(defn write-rdf
  "Side-effect: writes contents of `g` to `target` in `fmt`
  Where
  - `g` is a jena igraph
  - `target` names a file
  - `fmt` names an RDF fmt, e.g. 'Turtle'
  - `base` (optional) isthe base URI of any relative URIs. Default is nil."
  ([g target fmt]
   (write-rdf g target fmt nil))
  ([g target fmt base]
  (with-open [out (io/output-stream target)]
    (.write (.getWriter (:model g) fmt)
            (:model g)
            out
            (if (keyword? base)
              (voc/uri-for base)
              base)))))
  
