package com.lawfirm.law.firm.tenant;

import java.util.Arrays;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FlywayChecksumRepair {

    private static final Logger log = LoggerFactory.getLogger(FlywayChecksumRepair.class);

    private static final Map<String, Map<Integer, Integer>> KNOWN_CHECKSUM_CHANGES =
            Map.ofEntries(
                    Map.entry("1", Map.of(-1689413723, -1651995712)),
                    Map.entry("2", Map.of(521553349, -1320944835)),
                    Map.entry("3", Map.of(-747736188, -951856467)),
                    Map.entry("4", Map.of(99380302, 52288552)),
                    Map.entry("5", Map.of(1947964257, 1991011277)),
                    Map.entry("6", Map.of(-925644842, 960664829)),
                    Map.entry("7", Map.of(-50407477, -1188028560)),
                    Map.entry("8", Map.of(-1838298526, -510831473)),
                    Map.entry("9", Map.of(386827944, -442837776)),
                    Map.entry("23", Map.of(2142235456, 1571331339, -107208586, 1571331339)));

    private FlywayChecksumRepair() {}

    static void repairKnownChecksumChanges(Flyway flyway, String schema) {
        MigrationInfo[] allMigrations = flyway.info().all();
        MigrationInfo[] mismatches =
                Arrays.stream(allMigrations)
                        .filter(info -> info.isApplied() && !info.isChecksumMatching())
                        .toArray(MigrationInfo[]::new);
        if (mismatches.length == 0) {
            return;
        }

        boolean hasFailedMigration =
                Arrays.stream(allMigrations)
                        .anyMatch(
                                info ->
                                        info.getState() == MigrationState.FAILED
                                                || info.getState() == MigrationState.FUTURE_FAILED
                                                || info.getState()
                                                        == MigrationState.MISSING_FAILED);
        if (hasFailedMigration) {
            throw new IllegalStateException(
                    "Flyway history in schema "
                            + schema
                            + " contains a failed migration; automatic repair was not run.");
        }

        boolean hasOtherMetadataDrift =
                Arrays.stream(allMigrations)
                        .filter(info -> info.isApplied())
                        .anyMatch(info -> !info.isDescriptionMatching() || !info.isTypeMatching());
        if (hasOtherMetadataDrift) {
            throw new IllegalStateException(
                    "Flyway history in schema "
                            + schema
                            + " contains description or type drift; automatic repair was not run.");
        }

        for (MigrationInfo info : mismatches) {
            String version =
                    info.getVersion() == null ? "<repeatable>" : info.getVersion().toString();
            Integer appliedChecksum = info.getAppliedChecksum();
            Integer resolvedChecksum = info.getResolvedChecksum();
            Map<Integer, Integer> knownChanges = KNOWN_CHECKSUM_CHANGES.get(version);
            boolean knownChecksumChange =
                    appliedChecksum != null
                            && knownChanges != null
                            && knownChanges.get(appliedChecksum) != null
                            && knownChanges.get(appliedChecksum).equals(resolvedChecksum);
            boolean successfulAppliedMigration =
                    info.getState() == MigrationState.SUCCESS
                            || info.getState() == MigrationState.OUTDATED;
            if (!knownChecksumChange
                    || !successfulAppliedMigration
                    || !info.isDescriptionMatching()
                    || !info.isTypeMatching()) {
                throw new IllegalStateException(
                        "Flyway checksum divergence in schema "
                                + schema
                                + " for migration "
                                + version
                                + " (applied="
                                + appliedChecksum
                                + ", resolved="
                                + resolvedChecksum
                                + ", state="
                                + info.getState()
                                + "). Automatic repair is limited to verified historical edits.");
            }
        }

        log.warn(
                "Flyway: repairing verified historical checksum changes in schema {}: {}",
                schema,
                Arrays.stream(mismatches)
                        .map(
                                info ->
                                        info.getVersion()
                                                + " ("
                                                + info.getAppliedChecksum()
                                                + " -> "
                                                + info.getResolvedChecksum()
                                                + ")")
                        .toList());
        flyway.repair();
    }
}
