package ir.bita.esm.service.handler;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.k8s.KubernetesDeployer;
import ir.bita.esm.service.command.ChangeServicePhaseCommand;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeServicePhaseHandlerTest {

    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private KubernetesDeployer kubernetesDeployer;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private ChangeServicePhaseHandler handler;

    @Test
    void shouldDeployWhenTransitioningFromDraftToTest() {
        ServiceCollection collection = ServiceCollection.builder()
                .name("payment")
                .basePath("/esb/payment")
                .build();

        ServiceEntity service = ServiceEntity.builder()
                .name("PaymentService")
                .serviceVersion("1.0")
                .collection(collection)
                .phase(ServicePhase.DRAFT)
                .build();

        when(serviceRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(service));
        when(serviceRepository.save(any(ServiceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeServicePhaseCommand cmd = ChangeServicePhaseCommand.builder()
                .serviceId(10L)
                .newPhase(ServicePhase.TEST)
                .build();

        handler.handle(cmd);

        verify(kubernetesDeployer, times(1)).deployService(any(ServiceEntity.class));
        verify(serviceRepository, times(1)).save(any(ServiceEntity.class));
    }
}
