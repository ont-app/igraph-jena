(ns ont-app.igraph-jena.core
  {
   :clj-kondo/config '{:linters {:unresolved-symbol {:level :off}
                                 :unresolved-namespace {:level :off}
                                 }}
   }
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as spec]
   [cljstache.core :as stache]
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
   [ont-app.graph-log.levels :refer [trace value-trace debug]]
   [ont-app.vocabulary.lstr]
   [ont-app.vocabulary.dstr :as dstr]
   [ont-app.igraph-jena.ont :as ont]
   )
  (:import
   [ont_app.vocabulary.lstr
    LangStr]
   [ont_app.vocabulary.dstr
    DatatypeStr]
   [org.apache.jena.rdf.model.impl
    LiteralImpl
    ]
   [org.apache.jena.riot
    RDFDataMgr
    ]
   [org.apache.jena.query
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

;; TODO: Explore the trade-offs this way vs. (binding [rdf/query-template-defaults query-template-defaults]


(defmethod voc/untag :xsd/duration
  [obj]
  ;; Returns instance of org.apache.jena.datatypes.xsd.XSDDuration
  (-> (org.apache.jena.datatypes.xsd.impl.XSDDurationType.)
      (.parseValidated (str obj))))


;; Specify which dstr tags should be rendered untagged in the graph...
(doseq [to-untag [:transit/json
                  :xsd/dateTime
                  :xsd/double
                  :xsd/int
                  :xsd/integer
                  :xsd/long
                  :xsd/Boolean
                  ]]
  (derive to-untag :jena/UntaggedInGraph))

(defmethod rdf/read-literal LiteralImpl
  [elt]
  (trace ::starting-read-literal
         ::elt elt)
  (value-trace
   ::read-literal-result
   [::elt elt]
   (cond
     (re-find #"langString$" (.getDatatypeURI elt))
     (ont-app.vocabulary.lstr/->LangStr (.getLexicalForm elt)
                                        (.getLanguage elt))

     :else ;; else it's some other kinda literal
     (if-let [[_datum datatype] (dstr/parse (str elt))]
       (let [tag (voc/tag (if (voc/resource= datatype :transit/json)
                            (rdf/read-transit-json (.getLexicalForm elt))
                            ;; else no special encoding of the datum
                            (.getLexicalForm elt))
                          (voc/as-kwi datatype))]
         (if (isa? (voc/as-kwi datatype) :jena/UntaggedInGraph)
           (voc/untag tag identity)
           ;; else don't untag
           tag))
        ;; else can't parse out "..."^^"..."
       (.getValue elt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; RESOURCE TYPE: jena/Resource
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(voc/register-resource-type-context! ::resource-type-context ::rdf/resource-type-context)
(derive :jena/URI :jena/Resource)
(derive :jena/Bnode :jena/Resource)
(derive :jena/BnodeKwi :rdf-app/BnodeKwi)

(def bnode-tag-re "round-trippable bnode string"
  (re-pattern (str "^"
                   "<"
                   (subs (str rdf/bnode-name-re) 1) ;; drop the ^ starts-with marker
                   ">")))

(defmethod voc/resource-type [::resource-type-context (type "")]
  [this]
  ;; check for a string formatted like a bnode...
  (if (re-matches bnode-tag-re this)
    :jena/BnodeString
    ;; else handle it like any other string....
    (let [m (methods voc/resource-type)
          parent-context (unique (parents (::voc/hierarchy @voc/resource-types)
                                          ::resource-type-context))
          redispatch (m [parent-context (type this)])]
      (redispatch this))))

(defmethod voc/resource-type [::resource-type-context (type :x)] ;; keyword
  [this]
  (cond
    ;; Check for a bnode kwi...
    (and (= (namespace this) "jena")
         (re-matches rdf/bnode-name-re (name this)))
    :jena/BnodeKwi

    (and (= (namespace this) "rdf")
         (re-matches rdf/bnode-name-re (name this)))
    :rdf-app/BnodeKwi

    :else ;; else handle like any other keyword...
    (let [m (methods voc/resource-type)
          parent-context (unique (parents (::voc/hierarchy @voc/resource-types)
                                          ::resource-type-context))
          redispatch (m [parent-context (type this)])
          ]
      (redispatch this))))

(defmethod voc/resource-type [::resource-type-context
                              org.apache.jena.rdf.model.impl.ResourceImpl]
  [this]
  (value-trace
   ::resource-type-for-ResourceImpl
   [::this this]
   (if-let [u (.getURI this)]
     (if (spec/valid? :voc/uri-str-spec u)
       :jena/URI
       ;; else
       :jena/Bnode)
     ;; else no URI field
     (if-let [_id (.getId this)]
       :jena/Bnode
       ;;else
       (throw (ex-info "Could not infer resource-type for " this
                       {:type ::could-not-find-resource-type
                        ::this this}))))))


;; bnode strings
(defmethod voc/as-uri-string :jena/BnodeString
  [this]
  (str "_:" (rdf/normalize-bnode-string this)))

#_(defmethod voc/as-kwi :jena/BnodeString
  [this]
  (let [rdf-kwi (-> (methods voc/as-kwi) :rdf-app/BnodeString)
        ]
    (rdf-kwi (rdf/normalize-bnode-string this))))

(defmethod voc/as-kwi :jena/BnodeString
  [this]
  (keyword "jena" (rdf/normalize-bnode-string this)))


;; bnode kwis
(defmethod voc/as-uri-string :jena/BnodeKwi
  [this]
  {:post [(= (voc/resource-type %) :jena/BnodeString)]}
  (let [[_ parsed-name] (re-matches rdf/bnode-name-re (name this))]
    (when (not parsed-name)
      (throw (ex-info (str "Cound not parse bnode kwi " this)
                      {:type ::CoundNotParseBnodeKwi
                       ::this this})))
    (str "<_:" parsed-name ">")))

(defmethod voc/as-qname :rdf-app/BnodeKwi
  [this]
  (voc/as-uri-string this))

;; uri objects
(defmethod voc/as-uri-string :jena/URI
  [this]
  (voc/as-uri-string (.getURI this)))

(defmethod voc/as-kwi :jena/URI
  [this]
  (voc/as-kwi (voc/as-uri-string this)))

(defmethod voc/as-qname :jena/URI
  [this]
  (voc/as-qname (voc/as-kwi this)))

;; bnode objects
(defmethod voc/as-uri-string :jena/Bnode
  [this]
  (voc/as-uri-string (or (.getURI this)
                         (-> (.getId this) str))))

(defmethod voc/as-kwi :jena/Bnode
  [this]
  (let [bnode-string-kwi (-> (methods voc/as-kwi) :jena/BnodeString)
        ]
    (bnode-string-kwi (or (.getURI this) (-> (.getId this) str)))))

(defmethod voc/as-qname :jena/Bnode
  [this]
  (voc/as-uri-string this))

;;;;;;;;;;;;;;;;;;;;;;;;
;; DEALING WITH QUERIES
;;;;;;;;;;;;;;;;;;;;;;;;

(defn- interpret-binding-element
  "Returns a KWI or literal value as appropriate to `elt`
  Where
  - `elt` is bound to some variable in a query posed to a jena model."
  [elt]
  (value-trace
   ::InterpretBindingElementResult
   [:elt elt]
   ;; Sadly, even though elt satifies voc/Resource
   (cond (instance? Resource elt)
         (voc/as-kwi elt)
         ;; else it's not a resource, it's a literal
         :else
         (rdf/read-literal elt))))

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
  (value-trace
   ::returning-default-object-resource
   [::object object]
   (ResourceFactory/createTypedLiteral object)))

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
      (voc/as-uri-string s)

      (instance? java.net.URI s)
      (str s)

      :else
      (throw (ex-info "Unexpected subject"
                      {:type ::UnexpectedSubject
                       ::s s}))))
   ,
   (ResourceFactory/createProperty
    (cond
      (keyword? p)
      (voc/as-uri-string p)

      (instance? java.net.URI p)
      (str p)

      :else
      (throw (ex-info "Unexpected property"
                      {:type ::UnexpectedProperty
                       ::p p
                       }))))
   ,
   (create-object-resource g o)))


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

(defmethod print-method org.apache.jena.rdf.model.impl.InfModelImpl
  [model ^java.io.Writer w]
  (.write w (format "<InfModelImpl hash=%s size=%s>" (hash model) (.size model))))

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
   (new JenaGraph (.getNamedModel ds (voc/as-uri-string kwi)))))

(defmethod create-object-resource [JenaGraph clojure.lang.Keyword]
  [_g kwi]
  (ResourceFactory/createResource
   (try (voc/as-uri-string kwi)
        (catch clojure.lang.ExceptionInfo e
          (let [d (ex-data e)]
            (case (:type d)

              ::voc/NoUriDeclaredForPrefix
              (str kwi)

              ::voc/NoIRIForKw
              (str kwi)

              ;; else it's some other error
              (throw e)))))))

(defmethod create-object-resource [JenaGraph java.net.URL]
  [_g url]
  (ResourceFactory/createResource (str url)))

(defmethod create-object-resource [JenaGraph LangStr]
  [_g lstr]
  (ResourceFactory/createLangLiteral (str lstr) (.lang lstr)))

(defmethod create-object-resource [JenaGraph DatatypeStr]
  [g dstr]
  (.createTypedLiteral (:model g)
                       (str dstr)
                       (voc/as-uri-string (.datatype dstr))))


(defmethod create-object-resource [JenaGraph :rdf-app/TransitData]
  [g transit-data]
  (.createTypedLiteral (:model g)
                       (rdf/render-transit-json transit-data)
                       (voc/as-uri-string :transit/json)))

(defmethod add-to-graph [JenaGraph :normal-form]
  [g to-add]
  (let [g' (native-normal/make-graph :contents to-add)
        ]
    (add-to-graph g
                  ^{::igraph/triples-format :vector-of-vectors}
                  (reduce-spo (fn [v s p o] (conj v [s p o])) [] g'))))

(defmethod add-to-graph [JenaGraph :vector]
  [g v]
  {:pre [(odd? (count v))
         (>= (count v) 3)]}
  (let [bnode-error (fn [bnode] (ex-info
                                 (str "Adding a direct statement with bnode when metadata"
                                      " ::no-bnodes was specified.")
                                 {:type ::unexpected-bnode-in-add-to-graph
                                  ::g g
                                  ::v v
                                  ::bode bnode}))
        collect-triple (fn collect-triple [s g [p o]]
                         ;; Need to load from ttl to round-trip bnodes properly
                         (when (rdf/bnode-kwi? s)
                           (throw (bnode-error s)))
                         (when (and (keyword? o) (rdf/bnode-kwi? o))
                           (throw (bnode-error o)))
                         (.add (:model g) (make-statement g s p o))
                         g)]
    (reduce (partial collect-triple (first v)) g (partition 2 (rest v)))
    g))

(defn vectors-to-ttl
  "Returns a string of turtle for `vectors`
  - Where
    - `vectors` := [[s [p o], ...]]
  "
  [g vectors]
  {:pre [(spec/assert ::igraph/vector-of-vectors vectors)]}
  (let [collect-triple (fn collect-triple [s sacc [p o]]
                         (str sacc
                              (stache/render "{{{s}}} {{{p}}} {{{o}}} ."
                                             {:s (voc/as-qname s)
                                              :p (voc/as-qname p)
                                              :o (if (spec/valid? :voc/kwi-spec o)
                                                   (voc/as-qname o)
                                                   (rdf/render-literal o))
                                              })))
        collect-ttl (fn [sacc v]
                      (reduce (partial collect-triple (first v)) sacc
                              (partition 2 (rest v))))]
    (value-trace
     ::vectors-to-ttl-result
     [::g g ::vectors vectors]
     (voc/prepend-prefix-declarations
      voc/turtle-prefixes-for
      (reduce collect-ttl "" vectors)))))

(declare standard-io-context)
(defmethod add-to-graph [JenaGraph :vector-of-vectors]
  [g vs]
  ;; In order to handle bnodes properly, we need to write to ttl and load from that
  ;; It's a bit faster to just write statements to the model directly
  ;; We'll do that if we declare ::rdf/no-bnodes in the metadata
  (if (-> (meta vs) ::rdf/no-bnodes)
    (doseq [v vs]
      (add-to-graph g ^{::igraph/triples-format :vector} v))
    ;; else there may be blank nodes. Read turtle ensure proper-round-tripping.
    (rdf/read-rdf standard-io-context g (vectors-to-ttl g vs)))
  g)

(defmethod remove-from-graph [JenaGraph :normal-form]
  [g v]
  (let [g' (native-normal/make-graph :contents v)]
    (remove-from-graph
     g
     ^{::igraph/triples-format :vector-of-vectors}
     (reduce-spo (fn [v s p o] (conj v [s p o])) [] g'))))

(defmethod remove-from-graph [JenaGraph :vector]
  [g to-remove]
  (let [remove-triple (fn [s p o]
                        (.removeAll
                         (:model g)
                         (ResourceFactory/createResource
                          (voc/as-uri-string s))
                         (ResourceFactory/createProperty
                          (voc/as-uri-string p))
                         (create-object-resource g o)
                         )
                        g)]
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
            (remove-from-graph g ^{::igraph/triples-format :vector} [s p o]))))
    2 (let [[s p] v]
        (doseq [o (g s p)]
          (remove-from-graph g ^{::igraph/triples-format :vector} [s p o]))))
  g)


(defmethod remove-from-graph [JenaGraph :vector-of-vectors]
  [g vs]
  (doseq [v vs]
    (remove-from-graph g ^{::igraph/triples-format :vector} v))
  g)

;;;;;;;;
;; I/O
;;;;;;;;;

;; Jena will figure out how to load local files and web resources by name...
;; (derive :rdf-app/FileResource :rdf-app/LocalFile)
(derive ont_app.igraph_jena.core.JenaGraph :rdf-app/IGraph)
(derive :rdf-app/LocalFile ::LoadableByName)
(derive :rdf-app/WebResource :rdf-app/CachedResource)

(prefer-method rdf/load-rdf
               [ont_app.igraph_jena.core.JenaGraph :ont-app.igraph-jena.core/LoadableByName]
               [:rdf-app/IGraph :rdf-app/CachedResource])


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
                         (set))
        get-derivable (fn [macc media-type]
                        ;; macc := {child parent, ...}
                        (let [parent (unique
                                      (filter media-types (ont media-type subsumedBy)))

                              ]
                          (assoc macc media-type parent)))]
    (reduce get-derivable {} media-types)))

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
                    ]])))

