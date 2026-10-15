package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class FlywayChecksumRepairTest {

    @Test
    void repairsRecognizedSuccessfulV23ChecksumDrift() {
        Flyway flyway = flywayWithMismatch("23", 2142235456, 1571331339, MigrationState.OUTDATED);

        FlywayChecksumRepair.repairKnownChecksumChanges(flyway, "tenant_tania");

        verify(flyway).repair();
    }

    @Test
    void repairsRecognizedIntermediateV23ChecksumDrift() {
        Flyway flyway = flywayWithMismatch("23", -107208586, 1571331339, MigrationState.OUTDATED);

        FlywayChecksumRepair.repairKnownChecksumChanges(flyway, "tenant_tania");

        verify(flyway).repair();
    }

    @Test
    void refusesUnknownChecksumDrift() {
        Flyway flyway = flywayWithMismatch("23", 12345, 1571331339, MigrationState.OUTDATED);

        assertThrows(
                IllegalStateException.class,
                () -> FlywayChecksumRepair.repairKnownChecksumChanges(flyway, "tenant_tania"));

        verify(flyway, never()).repair();
    }

    @Test
    void refusesToRepairFailedMigration() {
        Flyway flyway = flywayWithMismatch("23", 2142235456, 1571331339, MigrationState.FAILED);

        assertThrows(
                IllegalStateException.class,
                () -> FlywayChecksumRepair.repairKnownChecksumChanges(flyway, "tenant_tania"));

        verify(flyway, never()).repair();
    }

    @Test
    void refusesToRepairDescriptionDrift() {
        Flyway flyway = flywayWithMismatch("23", 2142235456, 1571331339, MigrationState.OUTDATED);
        MigrationInfo info = flyway.info().all()[0];
        when(info.isDescriptionMatching()).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> FlywayChecksumRepair.repairKnownChecksumChanges(flyway, "tenant_tania"));

        verify(flyway, never()).repair();
    }

    private static Flyway flywayWithMismatch(
            String version, int appliedChecksum, int resolvedChecksum, MigrationState state) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo info = mock(MigrationInfo.class);

        when(flyway.info()).thenReturn(infoService);
        when(infoService.all()).thenReturn(new MigrationInfo[] {info});
        when(info.isApplied()).thenReturn(true);
        when(info.isChecksumMatching()).thenReturn(false);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(info.getAppliedChecksum()).thenReturn(appliedChecksum);
        when(info.getResolvedChecksum()).thenReturn(resolvedChecksum);
        when(info.getState()).thenReturn(state);
        when(info.isDescriptionMatching()).thenReturn(true);
        when(info.isTypeMatching()).thenReturn(true);
        return flyway;
    }
}
