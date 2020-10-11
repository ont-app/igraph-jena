<img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="NaturalLexicon logo" :width=100 height=100/>

# ont-app/igraph-jena

This is a port of the Jena APIs to the [IGraph](https://github.com/ont-app/igraph) protocol.

Part of the ont-app library, dedicated to Ontology-driven development.

## Usage

Create the graph thus:

```
(ns my-ns
  (:require 
    [ont-app.igraph-jena.core :as jgraph])
  (:import 
    [org.apache.jena.riot RDFDataMgr]))
  
(jgraph/make-jena-graph (RDFDataMgr/loadModel "my-file.ttl"))
```

Then apply the standard methods for [IGraph member
access](https://github.com/ont-app/igraph#h2-igraph-protocol), with
[mutable](https://github.com/ont-app/igraph#IGraphMutable) member
manipulation operations `add!` and `subtract!`.

## License

Copyright © 2020 Eric D. Scott

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
