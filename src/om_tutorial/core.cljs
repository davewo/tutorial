(ns om-tutorial.core
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.next.impl.parser :as p]
            [om.dom :as dom]))

(enable-console-print!)

(def app-state
  (atom {:config  {:window/size [1920 1200]}
         :people  {1 {:db/id 1 :name "John" :likes 0}
                   4 {:db/id 4 :name "Mary" :likes 0}
                   5 {:db/id 5 :name "Bob" :likes 0}
                   2 {:db/id 2 :name "Gwen" :likes 0}
                   3 {:db/id 3 :name "Jeff" :likes 0}}
         :friends #{4}
         :family  #{2 1 3 5}
         })
  )

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(declare Person reconciler)

(defmethod mutate 'make-friend
  [{:keys [state]} key {:keys [id]}]
  {:value  [:db/id :name :is-friend]
   :action (fn []
             (swap! state update :friends conj id)
             )})

(defmethod read :default [e k p]
  (println "DEFAULT READ " k p)
  nil)

(defn read-people [state key]
  (let [ids (get state key)
        friend-ids (get state :friends)
        get-person (fn [id] (assoc (get-in state [:people id]) :is-friend (friend-ids id)))]
    {:value (into [] (reverse (sort-by :name (map get-person ids))))}
    ))

(defmethod read :family [{:keys [state selector]} key params]
  (println selector key params)
  (read-people @state key))
(defmethod read :friends [{:keys [state]} key _] (read-people @state key))
(defmethod read :friend-ids [{:keys [state]} key _] (get @state :friends))

(defui Person
       static om/Ident
       (ident [this {:keys [db/id]}] [:db/id id])
       static om/IQuery
       (query [this] [:db/id :name :is-friend])
       Object
       (render [this]
               (let [{:keys [db/id name is-friend]} (-> this om/props)]
                 (dom/li nil
                         name
                         " "
                         (if is-friend
                           (dom/a #js {:href "#" :onClick #(om/transact! this `[(~'un-friend {:id ~id})])} "Un-Friend!")
                           (dom/a #js {:href "#" :onClick #(om/transact! this `[(~'make-friend {:id ~id})])} "Make Friend!")
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
       (query [this] (om/get-query Widget))
       Object
       (render [this]
               (dom/div nil
                        (dom/h1 nil "My App")
                        (widget (om/props this))
                        )))

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
