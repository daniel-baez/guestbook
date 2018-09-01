(ns guestbook.core
  (:require 
    [reagent.core :as reagent :refer [atom]]
    [guestbook.ws :as ws]
    [ajax.core :refer [GET]]))

(defn get-messages [messages]
  (GET "/messages"
       {:headers {"Accept" "application/transit+json"}
        :handler #(reset! messages (vec %))}))

(defn messages-list [messages]
  (let [messages (reverse @messages)]
    [:ul.content
     (for [{:keys [timestamp message name]} messages]
       ^{:key timestamp}
       [:li
        [:time (.toLocaleString timestamp)]
        [:p message]
        [:p " - " name] ])]))

(defn errors-component [errors id]
  (when-let [error (id @errors)]
    [:div.alert.alert-danger (clojure.string/join error)]))

(defn message-form [fields errors]
  [:div.content 
   [errors-component errors :server-error]
   [:div.form-group
    [errors-component errors :name]
    [:p "Name:"
     [:input.form-control 
      {:type :text
       :name :name
       :on-change #(swap! fields assoc :name (-> % .-target .-value))
       :value (:name @fields)}]]

    [errors-component errors :message]
    [:p "Message:"
     [:textarea.form-control {:rows 4 
                              :cols 50 
                              :name :message 
                              :on-change #(swap! fields assoc :message (-> % .-target .-value))
                              :value (:message @fields)}]]

    [:input.btn.btn-primary 
     {:type :submit 
      :on-click #(ws/send-message! @fields)
      :value "Comment!"}]]])

(defn response-handler [messages fields errors]
  (fn [message]
    (if-let [response-errors (:errors message)]
      (reset! errors response-errors)
      (do
        (reset! fields nil)
        (reset! errors nil)
        (swap! messages conj message)))))

(defn home []
  (let [messages (atom nil)
        fields (atom nil)
        errors (atom nil)]
    (ws/connect! (str "ws://" (.-host js/location) "/ws")
                 (response-handler messages fields errors))
    (get-messages messages)
    (fn []
      [:div
       [:div.row
        [:div.span12 [message-form fields errors]]]
       [:div.row
        [:div.span12
         [messages-list messages]]]])))

(reagent/render
  [home]
  (.getElementById js/document "content"))
