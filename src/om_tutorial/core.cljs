(ns om-tutorial.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.next.impl.parser :as p]
            [om.dom :as dom]))

(enable-console-print!)

;; My app state is intentionally denormalized in a particular way that is a bit different from tutorials (in
;; that my people refs are stored in a set of pure IDs instead of [:people 1] refs), but since read fronts
;; it all, it should not matter.
(def app-state
  (atom {:people  {1 {:db/id 1 :name "John" :likes 0}
                   4 {:db/id 4 :name "Mary" :likes 0}
                   5 {:db/id 5 :name "Bob" :likes 0}
                   2 {:db/id 2 :name "Gwen" :likes 0}
                   3 {:db/id 3 :name "Jeff" :likes 0}}
         :friends #{4 1}
         :family  #{2 1 3 5}
         })
  )

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(declare Widget Person reconciler)

(defmethod mutate 'make-friend
  [{:keys [state]} key {:keys [id]}]
  {;:value  ??? ;; Not sure what this should be yet. ignore
   :action (fn [] (swap! state update :friends conj id) )})

(defmethod mutate 'un-friend
  [{:keys [state]} key {:keys [id]}]
  {;:value  ???
   :action (fn [] (swap! state update :friends disj id) )})

(defmethod mutate 'like-it
  [{:keys [state]} key {:keys [id]}]
  {:value  [:likes]
   :action (fn [] (swap! state update-in [:people id :likes] inc))})

(defn read-people
  "Read a group of people. Key is something like :friends or :family. Selector is a vector of keys you care about.
   Returns parse result of people sorted by name."
  [state key selector]
  (let [ids (get state key)
        friend-ids (get state :friends)
        get-person (fn [id]
                     (-> state (get-in [:people id]) (assoc :is-friend (boolean (friend-ids id))) (select-keys selector)))]
    {:value (into [] (sort-by :name (map get-person ids)))}
    ))

(defn dbg [k v] (println "READ " k v) v)
(defmethod read :default [e k p] (println "ERROR: UNEXPECTED READ. " k p) nil)
(defmethod read :root [{:keys [state parse selector] :as env} key params] (dbg key {:value (parse env selector)}))
(defmethod read :family [{:keys [state selector]} key params] (dbg key (read-people @state key selector)))
(defmethod read :friends [{:keys [selector state]} key _] (dbg key (read-people @state key selector)))

(defui Person
       static om/Ident
       (ident [this {:keys [db/id]}] [:db/id id])
       static om/IQuery
       (query [this] [:db/id :likes :name :is-friend])
       Object
       (render [this]
               (let [{:keys [db/id name likes is-friend]} (-> this om/props)]
                 (dom/li nil
                         (str name " has liked " likes " things.")
                         ;; Transact on likes is fine...only Person will need to re-render
                         (dom/a #js {:href "#" :onClick #(om/transact! this `[(~'like-it {:id ~id})])} "Like something")
                         " "
                         ; NOTE: is-friend is completely derived by the parser (is this person in the :friends set)
                         ;; QUESTION: I could do this with callback from widget, but it adds a lot of extra code.
                         ;; Also, from a pure reasoning standpoint, is-friend is part of Person's UI state
                         ;; so making un-friend and make-friend callable from here seems desirable.
                         ;; I can argue that something above me "Owns it"...or at least that it affects the
                         ;; rendering of the friends list (which is a sibling)...but why should I have to care that people are even
                         ;; rendered elsewhere on the screen?
                         (if is-friend
                           (dom/a #js {:href "#" :onClick #(om/transact! reconciler `[(~'un-friend {:id ~id})])} "Un-Friend!")
                           (dom/a #js {:href "#" :onClick #(om/transact! reconciler `[(~'make-friend {:id ~id})])} "Make Friend!")
                           )))))

(def person (om/factory Person {:keyfn :db/id}))

(defui PersonList
       Object
       (render [this]
               (dom/ul nil (mapv person (om/props this)))))

(def person-list (om/factory PersonList))

(defui Widget
       static om/IQuery
       (query [this] (let [pquery (om/get-query Person)]
                       [{:friends pquery} {:family pquery}]))
       Object
       (render [this]
               (let [{:keys [friends family]} (-> this om/props)]
                 (dom/div nil
                          (dom/div nil "Friends:")
                          (person-list friends)
                          (dom/div nil "Family:")
                          (person-list family)
                          ))
               )
       )

(def widget (om/factory Widget))

(defui Root
       static om/IQuery
       (query [this] [{:root (om/get-query Widget)}])
       Object
       (render [this]
               (let [{:keys [root]} (-> this om/props)]
                 (dom/div nil
                          (dom/h1 nil "My App")
                          (widget root)
                          ))))

(def parser (om/parser {:read read :mutate mutate}))

(def reconciler
  (om/reconciler
    {:state  app-state
     :parser parser}))

(om/add-root! reconciler Root (gdom/getElement "app"))

(comment
  (in-ns 'om-tutorial.core)
  (require '[cljs.pprint :as pp])

  (om/get-query Root)
  (parser {:state app-state} (om/get-query Root))

  (def norm-data (om/normalize Root @app-state true))
  (pp/pprint norm-data)

  (println @app-state)

  (om/from-history reconciler #uuid "c0785384-69d9-454b-a438-826346560c85")

  )
