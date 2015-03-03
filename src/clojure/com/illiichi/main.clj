(ns com.illiichi.main
  (:use [neko.activity :only [defactivity set-content-view! *a]]
        [neko.find-view :only [find-view find-views]]
        [neko.threading :only [on-ui]]
        [overtone.at-at :only [every mk-pool stop-and-reset-pool!]]
        [overtone.osc])
    (:import (android.content Context)
             (android.hardware SensorManager SensorEventListener Sensor SensorEvent)))

(def state (atom {:start? false}))

(def client (atom nil))
(def listeners (atom nil))
 
(def my-pool (mk-pool))
(def acc-stack (agent []))
(def rotate-stack (agent []))

(defn send-every [id stack interval]
  (let [mean #(/ (reduce + %) (count %))
        task (fn [coll]
               (when (not (empty? coll))
                 (apply osc-send @client (str "/" id) (map mean coll)))
               [])]
    (every interval #(send-off stack task) my-pool)))

(defn push-vector [stack vals]
  (let [push (fn [xs xss]
               (prn (first (seq xs)))
               (if (empty? xss)
                 (map vector xs)
                 (map conj xss xs)))]
    (send-off stack #(push vals %))))

(defn err-handler-fn [ag ex]
  (println "evil error occured: " ex " and we still have value " @ag))
(set-error-handler! acc-stack err-handler-fn)
(set-error-handler! rotate-stack err-handler-fn)

(defn create-listeners [interval sensorManager]
  (let [create (fn [id sensor-type stack]
                 (let [listener (reify SensorEventListener
                                  (onAccuracyChanged [this sensor value] ())
                                  (onSensorChanged [this event]
                                    (apply #'push-vector stack (seq (list (. event values))))))]
                   (send-every id stack interval)
                   (.. sensorManager
                       (registerListener listener
                                         (.getDefaultSensor sensorManager sensor-type)
                                         ;; SensorManager/SENSOR_DELAY_FASTEST
                                         ;; SensorManager/SENSOR_DELAY_UI
                                         SensorManager/SENSOR_DELAY_GAME))
                   listener))]
    [(create "rotate" Sensor/TYPE_ROTATION_VECTOR rotate-stack)
     (create "acc" Sensor/TYPE_LINEAR_ACCELERATION acc-stack)]))

(def sensorManager (atom nil))

(defn start-send [address port interval]
  (for [stack [acc-stack rotate-stack]]
    (send stack (fn [_] [])))
  (reset! client (osc-client address port))
  (reset! listeners (create-listeners interval @sensorManager))
  (prn "start"))

(defn stop-send []
  (let [xs @listeners
        manager @sensorManager]
    ;; (map (fn [x] (prn "- ho -" x)
    ;;        (.unregisterListener manager x)) xs)
    (.unregisterListener manager (first xs))
    (.unregisterListener manager (second xs))
   (stop-and-reset-pool! my-pool)
   (reset! client nil)
   (reset! listeners nil)
   (prn "stop")))

(defn toggle-state [activity]
  (let [state (swap! state #(update-in % [:start?] not))]
    (-> (find-views activity "button")
        (first)
        (.setText (if (:start? state)
                    "停止する" "再開")))
    (if (:start? state)
      (let [address  (str      (.getText (find-view activity ::address)))
            port     (Integer. (str (.getText (find-view activity ::port))))
            interval (Integer. (str (.getText (find-view activity ::interval))))]
        (start-send address port interval))
      (stop-send))))

(defactivity com.illiichi.MyActivity
  :key :main
  :on-create
  (fn [this bundle]
    (reset! sensorManager (.getSystemService (*a) Context/SENSOR_SERVICE))
    (on-ui
      (set-content-view! (*a)
        [:linear-layout {:orientation :vertical}
         [:linear-layout {}
          [:text-view {:text "address"}]
          [:edit-text {:text "192.168.0.4" :id ::address}]]
         [:linear-layout {}
          [:text-view {:text "port"}]
          [:edit-text {:text "1234" :id ::port}]]
         [:linear-layout {}
          [:text-view {:text "interval(ms)"}]
          [:edit-text {:text "200" :id ::interval}]]
         [:button {:id "button"
                   :text "ready?" :on-click (fn [_] (toggle-state (*a)))}]]))))

;; (
;; SynthDef("dynenv", {arg val = 0, dur = 1, ch = 0;
;; 	Out.ar(ch, DynEnv.ar(val, dur));
;; }).add
;; )
;; (

;; var controlGroup = Group.new;
;; var soundGroup = Group.new;

;; var rotateBus = Bus.audio(s, 1);
;; var accBus = Bus.audio(s, 1);

;; var acc = Synth("dynenv", [\ch, accBus], controlGroup);
;; var rotate = Synth("dynenv", [\ch, rotateBus], controlGroup);
;; acc.set(\dur, 1/(60 - 2));
;; rotate.set(\dur, 1/(60 - 2));

;; soundGroup.moveAfter(controlGroup);

;; OSCFunc({|msg, time, addr, recvPort|
;; 	var v;
;; 	msg.removeAt(0);
;; 	v = msg.collect({|x| x * x}).sum.sqrt;
;; 	acc.set(\val, v);
;; }, '/acc', nil,1234);

;; OSCFunc({|msg, time, addr, recvPort|
;; 	rotate.set(\val, msg.at(1));
;; }, '/rotate', nil,1234);

;; {PMOsc.ar(440 * In.ar(rotateBus).abs, 10 * In.ar(accBus).abs, 3)}.play(soundGroup)
;; )
