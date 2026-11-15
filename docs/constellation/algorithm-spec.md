# Submission constellation algorithm

The submission path is a deterministic `REPLAY`; it is never labelled as live vehicle collection.
All nine raw stops are fed through the production `VisitDetector` with a logical ten-minute
P-gear stay. UI delay may be accelerated, but detector time and persisted visit duration are not.

The outbound leg creates five ordered stars: Gumi, Daegu, Gyeongju, Ulsan, and Busan. On return,
Ulsan, Gyeongju, and Gumi reuse their existing stars. Gimhae creates one `RETURN_NEW` star. The
stored constellation therefore contains four `OUTBOUND` edges and one Busan-to-Gimhae `BRANCH`.

The journey `id` is the persisted `tripId`; its `constellationId` is
`constellation-trip-<tripId>`. Completion is rejected until all nine points exist. Repeating
completion returns the same completed record, and persistence rejects `COMPLETED -> ACTIVE`.

Rotation frames use `MelodyRotationMapper`: the displayed normalized angle is passed directly to
`MelodyGenerator.preview`. Automatic optimization runs after that preview path and animates each
intermediate displayed angle through the same mapper.
