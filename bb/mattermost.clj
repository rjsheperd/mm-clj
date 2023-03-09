(ns mattermost
  (:require [babashka.curl   :as curl]
            [cheshire.core   :as json]
            [clojure.edn     :as edn]
            [clojure.java.io :as io]
            [clojure.string  :as str]))

(defonce ^:private config (atom {}))
(defonce ^:private last-updated (atom nil))

(defn- update-data [data]
  (if (.exists (io/file ".data.edn"))
    (spit ".data.edn" (prn-str (merge (edn/read-string (slurp ".data.edn")) data)))
    (spit ".data.edn" (prn-str data))))

(defn- get-data [& keys]
  (when (.exists (io/file ".data.edn"))
    (get-in (edn/read-string (slurp ".data.edn")) keys)))

(defn- get-auth [& keys]
  (when (.exists (io/file ".auth.edn"))
    (get-in (edn/read-string (slurp ".auth.edn")) keys)))

(defn- simple-get [url options]
  (curl/get (str (get-auth :mm-url) url) options))

(defn- simple-post [url options]
  (curl/post (str (get-auth :mm-url) url) options))

(defn- auth-get [url]
  (-> (simple-get url {:headers {"Accept"        "application/json"
                                 "Content-Type"  "application/json"
                                 "Authorization" (str "Bearer " (get-data :token))}})
      (:body)
      (json/parse-string true)))

(defn- auth-post [url body]
  (-> (simple-post url {:headers {"Accept"        "application/json"
                                  "Content-Type"  "application/json"
                                  "Authorization" (str "Bearer " (get-data :token))}
                        :body    (json/generate-string body)})
      (:body)
      (json/parse-string true)))

;;; Authentication
(defn authenticate []
  (when (nil? (get-data :token))
    (let [res   (simple-get "/api/v4/users/login"
                            {:headers {"Accept" "application/json" "Content-Type" "application/json"}
                             :body    (json/generate-string {:login_id (get-auth :username)
                                                             :password (get-auth :password)})})
          user  (-> res (:body) (json/parse-string true))
          token (get-in res [:headers "token"])]
      (update-data {:token token :user-id (:id user)}))))

;;; Me
(defn me []
  (auth-get "/api/v4/users/me"))

;;; Channels
(defn channels []
  (auth-get (format "/api/v4/users/%s/channels" (get-data :user-id))))

(defn team-channels [team-id]
  (auth-get (format "/api/v4/users/%s/teams/%s/channels" (get-data :user-id) team-id)))

;;; Teams
(defn teams []
  (if-let [teams (get-data :teams)]
    teams
    (let [res (auth-get (format "/api/v4/users/%s/teams" (get-data :user-id)))]
      (update-data {:teams (mapv #(select-keys % [:id :name :display_name]) res)})
      res)))

;;; Team Members
(defn team-members [team-id]
  (auth-get (format "/api/v4/users?in_team=%s&per_page=200" team-id)))

;;; Unread Messages
(defn unread []
  (auth-get (format "/api/v4/users/%s/teams/unread" (get-data :user-id))))

(defn channels []
  (auth-get (format "/api/v4/users/%s/channels" (get-data :user-id))))

(defn channel-unread [channel-id]
  (auth-get (format "/api/v4/users/%s/channels/%s/unread" (get-data :user-id) channel-id)))

(defn channel-posts [channel-id]
  (auth-get (format "/api/v4/channels/%s/posts" channel-id)))

;;; Post
(defn post [channel-id message]
  (auth-post (format "/api/v4/posts") {:channel_id channel-id :message message}))

(comment
  (authenticate)
  (unread)

  (def my-channels (channels))
  (keys (first my-channels))

  (def alerts-id (:id (first (filter #(str/includes? (:display_name %) "alerts") my-channels))))
  (post alerts-id "Hello")

  )
