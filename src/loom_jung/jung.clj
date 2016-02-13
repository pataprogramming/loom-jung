(ns loom-jung.jung
  (:import (edu.uci.ics.jung.graph UndirectedSparseGraph DirectedSparseGraph)))

;; This namespace holds methods for creating and manipulating JUNG graphs
;; directly. This code has been taken from a previous project, and is not
;; currently suitable for general use. The currently recommended approach
;; is to use the wrapper in loom-jung.wrap to treat your Loom graphs
;; as if they were JUNG graphs.

(defn dissoc-in [coll keys key] ; FIXME: switch to private
  (assoc-in coll keys (dissoc (get-in coll keys) key)))

(defn jung-directed-graph []
  (DirectedSparseGraph.))

(defn jung-undirected-graph []
  (UndirectedSparseGraph.))

(defrecord JungGraphHandler [jg properties])

(defn jung-graph-handler
  ([]  (JungGraphHandler. (jung-undirected-graph) (atom {})))
  ([g] (JungGraphHandler. g (atom {}))))

(defn jung-add-vertex! [jgh v]
  (when (.addVertex (:jg jgh) v)
    v))

(defn jung-remove-vertex! [jgh v]
  (swap! (:properties jgh) #(dissoc % v))
  (when (.removeVertex (:jg jgh ) v)
    v))

(defn jung-add-edge! [jgh v w]
  (when (and (.containsVertex (:jg jgh) v)
           (.containsVertex (:jg jgh) w))
    (when (.containsEdge (:jg jgh) [w v])
      [w v]
      (do
        (when (.addEdge (:jg jgh) [v w] v w)
          [v w])))))

(defn jung-remove-edge! [jgh v w]
  (if (.containsEdge (:jg jgh) [v w])
    (when (.removeEdge (:jg jgh) [v w])
      [v w])
    (when (.containsEdge (:jg jgh) [w v])
      (when (.removeEdge (:jg jgh) [w v])
        [w v]))))

(defn jung-empty! [jgh]
  ;; FIXME: Race conditions! Need java lock for these functions
  (dosync
   (swap! (:properties jgh) {}))
  (let [g  (:jg jgh)]
    (println "removing vertices " (.getVertices g))
    (doseq [e (vec (.getEdges g))] (.removeEdge g e))
    (doseq [v (vec (.getVertices g))] (.removeVertex g v))
    (println g)))

(defn jung-set-property! [jgh v key val]
  (when (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key] val)))))

(defn jung-loc-step [old-val {:keys [val action func] :as val-action}]
  (case action
    :set
    val
    :assoc ; :val should be [key value] for map in mem loc
    (apply assoc old-val val)
    :dissoc
    (dissoc old-val val)
    :conj
    (conj old-val val)
    :disj
    (disj old-val val)
    :update
    (apply func old-val val)))

(defn jung-update-property! [jgh v key val-action]
  (when (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key]
                                         (jung-loc-step (get-in % [v key]) val-action))))))

(defn jung-clear-property! [jgh v key]
  (dosync
   (swap! (:properties jgh) #(dissoc-in % [v key]))))

(defn jung-get-property [jgh v key]
  (get-in @(:properties jgh) [v key]))

(defn apply-event-to-graph [jgh [k a1 a2 a3]]
  (case k
    :add-vertex
    (do
      (jung-add-vertex! jgh a1)
      (when (and a2 a3)
        (jung-set-property! jgh a1 a2 a3 )))
    :remove-vertex
    (do
      (jung-remove-vertex! jgh a1))
    :add-edge
    (jung-add-edge! jgh a1 a2)
    :remove-edge
    (jung-remove-edge! jgh a1 a2)
    :set-property
    (do
      (jung-set-property! jgh a1 a2 a3))
    :clear-property
    (jung-clear-property! jgh a1 a2)
    :clear
    (doseq [v (.getVertices (:jg jgh))]
      (jung-remove-vertex! jgh a1))))
