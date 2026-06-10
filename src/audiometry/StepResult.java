package audiometry;

import java.util.Optional;

/**
 * AudiometryEngine.nextStep'in donus tipi.
 *
 *  - nextState:        bir sonraki sunum icin guncel TestState (her zaman var)
 *  - threshold:        bu sunumda esik bulunduysa kaydedilecek sonuc, yoksa empty
 *
 * Pure-function akisi: yan etki yok, tum karar TestState + heard girisinden uretilir.
 */
public record StepResult(
        TestState nextState,
        Optional<ThresholdResult> threshold
) {}
