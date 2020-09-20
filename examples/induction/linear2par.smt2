; solve with: viper --treegrammar --onquant 1

(declare-datatypes ((nat 0)) (( (o) (s (p nat)))))

(define-fun-rec P ((x nat) (y nat) (z nat)) Bool
  (match z
    (( o true)
    ( (s z) (P x y z)))))

(prove (forall ((x nat) (y nat)) (P x y y)))
