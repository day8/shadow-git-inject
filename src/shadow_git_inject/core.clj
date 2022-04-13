(ns shadow-git-inject.core
  (:require
    [clojure.walk :as walk]
    [clojure.string :as string]
    [clojure.java.shell :refer [sh]]
    [clojure.java.io :as io])
  (:import
    (java.io BufferedReader StringReader IOException)
    (java.time LocalDateTime)
    (java.time.format DateTimeFormatter))
  (:refer-clojure :exclude [boolean?]))

(def default-config
  "The default configuration values."
  {:git               "git"
   :describe-pattern  "(?<tag>.*)-(?<ahead>\\d+)-g(?<ref>[0-9a-f]*)(?<dirty>(-dirty)?)"
   :version-pattern   "^v(\\d+\\.\\d+\\.\\d+)$"})

(defmacro let-groups
  "Let for binding groups out of a j.u.r.Pattern j.u.r.Matcher."
  {:style/indent [1]}
  [[bindings m] & body]
  (let [s (with-meta (gensym "matcher") {:tag java.util.regex.Matcher})]
    `(let [~s ~m
           ~@(mapcat identity
                     (for [b bindings]
                       `[~b (.group ~s ~(name b))]))]
       ~@body)))

;; Duplicate of clojure.core/boolean? as it is only available in Clojure 1.9+
(defn boolean?
  "Return true if x is a Boolean"
  [x] (instance? Boolean x))

(defn ensure-pattern
  "Given a string, compiles it to a java.util.regex.Pattern."
  [x label]
  (cond (string? x)
        (re-pattern x)

        (instance? java.util.regex.Pattern x)
        x

        :else
        (throw (IllegalArgumentException. (str "shadow-git-inject " label " requires a string or a java.util.regex.Pattern!")))))

(defn initial-commit
  [{:keys [git] :as config}]
  (let [{:keys [exit out] :as child} (apply sh [git "rev-list" "--max-parents=0" "HEAD"])]
    (if-not (= exit 0)
      (binding [*out* *err*]
        (printf "Warning: shadow-git-inject git exited %d\n%s\n\n"
                exit child)
        (.flush *out*)
        nil)
      (first (string/split-lines (string/trim out))))))

