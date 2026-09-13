package studio.environment.supervisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Postgres16ClientWitnessCleanupOracleTest {
    @Test void exactMissingContainerAndVolumeAreConclusiveAbsence() {
        assertDoesNotThrow(() -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "container", "es-owned-container", 1, "Error response from daemon: No such container: es-owned-container\n"));
        assertDoesNotThrow(() -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "es-owned-volume", 1, "Error response from daemon: No such volume: es-owned-volume\n"));
    }

    @Test void presentResourceIsRejected() {
        var failure = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "es-owned-volume", 0, "[{\"Name\":\"es-owned-volume\"}]"));
        assertTrue(failure.getMessage().contains("STILL_PRESENT"));
    }

    @Test void daemonAndTransportFailuresAreNotAbsenceEvidence() {
        var failure = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "container", "es-owned-container", 1, "Cannot connect to the Docker daemon at unix:///tmp/missing.sock. Is the docker daemon running?"));
        assertTrue(failure.getMessage().contains("ABSENCE_UNCERTAIN"));
    }

    @Test void differentMissingResourceDoesNotProveOwnedResourceAbsence() {
        var failure = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "es-owned-volume", 1, "Error response from daemon: No such volume: other-volume\n"));
        assertTrue(failure.getMessage().contains("ABSENCE_UNCERTAIN"));
    }

    @Test void prefixResourceNameDoesNotProveOwnedResourceAbsence() {
        var volume = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "independent-mock-volume", 1, "Error response from daemon: get independent-mock-volume-different: no such volume\n"));
        assertTrue(volume.getMessage().contains("ABSENCE_UNCERTAIN"));
        var container = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "container", "independent-mock-container", 1, "Error response from daemon: No such container: independent-mock-container-different\n"));
        assertTrue(container.getMessage().contains("ABSENCE_UNCERTAIN"));
    }

    @Test void ownedNameMentionedOutsideMissingDiagnosticDoesNotProveAbsence() {
        var failure = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "independent-mock-volume", 1,
                "cleanup target independent-mock-volume\nError response from daemon: No such volume: other-volume\n"));
        assertTrue(failure.getMessage().contains("ABSENCE_UNCERTAIN"));
    }

    @Test void caseFoldedResourceNameDoesNotProveOwnedResourceAbsence() {
        var volume = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "independent-mock-volume", 1,
                "Error response from daemon: get INDEPENDENT-MOCK-VOLUME: no such volume\n"));
        assertTrue(volume.getMessage().contains("ABSENCE_UNCERTAIN"));

        var container = assertThrows(AssertionError.class, () -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "container", "independent-mock-container", 1,
                "Error response from daemon: No such container: INDEPENDENT-MOCK-CONTAINER\n"));
        assertTrue(container.getMessage().contains("ABSENCE_UNCERTAIN"));
    }

    @Test void diagnosticProseCaseCanVaryWhenResourceIdentityMatchesExactly() {
        assertDoesNotThrow(() -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "container", "independent-mock-container", 1,
                "Error response from daemon: no such container: independent-mock-container\n"));
        assertDoesNotThrow(() -> Postgres16ClientWitnessTest.requireMissingDockerResource(
                "volume", "independent-mock-volume", 1,
                "Error response from daemon: GET independent-mock-volume: NO SUCH VOLUME\n"));
    }
}
