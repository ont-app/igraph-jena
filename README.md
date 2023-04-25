# <img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="NaturalLexicon logo" :width=100 height=100/> ont-app/igraph-jena

This is a port of the Jena APIs to the
[IGraph](https://github.com/ont-app/igraph) protocol.

Part of the ont-app library, dedicated to Ontology-driven development.

## Usage

Available at [![Clojars Project](https://img.shields.io/clojars/v/ont-app/igraph-jena.svg)](https://clojars.org/ont-app/igraph-jena).


Require thus:

```
(ns my-ns
  (:require 
    [ont-app.igraph-jena.core :as jgraph])
  (:import 
    ;; any Jena-specific stuff
    ))
```
    
### Creating a graph:

Create the graph thus:

With no arguments (returns a Jena default model):

```
> (def g (jgraph/make-jena-graph))
> (type (:model g))
org.apache.jena.rdf.model.impl.ModelCom
> 
```

We can specify a file
```
> (def g (jgraph/make-jena-graph (RDFDataMgr/loadModel "/path/to/test-data.ttl"))
```
Or equivalently use the `load-rdf` function:

```
> (def g (jgraph/load-rdf "/path/to/test-data.ttl"))

```

If we have an existing Jena Model, we can define an IGraph wrapper around it:
```
> (def g (jgraph/make-jena-graph <existing-jena-model>))
```

... or if we have a Jena DataSet and the name of a graph (nil for default graph):

```
> (def g (jgraph/make-jena-graph <existing-dataset> <graph-name-or-nil>))
```
### Member access and manipulation

Then apply the standard methods for [IGraph member
access](https://github.com/ont-app/igraph#h2-igraph-protocol), with
[mutable](https://github.com/ont-app/igraph#IGraphMutable) member
manipulation operations `add!` and `subtract!`.

For example:

```
> (g :eg/Thing2)
{:eg/number #{2},
 :rdfs/label #{#voc/lstr "Thing 2@en"},
 :rdf/type #{:eg/Thing}}
> 
> (g :eg/Thing2 :eg/number)
#{2}
>
> (g :eg/Thing2 :eg/number 2)
true
> 
> (add! g [[:eg/Thing3 :rdf/type :eg/Thing]
           [:eg/Thing3 :rdfs/label #voc/lstr"Thing3@en"]
           [:eg/Thing3 :rdf/number 3]])
            
```

Set the IGraph docs for more details.

#### Support for encoding clojure containers in transit

It is possible to store Clojure data directly in the graph encoded as
typed literals in [transit](https://github.com/cognitect/transit-clj) format:

```
(add! g :eg/Thing4 :eg/hasMap {:sub-map {:a "this is a string"}
                               :vector [1 2 3]
                               :set #{:a :b :c}
                               :list '(1 2 3)
                               })
```

The resulting in-memory graph would look like this:

```
{
 # ... yadda
 :eg/Thing4
 #:eg{:hasMap
      #{{:sub-map {:a "this is a string"},
         :vector [1 2 3],
         :set #{:c :b :a},
         :list (1 2 3)}}},
 # yadda ...
```

After a call to `write-rdf`, the resulting turtle would look like this:

```
@prefix eg:   <http://rdf.example.com#> .

# ... yadda
eg:Thing4  eg:hasMap  "[\"^ \",\"~:sub-map\",[\"^ \",\"~:a\",\"this is a string\"],\"~:vector\",[1,2,3],\"~:set\",[\"~#set\",[\"~:c\",\"~:b\",\"~:a\"]],\"~:list\",[\"~#list\",[1,2,3]]]"^^<http://rdf.naturallexicon.org/ns/cognitect.transit#json> .
# yadda ...
```

This is not the most human-readable stuff on earth, but transit has
[several
advantages](https://cognitect.com/blog/2014/7/22/transit). One
disadvantage is that it's hard to formulate queries in SPARQL that
match these values directly. Note also that none of the internals of
the transit representation would be available under SPARQL
either. You'd need to read the file back into an RDF-based IGraph, or
use [transit's API](https://cognitect.github.io/transit-clj/) to
import the contents into the platform of your choice.

You should also be able to roll your own custom transit support for
other data structures.

See the [corresponding section in the igraph/rdf
module](https://github.com/ont-app/rdf#h3-transit-encoded-values) for
more details.

### I/O

The `ont-app/rdf` module has multimethods defined for I/O. The
functions described here are wrappers around those methods.

### Loading a file into a new graph

```
> (load-rdf to-load) -> <new graph>
```

The `to-load` argument can be either a string naming an existing file
path, a java file, a jar resource or a URL registered in
@`rdf/resource-catalog`.

### Reading a file into an existing graph

```
> (read-rdf g to-read) -> <updated graph>
```

Again, he `to-read` argument can be either a string naming an existing
file path, a java file, a jar resource or a URL registered in
@`rdf/resource-catalog`.


### Writing the contents of a graph to a file

At present only writing to local files is supported.


```
> (write-with-jena-writer g "/tmp/testing.ttl" :formats/Turtle)

```

There is also:

```
(defmethod rdf/write-rdf [JenaGraph :rdf-app/LocalFile :dct/MediaTypeOrExtent]
```

The supported formats arguments are derived automatically from the
`ontology` graph defined in `ont-app.igraph-jena.ont`.


This expression will give you the list of available formats:

```
(descendents :dct/MediaTypeOrExtent)
```

See also the [ont-app/rdf](https://github.com/ont-app/rdf)
documentation.


## See also

- [ont-app/igraph](https://github.com/ont-app/igraph) defines the IGraph protocols.
- [igraph/vocabulary](https://github.com/ont-app/vocabulary) provides support for mapping between namespaced keywords and URIs.
- [igraph/graph-log](https://github.com/ont-app/graph-log) provides a graph-based logging facility useful for debugging.

## License

Copyright © 2020-23 Eric D. Scott

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

<table>
<tr>
<td width=75>
<img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="Natural Lexicon logo" :width=50 height=50/> </td>
<td>
<p>Natural Lexicon logo - Copyright © 2020 Eric D. Scott. Artwork by Athena M. Scott.</p>
<p>Released under <a href="https://creativecommons.org/licenses/by-sa/4.0/">Creative Commons Attribution-ShareAlike 4.0 International license</a>. Under the terms of this license, if you display this logo or derivates thereof, you must include an attribution to the original source, with a link to https://github.com/ont-app, or  http://ericdscott.com. </p> 
</td>
</tr>
<table>
