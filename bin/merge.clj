#!/usr/bin/env bb

;; Merge reflection entries from a native-image agent run into the committed
;; reachability metadata, filtered by library package.
;;
;; Usage:
;;   bin/merge.clj <match> [<match> ...]
;;   bin/merge.clj --src PATH --dest PATH <match> [<match> ...]
;;
;; <match> is a case-insensitive substring tested against each entry's "type"
;; (e.g. "mchange", "org.sqlite", "batik"). An entry is merged only if its type
;; matches at least one <match> AND is not a Clojure-generated class. Clojure
;; namespace/fn classes are skipped on purpose: the native build initializes
;; everything reachable at build time, and those classes run load-time side
;; effects that break in that context (the whole reason we don't just copy the
;; agent's full output). Only inert Java library classes belong here.
;;
;; Existing entries are preserved; duplicates are not re-added.

(require '[cheshire.core :as json]
         '[clojure.string :as str]
         '[babashka.fs :as fs])

(def default-src
  "scratchpad/reachability-metadata.json")

(def default-dest
  "cms/core/resources/META-INF/native-image/systems.bread/cms/reachability-metadata.json")

(defn parse-args [args]
  (loop [args args, opts {:src default-src :dest default-dest :matches []}]
    (if-let [a (first args)]
      (case a
        "--src"  (recur (drop 2 args) (assoc opts :src (second args)))
        "--dest" (recur (drop 2 args) (assoc opts :dest (second args)))
        ("-h" "--help") (assoc opts :help true)
        (recur (rest args) (update opts :matches conj a)))
      opts)))

(defn clojure-class?
  "True for JVM classes emitted by Clojure's AOT compiler (namespaces, fns,
  reify/proxy/eval forms). These must NOT be build-time initialized."
  [^String t]
  (or (str/ends-with? t "__init")
      (str/includes? t "$fn__")
      (str/includes? t "$reify__")
      (str/includes? t "$eval")
      (re-find #"\$[a-z][a-z0-9_]*__\d+" t))) ; $some_fn__123

(defn matches? [matches ^String t]
  (let [lc (str/lower-case t)]
    (some #(str/includes? lc (str/lower-case %)) matches)))

(defn -main [& argv]
  (let [{:keys [src dest matches help]} (parse-args argv)]
    (when (or help (empty? matches))
      (println "usage: bin/merge.clj [--src PATH] [--dest PATH] <match> [<match> ...]")
      (println "  <match>  case-insensitive substring matched against each entry's \"type\"")
      (System/exit (if help 0 1)))

    (when-not (fs/exists? src)
      (binding [*out* *err*]
        (println (str "source metadata not found: " src
                      "\n(run bin/graal-debug.sh first to capture an agent trace)")))
      (System/exit 1))

    (let [src-refl  (get (json/parse-string (slurp src)) "reflection" [])
          dest-data (if (fs/exists? dest)
                      (json/parse-string (slurp dest))
                      {"reflection" []})
          existing  (get dest-data "reflection" [])
          have      (set existing)
          str-type? #(string? (get % "type"))

          matched   (filter #(and (str-type? %) (matches? matches (get % "type"))) src-refl)
          {clj true, java false} (group-by #(boolean (clojure-class? (get % "type"))) matched)
          fresh     (->> java (remove have) distinct vec)
          updated   (assoc dest-data "reflection" (into existing fresh))]

      (fs/create-dirs (fs/parent dest))
      (spit dest (json/generate-string updated {:pretty true}))

      (println (format "matched %d entries for %s" (count matched) (pr-str matches)))
      (when (seq clj)
        (println (format "  skipped %d Clojure-generated class(es):" (count clj)))
        (doseq [e clj] (println "    -" (get e "type"))))
      (println (format "  added %d new (skipped %d already present)"
                       (count fresh) (- (count java) (count fresh))))
      (println (format "wrote %d total reflection entries to %s"
                       (count (get updated "reflection")) dest)))))

(apply -main *command-line-args*)