(defn parse-tags
  [config out]
  (reduce
    (fn [ret line]
      (if-let [[_ decorations] (re-find #"[0-9a-fA-F]{7} \(([^)]*)\) .*" line)]
        (reduce
          (fn [ret decoration]
            (if-let [[_ tag] (re-find #"tag: ([0-9a-zA-Z`!@#$%&()-_+={}|;'<>,./]+)" decoration)]
              (conj ret tag)
              ret))
          ret
          (string/split decorations #","))
        ret))
    []
    (string/split-lines (string/trim out))))

(defn tags
  [{:keys [git] :as config}]
  (let [{:keys [exit out] :as child} (apply sh [git "log" "--oneline" "--decorate" "--simplify-by-decoration" "--ancestry-path" (str (initial-commit config) "..HEAD")])]
    (if-not (= exit 0)
      (binding [*out* *err*]
        (printf "Warning: shadow-git-inject git exited %d\n%s\n\n"
                exit child)
        (.flush *out*)
        nil)
      (parse-tags config out))))

(defn latest-version-tag
  [{:keys [version-pattern] :as config}]
  (let [pattern (ensure-pattern version-pattern ":version-pattern")]
    (first (filter #(re-matches pattern %) (tags config)))))

(defn resolve-ref
  "Fetches the git ref of ref, being a tag or ref name."
  [{:keys [git] :as config} ref]
  (let [{:keys [exit out] :as child} (apply sh [git "rev-parse" "--verify" ref])]
    (if-not (= exit 0)
      (binding [*out* *err*]
        (printf "Warning: shadow-git-inject git exited %d\n%s\n\n"
                exit child)
        (.flush *out*)
        nil)
      (string/trim out))))

(defn parse-describe
  "Used to parse the output of git-describe, using the configured `describe-pattern`.
  Returns a map `{:tag, :ahead, :ahead?, :ref, :ref-short, :dirty?}`
  if the pattern matches, otherwise returns the empty map."
  [{:keys [describe-pattern] :as config} out]
  (let [pattern (ensure-pattern describe-pattern ":describe-pattern")
        matcher (re-matcher pattern out)]
    (if-not (.matches matcher)
      (do (binding [*out* *err*]
            (printf (str "Warning: shadow-git-inject couldn't match the current repo status:\n%s\n\n"
                         "Against pattern:\n%s\n\n")
                    (pr-str out) pattern)
            (.flush *out*))
          {})
      (let-groups [[tag ahead ref dirty] matcher]
                  {:tag       tag
                   :ahead     (Integer/parseInt ahead)
                   :ahead?    (not= ahead "0")
                   :ref       (resolve-ref config "HEAD")
                   :ref-short ref
                   :dirty?    (not= "" dirty)}))))

(defn describe
  "Uses git-describe to parse the status of the repository.
  Using the configured `git` and `describe-pattern` to parse the output.
  Returns a map `{:tag, :ahead, :ahead?, :ref, :ref-short, :dirty?}`
  if the pattern matches, otherwise returns the empty map."
  [{:keys [git] :as config}]
  (let [version-tag (latest-version-tag config)]
    (if-not version-tag
      {}
      (let [{:keys [exit out] :as child} (apply sh [git "describe" "--tags" "--dirty" "--long" "--match" version-tag])]
        (if-not (= exit 0)
          (binding [*out* *err*]
            (printf "Warning: shadow-git-inject git exited %d\n%s\n\n"
                    exit child)
            (.flush *out*)
            {})
          (parse-describe config (string/trim out)))))))

(defn git-status-to-version
  [{:keys [version-pattern ignore-dirty?] :as config}]
  (try
    (let [{:keys [tag ahead ahead? dirty? ref-short]} (describe config)
          ignore-dirty?' (cond
                           (and (keyword? ignore-dirty?)
                                (= (namespace ignore-dirty?) "env"))
                           (= "true" (System/getenv (string/upper-case (name ignore-dirty?))))

                           (boolean? ignore-dirty?)
                           ignore-dirty?

                           :default
                           false)]
      (if-not (string? tag)
        ;; If git status is nil (e.g. IntelliJ evaluating project.clj):
        "git-version-tag-not-found"
        (let [[_ version] (re-find (re-pattern version-pattern) tag)]
          (if (and (not ahead?)
                   (or ignore-dirty?' (not dirty?)))
            ;; If this is a release version:
            version
            (str version "-" ahead "-" ref-short "-SNAPSHOT")))))
    (catch IOException _
      ;; If git binary is not available (e.g. not in path):
      "git-command-not-found")))

(def x->f
  {:shadow-git-inject/build-iso-date-time (fn [_] (.format (LocalDateTime/now) DateTimeFormatter/ISO_DATE_TIME))
   :shadow-git-inject/build-iso-week-date (fn [_] (.format (LocalDateTime/now) DateTimeFormatter/ISO_WEEK_DATE))
   :shadow-git-inject/version             git-status-to-version
   :shadow-git-inject/username            (fn [_] (System/getProperty "user.name"))})

(defn inject
  [state config]
  (walk/prewalk
     (fn [x]
       (reduce-kv
         (fn [ret k f]
           (cond
             (keyword? x)
             (if (= x k)
               (f config)
               ret)

             (string? x)
             (let [s (str (namespace k) "/" (name k))]
               (if (string/includes? x s)
                 (string/replace x (re-pattern s) (f config))
                 ret))

             :default
             ret))
         x
         x->f))
     state))

(defn hook
  {:shadow.build/stage :configure}
  [build-state & args]
  (let [git-inject (get-in build-state [:shadow.build/config :git-inject])
        config (merge default-config git-inject)]
    (update-in build-state [:compiler-options :closure-defines] inject config)))
