(ns ont-app.igraph-jena.core
  {
   :clj-kondo/config '{:linters {:unresolved-symbol {:level :off}
                                 :unresolved-namespace {:level :off}
                                 }}
   }
  (:require
   [clojure.java.io :as io]
   [ont-app.igraph.core :as igraph :refer [IGraph
                                           IGraphMutable
                                           add-to-graph
                                           get-p-o
                                           normal-form
                                           match-or-traverse
                                           reduce-spo
                                           remove-from-graph
                                           unique
                                           ]]
   [ont-app.igraph.graph :as native-normal]
   [ont-app.vocabulary.core :as voc]
   [ont-app.rdf.core :as rdf]
   [ont-app.graph-log.levels :refer [trace debug]]
   [ont-app.vocabulary.lstr]
   [ont-app.igraph-jena.ont :as ont]
   )
  (:import
   [ont_app.vocabulary.lstr
    LangStr]
   [org.apache.jena.rdf.model.impl
    LiteralImpl]
   [org.apache.jena.riot
    RDFDataMgr
    ;;RDFFormat
    ]
   [org.apache.jena.query
    ;; Dataset
    ;; QueryExecution
    QueryExecutionFactory
    QueryFactory
    ]
   [org.apache.jena.rdf.model
    ;; Model
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

;; TODO remove this when ont-app/rdf issue 8 is fixed
(voc/put-ns-meta!
 'cognitect.transit
 {
  :vann/preferredNamespacePrefix "transit"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/ns/cognitect.transit#"
  :dc/description "Functionality for the transit serialization format"
  :foaf/homepage "https://github.com/cognitect/transit-format"
  })

(def ontology
  "A native-normal graph holding the supporting ontology for igraph-jena"
  @ont/ontology-atom)

(defonce
  ^{:doc "Jena-specific values for rdf/query-template-defaults. Supports bnode-round-tripping."}
  query-template-defaults
  (merge @rdf/query-template-defaults
         {:rebind-_s "IRI(?_s)"
          :rebind-_p "IRI(?_p)"
          :rebind-_o "IF(isBlank(?_o), IRI(?_o), ?_o)"
          }))

(reset! rdf/query-template-defaults query-template-defaults)

;; TODO: Eplore the trade-offs this way vs. (binding [rdf/query-template-defaults query-template-defaults]

(defmethod rdf/render-literal LiteralImpl
  [elt]
  (cond
    (re-find #"langString$" (.getDatatypeURI elt))
    (ont-app.vocabulary.lstr/->LangStr (.getLexicalForm elt)
                                       (.getLanguage elt))
    (= (voc/uri-for :transit/json)
       (.getDatatypeURI elt))
    (rdf/read-transit-json (.lexicalValue (.getValue elt)))
    
    :else ;; else it's some other kinda literal
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

(defn create-object-resource-dispatch
  [g o]
  [(type g) (type o)])

(defmulti create-object-resource
  "returns a resource suitable as an object given `g` and `object`
  - where
    - `g` is a Jena IGraph
    - `object` is any argument
  - dispatched on fn [g obj] -> [(type g) (type obj)]
  "
  create-object-resource-dispatch)

(defmethod create-object-resource :default
  [_g object]
  (ResourceFactory/createTypedLiteral object))

(defn make-statement
  "Returns a Jena Statment for `s` `p` and `o`"
  [g s p o]
  (trace ::starting-make-statement
         ::g g
         ::s s
         ::p p
         ::o o)
  (ResourceFactory/createStatement
   (ResourceFactory/createResource
    (cond
      (keyword? s)
      (voc/uri-for s)

      (instance? java.net.URI s)
      (str s)

      :else
      (throw (ex-info "Unexpected subject"
                      {:type ::UnexpectedSubject
                       ::s s
                       }))))
   ,
   (ResourceFactory/createProperty
    (cond
      (keyword? p)
      (voc/uri-for p)

      (instance? java.net.URI p)
      (str p)

      :else
      (throw (ex-info "Unexpected property"
                      {:type ::UnexpectedProperty
                       ::p p
                       }))))
   ,
   (create-object-resource g o)
   ))

(defn get-subjects
  "Returns a sequence of KWIs corresponding to subjects in `jena-model`"
  [jena-model]
  (->> (.listSubjects jena-model)
       (iterator-seq)
       (map interpret-binding-element)
       ))

(defn- get-normal-form
  "Returns IGraph normal form representaion of `g`."
  [g]
  (rdf/query-for-normal-form query-jena-model g)
  )

(defn- do-get-p-o
  "Implements get-p-o for Jena"
  [g s]
  (rdf/query-for-p-o query-jena-model g s)
  )

(defn- do-get-o
  "Implements get-o for Jena"
  [g s p]
  (rdf/query-for-o query-jena-model g s p)
  )

(defn- do-ask
  "Implements ask for Jena"
  [g s p o]
  ;; Transit data is a special case which is hard (impossible?) to query for with SPARQL
  (if (isa? (type o) :rdf-app/TransitData)
    (-> (do-get-o g s p)
        (clojure.set/intersection #{o})
        (seq)
        (not))
    ;; else this is not transit data
    (rdf/ask-s-p-o ask-jena-model  g s p o)
    ))

(defrecord JenaGraph
    [model]
  IGraph
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

  IGraphMutable
  (add! [g to-add] (add-to-graph g to-add))
  (subtract! [g to-subtract] (remove-from-graph g to-subtract))
  )

(defmethod print-method JenaGraph [g ^java.io.Writer w]
  (.write w (format "<JenaGraph hash=%s size=%s>" (hash g) (.size (:model g)))))

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

(defmethod create-object-resource [JenaGraph clojure.lang.Keyword]
  [_g kwi]
  (ResourceFactory/createResource
   (try (voc/uri-for kwi)
        (catch clojure.lang.ExceptionInfo e
          (let [d (ex-data e)
                ]
            (case (:type d)

              ::voc/NoUriDeclaredForPrefix
              (str kwi)

              ::voc/NoIRIForKw
              (str kwi)

              ;; else it's some other error
              (throw e)))))))

(defmethod create-object-resource [JenaGraph LangStr]
  [_g lstr]
  (ResourceFactory/createLangLiteral (str lstr) (.lang lstr)))


(defmethod create-object-resource [JenaGraph :rdf-app/TransitData]
  [g transit-data]
  (.createTypedLiteral (:model g)
                       (rdf/render-transit-json transit-data)
                       (voc/uri-for :transit/json)))

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
  (let [collect-triple (fn collect-triple [s g [p o]]
                         (.add (:model g) (make-statement g s p o))
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
  [g to-remove]
  (let [remove-triple (fn [s p o]
                        (.removeAll
                         (:model g)
                         (ResourceFactory/createResource
                          (voc/uri-for s))
                         (ResourceFactory/createProperty
                          (voc/uri-for p))
                         (create-object-resource g o)
                         )
                        g)
        ]
    (if (empty? to-remove)
      g
      ;; else there are arguments
      (let [[s & po-s] to-remove]
        (assert (even? (count po-s)))
        (doseq [[p o] (partition 2 po-s)]
          (remove-triple s p o))
        g))))

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
          (remove-from-graph g ^:vector [s p o]))))
  g)


(defmethod remove-from-graph [JenaGraph :vector-of-vectors]
  [g vs]
  ;; todo: is there a more efficient way to do this?
  (doseq [v vs]
    (remove-from-graph g ^:vector v))
  g)

;;;;;;;;
;; I/O
;;;;;;;;;

;; Jena will figure out how to load local files and web resources by name...
(derive :rdf-app/FileResource :rdf-app/LocalFile)
(derive :rdf-app/LocalFile ::LoadableByName)
(derive :rdf-app/WebResource ::LoadableByName)

(defn derivable-media-types
  "Returns {child parent, ...} for media types
  - where
    - `child` should be declared to derive from `parent`, being subsumed by
      `:dct/MediaTypeOrExtent`
  - note
    - these derivations would inform method dispatch for rdf/write-rdf methods.
  "
  [ont]
  (let [subsumedBy (igraph/traverse-or :rdf/type
                                       :rdfs/subClassOf)
        subsumedBy* (igraph/transitive-closure subsumedBy)

        media-types (->> (igraph/query ont
                                       [[:?media-type subsumedBy* :dct/MediaTypeOrExtent]])
                         (map :?media-type)
                         (set)
                         )
        get-derivable (fn [macc media-type]
                        ;; macc := {child parent, ...}
                        (let [parent (unique
                                      (filter media-types (ont media-type subsumedBy)))

                              ]
                          (assoc macc media-type parent)))

        ]
    (reduce get-derivable {} media-types
            )))

;; Declare derivations for media types for write method dispatch...
(doseq [[child parent] (derivable-media-types ontology)]
  (when parent
    (derive child parent)))


(def standard-io-context
  "The standard context argument to igraph/rdf i/o methods"
  (-> @rdf/default-context
      (igraph/add [[#'rdf/load-rdf
                    :rdf-app/hasGraphDispatch JenaGraph
                    ]
                   [#'rdf/read-rdf
                    :rdf-app/hasGraphDispatch JenaGraph
                    ]
                   [#'rdf/write-rdf
                    :rdf-app/hasGraphDispatch JenaGraph
                    ]
                   ])))

(defn load-rdf
  "Returns a new graph initialized with `to-load`
  This is a wrapper around `rdf/load-rdf` with context as `standard-io-context`"
  
  [to-load]
  (rdf/load-rdf standard-io-context to-load))

(defmethod rdf/load-rdf [JenaGraph ::LoadableByName]
  [_context rdf-resource]
  (try (make-jena-graph (RDFDataMgr/loadModel (str rdf-resource)))
       (catch Error e
         (throw (ex-info "Jena riot error"
                         (merge
                          (ex-data e)
                          {:type ::RiotError
                           ::file-name (str rdf-resource)
                           }))))
       ))

(defn read-rdf
  "Side-effect: updates `g` to include contents of `to-read`
  This is a wrapper around rdf/read-rdf
  "
  [g to-read]
  (rdf/read-rdf standard-io-context g to-read))

(defmethod rdf/read-rdf [JenaGraph ::LoadableByName]
  [_context g rdf-file]
  (.read (:model g) (str rdf-file))
  g)

;; output ...

(defn write-with-jena-writer
  "Side-effect: writes contents of `g` to `target` in `fmt`
  - Where
    - `g` is a jena igraph
    - `target` names a file
    - `fmt` names an RDF format, e.g. 'Turtle' or 'text/turtle'
    - `base` (optional) isthe base URI of any relative URIs. Default is nil."
  ([g target fmt]
   (write-with-jena-writer g target fmt nil))

  ([g target fmt base]
  (with-open [out (io/output-stream target)]
    (.write (.getWriter (:model g) fmt)
            (:model g)
            out
            (if (keyword? base)
              (voc/uri-for base)
              base)))))

(defmethod rdf/write-rdf [JenaGraph :rdf-app/LocalFile :dct/MediaTypeOrExtent]
  [_context g rdf-file fmt]
  (let [ont-and-catalog (igraph/union @rdf/resource-catalog
                                      ontology
                                      )
        mime-type (unique (ont-and-catalog fmt :formats/media_type))
        base (unique (ont-and-catalog rdf-file :rdf-app/baseUri))
        ;; ...optional
        ]
    (assert mime-type)
    (write-with-jena-writer g rdf-file mime-type base)
    ))

(defmethod rdf/write-rdf [JenaGraph :rdf-app/LocalFile :riot-format/RiotFormat]
  [context g rdf-file fmt]
  ;; pending further research, we'll just use the standard format.
  (let [media-type (unique (ontology fmt :dcat/mediaType))
        ]
    (assert media-type)
    ;; ... a URI associated with a mime type string ....
    (assert (ontology media-type :formats/media_type))

    (rdf/write-rdf context g rdf-file media-type)))