(defn load-rdf
  "Returns a new graph initialized with `to-load`
  This is a wrapper around `rdf/load-rdf` with context as `standard-io-context`"
  
  [to-load]
  (rdf/load-rdf standard-io-context to-load))

(defmethod rdf/load-rdf [JenaGraph ::LoadableByName]
  [_context rdf-resource]
  (trace ::starting-load-rdf-loadable-by-name
         ::rdf-resource  rdf-resource)
  (try (make-jena-graph (RDFDataMgr/loadModel (str rdf-resource)))
       (catch Error e
         (throw (ex-info "Jena riot error"
                         (merge
                          (ex-data e)
                          {:type ::RiotError
                           ::file-name (str rdf-resource)
                           }))))))

(defn read-rdf
  "Side-effect: updates `g` to include contents of `to-read`
  This is a wrapper around rdf/read-rdf
  "
  [g to-read]
  (rdf/read-rdf standard-io-context g to-read))

(defmethod rdf/read-rdf [JenaGraph ::LoadableByName]
  [_context g rdf-file]
  (try (RDFDataMgr/read (:model g) (str rdf-file))
       (catch Error e
         (throw (ex-info "Jena riot error"
                         (merge
                          (ex-data e)
                          {:type ::RiotError
                           ::file-name (str rdf-file)
                           })))))
  g)

