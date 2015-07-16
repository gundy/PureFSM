package pure.fsm.core.cleanup;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pure.fsm.core.Transition;
import pure.fsm.core.repository.StateMachineRepository;
import pure.fsm.core.repository.StateMachineRepository.Lock;
import pure.fsm.core.state.FinalState;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CleanUpFinalisedStateMachines {

    private final static Logger LOG = LoggerFactory.getLogger(CleanUpFinalisedStateMachines.class);

    private final StateMachineRepository repository;
    private final Collection<OnCleanupListener> cleanupListeners;
    private final long scheduleFrequency;
    private final TimeUnit scheduleTimeUnit;
    private final long keepFinalised;
    private final ChronoUnit keepFinalisedTimeUnit;
    private final ScheduledExecutorService scheduledExecutorService;

    public CleanUpFinalisedStateMachines(StateMachineRepository repository,
                                         Collection<OnCleanupListener> cleanupListeners,
                                         long scheduleFrequency, TimeUnit scheduleTimeUnit,
                                         long keepFinalised, ChronoUnit keepFinalisedTimeUnit) {
        this.repository = repository;
        this.cleanupListeners = cleanupListeners;
        this.scheduleFrequency = scheduleFrequency;
        this.scheduleTimeUnit = scheduleTimeUnit;
        this.keepFinalised = keepFinalised;
        this.keepFinalisedTimeUnit = keepFinalisedTimeUnit;

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void startScheduler() {
        scheduledExecutorService.scheduleWithFixedDelay(
                this::checkForFinalizedStateMachinesAndCleanupIfRequired,
                0, scheduleFrequency,
                scheduleTimeUnit);
    }

    public void stopScheduler() {
        scheduledExecutorService.shutdown();
    }

    public void checkForFinalizedStateMachinesAndCleanupIfRequired() {
        LOG.info("About to check for outdated finalized state machines.");

        repository.getAllIds().forEach(id -> {
            Transition transition = repository.get(id);
            cleanupStateMachineIfRequired(id, transition);
        });
    }

    @VisibleForTesting
    void cleanupStateMachineIfRequired(String id, Transition transition) {
        if (transition.getState() instanceof FinalState) {
            try {
                Optional<Lock> lock = repository.tryLock(id, 1, SECONDS);
                if (lock.isPresent() && shouldCleanup(lock.get().getLatestTransition())) {
                    LOG.info("unlocking and removing state machine [{}]",
                            lock.get().getLatestTransition().getContext().stateMachineId());

                    cleanupListeners.forEach(l -> l.onCleanup(transition));

                    lock.get().unlockAndRemove();
                }
            } catch (Exception e) {
                LOG.error("Error with sm [" + id + "]", e);
            }
        }
    }

    protected boolean shouldCleanup(Transition transition) {

        return transition.getTransitioned().plus(keepFinalised, keepFinalisedTimeUnit).isBefore(LocalDateTime.now());
    }
}