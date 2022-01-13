(ns hooks.slash
  (:require [clj-kondo.hooks-api :as api]))

(defn strip-pattern [pattern]
  (api/vector-node (vec (remove api/string-node? (:children pattern)))))

(defn transform-handler-contents [[pattern interact-sym opt-sym & body]]
  (api/list-node
   (list*
    (api/token-node 'let)
    (api/vector-node
     [(strip-pattern pattern) (api/token-node nil)
      interact-sym (api/token-node nil)
      opt-sym (api/token-node nil)])
    body)))

(defn handler [{:keys [node]}]
  {:node (transform-handler-contents (rest (:children node)))})

(defn defhandler [{:keys [node]}]
  (let [[name & handler-contents] (rest (:children node))]
    {:node (api/list-node (list (api/token-node 'def) name (transform-handler-contents handler-contents)))}))

(defn group [{:keys [node]}]
  (let [[pattern & handlers] (rest (:children node))]
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [(strip-pattern pattern) (api/token-node nil)])
             handlers))}))

(defn defpaths [{:keys [node]}]
  (let [[name & handlers] (rest (:children node))]
    {:node (api/list-node
            (list
             (api/token-node 'def)
             name
             (api/vector-node (vec handlers))))}))
