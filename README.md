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
> (def g (jgraph/make-jena-graph (RDFDataMgr/loadModel "resources/test-data.ttl"))
```
Or equivalently use the `read-rdf` function:

```
> (def g (jgraph/read-rdf "resources/test-data.ttl"))

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
 :rdfs/label #{#lstr "Thing 2@en"},
 :rdf/type #{:eg/Thing}}
> 
> (g :eg/Thing2 :eg/number)
#{2}
>
> (g :eg/Thing2 :eg/number 2)
true
> 
> (add! g [[:eg/Thing3 :rdf/type :eg/Thing]
           [:eg/Thing3 :rdfs/label #lstr"Thing3@en"]
           [:eg/Thing3 :rdf/number 3]])
            
```

Set the IGraph docs for more details.

### Serializing output

```
> (write-rdf g "/tmp/testing.ttl" "turtle")
> 
```

## See also

- [ont-app/igraph](https://github.com/ont-app/igraph) defines the IGraph protocols.
- [igraph/vocabulary](https://github.com/ont-app/vocabulary) provides support for mapping between namespaced keywords and URIs.
- [igraph/graph-log](https://github.com/ont-app/graph-log) provides a graph-based logging facility useful for debugging.

## License

Copyright © 2020-21 Eric D. Scott

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
