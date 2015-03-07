(ns lambdacd.internal.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.pipeline-state-persistence :as persistence]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{ home-dir :home-dir }]
  (persistence/read-build-history-from home-dir))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        new-step-result (merge current-step-result step-result)]
    (assoc current-state step-id new-step-result)))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))

(defn- current-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defn current-build-number [{pipeline-state :_pipeline-state }]
  (current-build-number-in-state @pipeline-state))

(defn finished-step? [step-result]
  (let [status (:status step-result)
        is-waiting (= :waiting status)
        is-running (= :running status)]
  (not (or is-waiting is-running))))

(defn- finished-step-count-in [build]
  (let [results (vals build)
        finished-steps (filter finished-step? results)
        finished-step-count (count finished-steps)]
    finished-step-count))

(defn- call-callback-when-most-recent-build-running [callback key reference old new]
  (let [cur-build-number (current-build-number-in-state new)
        cur-build (get new cur-build-number)
        old-cur-build (get old cur-build-number)
        finished-step-count-new (finished-step-count-in cur-build)
        finished-step-count-old (finished-step-count-in old-cur-build)]
    (if (and (= 1 finished-step-count-new) (not= 1 finished-step-count-old))
      (callback))))

(defn notify-when-most-recent-build-running [{pipeline-state :_pipeline-state} callback]
  (add-watch pipeline-state :notify-most-recent-build-running (partial call-callback-when-most-recent-build-running callback)))


(defn update [{step-id :step-id state :_pipeline-state build-number :build-number { home-dir :home-dir } :config } step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build-number step-id step-result))]
      (persistence/write-build-history home-dir build-number new-state))))

(defn running [ctx]
  (update ctx {:status :running}))