(defmethod rdf/read-rdf [JenaGraph java.lang.String]
  ;; rdf-string is a string of typically turtle
  ;; assumes media type is :formats/Turtle, unless otherwise specified in `context`
  [context g rdf-string]
  (let [base (unique (context  #'ont-app.rdf.core/read-rdf :rdf-app/baseUri))
        format (or (context  #'ont-app.rdf.core/read-rdf :dcat/mediaType)
                   :formats/Turtle)]
    (.read (:model g)
             (-> rdf-string (.getBytes) (java.io.ByteArrayInputStream.))
             (when base (java.net.URI. (voc/as-uri-string base)))
             (unique (ontology format :formats/media_type)))))

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
              (voc/as-uri-string base)
              base)))))

(defmethod rdf/write-rdf [JenaGraph :rdf-app/LocalFile :dct/MediaTypeOrExtent]
  [_context g rdf-file fmt]
  (let [ont-and-catalog (igraph/union @rdf/resource-catalog ontology)
        mime-type (unique (ont-and-catalog fmt :formats/media_type))
        base (unique (ont-and-catalog rdf-file :rdf-app/baseUri))]
    (assert mime-type)
    (write-with-jena-writer g rdf-file mime-type base)))

(defmethod rdf/write-rdf [JenaGraph :rdf-app/LocalFile :riot-format/RiotFormat]
  [context g rdf-file fmt]
  ;; pending further research, we'll just use the standard format.
  (let [media-type (unique (ontology fmt :dcat/mediaType))]
    (assert media-type)
    ;; ... a URI associated with a mime type string ....
    (assert (ontology media-type :formats/media_type))
    (rdf/write-rdf context g rdf-file media-type)))
