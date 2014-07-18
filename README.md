# MUD - Musical Universes of Discourse

#### Why?

Overtone is extremely powerful at the cost of verbosity. Live coding requires quite a lot of code.
Snippet expansion can help alleviate but it does hinder the range of expression and clutters the visual
experience for the audience reading along with the code.

#### What?

Focus on immediacy of expression and creation.
MUD is a layer over Overtone to make live composition more power and immediate.

#### Goals

* Its centered around using Supercollider for timing rather than Java.
* Buffers are the default way to express patterns.
* Keep it Clojure, support Clojures fns to manipulate patterns.

## Functionality

## Chords

Use a single synth/inst def to play chords.

```clojure
(def singing-chord-g
  (chord-synth general-purpose-assembly 3 :amp 0.0 :noise-level 0.05 :beat-trg-bus (:beat time/beat-1th) :beat-bus (:count time/beat-1th) :attack 0.1 :release 0.1))
  
(:bufs singing-chord-g) ;; Access all the bufs of a chord.
(:synths singing-chord-g) ;; Access all the running synths of a chord.

(chord-pattern singing-chord-g [[:C3 :E3 :F3]]

(doseq [synth (:synths singing-chord-g)] (ctl synth :amp 0.2))
```

## Madeup Examples

These examples do not exist, they are sketches in code.

```clojure
(use 'mud.core)

(defonce notes-buf (buffer 128))

;;Write a pattern immediately. Supports automatic conversion of degrees or notes.
(pat notes-buf [5 3 7] :minor :F3) ;; Degrees
(pat notes-buf [:A5 :A3 :A7])      ;; Notes
(pat notes-buf [300 400 600])      ;; Frequencies
(pat [notes1-buf notes2-buf notes3-buf] [5 3 7] :minor :F3) ;; Chords


;;Write a pattern on a beat one time (based on main-beat by default)
(pat-at 8 notes-buf [5 3 7] :minor :F3)

;;Keep writing a pattern every nth beat.
(pat-repeat 8 notes-buf #(degrees (shuffle [5 3 7]) :minor :F3))

;;Create a sequencer which specifies samples as well as on/offs
(defonce drums (seqer [kick-s _ kick-s _ snare-s [kick-s snare-s]]))
```

## License

Copyright © 2014 Joseph Wilk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
