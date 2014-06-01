(ns cassiopeia.engine.timing
  (:use [overtone.live])
  (:require
   [overtone.synth.timing :as timing]))

(defonce timing-g (group "stars timing" :tgt (foundation-safe-pre-default-group)))

(defonce root-b       (control-bus))
(defonce inv-root-b   (control-bus))
(defonce offset-b     (control-bus))
(defonce pi-offset-b  (control-bus))
(defonce count-b      (control-bus))
(defonce pi-count-b   (control-bus))
(defonce x-b          (control-bus))
(defonce pi-x-b       (control-bus))
(defonce sin-b        (control-bus))
(defonce beat-b       (control-bus))
(defonce x-mul-b      (control-bus))
(defonce beat-count-b (control-bus))

(defonce count-trig-id (trig-id))
(defonce broadcast-count-trig-id (trig-id))

(defsynth root-saw [rate 10 saw-bus 0 inv-saw-bus 0]
  (let [saw (lf-saw:kr rate)]
    (out:kr inv-saw-bus (* -1 saw))
    (out:kr saw-bus saw)))

(defsynth saw-counter [in-bus 0 out-bus 0] (out:kr out-bus (pulse-count:kr (in:kr in-bus))))
(defsynth pi-counter  [counter-bus 0 out-bus 0] (out:kr out-bus (* (* Math/PI 2) (in:kr counter-bus))))

(defsynth offset    [root-bus 0 out-bus 0] (out:kr out-bus (/ (+ 1 (in:kr root-bus)) 2)))
(defsynth pi-offset [root-bus 0 out-bus 0] (out:kr out-bus (* (+ 1 (in:kr root-bus)) Math/PI)))

(defsynth get-beat [beat-b 0 beat-count-b 0 id 0] (send-trig (in:kr beat-b) id (in:kr beat-count-b)))

(defsynth x [count-bus 0 offset-bus 0 out-bus 0 smoothness 0.005]
  (let [cnt (in:kr count-bus)
        off (in:kr offset-bus)]
    (out:kr out-bus (lag (+ cnt off) smoothness))))

(defsynth pi-x [pi-count-bus 0 pi-offset-bus 0 out-bus 0 smoothness 0.005]
  (let [cnt (in:kr pi-count-bus)
        off (in:kr pi-offset-bus)]
    (out:kr out-bus (lag (+ cnt off) smoothness))))

(defsynth sin-x [pi-x-bus 0 out-bus 0 mul 1 smoothness 0]
  (let [px (in:kr pi-x-bus)]
    (out:kr out-bus (lag (sin (* px mul)) smoothness))))

(defsynth buf-phasor [saw-x-b 0 out-bus 0 buf 0 loop? 0]
  (let [n-samps (buf-frames buf)
        phase   (* n-samps (in:kr saw-x-b))]
    (out:kr out-bus (buf-rd:kr 1 buf phase :loop loop? :interpolation 1))))

(defsynth saw-x-pulser [out-bus 0 freq-mul 1 smoothness 0 x-bus (:id x-b)]
  (let [s (+ 1 (* -2 (mod (* freq-mul (in:kr x-bus)) 1)))]
    (out:kr out-bus (trig1 s 0.01))))

(defsynth saw-x [out-bus 0 freq-mul 1 mul 1 add 0 x-bus (:id x-b)]
  (out:kr out-bus (lag (mul-add (mod (* freq-mul (in:kr x-bus)) 1) mul add))))

(defsynth x-mul [x-bus 0 out-bus 0 mul 1]
  (let [x (in:kr x-bus)]
    (out:kr out-bus (* x mul))))

(defsynth broadcast-beat-trigger
  [in-bus 0  beat-bus 0 beat-count-bus 0 trig-id 0 rate 10]
  (let [src  (in:kr in-bus)
        beat (in:kr beat-bus)
        cnt  (in:kr beat-count-bus)]
    (send-reply (* -1 src) "/global/beat-trigger/" [beat cnt] trig-id)))

(def current-beat (atom 30))

(defonce root-s (root-saw [:head timing-g] :saw-bus root-b :inv-saw-bus inv-root-b :rate 2))
(defonce count-s (saw-counter [:after root-s] :out-bus count-b :in-bus inv-root-b))
(defonce pi-count-s (pi-counter [:after root-s] :out-bus pi-count-b :counter-bus count-b))
(defonce offset-s (offset [:after count-s] :root-bus root-b :out-bus offset-b))
(defonce pi-offset-s (pi-offset [:after pi-count-s] :out-bus pi-offset-b :root-bus root-b))
(defonce x-s (x [:after offset-s] :count-bus count-b :offset-bus offset-b :out-bus x-b))
(defonce pi-x-s (pi-x [:after offset-s] :pi-count-bus pi-count-b :pi-offset-bus pi-offset-b :out-bus pi-x-b))
(defonce sin-x-s (sin-x [:after pi-x-s] :pi-x-bus pi-x-b :mul 1 :out-bus sin-b))
(defonce x-mul-s (x-mul  [:after x-s] :x-bus x-b :mul 0.1 :out-bus x-mul-b))
(defonce divider-s (timing/divider [:after root-s] :div @current-beat :in-bus inv-root-b :out-bus beat-b))
(defonce counter-s (timing/counter [:after divider-s] :in-bus beat-b :out-bus beat-count-b))
(defonce get-beat-s (get-beat [:after divider-s] beat-b beat-count-b count-trig-id))

(defonce b-trigger (broadcast-beat-trigger [:after (foundation-monitor-group)]
                                           :in-bus root-b
                                           :beat-bus beat-b
                                           :beat-count-bus beat-count-b
                                           :trig-id broadcast-count-trig-id))

(defn beat-bus [freq-mul]
  (let [t-id (trig-id)
        g    (group (str "Beat Bus " t-id " - x" freq-mul) :after x-s)
        b    (control-bus  (str "beat-bus x" freq-mul))
        c    (control-bus  (str "beat-cnt-bus x" freq-mul))
        p    (saw-x-pulser [:head g] b freq-mul)
        cs   (saw-counter  [:after p] b c)
        gs   (get-beat     [:after cs] b c t-id)]
    (with-meta {:beat b
                :count c
                :group g
                :saw-x-pulser-s p
                :saw-counter-s cs
                :beat-trig-s gs
                :trig-id t-id}
            {:type ::beat-bus})))

(defn beat-bus? [o] (isa? (type o) ::beat-bus))

(defonce main-beat (beat-bus 1))

(defonce beat-8x (beat-bus 8))
(defonce beat-7x (beat-bus 7))
(defonce beat-6x (beat-bus 6))
(defonce beat-5x (beat-bus 5))
(defonce beat-4x (beat-bus 4))
(defonce beat-3x (beat-bus 3))
(defonce beat-2x (beat-bus 2))

(defonce beat-1th main-beat)
(defonce beat-2th (beat-bus 1/2))
(defonce beat-3th (beat-bus 1/3))
(defonce beat-4th (beat-bus 1/4))
(defonce beat-5th (beat-bus 1/5))
(defonce beat-6th (beat-bus 1/6))
(defonce beat-7th (beat-bus 1/7))
(defonce beat-8th (beat-bus 1/8))
(defonce beat-12th (beat-bus 1/12))
(defonce beat-16th (beat-bus 1/16))
