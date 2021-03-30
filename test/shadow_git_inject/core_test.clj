(ns shadow-git-inject.core-test
    (:require
      [clojure.test :refer [deftest is]]
      [shadow-git-inject.core :refer [hook]]))

(deftest hook-test
   (let [config   {:version     :shadow-git-inject/version
                   :builds {:app {:target           :browser
                                  :compiler-options {:closure-defines {'app/version :shadow-git-inject/version}}}}
                   :timestamp   :shadow-git-inject/build-iso-date-time
                   :username    :shadow-git-inject/username}
         config' (hook config)]
        (is (string? (:version config')))
        (is (string? (get-in config' [:builds :app :compiler-options :closure-defines 'app/version])))
        (is (string? (:timestamp config')))
        (is (string? (:username config')))))
