(defproject day8/shadow-git-inject "lein-git-inject/version"
  :description "Injects shadow-cljs.edn with some execution context."
  :url         "https://github.com/day8/shadow-git-inject"
  :licence     {:name "EPL-2.0"
                :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]]

  ;; This is only for versioning of the shadow-git-inject artefact on
  ;; Clojars, it is not a dependency of shadow-git-inject.
  :plugins     [[day8/lein-git-inject "0.0.14"]]

  :middleware  [leiningen.git-inject/middleware]

  :release-tasks [["vcs" "assert-committed"]
                  ["deploy" "clojars"]]

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/CLOJARS_USERNAME
                                    :password      :env/CLOJARS_TOKEN}]])
