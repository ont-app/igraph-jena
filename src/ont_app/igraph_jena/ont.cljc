(ns ont-app.igraph-jena.ont
  {
   :clj-kondo/config {:linters {:unresolved-namespace {:level :off}}}
   ;; ... RDFFormat works fine
   }
  (:require
   ;;
   [ont-app.igraph.core :as igraph :refer [add]]
   [ont-app.vocabulary.core :as voc]
   [ont-app.rdf.ont :as rdf-ont]
   [ont-app.igraph-vocabulary.core :as igv]
   )
  (:import
   [org.apache.jena.riot
    RDFFormat
    ]
  ))

(voc/put-ns-meta!
 'ont-app.validation.ont
 {
  :vann/preferredNamespacePrefix "igraph-jena"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/igraph-jena/ont#"
  })

(def ontology-atom (atom @rdf-ont/ontology-atom))

(swap! ontology-atom igraph/union igv/ontology)

(defn update-ontology! [to-add]
  (swap! ontology-atom add to-add))


;;;;;;;;
;; RIOT
;;;;;;;;

(voc/put-ns-meta!
 'ont-app.igraph-jena.ont.riot.RDFFormat
 {:vann/preferredNamespaceUri "http://rdf.naturallexicon.org/ns/org.apache.jena.riot.RDFFormat#"
  :vann/preferredNamespacePrefix "riot-format"
  })

(update-ontology!
 [[:riot-format/RiotFormat
   :rdf/type :igraph/JavaClass
   :igraph/compiledAs RDFFormat
   :rdfs/comment "Some research is still needed to determine how this would inform our i/o"
   ;; TODO: research how to use this for i/o
   ]
  [:igraph/JavaClass :rdfs/subClassOf :igraph/CompiledObject
   :dc/description "A Java class available in a JAR"
   ;; TODO: move this back to igv
   ]
  [:riot-format/JSONLD
   :rdf/type :riot-format/RiotFormat
   :dcat/mediaType :formats/JSON-LD
   :igraph/compiledAs RDFFormat/JSONLD
   ]
  [:riot-format/JSONLD_COMPACT_FLAT
   :rdf/type :riot-format/RiotFormat
   :dcat/mediaType :formats/JSON-LD
   :igraph/compiledAs RDFFormat/JSONLD_COMPACT_FLAT
   ]
  [:riot-format/TURTLE
   :rdf/type :riot-format/RiotFormat
   :dcat/mediaType :formats/Turtle
   :igraph/compiledAs RDFFormat/TURTLE
   ]
  ])

(comment ;; these are the RDF formats in jena. TODO integrate into ont.
  org.apache.jena.riot.RDFFormat/ABBREV
  org.apache.jena.riot.RDFFormat/ASCII
  org.apache.jena.riot.RDFFormat/BLOCKS
  org.apache.jena.riot.RDFFormat/FLAT
  org.apache.jena.riot.RDFFormat/JSONLD
  org.apache.jena.riot.RDFFormat/JSONLD_COMPACT_FLAT
  org.apache.jena.riot.RDFFormat/JSONLD_COMPACT_PRETTY
  org.apache.jena.riot.RDFFormat/JSONLD_EXPAND_FLAT
  org.apache.jena.riot.RDFFormat/JSONLD_EXPAND_PRETTY
  org.apache.jena.riot.RDFFormat/JSONLD_FLAT
  org.apache.jena.riot.RDFFormat/JSONLD_FLATTEN_FLAT
  org.apache.jena.riot.RDFFormat/JSONLD_FLATTEN_PRETTY
  org.apache.jena.riot.RDFFormat/JSONLD_FRAME_FLAT
  org.apache.jena.riot.RDFFormat/JSONLD_FRAME_PRETTY
  org.apache.jena.riot.RDFFormat/JSONLD_PRETTY
  org.apache.jena.riot.RDFFormat/NQ
  org.apache.jena.riot.RDFFormat/NQUADS
  org.apache.jena.riot.RDFFormat/NQUADS_ASCII
  org.apache.jena.riot.RDFFormat/NQUADS_UTF8
  org.apache.jena.riot.RDFFormat/NT
  org.apache.jena.riot.RDFFormat/NTRIPLES
  org.apache.jena.riot.RDFFormat/NTRIPLES_ASCII
  org.apache.jena.riot.RDFFormat/NTRIPLES_UTF8
  org.apache.jena.riot.RDFFormat/PLAIN
  org.apache.jena.riot.RDFFormat/PRETTY
  org.apache.jena.riot.RDFFormat/RDFJSON
  org.apache.jena.riot.RDFFormat/RDFNULL
  org.apache.jena.riot.RDFFormat/RDFXML
  org.apache.jena.riot.RDFFormat/RDFXML_ABBREV
  org.apache.jena.riot.RDFFormat/RDFXML_PLAIN
  org.apache.jena.riot.RDFFormat/RDFXML_PRETTY
  org.apache.jena.riot.RDFFormat/RDF_THRIFT
  org.apache.jena.riot.RDFFormat/RDF_THRIFT_VALUES
  org.apache.jena.riot.RDFFormat/TRIG
  org.apache.jena.riot.RDFFormat/TRIG_BLOCKS
  org.apache.jena.riot.RDFFormat/TRIG_FLAT
  org.apache.jena.riot.RDFFormat/TRIG_PRETTY
  org.apache.jena.riot.RDFFormat/TRIX
  org.apache.jena.riot.RDFFormat/TTL
  org.apache.jena.riot.RDFFormat/TURTLE
  org.apache.jena.riot.RDFFormat/TURTLE_BLOCKS
  org.apache.jena.riot.RDFFormat/TURTLE_FLAT
  org.apache.jena.riot.RDFFormat/TURTLE_PRETTY
  org.apache.jena.riot.RDFFormat/UTF8
  org.apache.jena.riot.RDFFormat/ValueEncoding
  )
