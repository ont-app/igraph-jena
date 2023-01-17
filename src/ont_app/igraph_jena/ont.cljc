(ns ont-app.igraph-jena.ont
  (:require
   ;;
   [ont-app.igraph.core :as igraph :refer [add]]
   [ont-app.igraph.graph :as g :refer [make-graph]]
   [ont-app.vocabulary.core :as voc]
   [ont-app.rdf.core :as rdf-app]
   [ont-app.rdf.ont :as rdf-ont]
   [ont-app.igraph-vocabulary.core :as igv]
   )
  (:import
   [org.apache.jena.riot
    RDFFormat]
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
org.apache.jena.riot.RDFFormat/ABBREV <sf>
org.apache.jena.riot.RDFFormat/ASCII <sf>
org.apache.jena.riot.RDFFormat/BLOCKS <sf>
org.apache.jena.riot.RDFFormat/FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD <sf>
org.apache.jena.riot.RDFFormat/JSONLD_COMPACT_FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD_COMPACT_PRETTY <sf>
org.apache.jena.riot.RDFFormat/JSONLD_EXPAND_FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD_EXPAND_PRETTY <sf>
org.apache.jena.riot.RDFFormat/JSONLD_FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD_FLATTEN_FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD_FLATTEN_PRETTY <sf>
org.apache.jena.riot.RDFFormat/JSONLD_FRAME_FLAT <sf>
org.apache.jena.riot.RDFFormat/JSONLD_FRAME_PRETTY <sf>
org.apache.jena.riot.RDFFormat/JSONLD_PRETTY <sf>
org.apache.jena.riot.RDFFormat/NQ <sf>
org.apache.jena.riot.RDFFormat/NQUADS <sf>
org.apache.jena.riot.RDFFormat/NQUADS_ASCII <sf>
org.apache.jena.riot.RDFFormat/NQUADS_UTF8 <sf>
org.apache.jena.riot.RDFFormat/NT <sf>
org.apache.jena.riot.RDFFormat/NTRIPLES <sf>
org.apache.jena.riot.RDFFormat/NTRIPLES_ASCII <sf>
org.apache.jena.riot.RDFFormat/NTRIPLES_UTF8 <sf>
org.apache.jena.riot.RDFFormat/PLAIN <sf>
org.apache.jena.riot.RDFFormat/PRETTY <sf>
org.apache.jena.riot.RDFFormat/RDFJSON <sf>
org.apache.jena.riot.RDFFormat/RDFNULL <sf>
org.apache.jena.riot.RDFFormat/RDFXML <sf>
org.apache.jena.riot.RDFFormat/RDFXML_ABBREV <sf>
org.apache.jena.riot.RDFFormat/RDFXML_PLAIN <sf>
org.apache.jena.riot.RDFFormat/RDFXML_PRETTY <sf>
org.apache.jena.riot.RDFFormat/RDF_THRIFT <sf>
org.apache.jena.riot.RDFFormat/RDF_THRIFT_VALUES <sf>
org.apache.jena.riot.RDFFormat/TRIG <sf>
org.apache.jena.riot.RDFFormat/TRIG_BLOCKS <sf>
org.apache.jena.riot.RDFFormat/TRIG_FLAT <sf>
org.apache.jena.riot.RDFFormat/TRIG_PRETTY <sf>
org.apache.jena.riot.RDFFormat/TRIX <sf>
org.apache.jena.riot.RDFFormat/TTL <sf>
org.apache.jena.riot.RDFFormat/TURTLE <sf>
org.apache.jena.riot.RDFFormat/TURTLE_BLOCKS <sf>
org.apache.jena.riot.RDFFormat/TURTLE_FLAT <sf>
org.apache.jena.riot.RDFFormat/TURTLE_PRETTY <sf>
org.apache.jena.riot.RDFFormat/UTF8 <sf>
org.apache.jena.riot.RDFFormat/ValueEncoding <sf>
)
