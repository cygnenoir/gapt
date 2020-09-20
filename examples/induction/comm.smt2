; The qtys is needed for qmaxsat, otherwise we get a grammar corresponding to a
; quantified lemma:

; solve with: viper --treegrammar --cansolsize 2 2

(declare-datatypes ((nat 0)) (( (o) (s (p nat)))))
(define-fun-rec plus ((x nat) (y nat)) nat
  (match y
    (( o x)
    ( (s y1) (s (plus x y1))))))
(assert (forall ((x nat))
  (= (plus o x) x)))
(assert (forall ((x nat) (y nat))
  (= (plus (s x) y) (s (plus x y)))))
(declare-const k nat)
(prove (forall ((x nat))
  (= (plus x k) (plus k x))))
