(ns tools.project.add-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [tools.project.add :as add]))

(deftest parse-spec
  (is (= {:lib 'org.clojure/data.json :version nil}
         (add/parse-spec "org.clojure/data.json")))
  (is (= {:lib 'org.clojure/data.json :version "2.5.0"}
         (add/parse-spec "org.clojure/data.json@2.5.0")))
  (is (= {:lib 'hiccup/hiccup :version nil} (add/parse-spec "hiccup")))
  (is (thrown? clojure.lang.ExceptionInfo (add/parse-spec "a/b@")))
  (is (thrown? clojure.lang.ExceptionInfo (add/parse-spec "a/b@1@2")))
  (is (thrown? clojure.lang.ExceptionInfo (add/parse-spec "a/b/c")))
  (is (thrown? clojure.lang.ExceptionInfo (add/parse-spec "{:evil true}"))))

(deftest pick-latest
  (testing "skips pre-releases"
    (is (= {:mvn/version "1.12.0"}
           (add/pick-latest [{:mvn/version "1.11.0"} {:mvn/version "1.12.0"}
                             {:mvn/version "1.13.0-alpha1"} {:mvn/version "1.13.0-RC2"}]))))
  (testing "falls back to a pre-release when that is all there is"
    (is (= {:mvn/version "0.1.0-beta2"}
           (add/pick-latest [{:mvn/version "0.1.0-beta1"} {:mvn/version "0.1.0-beta2"}]))))
  (testing "git coords"
    (is (= {:git/tag "v2" :git/sha "bbb"}
           (add/pick-latest [{:git/tag "v1" :git/sha "aaa"} {:git/tag "v2" :git/sha "bbb"}]))))
  (is (nil? (add/pick-latest []))))

(def ^:private v {:mvn/version "2.5.2"})

(deftest add-to-multiline-deps-keeps-formatting
  (let [s "{:paths [\"src\"]

 :deps
 {org.clojure/clojure {:mvn/version \"1.12.0\"}
  ;; keep me
  a/b {:mvn/version \"1\"}}
 :aliases {}}
"]
    (is (= {:content "{:paths [\"src\"]

 :deps
 {org.clojure/clojure {:mvn/version \"1.12.0\"}
  ;; keep me
  a/b {:mvn/version \"1\"}
  org.clojure/data.json {:mvn/version \"2.5.2\"}}
 :aliases {}}
"}
           (add/add-dep-string s 'org.clojure/data.json v)))))

(deftest add-to-single-line-and-empty-deps
  (is (= {:content "{:deps {a/b {:mvn/version \"1\"} x/y {:mvn/version \"2.5.2\"}}}"}
         (add/add-dep-string "{:deps {a/b {:mvn/version \"1\"}}}" 'x/y v)))
  (is (= {:content "{:deps {x/y {:mvn/version \"2.5.2\"}}}"}
         (add/add-dep-string "{:deps {}}" 'x/y v))))

(deftest add-without-deps-key
  (is (= {:content "{:paths [\"src\"] :deps {x/y {:mvn/version \"2.5.2\"}}}"}
         (add/add-dep-string "{:paths [\"src\"]}" 'x/y v)))
  (is (= {:content "{:paths [\"src\"]\n :aliases {}\n :deps {x/y {:mvn/version \"2.5.2\"}}}"}
         (add/add-dep-string "{:paths [\"src\"]\n :aliases {}}" 'x/y v))))

(deftest update-existing-dep
  (is (= {:content "{:deps {x/y {:mvn/version \"2.5.2\"} ;; note\n }}"
          :previous {:mvn/version "1"}}
         (add/add-dep-string "{:deps {x/y {:mvn/version \"1\"} ;; note\n }}" 'x/y v))))

(deftest add-dep-edits-file
  (let [f (io/file (System/getProperty "java.io.tmpdir") (str "cljp-add-" (random-uuid) ".edn"))]
    (try
      (spit f "{:deps {a/b {:mvn/version \"1\"}}}")
      (is (= {:lib 'x/y :coord v :previous nil}
             (add/add-dep {:lib 'x/y :version "2.5.2" :file (str f)})))
      (is (= "{:deps {a/b {:mvn/version \"1\"} x/y {:mvn/version \"2.5.2\"}}}" (slurp f)))
      (is (= {:lib 'x/y :coord v :previous v}
             (add/add-dep {:lib 'x/y :version "2.5.2" :file (str f)}))
          "re-adding the same version is a no-op")
      (finally (io/delete-file f true))))
  (is (thrown? clojure.lang.ExceptionInfo
               (add/add-dep {:lib 'x/y :version "1" :file "/nonexistent/deps.edn"}))))
