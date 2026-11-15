import com.example.starlog2.contracts.Outcome
import com.example.starlog2.contracts.SourceMode
import com.example.starlog2.starlog.journey.ConstellationEdgeKind
import com.example.starlog2.starlog.journey.JourneyLeg
import com.example.starlog2.starlog.journey.JourneyStatus
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.journey.JourneyControlResponse
import com.example.starlog2.starlog.gleo.GleoJourneyCallbackData
import com.example.starlog2.starlog.storage.TransactionalMemoryRepository

fun main() {
    val repository = TransactionalMemoryRepository()
    val controller = JourneyController(repository, { 100L }, { "voice-demo" })
    check(controller.status().snapshot.journeyStatus == "READY")
    val started = controller.start() as JourneyControlResponse.Success
    check(started.snapshot.recordedPointCount == 0)
    check((controller.start() as JourneyControlResponse.Success).record == started.record)
    check(controller.end() is JourneyControlResponse.Asking)
    check(controller.advance() is Outcome.Err)
    controller.start()
    repeat(9) { check(controller.advance() is Outcome.Ok) }
    val ended = controller.end() as JourneyControlResponse.Success
    val completed = checkNotNull(ended.record)
    check(completed.status == JourneyStatus.COMPLETED)
    check(completed.points.size == 9)
    check(completed.points.count { it.leg == JourneyLeg.OUTBOUND && it.createsNewStar } == 5)
    check(completed.points.none { it.leg == JourneyLeg.RETURN && it.createsNewStar })
    check(completed.points.count { it.leg == JourneyLeg.RETURN_NEW && it.createsNewStar } == 1)
    check(completed.edges.count { it.kind == ConstellationEdgeKind.OUTBOUND } == 4)
    check(completed.edges.count { it.kind == ConstellationEdgeKind.BRANCH } == 1)
    check((repository.listVisits(SourceMode.REPLAY) as Outcome.Ok).value.size == 6)
    check((repository.listConstellations(SourceMode.REPLAY) as Outcome.Ok).value.single().first.tripId == completed.id)
    check((controller.end() as JourneyControlResponse.Success).record == completed)
    check(repository.saveJourney(completed.copy(status = JourneyStatus.ACTIVE, completedAtMs = null)) is Outcome.Err)
    val callback = GleoJourneyCallbackData.from(ended)
    check(callback["tripId"] == "voice-demo")
    check(callback["journeyStatus"] == "COMPLETED")
    println("PURE_GLEO_DEMO_CYCLE_PASS points=9 stars=6 outboundEdges=4 branchEdges=1")
}
