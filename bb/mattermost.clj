(ns mattermost
  (:require [babashka.curl :as curl]
            [clojure.edn   :as edn]
            [cheshire.core :as json]))

(defonce ^:private config (atom {}))
(defonce ^:private last-updated (atom nil))

(defn- update-config [config]
  (spit ".config.edn" (pr-str (merge (edn/read-string (slurp ".config.edn")) config))))

(defn- get-config [& keys]
  (get-in (edn/read-string (slurp ".config.edn")) keys))

(defn- simple-get [url options]
  (curl/get (str (get-config :mm-url) url) options))

(defn- auth-get [url]
  (-> (simple-get url {:headers {"Accept"        "application/json"
                                 "Content-Type"  "application/json"
                                 "Authorization" (str "Bearer " (get-config :token))}})
      (:body)
      (json/parse-string true)))

;;; Authentication
(defn authenticate []
  (when (nil? (get-config :token))
    (let [res   (simple-get "/api/v4/users/login"
                            {:headers {"Accept" "application/json" "Content-Type" "application/json"}
                             :body (json/generate-string {:login_id (get-config :username)
                                                          :password (get-config :password)})})
          user  (-> res (:body) (json/parse-string true))
          token (get-in res [:headers "token"])]
      (update-config {:token token :user-id (:id user)}))))

;;; Me
(defn me []
  (auth-get "/api/v4/users/me"))

;;; Channels
(defn channels []
  (auth-get (format "/api/v4/users/%s/channels" (get-config :user-id))))

(defn team-channels [team-id]
  (auth-get (format "/api/v4/users/%s/teams/%s/channels" (get-config :user-id) team-id)))

;;; Teams
(defn teams []
  (if-let [teams (get-config :teams)]
    teams
    (let [res (auth-get (format "/api/v4/users/%s/teams" (get-config :user-id)))]
      (update-config {:teams (mapv #(select-keys % [:id :name :display_name]) res)})
      res)))

;;; Team Members
(defn team-members [team-id]
  (auth-get (format "/api/v4/users?in_team=%s&per_page=200" team-id)))

;;; Unread Messages
(defn unread []
  (auth-get (format "/api/v4/users/%s/teams/unread" (get-config :user-id))))

(defn channel-unread [channel-id]
  (auth-get (format "/api/v4/users/%s/channels/%s/unread" (get-config :user-id) channel-id)))
