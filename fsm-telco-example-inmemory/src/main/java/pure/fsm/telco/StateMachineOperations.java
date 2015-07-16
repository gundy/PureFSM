package pure.fsm.telco;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pure.fsm.core.Transition;
import pure.fsm.core.cleanup.CleanUpFinalisedStateMachines;
import pure.fsm.core.repository.StateMachineRepository;
import pure.fsm.core.event.Event;
import pure.fsm.core.template.StateMachineTemplate;
import pure.fsm.core.timeout.TimeoutTicker;
import pure.fsm.repository.inmemory.InMemoryStateMachineRepository;
import pure.fsm.telco.state.InitialState;
import pure.fsm.telco.state.TelcoStateFactory;

import java.time.temporal.ChronoUnit;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static pure.fsm.core.StateFactoryRegistration.registerStateFactory;
import static pure.fsm.core.template.DefaultStateMachineCallable.handleWithTransition;
import static pure.fsm.telco.TelcoRechargeContext.initialTelcoRecharge;

class StateMachineOperations {

    private final static Logger LOG = LoggerFactory.getLogger(StateMachineOperations.class);

    final StateMachineRepository repository = new InMemoryStateMachineRepository();
    final StateMachineTemplate template = new StateMachineTemplate(repository, newArrayList());
    final TelcoStateFactory stateFactory = new TelcoStateFactory();
    final TimeoutTicker timeoutTicker = new TimeoutTicker(repository, template, 1, SECONDS);
    final CleanUpFinalisedStateMachines cleaner = new CleanUpFinalisedStateMachines(repository, newArrayList(), 5, SECONDS, 5, ChronoUnit.SECONDS);

    public Transition getStateMachine(String stateMachineId) {
        return repository.get(stateMachineId);
    }

    public void scheduleEventOnThread(String stateMachineId, final Event event) {
        new Thread(() -> template.tryWithLock(stateMachineId,
                handleWithTransition((prevTransition, stateMachine) -> stateMachine.handleEvent(prevTransition, event)))).start();
    }

    public String createStateMachineInInitialState() {
        registerStateFactory(stateFactory);
        return repository.create(
                stateFactory.getStateByClass(InitialState.class), TelcoStateFactory.class, newArrayList(initialTelcoRecharge()));
    }

    public void logCurrentState(String stateMachineId) {
        LOG.info("Ending.... current state for [{}] is: [{}]", stateMachineId,
                getStateMachine(stateMachineId).getState().getClass().getSimpleName());
    }

    public StateMachineRepository getRepository() {
        return repository;
    }

    public StateMachineTemplate getTemplate() {
        return template;
    }

    public TelcoStateFactory getStateFactory() {
        return stateFactory;
    }

    public TimeoutTicker getTimeoutTicker() {
        return timeoutTicker;
    }

    public CleanUpFinalisedStateMachines getCleaner() {
        return cleaner;
    }
}
