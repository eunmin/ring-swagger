(ns ring.swagger.schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [cheshire.core :as cheshire]
            [clj-time.core :as t]
            [ring.swagger.schema :refer :all]
            [ring.swagger.common :refer :all]
            ring.swagger.json)
  (:import  [java.util Date UUID]
            [java.util.regex Pattern]
            [org.joda.time DateTime LocalDate]))

(s/defschema SubType  {:alive Boolean})
(s/defschema AllTypes
  {:a Boolean
   :b Double
   :c Long
   :d String
   :e {:f [s/Keyword]
       :g #{String}
       :h #{(s/enum :kikka :kakka :kukka)}
       :i Date
       :j DateTime
       :k LocalDate
       :l (s/maybe String)
       :m (s/both Long (s/pred odd? 'odd?))
       :n SubType
       :o [{:p #{{:q String}}}]
       :u UUID
       :v Pattern
       :w #"a[6-9]"}})

(def model {:a true
            :b 2.2
            :c 16
            :d "kikka"
            :e {:f [:kikka :kikka :kukka]
                :g #{"kikka" "kakka"}
                :h #{:kikka}
                :i (Date.)
                :j (t/now)
                :k (t/today)
                :l nil
                :m 1
                :n {:alive true}
                :o [{:p #{{:q "abba"}}}]
                :u (UUID/fromString "77e70512-1337-dead-beef-0123456789ab")
                :v "a[6-9]"
                :w "a9"}})

;; since every java.util.regex.Pattern is not equal
(defn- pattern-to-str
  [model]
  (update-in model [:e :v] str))

(fact "All types can be read from json"
  (let [json   (cheshire/generate-string model)
        jmodel (cheshire/parse-string json true)]

    (fact "json can be parsed"
      json => truthy
      jmodel => truthy)

    (fact "by default, models don't match"
      jmodel =not=> model)

    (fact "coerce! makes models match"
      (pattern-to-str (coerce! AllTypes jmodel)) => model)))

(fact "date-time coercion"
  (fact "with millis"
    (coerce! {:d Date} {:d "2014-02-24T21:37:40.477Z"}) =not=> (throws Exception))
  (fact "without millis"
    (coerce! {:d Date} {:d "2014-02-24T21:37:40Z"}) =not=> (throws Exception)))

(fact "named-schema"

  (fact "schema is named"
    (s/defschema AbbaSchema {:s String})
    AbbaSchema => named-schema?)

  (fact "def is not named"
    (def AbbaDef {:s String})
    AbbaDef =not=> named-schema?))

(fact "coercion"
  (let [valid {:a "kikka"}
        invalid {}]

    (s/defschema MapModel {:a String})

    (fact "coerce works for both models and schemas"
      (coerce MapModel valid) => valid
      (coerce {:a String} valid) => valid

      (coerce MapModel invalid) => error?
      (coerce {:a String} invalid) => error?)

    (fact "coerce! works for both models and schemas"
      (coerce! MapModel valid) => valid
      (coerce! {:a String} valid) => valid

      (coerce! MapModel invalid) => (throws clojure.lang.ExceptionInfo)
      (coerce! {:a String} invalid) => (throws clojure.lang.ExceptionInfo))

    (fact "both runs all predicates"
      (let [OddModel (s/both Long (s/pred odd? 'odd?))]
        (coerce OddModel 1) => 1
        (coerce OddModel 2) => error?))))

(facts "schema coercion"
  (let [Schema {:a Long :b Double :c Boolean :d s/Keyword :u UUID}
        value  {:a "1"  :b "2.2"  :c "true"  :d "kikka" :u "77e70512-1337-dead-beef-0123456789ab"}
        target {:a 1    :b 2.2    :c true    :d :kikka  :u (UUID/fromString "77e70512-1337-dead-beef-0123456789ab")}]

    (fact ":query coercion can convert string to Longs, Doubles, Booleans and UUIDs"
      (coerce! Schema value :query) => target)

    (fact ":json coercion cant convert string to Longs,Doubles, Booleans and UUIDs"
      (coerce! Schema value :json) => (throws Exception))

    (fact "custom coersion"
      (coerce! {:a String :b String}
               {:a "kikka" :b "kukka"}
               (fn [_]
                 (fn [x]
                   (if (string? x) (.toUpperCase x) x))))

      => {:a "KIKKA" :b "KUKKA"})))


