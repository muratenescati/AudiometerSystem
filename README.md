# AudiometerSystem
End-to-end clinical audiometer system integrating Proteus hardware simulation with a Java/Swing app via serial link. Features a state-driven controller, Hughson-Westlake threshold engine, and real-time audiogram rendering for six-frequency tests.

# Audiometer-System-Modernization

This repository contains the refactored, high-performance, and standard-compliant architecture of a biomedical Audiometer system. The project focuses on transitioning a legacy stateful codebase into a modern, **functional, and robust architecture** that adheres to **IEC 60645-1** biomedical standards.

## How to run
To launch the clinical application and start the test, execute the following file:
`/src/audiometry/AudiometryIEC60645Test`

##  Key Features

* **Functional Paradigm:** Decoupled medical algorithms into pure, side-effect-free functions using immutable `record` structures.
* **Modern Concurrency:** Replaced CPU-blocking loops with non-blocking `Stream API` and `CompletableFuture` for asynchronous serial data processing.
* **Robust Error Handling:** Eliminated null-checks and runtime exceptions by adopting the `Optional` pattern throughout the `SerialService` and `AudiometryEngine`.
* **Automated Validation:** Implemented **Property-Based Testing (using jqwik)** to verify logic consistency across 524 randomized and edge-case scenarios, ensuring a guaranteed 100% compliance with decibel safety boundaries.
* **Standard Compliance:** Strictly enforces IEC 60645-1 safety protocols for frequency and intensity management.

##  Tech Stack

* **Language:** Java 19+
* **Testing Framework:** JUnit 5, jqwik (Property-Based Testing)
* **Hardware Communication:** jSerialComm
* **Architecture:** Functional Programming (FP), Immutability, Asynchronous Data Pipelines

##  Validation & Testing

The system has been statically proven to be robust via property-based testing. The test suite validates:
1. **Safety Boundaries:** Intensity levels never fall below -10 dB or exceed 120 dB HL.
2. **Logic Consistency:** Confirms the accuracy of the Hughson-Westlake threshold search algorithm (+5/-10 dB rules).
3. **Stability:** Achieved a successful `Exit code 0` across exhaustive test runs.

##  Project Structure

* `/src/audiometry`: Contains the pure functions and immutable data models (`TestState`, `StepResult`).
* `/src/gui`: Modernized UI with reactive event listeners.
* `/src/lib`: Manual dependency management for test engines and hardware drivers.

##  Requirements

- **JDK:** 17 or higher
- **IDE:** IntelliJ IDEA (Recommended)
- **Dependencies:** Ensure all `.jar` files in `/src/lib` are added to the Project Classpath.

---
## Documentation

- You can review the Project Report (PDF) for all architectural details of the system, testing stages, and signal analysis results. /docs/AudiometerSystem_Report

