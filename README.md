# loom-jung

`loom-jung` provides wrappers for
[`loom`](https://github.com/aysylu/loom) graphs for use with JUNG, the
[Java Universal Network/Graph
Framework](http://jung.sourceforge.net/).  This is particularly useful
for taking advantage of JUNG's Swing-based graph visualization
facilities.

Unfortunately, JUNG has not seen a release since 2010. The functionality in
`loom-jung` is experimental, and is probably best considered as a proof of
concept.

JUNG's API is extremely verbose, and `loom-jung` currently only covers a
few functions. Doing anything significant with this library would require
a fairly deep understanding JUNG.

## Usage

`loom-jung` wraps Loom graphs and JUNG's visualization facilities. The
Swing portions are implemented as extensions
[Seesaw](http://github.com/daveray/seesaw).  At the moment,
`loom-jung` is a proof-of-concept, providing only a few features to
demonstrate basic visualizations.  The API is likely to change extensively.


To see it in action, start up a REPL from the project directory.
```
% lein repl

> (require [loom-jung.visualization :refer :all])
> (require [loom-jung.layout :refer :all])
```

To have something to visualize, we'll need a Loom graph. As we want a
dynamic visualization, we'll store it in an atom.

```
> (def ga (atom (graph)))
```

Add some nodes and edges to it:
```
user=> (swap! ga add-edges [1 2] [2 3] [3 4] [4 5] [5 1] [1 6] [2 7] [3 8] [4 9] [5 10])
```

A visualizer is a Swing component. At a minimum, it needs a graph supplied to it at creation.
```
> (def vv (visualizer ga))
```

To see it in action, add it to a Swing JFrame:
```
> (def f (frame))
> (-> f (config! :content vv) pack! show!)
```

JUNG has extensive facilities for customizing and interacting with the visualizations. The Java
API is extremely verbose. `loom-jung` tries to simplify this as much as possible. For the
currently wrapped API functions, you should be able to a standard Clojure function anyplace
that a Transformer is expected.

For example, to add labels to the vertics:
```
> (config! vv :vertex-label str)
```

Nearly every aspect of the graph is adjustable, though most features are not yet supported by
the `loom-jung` API. Here's an example of using a function to change node colors::
```
> (config! vv :vertex-fill #(if (even? %) Color/RED Color/BLUE))
```

`spring-layout` is used by default, and several other layouts are also
wrapped. For example, to give `circle-layout` a try:
```
> (def cl (circle-config g))
> (config! vv :layout cl)
```

`spring-layout` is a dynamic layout that constantly refreshes, so any
changes you make are reflected immediately in the visualization. This
is not the case for most JUNG layouts, which must be manually
redrawn. Unfortunately, this makes the JUNG visualizations less
immediately useful to most Clojure programmers.

```
> (swap! ga add-edges [11 12] [12 13] [11 13] [11 14])
> (.repaint vv)
> (config! vv :vertex-fill (constantly Color/BLACK))
> (.repaint vv)
```

## Supported Properties

The following configuration properties are currently implemented for
the `visualizer`:
```
:layout

:vertex-draw
:vertex-fill
:vertex-font
:vertex-label
:vertex-shape
:vertex-stroke

:edge-draw
:edge-label
:edge-shape
:edge-stroke

:arrow?
:arrow-draw
:arrow-fill
:arrow-stroke

:vertex-renderer
:edge-renderer
```

Making effective use of these requires some knowledge of JUNG.  The
most immediately interesting to play around with are the `:vertex-*`
and `:edge-*` options (except for `:vertex-renderer` and
`:edge-renderer`).  These take a function that expects a vertex or
edge, and returns a `java.awt.*` object of the appropriate type.
Note that `:arrow?` expects a predicate function that takes an edge
and returns a boolean (`true` indicates that, in a directed graph,
an arrowhead should be drawn). The other `:arrow-*` functions expect
a JUNG `Context` object, and will be more difficult to work with until
the `loom-jung` API is more developed.

## License

Copyright © 2014-2016 Paul L. Snyder <paul@pataprogramming.com>

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
